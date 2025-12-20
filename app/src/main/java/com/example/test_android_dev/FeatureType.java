package com.example.test_android_dev;

import androidx.annotation.DrawableRes;

public enum FeatureType {
    NAVIGATION("路径导航", R.drawable.ic_navigation),
    OBSTACLE_AVOIDANCE("实时避障", R.drawable.ic_obstacle),
    QA_VOICE("语音问答", R.drawable.ic_qa_voice),
    OCR("文字识别", R.drawable.ic_ocr),
    SCENE_DESCRIPTION("场景描述", R.drawable.ic_scene),
    UNKNOWN("未知功能", R.drawable.ic_qa_voice); // Default icon

    private final String description;
    @DrawableRes
    private final int iconResId;

    FeatureType(String description, @DrawableRes int iconResId) {
        this.description = description;
        this.iconResId = iconResId;
    }

    public String getDescription() {
        return description;
    }

    public int getIconResId() {
        return iconResId;
    }
}