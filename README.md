## BlindAssist 项目后续开发规划与构建说明

本文档说明本项目后续的开发内容、推荐开发顺序、需要补充的逻辑代码以及如何构建可安装的 Android App 与启动后端服务。

---

### 一、整体开发阶段与优先级

推荐按以下阶段迭代，每一阶段都能产出一个可运行的版本：

1. **基础可用版本（MVP）**
   - 语音问答打通（语音输入 → 文本 → `/api/qa/ask` → 文本回答 → 语音播报）。
   - 功能轮盘基本交互（可以切换到“问答模式”并听到语音反馈）。

2. **视觉功能版本**
   - 图像文字识别（OCR）：对准药品/说明书 → 拍照 → `/api/vision/ocr` → 读出文字。
   - 场景描述：对准前方 → 拍照 → `/api/vision/scene` → 读出环境描述。

3. **导航与避障联动版本**
   - 导航：语音说明目的地 → `/api/voice/command` 分类为导航 → `/api/navigation/route` → 播报路线步骤。
   - 避障：开启摄像头视频流 + WebSocket `/ws/obstacle` → 实时语音避障提示。

4. **无障碍与体验优化版本**
   - 网络异常/权限拒绝/无法理解指令等场景的语音反馈。
   - 多轮问答会话（带 sessionId）、导航进度播报、模式切换手势优化。

5. **部署与上线版本**
   - Android APK 签名打包。
   - 后端打包为独立 JAR，在本机或云服务器上长期运行。

---

### 二、Android 客户端需要补充的逻辑

当前 Android 客户端位于 `app` 模块，核心类包括（括号中为当前完成度）：
- `MainActivity`：负责权限申请与初始化（**已实现基础版本**）。
- `VoiceManager`：语音输出 TTS 已基于 `TextToSpeech` 实现，ASR 仍为占位（**TTS 已完成，ASR 待接入**）。
- `ImageCaptureManager`：摄像头采集接口（**方法已定义，内部仍为 TODO**）。
- `NetworkClient`：基于 OkHttp 的 HTTP / WebSocket 通信（**主体已实现，但 BASE_URL 为占位且未解析真实响应内容**）。
- `FeatureRouter`：根据功能类型调度各业务流程（**各模式主流程已实现，为示例/假数据版**）。
- `FeatureWheelView`：自定义功能轮盘 View，支持滑动选择与双击确认（**已实现**）。

#### 2.1 语音模块 `VoiceManager`

**现状**：
- 已在 `VoiceManager` 中使用 `TextToSpeech` 完成中文 TTS（`speak` / `speakImmediate` 可用）。
- `startListening` 仍为 TODO，当前只预留了回调接口 `VoiceCallback`。

**后续需要补充：**

- **ASR（语音转文本）接入**
  - 方案 A：使用 Android 原生 `SpeechRecognizer`，本地在线识别。
  - 方案 B：使用第三方 SDK（如讯飞/百度/阿里等）。
  - 方案 C：录音后上传到后端，由后端调用大模型或云 ASR。
  - 实现建议（以 A / B 为例）：
    - 在 `startListening(VoiceCallback callback)` 中：
      - 初始化并启动 ASR 会话；
      - 在识别结束或部分结果可用时，将文本通过 `callback.onResult(text)` 返回；
      - 对错误情况进行区分（网络问题/说话时间过短等），以便上层用 TTS 做不同提示。
    - 在类中增加必要的释放逻辑（如 `release()`），在应用退出时销毁 ASR/TTS 资源。

**开发顺序建议：**
1. 保持现有 TTS 实现不变，优先在 `startListening` 中接入一个可用的 ASR（可以先用最简单实现）。
2. 等整体流程稳定后，再根据实际 ASR 行为微调 `FeatureRouter` 中各模式的语音提示与超时处理。

#### 2.2 图像采集模块 `ImageCaptureManager`

**现状**：`captureCurrentFrame` / `captureHighResFrame` 等方法已定义但返回空数组，还未真正集成 CameraX/Camera2。

**后续需要补充：**

- **CameraX 初始化**
  - 在 `init(Activity activity)` 中：
    - 初始化 CameraX，用隐藏的预览或小预览 Surface/Texture 进行绑定。

- **单帧采集 `captureSingleFrame(ImageFrameCallback callback)`**
  - 使用 CameraX `ImageCapture`：
    - 打开相机后调用 `takePicture`。
    - 在回调里将 `ImageProxy` 转换为 JPEG `byte[]`。
    - 调用 `callback.onFrame(imageBytes)` 返回到上层。
  - 上层根据场景调用：
    - OCR：`NetworkClient.uploadImageFrame(bytes, "ocr", ...)`。
    - 场景描述：`NetworkClient.uploadImageFrame(bytes, "scene_description", ...)`。

