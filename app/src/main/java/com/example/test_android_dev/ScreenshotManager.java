package com.example.test_android_dev;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Manages screenshot capture functionality for AutoGLM integration.
 * Handles both full screen and view-specific screenshots.
 */
public class ScreenshotManager {
    private static final String TAG = "ScreenshotManager";
    private static ScreenshotManager instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ScreenshotManager() {}

    public static synchronized ScreenshotManager getInstance() {
        if (instance == null) {
            instance = new ScreenshotManager();
        }
        return instance;
    }

    /**
     * Callback interface for screenshot capture results.
     */
    public interface ScreenshotCallback {
        void onScreenshotCaptured(byte[] imageBytes);
        void onError(String error);
    }

    /**
     * Capture a screenshot of the entire screen (requires appropriate permissions).
     * This method works on Android 5.0+ but may require special permissions on newer versions.
     */
    public void captureScreen(Activity activity, @NonNull ScreenshotCallback callback) {
        if (activity == null || activity.getWindow() == null) {
            callback.onError("Activity or window is null");
            return;
        }

        Window window = activity.getWindow();
        View decorView = window.getDecorView();

        // Create bitmap with same dimensions as the decor view
        Bitmap bitmap = Bitmap.createBitmap(
            decorView.getWidth(),
            decorView.getHeight(),
            Bitmap.Config.ARGB_8888
        );

        // Draw the view into the bitmap
        Canvas canvas = new Canvas(bitmap);
        decorView.draw(canvas);

        // Convert to JPEG bytes
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        byte[] imageBytes = stream.toByteArray();

        try {
            stream.close();
            bitmap.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error closing stream", e);
        }

        callback.onScreenshotCaptured(imageBytes);
    }

    /**
     * Capture a screenshot using PixelCopy API (Android 8.0+).
     * This is more reliable for capturing system UI elements.
     */
    public void captureScreenWithPixelCopy(Activity activity, @NonNull ScreenshotCallback callback) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            // Fallback to regular screenshot capture on older versions
            captureScreen(activity, callback);
            return;
        }

        if (activity == null || activity.getWindow() == null) {
            callback.onError("Activity or window is null");
            return;
        }

        Window window = activity.getWindow();
        View decorView = window.getDecorView();

        Bitmap bitmap = Bitmap.createBitmap(
            decorView.getWidth(),
            decorView.getHeight(),
            Bitmap.Config.ARGB_8888
        );

        PixelCopy.request(window, bitmap, copyResult -> {
            if (copyResult == PixelCopy.SUCCESS) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                byte[] imageBytes = stream.toByteArray();

                try {
                    stream.close();
                    bitmap.recycle();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing stream", e);
                }

                callback.onScreenshotCaptured(imageBytes);
            } else {
                Log.e(TAG, "PixelCopy failed with result: " + copyResult);
                bitmap.recycle();
                // Fallback to regular screenshot
                captureScreen(activity, callback);
            }
        }, mainHandler);
    }

    /**
     * Capture a specific view as a screenshot.
     */
    public byte[] captureView(View view) {
        if (view == null) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(
            view.getWidth(),
            view.getHeight(),
            Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        byte[] imageBytes = stream.toByteArray();

        try {
            stream.close();
            bitmap.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error closing stream", e);
        }

        return imageBytes;
    }
}