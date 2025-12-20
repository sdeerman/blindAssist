package com.example.test_android_dev;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 根据功能类型调度对应的业务流程
 */
public class FeatureRouter {
    private static final String TAG = "FeatureRouter";
    private static FeatureRouter instance;
    private VoiceManager voiceManager;
    private NetworkClient networkClient;
    private ImageCaptureManager imageManager;
    private ScheduledExecutorService obstacleExecutor;
    private String currentSessionId = "";

    private FeatureRouter() {}

    public static synchronized FeatureRouter getInstance() {
        if (instance == null) {
            instance = new FeatureRouter();
        }
        return instance;
    }

    public void init(Context context) {
        voiceManager = VoiceManager.getInstance();
        networkClient = NetworkClient.getInstance();
        imageManager = ImageCaptureManager.getInstance();
    }

    public void route(FeatureType feature) {
        Log.d(TAG, "Routing to: " + feature.name());
        // 停止之前的后台任务，避免资源冲突
        if (obstacleExecutor != null && !obstacleExecutor.isShutdown()) {
            obstacleExecutor.shutdown();
        }

        switch (feature) {
            case NAVIGATION:
                startNavigationFlow();
                break;
            case OBSTACLE_AVOIDANCE:
                startObstacleAvoidance(false);
                break;
            case QA_VOICE:
                startQAFlow();
                break;
            case OCR:
                startOCRFlow();
                break;
            case SCENE_DESCRIPTION:
                startSceneDescriptionFlow();
                break;
            default:
                voiceManager.speak("抱歉，该功能暂未实现。");
                break;
        }
    }

    private void startNavigationFlow() {
        voiceManager.speakImmediate("进入导航模式。要去哪里？", () -> {
            voiceManager.startListening(new VoiceManager.VoiceCallback() {
                @Override
                public void onResult(String text) {
                    // 此处简化，直接用识别结果作为目的地请求导航
                    requestRoute(text);
                }

                @Override
                public void onError(String error) {
                    voiceManager.speak("没有听到您的声音，已退出导航模式。");
                }
            });
        });
    }

    private void requestRoute(String destination) {
        networkClient.requestNavigation(0, 0, 0, 0, destination, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                voiceManager.speak("获取导航路径失败。");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // 实际应解析路线步骤并分步播报
                voiceManager.speak("找到路线，开始导航。第一步：向前直行。导航时将自动开启避障。");
                startObstacleAvoidance(true); // 导航时自动开启后台避障
            }
        });
    }

    private void startObstacleAvoidance(boolean isBackground) {
        if (!isBackground) voiceManager.speak("实时避障已开启。");

        networkClient.openObstacleWebSocket(new WebSocketListener() {
            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                // 实际应解析JSON并根据障碍物类型播报
                voiceManager.speakImmediate("注意，前方有障碍物。");
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure", t);
                // 仅在非后台模式下播报错误
                if (!isBackground) {
                    voiceManager.speak("避障连接中断。");
                }
            }
        });

        if (obstacleExecutor != null && !obstacleExecutor.isShutdown()) obstacleExecutor.shutdown();
        obstacleExecutor = Executors.newSingleThreadScheduledExecutor();
        obstacleExecutor.scheduleAtFixedRate(() -> {
            byte[] frame = imageManager.captureCurrentFrame();
            if (frame != null) {
                networkClient.sendFrameViaWS(frame);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void startQAFlow() {
        voiceManager.speakImmediate("我是您的智能助理，请提问。", () -> {
            voiceManager.startListening(new VoiceManager.VoiceCallback() {
                @Override
                public void onResult(String text) {
                    askQuestionToServer(text);
                }

                @Override
                public void onError(String error) {
                    voiceManager.speak("没有听到您的问题，请重试。");
                }
            });
        });
    }

    private void askQuestionToServer(String question) {
        networkClient.askQuestion(question, currentSessionId, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                voiceManager.speak("网络开小差了，请稍后再问。");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonString = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonString);
                        String answer = jsonObject.optString("answer", "无法理解回答，请稍后再试。");
                        // 播报完答案后，可以再次提问，形成多轮对话
                        voiceManager.speakImmediate(answer, () -> startQAFlow());
                    } catch (JSONException | IOException e) {
                        Log.e(TAG, "Failed to parse QA response", e);
                        voiceManager.speak("解析回答失败。");
                    }
                } else {
                    voiceManager.speak("服务器返回错误，请稍后再试。");
                }
            }
        });
    }

    private void startOCRFlow() {
        voiceManager.speakImmediate("请将手机对准文字，保持稳定，两秒后自动识别。", () -> {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                byte[] photo = imageManager.captureHighResFrame();
                if (photo == null) {
                    voiceManager.speak("拍照失败，请重试。");
                    return;
                }
                networkClient.uploadVisionRequest("ocr", photo, new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        voiceManager.speak("识别失败，请检查网络。");
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        // 实际应解析响应
                        voiceManager.speak("识别到文字：这是一段示例文字。");
                    }
                });
            }, 2000);
        });
    }

    private void startSceneDescriptionFlow() {
        voiceManager.speakImmediate("正在观察周围环境，请稍候。", () -> {
            byte[] photo = imageManager.captureHighResFrame();
            if (photo == null) {
                voiceManager.speak("拍照失败，请重试。");
                return;
            }
            networkClient.uploadVisionRequest("scene", photo, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    voiceManager.speak("获取场景描述失败。");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    // 实际应解析响应
                    voiceManager.speak("您面前是一个公园入口，左侧有长椅。");
                }
            });
        });
    }
}