- **视频流采集 `startVideoStream(VideoStreamListener listener)`**
  - 使用 CameraX `ImageAnalysis`：
    - 每隔固定时间（如 200ms）从 `ImageProxy` 里抓取一帧。
    - 将图像缩放/压缩成小分辨率 JPEG `byte[]`。
    - 通过 `NetworkClient` 的 WebSocket 客户端发送帧数据到 `/ws/obstacle`。
  - 在 `stopVideoStream()` 中停止分析器和关闭 WebSocket 连接。

**开发顺序建议：**
1. 优先实现 `captureSingleFrame`，打通 OCR 与场景描述接口。
2. 再逐步实现 `startVideoStream`，从低帧率开始，关注性能与带宽。

#### 2.3 网络模块 `NetworkClient`

**现状**：已使用 OkHttp 实现了大部分 HTTP/WebSocket 封装逻辑：
- `sendVoiceCommand` → POST `/api/voice/command`
- `requestNavigation` → POST `/api/navigation/route`
- `askQuestion` → POST `/api/qa/ask`
- `uploadVisionRequest` → POST `/api/vision/ocr|scene`
- `openObstacleWebSocket` / `sendFrameViaWS` → WebSocket `/ws/obstacle`

**仍需补充和完善：**

- **配置化 BASE_URL / WS_URL**
  - 当前常量 `BASE_URL = "http://your-backend-api.com"` 和 `WS_URL` 为占位。
  - 建议：
    - 抽取到单独的配置类或 `BuildConfig` 字段，方便按环境（本机/测试/生产）切换；
    - 在文档中说明如何配置为局域网 IP。

- **响应解析与错误处理**
  - 现在的实现直接把 OkHttp `Callback` 暴露给上层，`FeatureRouter` 中仍用固定文案（例如“这是为您找到的答案。”）。
  - 建议：
    - 引入 `Gson` 或 `Moshi`，在 `NetworkClient` 内解析 JSON，提供更高层次的结果对象（如 `VoiceCommandResult`、`QaResult` 等）；
    - 统一处理 HTTP 错误（非 200）和网络异常，并转换为易懂的错误类型，供上层用 TTS 提示（如“服务器繁忙，请稍后再试”）。

- **WebSocket 连接状态管理**
  - 增加 `closeObstacleWebSocket()`，并在应用退出或模式切换时调用，避免泄漏连接；
  - 为避障 WebSocket 增加重连/心跳逻辑（可视需要实现）。

#### 2.4 功能路由 `FeatureRouter`

**现状**：
- 已经实现了一个 `route(FeatureType feature)` 方法，根据枚举调用：
  - `startNavigationFlow` / `startObstacleAvoidance` / `startQAFlow` / `startOCRFlow` / `startSceneDescriptionFlow`。
- 各方法中已经调用 `VoiceManager` 和 `NetworkClient`，形成了完整的业务链路，但目前多为“示例/假数据”：
  - 导航固定请求 0 坐标，直接播报“第一步：向前直行”；
  - 避障对 WebSocket 返回只做固定提示；
  - QA 固定回答“这是为您找到的答案。”；
  - OCR/场景描述直接读出示例文本，而非解析真实响应。

**后续需要补充与增强：**

- **从真实响应中解析内容**
  - 在 `onResponse` 中通过 `response.body().string()` 获取 JSON，并用 `Gson`/`Moshi` 解析：
    - 导航：解析 `voiceSteps` 数组，按顺序逐条播报，而不是写死一句话；
    - 问答：解析 `answer` 字段，而非固定字符串；
    - OCR：解析 `text` 字段；
    - 场景描述：解析 `description` 字段；
    - 避障 WebSocket：解析 JSON 中的 `message` 或结构化字段，决定播报内容。

- **与 ASR 的结合与容错**
  - 当前 `startNavigationFlow` / `startQAFlow` 等中调用 `startListening` 后，假设一定拿到文本；
  - 在接入真实 ASR 后，需要针对“识别失败/空文本/用户长时间不说话”等场景增加重试和提示逻辑。

- **模式切换与资源回收**
  - 进入新模式时，考虑是否需要关闭上一个模式的定时任务/避障流等（例如退出导航时关闭 `obstacleExecutor` 和 WebSocket）。

