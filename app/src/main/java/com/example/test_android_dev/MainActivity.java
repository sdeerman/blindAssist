package com.example.test_android_dev;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
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
                    boolean isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this);
                    Log.d(TAG, "Speech recognition available: " + isRecognitionAvailable);

                    if (!isRecognitionAvailable) {
                        // Try to get more detailed information about why it's not available
                        try {
                            PackageManager pm = getPackageManager();
                            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                            java.util.List<android.content.pm.ResolveInfo> activities = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);

                            Log.d(TAG, "Found " + activities.size() + " speech recognition activities");
                            for (android.content.pm.ResolveInfo info : activities) {
                                Log.d(TAG, "Activity: " + info.activityInfo.packageName + "/" + info.activityInfo.name);
                            }

                            if (activities.isEmpty()) {
                                Log.e(TAG, "No speech recognition activities found");
                                statusText.setText("未找到语音识别服务");

                                // Check for specific Chinese OEM services
                                boolean hasChineseService = false;
                                String[] chinesePackages = {
                                    "com.iflytek.speechsuite",
                                    "com.baidu.input",
                                    "com.xiaomi.voiceassistant",
                                    "com.coloros.voiceservice",
                                    "com.miui.voiceassist"
                                };

                                for (String pkg : chinesePackages) {
                                    try {
                                        pm.getPackageInfo(pkg, 0);
                                        hasChineseService = true;
                                        Log.d(TAG, "Found Chinese speech service: " + pkg);
                                        break;
                                    } catch (PackageManager.NameNotFoundException e) {
                                        // Package not installed
                                    }
                                }

                                if (hasChineseService) {
                                    // Provide more specific guidance based on detected service
                                    String guidance = "检测到语音服务但无法使用，请在系统设置中启用语音输入功能";
                                    for (String pkg : chinesePackages) {
                                        try {
                                            pm.getPackageInfo(pkg, 0);
                                            if (pkg.contains("baidu")) {
                                                guidance = "检测到百度输入法，请在设置中将百度输入法设为默认语音输入引擎";
                                            } else if (pkg.contains("iflytek")) {
                                                guidance = "检测到讯飞输入法，请在设置中将讯飞输入法设为默认语音输入引擎";
                                            } else if (pkg.contains("xiaomi") || pkg.contains("miui")) {
                                                guidance = "检测到小米语音服务，请在MIUI设置中启用语音助手";
                                            } else if (pkg.contains("coloros")) {
                                                guidance = "检测到OPPO语音服务，请在ColorOS设置中启用语音输入";
                                            }
                                            break;
                                        } catch (PackageManager.NameNotFoundException e) {
                                            // Continue to next package
                                        }
                                    }
                                    VoiceManager.getInstance().speak(guidance);
                                } else {
                                    VoiceManager.getInstance().speak("设备上没有安装语音识别服务，请安装讯飞输入法、百度输入法或其他语音识别应用");
                                }
                            } else {
                                Log.e(TAG, "Speech recognition activities found but isRecognitionAvailable returned false");
                                statusText.setText("语音识别服务不可用");
                                VoiceManager.getInstance().speak("语音识别服务当前不可用，请检查设备的语音输入设置");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error checking speech recognition availability", e);
                            statusText.setText("语音识别初始化失败");
                            VoiceManager.getInstance().speak("语音识别功能初始化失败");
                        }
                        showTextInputDialog();
                        return true;
                    }

                    voiceButton.setText("松开结束");
                    voiceButton.setBackground(getDrawable(R.drawable.btn_speak_background));
                    statusText.setText("请说话...");

                    // Start continuous listening
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
                        public void onPartialResult(String partialText) {
                            // Update status with partial result for better UX
                            statusText.setText("正在听: " + partialText);
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
                    VoiceManager.getInstance().startContinuousListening(currentVoiceCallback);
                    v.performClick();
                    break;

                case MotionEvent.ACTION_UP:
                    // 播放释放动画
                    Animation releaseAnim = AnimationUtils.loadAnimation(this, R.anim.button_release);
                    voiceButton.startAnimation(releaseAnim);

                    // Request stop with 0.5s debounce
                    voiceButton.setText("松开中...");
                    VoiceManager.getInstance().requestStopWithDebounce();
                    break;

                case MotionEvent.ACTION_CANCEL:
                    // Handle touch cancellation (e.g., user slides finger away)
                    voiceButton.setText("按住说话");
                    voiceButton.setBackground(getDrawable(R.drawable.btn_speak_material));
                    VoiceManager.getInstance().stopListening();
                    currentVoiceCallback = null;
                    statusText.setText("已取消");
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
