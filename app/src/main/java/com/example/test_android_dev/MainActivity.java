package com.example.test_android_dev;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 应用入口 Activity：
 * - 初始化语音、图像与网络模块
 * - 展示并集成功能轮盘
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 1001;
    private FeatureWheelView featureWheelView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestBasicPermissions();
        initCoreManagers();
        setupFeatureWheel();

        // 精心编排启动语音：先播报欢迎语，完成后再播报当前功能
        VoiceManager.getInstance().speakImmediate("欢迎使用随行助手。请上下滑动屏幕选择功能，双击进入。", () -> {
            if (featureWheelView != null) {
                // 使用常规 speak，加入到欢迎语之后的队列
                VoiceManager.getInstance().speak(featureWheelView.getCurrentFeature().getDescription());
            }
        });
    }

    private void requestBasicPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET
        };
        boolean needRequest = false;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, REQ_PERMISSIONS);
        }
    }

    private void initCoreManagers() {
        VoiceManager.getInstance().init(getApplicationContext());
        ImageCaptureManager.getInstance().init(this);
        NetworkClient.getInstance().init(getApplicationContext());
        FeatureRouter.getInstance().init(getApplicationContext());
    }

    private void setupFeatureWheel() {
        FrameLayout container = findViewById(R.id.feature_wheel_container);
        featureWheelView = new FeatureWheelView(this);
        container.addView(featureWheelView);

        featureWheelView.setOnFeatureSelectedListener(new FeatureWheelView.OnFeatureSelectedListener() {
            @Override
            public void onItemSelected(FeatureType feature) {
                // 滑动到某项，播报功能名
                VoiceManager.getInstance().speak(feature.getDescription());
            }

            @Override
            public void onItemConfirmed(FeatureType feature) {
                // 双击确认，跳转到对应的功能流程
                FeatureRouter.getInstance().route(feature);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    VoiceManager.getInstance().speak("权限被拒绝，部分功能可能无法使用。请在设置中开启权限。");
                }
            }
        }
    }
}
