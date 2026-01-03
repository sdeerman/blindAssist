#!/bin/bash
# 客户端测试脚本

echo "=== BlindAssist 客户端测试脚本 ==="

# 1. 构建APK
echo "1. 构建Debug APK..."
./gradlew.bat assembleDebug

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ APK构建成功"

# 2. 安装到设备
echo "2. 安装APK到设备..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -ne 0 ]; then
    echo "❌ 安装失败"
    exit 1
fi

echo "✅ APK安装成功"

# 3. 启动应用
echo "3. 启动应用..."
adb shell am start -n com.example.test_android_dev/.MainActivity

# 4. 检查进程是否运行
sleep 3
if adb shell pidof com.example.test_android_dev > /dev/null; then
    echo "✅ 应用启动成功"
else
    echo "❌ 应用启动失败"
    exit 1
fi

# 5. 查看初始日志
echo "4. 检查初始化日志..."
adb logcat -d | grep -E "(MainActivity|VoiceManager|NetworkClient)" | tail -10

echo ""
echo "=== 测试完成 ==="
echo "请手动测试以下功能："
echo "1. 按住'按住说话'按钮进行语音测试"
echo "2. 检查TTS是否正常工作"
echo "3. 验证摄像头是否能正常启动"
echo "4. 查看Logcat中的AutoGLM相关日志"