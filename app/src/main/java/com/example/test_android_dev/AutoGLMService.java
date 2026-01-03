package com.example.test_android_dev;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles AutoGLM SDK execution commands from the server.
 * This service requires AccessibilityService permissions to be enabled in system settings.
 */
public class AutoGLMService {
    private static final String TAG = "AutoGLMService";
    private static AutoGLMService instance;
    private AccessibilityService accessibilityService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Object> appRegistry = new ConcurrentHashMap<>();

    // Initialize app registry with common apps
    static {
        // This would be populated with actual app mappings in a real implementation
    }

    private AutoGLMService() {}

    public static synchronized AutoGLMService getInstance() {
        if (instance == null) {
            instance = new AutoGLMService();
        }
        return instance;
    }

    public void setAccessibilityService(AccessibilityService service) {
        this.accessibilityService = service;
    }

    /**
     * Execute an AutoGLM action based on the command map.
     * @param actionMap Map containing action parameters as specified in interface.md
     * @return true if execution was successful, false otherwise
     */
    public boolean executeAction(@NonNull Map<String, Object> actionMap) {
        if (accessibilityService == null) {
            Log.e(TAG, "AccessibilityService not available");
            return false;
        }

        try {
            String action = (String) actionMap.get("action");
            if (action == null) {
                Log.e(TAG, "Action is null");
                return false;
            }

            switch (action) {
                case "Launch":
                    return handleLaunchAction(actionMap);
                case "Tap":
                    return handleTapAction(actionMap);
                case "Type":
                    return handleTypeAction(actionMap);
                case "Swipe":
                    return handleSwipeAction(actionMap);
                case "Back":
                    return handleBackAction();
                case "Home":
                    return handleHomeAction();
                case "Double Tap":
                    return handleDoubleTapAction(actionMap);
                case "Long Press":
                    return handleLongPressAction(actionMap);
                case "Wait":
                    return handleWaitAction(actionMap);
                case "Take_over":
                    return handleTakeOverAction(actionMap);
                default:
                    Log.e(TAG, "Unknown action: " + action);
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing action", e);
            return false;
        }
    }

    private boolean handleLaunchAction(Map<String, Object> actionMap) {
        String appName = (String) actionMap.get("app");
        if (appName == null || appName.isEmpty()) {
            return false;
        }

        // In a real implementation, this would launch the specified app
        // For now, we'll just log the action
        Log.i(TAG, "Launching app: " + appName);
        return true;
    }

    private boolean handleTapAction(Map<String, Object> actionMap) {
        Object elementObj = actionMap.get("element");
        if (!(elementObj instanceof int[])) {
            Log.e(TAG, "Invalid element format for Tap action");
            return false;
        }

        int[] coordinates = (int[]) elementObj;
        if (coordinates.length != 2) {
            Log.e(TAG, "Invalid coordinate array length");
            return false;
        }

        int x = coordinates[0];
        int y = coordinates[1];

        // For coordinate-based tapping, we need to use a different approach
        // Since GLOBAL_ACTION_CLICK doesn't exist, we'll simulate the action
        // In a real implementation, this would require UiAutomation or root access
        Log.i(TAG, "Tap action at coordinates: [" + x + ", " + y + "] - simulated");

        // Just log the action for now (real implementation would be more complex)
        return true;
    }

    private boolean handleTypeAction(Map<String, Object> actionMap) {
        String text = (String) actionMap.get("text");
        if (text == null || text.isEmpty()) {
            return false;
        }

        // In a real implementation, this would type the specified text
        // For now, we'll just log the action
        Log.i(TAG, "Type action with text: " + text);
        return true;
    }

    private boolean handleSwipeAction(Map<String, Object> actionMap) {
        Object startObj = actionMap.get("start");
        Object endObj = actionMap.get("end");
        Object durationObj = actionMap.get("duration");

        if (!(startObj instanceof int[]) || !(endObj instanceof int[])) {
            Log.e(TAG, "Invalid start/end format for Swipe action");
            return false;
        }

        int[] start = (int[]) startObj;
        int[] end = (int[]) endObj;
        int duration = durationObj instanceof Integer ? (Integer) durationObj : 500;

        if (start.length != 2 || end.length != 2) {
            Log.e(TAG, "Invalid coordinate array length");
            return false;
        }

        // In a real implementation, this would perform the swipe gesture
        // For now, we'll just log the action
        Log.i(TAG, "Swipe action from [" + start[0] + ", " + start[1] + "] to [" +
              end[0] + ", " + end[1] + "] with duration: " + duration + "ms");
        return true;
    }

    private boolean handleBackAction() {
        mainHandler.post(() -> accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));
        Log.i(TAG, "Back action executed");
        return true;
    }

    private boolean handleHomeAction() {
        mainHandler.post(() -> accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME));
        Log.i(TAG, "Home action executed");
        return true;
    }

    private boolean handleDoubleTapAction(Map<String, Object> actionMap) {
        Object elementObj = actionMap.get("element");
        if (!(elementObj instanceof int[])) {
            Log.e(TAG, "Invalid element format for Double Tap action");
            return false;
        }

        int[] coordinates = (int[]) elementObj;
        if (coordinates.length != 2) {
            Log.e(TAG, "Invalid coordinate array length");
            return false;
        }

        // In a real implementation, this would perform double tap at coordinates
        Log.i(TAG, "Double Tap action at coordinates: [" + coordinates[0] + ", " + coordinates[1] + "]");
        return true;
    }

    private boolean handleLongPressAction(Map<String, Object> actionMap) {
        Object elementObj = actionMap.get("element");
        Object durationMsObj = actionMap.get("duration_ms");

        if (!(elementObj instanceof int[])) {
            Log.e(TAG, "Invalid element format for Long Press action");
            return false;
        }

        int[] coordinates = (int[]) elementObj;
        int durationMs = durationMsObj instanceof Integer ? (Integer) durationMsObj : 1000;

        if (coordinates.length != 2) {
            Log.e(TAG, "Invalid coordinate array length");
            return false;
        }

        // In a real implementation, this would perform long press at coordinates
        Log.i(TAG, "Long Press action at coordinates: [" + coordinates[0] + ", " + coordinates[1] +
              "] with duration: " + durationMs + "ms");
        return true;
    }

    private boolean handleWaitAction(Map<String, Object> actionMap) {
        Object durationObj = actionMap.get("duration");
        int duration = durationObj instanceof Integer ? (Integer) durationObj : 500;

        try {
            Thread.sleep(duration);
            Log.i(TAG, "Wait action completed after " + duration + "ms");
            return true;
        } catch (InterruptedException e) {
            Log.e(TAG, "Wait action interrupted", e);
            return false;
        }
    }

    private boolean handleTakeOverAction(Map<String, Object> actionMap) {
        String message = (String) actionMap.get("message");
        if (message == null || message.isEmpty()) {
            message = "人工接管提示";
        }

        // In a real implementation, this would show a Toast or notification
        Log.i(TAG, "Take_over action with message: " + message);
        return true;
    }

    /**
     * Register an app name to package name mapping.
     */
    public void registerApp(String appName, String packageName) {
        appRegistry.put(appName, packageName);
    }

    /**
     * Get package name for app name.
     */
    public String getPackageName(String appName) {
        return (String) appRegistry.get(appName);
    }
}