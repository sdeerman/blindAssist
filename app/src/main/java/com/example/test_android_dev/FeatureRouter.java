package com.example.test_android_dev;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 功能路由，以用户交互体验为先，同时遵循 interface.md 规范
 */
public class FeatureRouter implements NetworkClient.VisionStreamListener {
    private static final String TAG = "FeatureRouter";
    private static FeatureRouter instance;

    private VoiceManager voiceManager;
    private NetworkClient networkClient;
    private ImageCaptureManager imageManager;

    private final String sessionId = UUID.randomUUID().toString();
    private boolean isVisionStreamConnected = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        connectToVisionStreamIfNeeded(feature);
        imageManager.stopVideoStream();

        switch (feature) {
            case OBSTACLE_AVOIDANCE: startObstacleAvoidanceFlow(); break;
            case OCR: startOCRFlow(); break;
            case SCENE_DESCRIPTION: startSceneDescriptionFlow(); break;
            case QA_VOICE: startQAFlow(); break;
            case NAVIGATION: startNavigationFlow(); break;
            default: voiceManager.speak("抱歉，该功能暂未实现。"); break;
        }
    }

    // --- Restoring Interactive Camera Flows ---

    private void startOCRFlow() {
        voiceManager.speakImmediate("好的，正在为您识别文字...", () -> {
            imageManager.captureHighResFrame(new ImageCaptureManager.ImageCaptureCallback() {
                @Override
                public void onImageCaptured(byte[] imageBytes) {
                    networkClient.uploadVisionFrame(imageBytes, "ocr", 1, new Callback() {
                        @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { voiceManager.speak("上传失败，请检查网络"); }
                        @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                            if (!response.isSuccessful()) { voiceManager.speak("上传失败，服务器错误"); }
                        }
                    });
                }
                @Override public void onError(String error) { voiceManager.speak("拍照失败: " + error); }
            });
        });
    }

    private void startSceneDescriptionFlow() {
        voiceManager.speakImmediate("好的，正在为您观察周边环境...", () -> {
            imageManager.captureHighResFrame(new ImageCaptureManager.ImageCaptureCallback() {
                @Override
                public void onImageCaptured(byte[] imageBytes) {
                    networkClient.uploadVisionFrame(imageBytes, "scene_description", 1, new Callback() {
                        @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { voiceManager.speak("上传失败，请检查网络"); }
                        @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                            if (!response.isSuccessful()) { voiceManager.speak("上传失败，服务器错误"); }
                        }
                    });
                }
                @Override public void onError(String error) { voiceManager.speak("拍照失败: " + error); }
            });
        });
    }

    // --- Restoring Interactive Voice Flows ---

    private void startQAFlow() {
        voiceManager.speakImmediate("我是您的智能助理，请提问。", () -> {
            mainHandler.postDelayed(() -> {
                voiceManager.startListening(new VoiceManager.VoiceCallback() {
                    @Override public void onResult(String text) {
                        Log.i(TAG, "用户提问: " + text);
                        askQuestionToServer(text);
                    }
                    @Override public void onError(String error) { voiceManager.speak("没有听到您的问题"); }
                });
            }, 200);
        });
    }

    private void askQuestionToServer(String question) {
        networkClient.askQuestion(question, sessionId, new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { voiceManager.speak("网络开小差了"); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    voiceManager.speak("服务器出错了");
                    return;
                }
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    String answer = json.optString("answer", "无法回答您的问题");
                    voiceManager.speak(answer, () -> voiceManager.speak("您可以继续提问"));
                } catch (Exception e) {
                    voiceManager.speak("解析回答失败");
                }
            }
        });
    }

    private void startNavigationFlow() {
        voiceManager.speakImmediate("进入导航模式。要去哪里？", () -> {
            mainHandler.postDelayed(() -> {
                voiceManager.startListening(new VoiceManager.VoiceCallback() {
                    @Override public void onResult(String text) {
                        Log.i(TAG, "导航目的地: " + text);
                        requestRoute(text);
                    }
                    @Override public void onError(String error) { voiceManager.speak("没有听到您的声音"); }
                });
            }, 200);
        });
    }

    private void requestRoute(String destination) {
        networkClient.requestNavigation(destination, new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { voiceManager.speak("获取导航失败"); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    voiceManager.speak("规划路线失败");
                    return;
                }
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray steps = json.optJSONArray("voiceSteps");
                    if (steps == null || steps.length() == 0) {
                        voiceManager.speak("未能规划出有效路线");
                        return;
                    }
                    Queue<String> stepsQueue = new LinkedList<>();
                    for(int i = 0; i < steps.length(); i++) stepsQueue.offer(steps.getString(i));
                    playNaviSteps(stepsQueue);
                } catch (Exception e) {
                    voiceManager.speak("解析导航数据失败");
                }
            }
        });
    }
    
    private void playNaviSteps(Queue<String> steps) {
        String nextStep = steps.poll();
        if (nextStep != null) {
            voiceManager.speak(nextStep, () -> playNaviSteps(steps));
        } else {
            voiceManager.speak("导航结束。");
        }
    }

    // --- Vision Stream and Other existing logic ---

    private void startObstacleAvoidanceFlow() {
        voiceManager.speak("实时避障已开启");
        imageManager.startVideoStream(frame -> {
            if (isVisionStreamConnected) {
                networkClient.sendVisionStreamFrame(frame);
            }
        });
    }
    
    public void destroy() {
        networkClient.closeVisionStream();
    }

    private void connectToVisionStreamIfNeeded(FeatureType feature) {
        if (feature == FeatureType.OCR || feature == FeatureType.SCENE_DESCRIPTION || feature == FeatureType.OBSTACLE_AVOIDANCE) {
            if (!isVisionStreamConnected) {
                networkClient.connectVisionStream(feature.name().toLowerCase(), sessionId, this);
            }
        }
    }

    @Override
    public void onConnected() {
        isVisionStreamConnected = true;
        voiceManager.speak("视觉服务已连接");
    }

    @Override
    public void onInstruction(String jsonMessage) {
        try {
            JSONObject json = new JSONObject(jsonMessage);
            String type = json.optString("type", "");
            JSONObject data = json.optJSONObject("data");
            if (data == null) return;
            switch (type) {
                case "ocr_result": voiceManager.speak("识别结果：" + data.optString("text", "")); break;
                case "scene_description_result": voiceManager.speak(data.optString("description", "")); break;
                case "obstacle_warning": voiceManager.speakImmediate(data.optString("message", "")); break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse WebSocket instruction", e);
        }
    }

    @Override
    public void onDisconnected(String reason) {
        isVisionStreamConnected = false;
        voiceManager.speak("视觉服务已断开");
    }
}
