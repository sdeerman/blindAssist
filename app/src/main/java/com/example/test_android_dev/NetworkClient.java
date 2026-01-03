package com.example.test_android_dev;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * 封装与后端接口通信，并内置模拟模式 (Mock Mode)
 */
public class NetworkClient {
    private static final String TAG = "NetworkClient";
    private static NetworkClient instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private OkHttpClient client;
    private WebSocket visionSocket;
    private MockNetworkModule mockModule;

    private String baseUrl = "http://192.168.1.100:8080"; // Replace with your actual backend IP
    private String wsBaseUrl = "ws://192.168.1.100:8080"; // Replace with your actual backend IP
    private WebSocket sdkWebSocket;

    public interface VisionStreamListener {
        void onInstruction(String jsonMessage);
        void onConnected();
        void onDisconnected(String reason);
    }

    public interface SdkExecutionListener {
        void onSdkCommand(String jsonMessage);
        void onSdkConnected();
        void onSdkDisconnected(String reason);
    }

    private NetworkClient() {
        if (Config.MOCK_MODE) {
            mockModule = new MockNetworkModule();
        } else {
            client = new OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build();
        }
    }

    public static synchronized NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    public void uploadVisionFrame(@NonNull byte[] imageBytes, @NonNull String sceneType, int frameSeq, @NonNull Callback callback) {
        if (Config.MOCK_MODE) {
            mockModule.handleUploadVisionFrame(sceneType, callback);
            return;
        }
        // Real implementation for /api/vision/frame
        try {
            RequestBody body = RequestBody.create(imageBytes, MediaType.parse("application/octet-stream"));
            HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/api/vision/frame").newBuilder();
            urlBuilder.addQueryParameter("sceneType", sceneType);
            if (frameSeq > 0) {
                urlBuilder.addQueryParameter("frameSeq", String.valueOf(frameSeq));
            }

            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .post(body)
                    .build();

            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            Log.e(TAG, "Error creating vision frame upload request", e);
            callback.onFailure(null, new IOException("Failed to create request"));
        }
    }

    public void uploadVisionFrames(@NonNull java.util.List<byte[]> frames, @NonNull String sceneType, int startSeq, @NonNull Callback callback) {
        if (Config.MOCK_MODE) {
            mockModule.handleUploadVisionFrames(frames, sceneType, startSeq, callback);
            return;
        }
        // Real implementation for /api/vision/frames (multipart)
        // This would require multipart form data construction
        // For now, we'll simulate by uploading each frame individually
        Log.w(TAG, "Multi-frame upload not fully implemented, using single frame upload");
        if (!frames.isEmpty()) {
            uploadVisionFrame(frames.get(0), sceneType, startSeq, callback);
        } else {
            callback.onFailure(null, new IOException("No frames to upload"));
        }
    }

    public void connectVisionStream(@NonNull String sceneType, @NonNull String sessionId, @NonNull VisionStreamListener listener) {
        if (Config.MOCK_MODE) {
            mockModule.startMockStream(sceneType, listener);
            return;
        }
        // ... real implementation
    }

    public void sendVisionStreamFrame(byte[] frame) {
        if (Config.MOCK_MODE) return; 
        if (visionSocket != null) visionSocket.send(ByteString.of(frame));
    }

    public void closeVisionStream() {
        if (Config.MOCK_MODE) {
            mockModule.stopMockStream();
            return;
        }
        if (visionSocket != null) visionSocket.close(1000, "Closed by user");
    }

