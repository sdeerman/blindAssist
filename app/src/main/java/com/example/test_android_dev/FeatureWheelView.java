package com.example.test_android_dev;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * 自定义功能轮盘 View：
 * - 支持单指上下滑动切换功能
 * - 双击确认选择
 * - 在屏幕上绘制当前选中的功能名称和图标
 */
public class FeatureWheelView extends View {

    public interface OnFeatureSelectedListener {
        void onItemSelected(FeatureType feature);
        void onItemConfirmed(FeatureType feature);
    }

    private final FeatureType[] features = {
            FeatureType.NAVIGATION,
            FeatureType.OBSTACLE_AVOIDANCE,
            FeatureType.QA_VOICE,
            FeatureType.OCR,
            FeatureType.SCENE_DESCRIPTION
    };

    private int currentIndex = 0;
    private GestureDetector gestureDetector;
    private OnFeatureSelectedListener listener;

    private Paint textPaint;
    private Rect textBounds = new Rect();
    private float scrollAccumulator = 0f;
    private boolean isScrolling = false; // Flag to track scroll state

    public FeatureWheelView(Context context) {
        super(context);
        init(context);
    }

    public FeatureWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // Init paints for drawing
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24, getResources().getDisplayMetrics()));
        textPaint.setTextAlign(Paint.Align.CENTER);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                isScrolling = true; // A scroll gesture is in progress
                scrollAccumulator += distanceY;
                int scrollThreshold = 400;

                if (Math.abs(scrollAccumulator) > scrollThreshold) {
                    if (scrollAccumulator > 0) {
                        currentIndex = (currentIndex + 1) % features.length;
                    } else {
                        currentIndex = (currentIndex - 1 + features.length) % features.length;
                    }
                    // Visual update only, no voice announcement here
                    invalidate();
                    scrollAccumulator = 0f;
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (listener != null) {
                    listener.onItemConfirmed(features[currentIndex]);
                }
                return true;
            }
        });

        setClickable(true);
        setFocusable(true);
    }

    public void setOnFeatureSelectedListener(OnFeatureSelectedListener listener) {
        this.listener = listener;
        // The initial announcement is now handled by MainActivity.
        // We still invalidate to draw the view for the first time.
        invalidate();
    }

    public FeatureType getCurrentFeature() {
        return features[currentIndex];
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean gestureHandled = gestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                scrollAccumulator = 0f;
                isScrolling = false;
                break;
            case MotionEvent.ACTION_UP:
                if (isScrolling) {
                    // Announce only when the scroll gesture is finished
                    if (listener != null) {
                        listener.onItemSelected(features[currentIndex]);
                    }
                    isScrolling = false;
                }
                break;
        }
        return gestureHandled || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        canvas.drawColor(Color.DKGRAY);

        if (features.length == 0) return;
        
        FeatureType currentFeature = features[currentIndex];

        Drawable icon = ContextCompat.getDrawable(getContext(), currentFeature.getIconResId());
        if (icon != null) {
            int iconSize = width / 3;
            int iconLeft = (width - iconSize) / 2;
            int iconTop = height / 2 - iconSize;
            int iconRight = iconLeft + iconSize;
            int iconBottom = iconTop + iconSize;
            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            icon.draw(canvas);
        }

        String currentFeatureText = currentFeature.getDescription();
        textPaint.getTextBounds(currentFeatureText, 0, currentFeatureText.length(), textBounds);
        float textX = width / 2f;
        float textY = height / 2f + (width / 4f);

        canvas.drawText(currentFeatureText, textX, textY, textPaint);
    }
}