#### 2.5 功能轮盘 UI

**现状**：
- 已添加 `FeatureWheelView` 自定义 View：
  - 单指上下滑动切换功能（维护 `FeatureType` 数组与当前索引）；
  - 双击确认当前功能，通过监听器回调。
- 可通过设置 `OnFeatureSelectedListener` 在每次滑动时调用 `VoiceManager.speak` 播报功能名，并在确认时调用 `FeatureRouter.route(feature)`。

**后续可以优化的点：**

- 在 `MainActivity` 中完成：
  - 将 `FeatureWheelView` 添加到 `feature_wheel_container`；
  - 设置 `OnFeatureSelectedListener`，在 `onItemSelected` 时播报当前功能名称，在 `onItemConfirmed` 时调用 `FeatureRouter.route`。
- 加强无障碍支持：
  - 为 View 设置 `contentDescription` 和合适的焦点策略，使 TalkBack 用户也能通过手势访问；
  - 根据用户偏好调整滑动灵敏度（`distanceY` 阈值）和双击识别时间窗口。

---

### 三、后端需要补充的逻辑

后端工程位于 `server` 目录，为 Spring Boot 项目。

当前已实现的接口包括：
- `/api/voice/command`：语音意图分类（规则实现）。
- `/api/navigation/route`：示例导航步骤。
- `/api/qa/ask`：示例问答。
- `/api/vision/ocr`：示例 OCR。
- `/api/vision/scene`：示例场景描述。
- WebSocket `/ws/obstacle`：示例避障指令。

#### 3.1 大模型与外部服务接入

- 在 `IntentClassificationService` 中：
  - 将规则改为调用大模型：
    - 向大模型发送用户文本和可用功能列表。
    - 让大模型返回结构化 JSON（feature + detail）。
    - 解析 JSON 并构造 `VoiceCommandResponse`。

- 在 `QaService` 中：
  - 使用大模型 API 处理问答：
    - 利用 `sessionId` 管理对话上下文（可以使用内存 Map 或 Redis）。
    - 对大模型的回复做一次“易懂化”处理（简化长句）。

- 在 `NavigationService` 中：
  - 对接高德地图 MCP 或 AutoGLM：
    - 使用 MCP 调用高德路线规划接口；
    - 或让 AutoGLM 控制手机上的地图 App（如果运行在同一设备环境）。
    - 将路线结果转换为简洁的语音步骤列表返回。

- 在 `VisionService` 中：
  - OCR：
    - 接入 OCR 引擎，例如 PaddleOCR、本地部署服务或云接口。
  - 场景描述：
    - 使用多模态大模型，输入图像获得文本描述。

#### 3.2 避障 WebSocket 实现

在 `ObstacleWebSocketHandler` 中：
- 将示例的 `sendFakeInstruction` 替换为真实视觉推理：
  - 收到二进制图像帧后放入线程池处理，避免阻塞 WebSocket I/O 线程。
  - 模型识别障碍物位置、距离、方向以及红绿灯状态等。
  - 组装简洁的 JSON 指令通过 `session.sendMessage` 返回。
- 考虑多客户端并发时对 `frameCounter` 和状态的隔离，可通过 session 属性或专用对象管理。

---

### 四、如何构建 Android APK

#### 4.1 调试版 APK

1. 在项目根目录（包含 `app` 的那一级）打开终端：

   ```bash
   # Windows PowerShell
   .\gradlew.bat assembleDebug

   # 或类 Unix 系统
   ./gradlew assembleDebug
   ```

2. 构建成功后，调试 APK 位于：
   - `app/build/outputs/apk/debug/app-debug.apk`

3. 使用 ADB 安装到真机：

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. 在手机上找到应用（名称如 `BlindAssist`）并运行。

#### 4.2 发布版 APK

1. 在 Android Studio 中使用 `Build > Generate Signed Bundle / APK` 创建签名密钥。
2. 配置 `release` 签名并生成 `app-release.apk` 或 `.aab`。
3. 使用该包进行内测或发布。

> 提示：客户端需要在 `NetworkClient` 中配置服务端的基础地址（IP/端口或域名），建议集中管理，后期方便切换环境。

---

### 五、如何启动后端服务

后端工程路径：`server`

#### 5.1 本地开发运行

前提：已安装 JDK 17+ 和 Maven。

1. 进入后端目录：

   ```bash
   cd server
   ```

2. 使用 Maven 运行 Spring Boot：

   ```bash
   mvn spring-boot:run
   ```