    public void connectSdkWebSocket(@NonNull String sessionId, @NonNull SdkExecutionListener listener) {
        if (Config.MOCK_MODE) {
            mockModule.startMockSdkStream(sessionId, listener);
            return;
        }

        String wsUrl = wsBaseUrl + "/ws/sdk-control?sessionId=" + sessionId;
        Request request = new Request.Builder().url(wsUrl).build();

        sdkWebSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                mainHandler.post(listener::onSdkConnected);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    String type = json.optString("type", "");

                    if ("control".equals(type)) {
                        mainHandler.post(() -> listener.onSdkCommand(text));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing SDK command", e);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                mainHandler.post(() -> listener.onSdkDisconnected(reason));
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                String errorMsg = t.getMessage() != null ? t.getMessage() : "WebSocket connection failed";
                mainHandler.post(() -> listener.onSdkDisconnected(errorMsg));
            }
        });
    }

    public void closeSdkWebSocket() {
        if (Config.MOCK_MODE) {
            mockModule.stopMockSdkStream();
            return;
        }
        if (sdkWebSocket != null) {
            sdkWebSocket.close(1000, "Closed by user");
            sdkWebSocket = null;
        }
    }

    public void reportSdkExecutionResult(String sessionId, String taskId, String actionType, boolean success, String errorMessage) {
        if (Config.MOCK_MODE) {
            mockModule.handleSdkExecutionResult(sessionId, taskId, actionType, success, errorMessage);
            return;
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("sessionId", sessionId);
            requestBody.put("taskId", taskId);

            JSONObject executionResult = new JSONObject();
            executionResult.put("status", success ? "success" : "failed");
            executionResult.put("actionType", actionType);
            if (!success && errorMessage != null) {
                executionResult.put("errorMessage", errorMessage);
            }
            executionResult.put("timestamp", System.currentTimeMillis());

            requestBody.put("executionResult", executionResult);

            RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/control/sdk-result")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to report SDK execution result", e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Server rejected SDK execution result: " + response.code());
                    }
                    try {
                        response.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error creating SDK execution result report", e);
        }
    }

    public void sendVoiceText(String text, @Nullable byte[] audioBytes, Callback callback) {
        if (Config.MOCK_MODE) {
            mockModule.handleSendVoiceText(text, audioBytes, callback);
            return;
        }
        // Real implementation for /api/voice/command
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("text", text);

            // Add audio metadata if audio bytes are provided
            if (audioBytes != null) {
                JSONObject audioMeta = new JSONObject();
                audioMeta.put("format", "pcm");
                audioMeta.put("sampleRate", 16000);
                audioMeta.put("durationMs", 2000); // Approximate duration
                requestBody.put("audioMeta", audioMeta);

                // Convert audio bytes to base64 (optional, can be skipped for now)
                // String audioDataBase64 = Base64.encodeToString(audioBytes, Base64.DEFAULT);
                // requestBody.put("audioDataBase64", audioDataBase64);
            }

            // Add client context
            JSONObject clientContext = new JSONObject();
            clientContext.put("deviceModel", android.os.Build.MODEL);
            clientContext.put("osVersion", android.os.Build.VERSION.RELEASE);
            requestBody.put("clientContext", clientContext.toString());

            RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/voice/command")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            Log.e(TAG, "Error creating voice command request", e);
            // Create a mock error response
            Response errorResponse = new Response.Builder()
                    .request(new Request.Builder().url("http://error/api").build())
                    .protocol(okhttp3.Protocol.HTTP_2)
                    .code(500).message("Internal Error").body(ResponseBody.create("", MediaType.parse("application/json")))
                    .build();
            callback.onFailure(null, new IOException("Failed to create request"));
        }
    }

    public void askQuestion(String question, String sessionId, Callback callback) {
        if (Config.MOCK_MODE) {
            mockModule.handleAskQuestion(question, sessionId, callback);
            return;
        }
        // ... real implementation
    }

    public void requestNavigation(String description, Callback callback) {
        if (Config.MOCK_MODE) {
            mockModule.handleRequestNavigation(description, callback);
            return;
        }
        // ... real implementation
    }

    /**
     * Inner class to handle all mock mode logic.
     */
    private static class MockNetworkModule {
        private ScheduledExecutorService mockExecutor;
        private VisionStreamListener mockListener;
        private String currentSceneType;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        void startMockStream(String sceneType, VisionStreamListener listener) {
            this.mockListener = listener;
            this.currentSceneType = sceneType;
            stopMockStream();
            mockExecutor = Executors.newSingleThreadScheduledExecutor();
            mainHandler.post(listener::onConnected);
            mockExecutor.scheduleAtFixedRate(this::generateAndSendMockData, 3, 5, TimeUnit.SECONDS);
        }

        void stopMockStream() {
            if (mockExecutor != null && !mockExecutor.isShutdown()) {
                mockExecutor.shutdownNow();
            }
        }

        void handleUploadVisionFrame(String sceneType, Callback callback) {
            mainHandler.post(() -> {
                try {
                    Response response = createMockResponse(200, "{}");
                    callback.onResponse(null, response);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> generateAndSendMockData(sceneType), 1500);
                } catch (IOException e) { /* Wont happen in mock */ }
            });
        }

        void handleUploadVisionFrames(java.util.List<byte[]> frames, String sceneType, int startSeq, Callback callback) {
            mainHandler.post(() -> {
                try {
                    JSONObject mockJson = new JSONObject();
                    mockJson.put("status", "ok");
                    mockJson.put("frameIds", "batch-" + System.currentTimeMillis());
                    mockJson.put("message", "frames received: " + frames.size());
                    Response response = createMockResponse(200, mockJson.toString());
                    callback.onResponse(null, response);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> generateAndSendMockData(sceneType), 1500);
                } catch (Exception e) { /* Wont happen */ }
            });
        }
        
        void handleAskQuestion(String question, String sessionId, Callback callback) {
             mainHandler.post(() -> {
                try {
                    JSONObject mockJson = new JSONObject();
                    mockJson.put("answer", "这是对您的问题的模拟回答: " + question);
                    mockJson.put("sessionId", sessionId);
                    Response response = createMockResponse(200, mockJson.toString());
                    callback.onResponse(null, response);
                } catch (Exception e) { /* Wont happen */ }
            });
        }
        
        void handleRequestNavigation(String destination, Callback callback) {
             mainHandler.post(() -> {
                try {
                    JSONObject mockJson = new JSONObject();
                    JSONArray steps = new JSONArray();
                    steps.put("第一步：向前直行100米");
                    steps.put("第二步：在路口右转");
                    steps.put("第三步：目的地就在您的左手边");
                    mockJson.put("voiceSteps", steps);
                    Response response = createMockResponse(200, mockJson.toString());
                    callback.onResponse(null, response);
                } catch (Exception e) { /* Wont happen */ }
            });
        }

        void handleSendVoiceText(String text, @Nullable byte[] audioBytes, Callback callback) {
            mainHandler.post(() -> {
                try {
                    JSONObject mockJson = new JSONObject();
                    // Determine feature based on text content
                    String feature = "UNKNOWN";
                    String detail = text;
                    String prompt = "用户指令: " + text;

                    if (text.contains("导航") || text.contains("去") || text.contains("路线")) {
                        feature = "NAVIGATION";
                    } else if (text.contains("避障") || text.contains("障碍") || text.contains("注意")) {
                        feature = "OBSTACLE_AVOIDANCE";
                    } else if (text.contains("文字") || text.contains("识别") || text.contains("OCR")) {
                        feature = "OCR";
                    } else if (text.contains("场景") || text.contains("环境") || text.contains("描述")) {
                        feature = "SCENE_DESCRIPTION";
                    } else if (text.contains("控制") || text.contains("操作")) {
                        feature = "CONTROL_SDK";
                    } else {
                        feature = "QA_VOICE";
                    }

                    mockJson.put("feature", feature);
                    mockJson.put("detail", detail);
                    mockJson.put("prompt", prompt);

                    Response response = createMockResponse(200, mockJson.toString());
                    callback.onResponse(null, response);
                } catch (Exception e) { /* Wont happen */ }
            });
        }

        private void generateAndSendMockData() {
             generateAndSendMockData(this.currentSceneType);
        }

        private void generateAndSendMockData(String sceneType) {
            if (mockListener == null) return;
            try {
                JSONObject data = new JSONObject();
                JSONObject root = new JSONObject();
                String type;
                switch (sceneType) {
                    case "ocr":
                        type = "ocr_result";
                        data.put("text", "模拟识别结果：请沿盲道直行");
                        break;
                    case "scene_description":
                        type = "scene_description_result";
                        data.put("description", "模拟场景：您正处于一个十字路口，前方是红灯");
                        break;
                    default:
                        type = "obstacle_warning";
                        data.put("message", "模拟避障：注意，前方有台阶");
                        break;
                }
                root.put("type", type);
                root.put("data", data);
                mainHandler.post(() -> mockListener.onInstruction(root.toString()));
            } catch (JSONException e) { /* Wont happen */ }
        }

        void startMockSdkStream(String sessionId, SdkExecutionListener listener) {
            mainHandler.post(listener::onSdkConnected);
            // Send a mock SDK command after a short delay
            mainHandler.postDelayed(() -> {
                try {
                    JSONObject command = new JSONObject();
                    command.put("type", "control");
                    command.put("sessionId", sessionId);
                    command.put("taskId", "task-123");

                    JSONObject data = new JSONObject();
                    data.put("action", "Tap");
                    data.put("element", new int[]{500, 1000});
                    command.put("data", data);

                    listener.onSdkCommand(command.toString());
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create mock SDK command", e);
                }
            }, 2000);
        }

        void stopMockSdkStream() {
            // Nothing to stop in mock mode
        }

        void handleSdkExecutionResult(String sessionId, String taskId, String actionType, boolean success, String errorMessage) {
            Log.i(TAG, "Mock SDK execution result - Session: " + sessionId + ", Task: " + taskId +
                  ", Action: " + actionType + ", Success: " + success);
            if (!success && errorMessage != null) {
                Log.e(TAG, "Error: " + errorMessage);
            }
        }
        
        private Response createMockResponse(int code, String jsonBody) {
            return new Response.Builder()
                .request(new Request.Builder().url("http://mock/api").build())
                .protocol(okhttp3.Protocol.HTTP_2)
                .code(code).message("OK").body(ResponseBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
        }
    }
}
