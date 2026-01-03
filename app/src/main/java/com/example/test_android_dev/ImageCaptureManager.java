package com.example.test_android_dev;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 封装摄像头采集，支持抓取单帧和视频流
 */
public class ImageCaptureManager {

    private static final String TAG = "ImageCaptureManager";
    private static ImageCaptureManager instance;

    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    public interface ImageCaptureCallback {
        void onImageCaptured(byte[] imageBytes);
        void onError(String error);
    }

    public interface VideoStreamListener {
        void onFrame(byte[] frame);
    }

    private ImageCaptureManager() {
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    public static synchronized ImageCaptureManager getInstance() {
        if (instance == null) {
            instance = new ImageCaptureManager();
        }
        return instance;
    }

    public void init(@NonNull Context context, @NonNull LifecycleOwner lifecycleOwner) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();
                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        imageCapture, imageAnalysis
                );
            } catch (Exception e) {
                Log.e(TAG, "CameraX initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void captureHighResFrame(@NonNull ImageCaptureCallback callback) {
        if (imageCapture == null) {
            callback.onError("Camera not initialized");
            return;
        }
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                byte[] data = imageToByteArray(image);
                image.close();
                callback.onImageCaptured(data);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                callback.onError("Capture failed: " + exception.getMessage());
            }
        });
    }

    public void startVideoStream(@NonNull VideoStreamListener listener) {
        if (imageAnalysis == null) return;
        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            byte[] data = imageToByteArray(imageProxy);
            if (data != null) {
                listener.onFrame(data);
            }
            imageProxy.close();
        });
    }

    public void stopVideoStream() {
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private byte[] imageToByteArray(ImageProxy image) {
        if (image.getFormat() == ImageFormat.YUV_420_888) {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
            byte[] nv21 = new byte[yBuffer.remaining() + uBuffer.remaining() + vBuffer.remaining()];
            yBuffer.get(nv21, 0, yBuffer.remaining());
            vBuffer.get(nv21, yBuffer.remaining(), vBuffer.remaining());
            uBuffer.get(nv21, yBuffer.remaining() + vBuffer.remaining(), uBuffer.remaining());
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 80, out);
            return out.toByteArray();
        } else if (image.getFormat() == ImageFormat.JPEG) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
        return null;
    }

    /**
     * Capture a screenshot of the current screen for AutoGLM control scenarios.
     * This method uses the ScreenshotManager to capture the screen content.
     */
    public void captureScreenshot(android.app.Activity activity, @NonNull ImageCaptureCallback callback) {
        ScreenshotManager.getInstance().captureScreenWithPixelCopy(activity, new ScreenshotManager.ScreenshotCallback() {
            @Override
            public void onScreenshotCaptured(byte[] imageBytes) {
                callback.onImageCaptured(imageBytes);
            }

            @Override
            public void onError(String error) {
                callback.onError("Screenshot capture failed: " + error);
            }
        });
    }

    public void release() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
    }
}
