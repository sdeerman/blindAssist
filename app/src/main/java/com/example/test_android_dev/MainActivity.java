package com.example.test_android_dev;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 应用入口 Activity - 新架构
 * - 界面只有一个“按住说话”按钮
 * - 管理核心模块的生命周期
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQ_PERMISSIONS = 1001;

    private VoiceManager voiceManager;
    private NetworkClient networkClient;
    private String sessionId = "session-" + System.currentTimeMillis();
    private VoiceManager.VoiceCallback currentVoiceCallback;
    private Button voiceButton;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestBasicPermissions();
        initCoreManagers();

        voiceButton = findViewById(R.id.voice_button);
        TextView statusText = findViewById(R.id.status_text);

        voiceButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 播放按下动画
                    Animation pressAnim = AnimationUtils.loadAnimation(this, R.anim.button_press);
                    voiceButton.startAnimation(pressAnim);

                    // 按下时立即启动语音识别，并给出视觉反馈
                    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                        // 提供备用文本输入方案
                        showTextInputDialog();
                        return true;
                    }

                    voiceButton.setText("正在听...");
                    voiceButton.setBackground(getDrawable(R.drawable.btn_speak_background));
                    statusText.setText("请说话...");

                    currentVoiceCallback = new VoiceManager.VoiceCallback() {
                        @Override
                        public void onResult(String text) {
                            voiceButton.setText("按住说话");
                            voiceButton.setBackground(getDrawable(R.drawable.btn_speak_material));
                            statusText.setText("处理中...");
                            currentVoiceCallback = null;
                            handleVoiceResult(text);
                        }

                        @Override
                        public void onError(String error) {
                            voiceButton.setText("按住说话");
                            voiceButton.setBackground(getDrawable(R.drawable.btn_speak_material));
                            statusText.setText("请重试");
                            currentVoiceCallback = null;
                            voiceManager.speak("没有听到声音，请重试");
                        }
                    };
                    voiceManager.startListening(currentVoiceCallback);
                    v.performClick();
                    break;
                case MotionEvent.ACTION_UP:
                    // 播放释放动画
                    Animation releaseAnim = AnimationUtils.loadAnimation(this, R.anim.button_release);
                    voiceButton.startAnimation(releaseAnim);
                    break;
            }
            return true;
        });

        voiceManager.speakImmediate("欢迎使用，请按住屏幕中央的按钮对我说话");
    }

    private void handleVoiceResult(String text) {
        Log.i(TAG, "识别结果: " + text);
        voiceManager.speak("好的，正在处理您的指令: " + text);

        // Use the new sendVoiceText method that follows the interface specification
        networkClient.sendVoiceText(text, null, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                voiceManager.speak("网络出错了，请稍后再试");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                 try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);

                        // Parse the response according to interface.md specification
                        String feature = json.optString("feature", "UNKNOWN");
                        String detail = json.optString("detail", "");
                        String prompt = json.optString("prompt", "");

                        // For now, just speak the prompt or detail
                        String responseText = !prompt.isEmpty() ? prompt : (!detail.isEmpty() ? detail : "无法处理您的指令");
                        voiceManager.speak(responseText);
                    } else {
                         voiceManager.speak("服务器返回了错误");
                    }
                 } catch (Exception e) {
                     Log.e(TAG, "Failed to parse response", e);
                 }
            }
        });
    }

    private void handleSdkCommand(String jsonMessage) {
        try {
            JSONObject json = new JSONObject(jsonMessage);
            String type = json.optString("type", "");
            String sessionId = json.optString("sessionId", "");
            String taskId = json.optString("taskId", "");
            JSONObject dataJson = json.optJSONObject("data");

            if ("control".equals(type) && dataJson != null) {
                // Convert JSON to Map for AutoGLMService
                Map<String, Object> actionMap = JsonUtils.toMap(dataJson);

                AutoGLMService service = AutoGLMService.getInstance();
                boolean success = service.executeAction(actionMap);

                // Report execution result back to server
                String actionType = (String) actionMap.get("action");
                String errorMessage = success ? null : "执行失败";

                networkClient.reportSdkExecutionResult(sessionId, taskId, actionType, success, errorMessage);

                if (success) {
                    Log.i(TAG, "SDK command executed successfully: " + actionType);
                } else {
                    Log.e(TAG, "SDK command execution failed: " + actionType);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle SDK command", e);
            voiceManager.speak("AutoGLM指令处理失败");
        }
    }

    private void initCoreManagers() {
        voiceManager = VoiceManager.getInstance();
        voiceManager.init(getApplicationContext());
        networkClient = NetworkClient.getInstance();
        ImageCaptureManager.getInstance().init(this, this);

        // Connect to SDK WebSocket for AutoGLM control
        networkClient.connectSdkWebSocket(sessionId, new NetworkClient.SdkExecutionListener() {
            @Override
            public void onSdkCommand(String jsonMessage) {
                handleSdkCommand(jsonMessage);
            }

            @Override
            public void onSdkConnected() {
                Log.i(TAG, "SDK WebSocket connected");
                voiceManager.speak("AutoGLM控制服务已连接");
            }

            @Override
            public void onSdkDisconnected(String reason) {
                Log.w(TAG, "SDK WebSocket disconnected: " + reason);
                voiceManager.speak("AutoGLM控制服务已断开");
            }
        });
    }

    private void requestBasicPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET
        };
        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                voiceManager.speak("感谢授权");
            } else {
                voiceManager.speak("权限被拒绝，部分功能可能无法使用");
            }
        }
    }

    private void showTextInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("文本输入");
        builder.setMessage("语音识别不可用，请输入您的指令：");

        final EditText input = new EditText(this);
        input.setHint("请输入您的问题或指令...");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(40, 0, 40, 0);
        input.setLayoutParams(lp);

        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                handleVoiceResult(text);
            } else {
                voiceManager.speak("请输入有效的指令");
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止任何正在进行的语音识别
        if (currentVoiceCallback != null) {
            currentVoiceCallback = null;
        }
        ImageCaptureManager.getInstance().release();
        VoiceManager.getInstance().destroy();
        networkClient.closeSdkWebSocket();
    }
}
