@echo off
REM 客户端测试脚本 (Windows)

echo === BlindAssist 客户端测试脚本 ===

REM 1. 构建APK
echo 1. 构建Debug APK...
call .\gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo ❌ 构建失败
    exit /b 1
)

echo ✅ APK构建成功

REM 2. 安装到设备
echo 2. 安装APK到设备...
adb install -r "app/build/outputs/apk/debug/app-debug.apk"

if %ERRORLEVEL% NEQ 0 (
    echo ❌ 安装失败
    exit /b 1
)

echo ✅ APK安装成功

REM 3. 启动应用
echo 3. 启动应用...
adb shell am start -n com.example.test_android_dev/.MainActivity

REM 4. 检查进程是否运行
timeout /t 3 /nobreak >nul
adb shell pidof com.example.test_android_dev >nul
if %ERRORLEVEL% EQU 0 (
    echo ✅ 应用启动成功
) else (
    echo ❌ 应用启动失败
    exit /b 1
)

REM 5. 查看初始日志
echo 4. 检查初始化日志...
adb logcat -d | findstr /R "(MainActivity.*VoiceManager.*NetworkClient)"

echo.
echo === 测试完成 ===
echo 请手动测试以下功能：
echo 1. 按住'按住说话'按钮进行语音测试
echo 2. 检查TTS是否正常工作
echo 3. 验证摄像头是否能正常启动
echo 4. 查看Logcat中的AutoGLM相关日志