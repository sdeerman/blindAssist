package com.example.test_android_dev;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility service for AutoGLM automation control.
 * This service must be enabled in the device's accessibility settings.
 */
public class AutoGLMAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Handle accessibility events if needed
    }

    @Override
    public void onInterrupt() {
        // Handle service interruption
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Initialize AutoGLMService with this accessibility service
        AutoGLMService.getInstance().setAccessibilityService(this);
    }
}