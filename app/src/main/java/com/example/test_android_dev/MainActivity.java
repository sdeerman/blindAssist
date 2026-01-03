package com.example.test_android_dev;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.test_android_dev.manager.AgentManager;
import com.example.test_android_dev.service.AutoGLMService;

/**
 * 应用入口 Activity
 * - 支持两种模式：用户语音界面 和 开发测试界面
 * - 通过 Config.DEBUG_MODE 控制界面切换
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQ_PERMISSIONS = 1001;

    // 语音界面相关
    private Button voiceButton;
    private TextView statusText;
    private VoiceManager.VoiceCallback currentVoiceCallback;

    // 测试界面相关
    private EditText etCommand;
    private LinearLayout debugLayout;
    private RelativeLayout voiceLayout;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestBasicPermissions();
        initCoreManagers();
        setupUI();
    }

    private void setupUI() {
        // 绑定两套界面的控件
        voiceLayout = findViewById(R.id.voice_layout);
        debugLayout = findViewById(R.id.debug_layout);
        voiceButton = findViewById(R.id.voice_button);
        statusText = findViewById(R.id.status_text);
        etCommand = findViewById(R.id.et_command);
        Button btnStart = findViewById(R.id.btn_start_test);
        Button btnStop = findViewById(R.id.btn_stop_test);

        // 根据配置显示不同界面
        if (Config.DEBUG_MODE) {
            // 显示开发测试界面
            voiceLayout.setVisibility(View.GONE);
            debugLayout.setVisibility(View.VISIBLE);
            setupDebugUI(btnStart, btnStop);
        } else {
            // 显示用户语音界面
            voiceLayout.setVisibility(View.VISIBLE);
            debugLayout.setVisibility(View.GONE);
            setupVoiceUI();
        }
    }

    /**
     * 设置语音交互界面（用户模式）
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupVoiceUI() {
        voiceButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 播放按下动画
                    Animation pressAnim = AnimationUtils.loadAnimation(this, R.anim.button_press);
                    voiceButton.startAnimation(pressAnim);

                    // 检查语音识别是否可用
                    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
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
                            VoiceManager.getInstance().speak("没有听到声音，请重试");
                        }
                    };
                    VoiceManager.getInstance().startListening(currentVoiceCallback);
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

        VoiceManager.getInstance().speakImmediate("欢迎使用，请按住屏幕中央的按钮对我说话");
    }

    /**
     * 设置开发测试界面（调试模式）
     */
    private void setupDebugUI(Button btnStart, Button btnStop) {
        btnStart.setOnClickListener(v -> {
            String command = etCommand.getText().toString().trim();
            if (TextUtils.isEmpty(command)) {
                Toast.makeText(this, "请输入指令", Toast.LENGTH_SHORT).show();
                return;
            }
            startTest(command);
        });

        btnStop.setOnClickListener(v -> {
            AgentManager.getInstance().stopTask();
            Toast.makeText(this, "任务已停止", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 处理语音识别结果
     */
    private void handleVoiceResult(String text) {
        Log.i(TAG, "识别结果: " + text);
        VoiceManager.getInstance().speak("好的，正在处理您的指令: " + text);
        statusText.setText("执行中: " + text);

        // 检查无障碍服务
        if (AutoGLMService.getInstance() == null) {
            Toast.makeText(this, "请先开启无障碍服务！", Toast.LENGTH_LONG).show();
            VoiceManager.getInstance().speak("请先开启无障碍服务");
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            return;
        }

        // 获取屏幕尺寸并启动任务
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        AgentManager.getInstance().startTask(text, width, height);
    }

    /**
     * 开发测试模式：手动输入指令测试
     */
    private void startTest(String command) {
        // 检查无障碍服务是否开启
        if (AutoGLMService.getInstance() == null) {
            Toast.makeText(this, "请先开启无障碍服务！", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            return;
        }

        // 获取屏幕宽高
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        // 启动 Agent 任务
        Toast.makeText(this, "发送指令: " + command, Toast.LENGTH_SHORT).show();
        AgentManager.getInstance().startTask(command, width, height);
    }

    /**
     * 显示文本输入对话框（语音识别不可用时的备选方案）
     */
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
                VoiceManager.getInstance().speak("请输入有效的指令");
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void initCoreManagers() {
        VoiceManager.getInstance().init(getApplicationContext());
        ImageCaptureManager.getInstance().init(this);
        NetworkClient.getInstance().init(getApplicationContext());
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
                VoiceManager.getInstance().speak("感谢授权");
            } else {
                VoiceManager.getInstance().speak("权限被拒绝，部分功能可能无法使用");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentVoiceCallback != null) {
            currentVoiceCallback = null;
        }
        AgentManager.getInstance().stopTask();
        ImageCaptureManager.getInstance().release();
        VoiceManager.getInstance().destroy();
    }
}