3. 启动成功后：
   - HTTP: `http://localhost:8080`
   - WebSocket: `ws://localhost:8080/ws/obstacle`

4. 在 Android 客户端中，将服务端地址设置为：
   - 若真机与电脑在同一局域网：`http://<电脑局域网IP>:8080`
   - 不要使用 `localhost` 或 `127.0.0.1`（对真机来说是手机自身）。

#### 5.2 打包为可部署 JAR

1. 在 `server` 目录打包：

   ```bash
   mvn clean package
   ```

2. 打包完成后，JAR 一般位于：
   - `server/target/blindassist-server-0.0.1-SNAPSHOT.jar`（具体名称视版本而定）。

3. 在目标服务器运行：

   ```bash
   java -jar blindassist-server-0.0.1-SNAPSHOT.jar
   ```

4. 可选：在服务器前面加 Nginx 等反向代理，启用 HTTPS，便于在公网和生产环境中使用。

---

### 六、推荐的近期开发步骤（Checklist）

1. Android 端：
   - [x] 在 `VoiceManager` 中实现 TTS（`TextToSpeech`）。
   - [ ] 在 `VoiceManager` 中接入实际 ASR，让各模式“听得懂”用户语音。
   - [x] 在 `NetworkClient` 中实现基础 HTTP/WebSocket 调用逻辑。
   - [x] 在 `FeatureRouter` 中实现导航/避障/问答/OCR/场景描述的主流程骨架。
   - [ ] 在 `FeatureRouter` 中解析真实后端响应内容，替换掉目前的示例/固定文案。
   - [ ] 在 `MainActivity` 中完成 `FeatureWheelView` 与 `FeatureRouter`/`VoiceManager` 的联动（滑动播报 + 双击确认）。
2. Android 视觉 & 网络：
   - [ ] 在 `ImageCaptureManager` 中使用 CameraX 实现 `captureSingleFrame`。
   - [ ] 在 `NetworkClient` 中实现 `/api/vision/ocr` 和 `/api/vision/scene`。
   - [ ] 在 `FeatureRouter` 中对 OCR 和场景描述使用真实响应文本进行播报。
3. 导航与避障：
   - [x] 在 `NetworkClient` 中实现 `/api/navigation/route` 调用和避障 WebSocket 客户端骨架。
   - [ ] 在 `FeatureRouter` 中解析导航结果（`voiceSteps`）并逐步播报，而非固定文案。
   - [ ] 在 `ImageCaptureManager` 中实现 `startVideoStream` / `stopVideoStream` 并与 WebSocket 发送逻辑打通。
4. 服务端：
   - [ ] 为意图分类、问答、导航、视觉接入实际大模型/MCP 或第三方服务。
   - [ ] 完善避障 WebSocket 中的图像推理逻辑。
5. 打包与部署：
   - [ ] 按本文件说明构建调试 APK 并在真机上测试。
   - [ ] 打包后端 JAR 并在目标服务器上启动。


---

## 更新日志

### 12-19修改

本次更新主要围绕 **提升用户体验和完善核心交互** 进行。

**主要变更内容：**

1.  **功能轮盘-视觉与交互升级:**
    *   **界面可视化**: 为原先不可见的功能轮盘增加了视觉反馈，现在屏幕会居中显示当前功能的 **图标和名称**。
    *   **新增图标资源**: 添加了5个简约风格的矢量图标，分别对应各项功能。
    *   **优化滑动体验**: 降低滑动切换的灵敏度，并优化了播报逻辑，现在只在用户 **手指离开屏幕时** 播报最终选定的功能，避免了滑动过程中连续、嘈杂的提示音。

2.  **语音系统-健壮性与时序问题修复:**
    *   **修复“说听冲突”**: 解决了在进入问答等功能时，提示音（“请提问”）和语音识别启动过近导致的冲突，确保提示音能完整地播出。
    *   **修复启动时序问题**: 完美解决了App刚启动时，“欢迎语”和“初始功能名称”播报顺序错乱或丢失的问题，确保了稳定、有序的启动体验。
    *   **增强语音管理器**: 对 `VoiceManager` 进行了底层升级，使其能够可靠地处理TTS引擎初始化期间的各种播报请求，确保任何语音指令（包括其回调任务）都不会丢失。

3.  **核心功能打通:**
    *   **实现语音识别**: 对接了 Android 原生的语音识别服务，应用现在可以“听懂”用户的语音指令。
    *   **打通问答流程**: 实现了完整的“语音提问 -> 网络请求 -> 解析答案 -> 语音播报”的闭环流程。
