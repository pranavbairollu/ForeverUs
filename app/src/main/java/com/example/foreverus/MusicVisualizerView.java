package com.example.foreverus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * A simulated music visualizer that displays animated bars.
 * Since we don't have real audio data from YouTube IFrame,
 * we simulate the effect with random noise interpolation.
 */
public class MusicVisualizerView extends View {

    private static final int BAR_COUNT = 30; // Number of bars to draw
    private static final long ANIMATION_DELAY = 16; // ~60fps

    private final Paint paint = new Paint();
    private final float[] heights = new float[BAR_COUNT];
    private final float[] targetHeights = new float[BAR_COUNT];
    private final Random random = new Random();
    private boolean isPlaying = false;
    private int accentColor = Color.WHITE;

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) return;

            updateHeights();
            invalidate();
            postDelayed(this, ANIMATION_DELAY);
        }
    };

    public MusicVisualizerView(Context context) {
        super(context);
        init();
    }

    public MusicVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MusicVisualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setAlpha(200);
    }
    
    // Gradient Shader for "Premium" Look
    private android.graphics.Shader gradientShader;

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Create a rich gradient: Cyan -> Magenta -> Blue
        gradientShader = new android.graphics.LinearGradient(
                0, 0, w, 0,
                new int[]{0xFF00E5FF, 0xFFAA00FF, 0xFF2979FF},
                null, 
                android.graphics.Shader.TileMode.CLAMP);
        paint.setShader(gradientShader);
    }

    /* ... (rest of class same until onDraw) ... */
    
    public void start() {
        if (!isPlaying) {
            isPlaying = true;
            post(animationRunnable);
        }
    }

    public void stop() {
        isPlaying = false;
        removeCallbacks(animationRunnable);
        // Optional: Reset bars
        for (int i = 0; i < BAR_COUNT; i++) {
            targetHeights[i] = 20; 
        }
        invalidate();
    }
    
    private void updateHeights() {
        // Simple random interpolation
        for (int i = 0; i < BAR_COUNT; i++) {
            // New target occasionally
            if (random.nextInt(10) > 7) {
                 float maxHeight = getHeight() * 0.8f;
                 targetHeights[i] = random.nextFloat() * maxHeight;
                 if (targetHeights[i] < 20) targetHeights[i] = 20; // Min height
            }
            // Interpolate
            float diff = targetHeights[i] - heights[i];
            heights[i] += diff * 0.2f; // smooth
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() == 0 || getHeight() == 0) return;

        float width = getWidth();
        float height = getHeight();
        float barWidth = width / (BAR_COUNT * 1.5f); // Leave some gaps
        float gap = barWidth * 0.5f;

        float startX = (width - (BAR_COUNT * (barWidth + gap))) / 2f;
        float centerY = height / 2f;
        
        // Ensure shader is set (sometimes context recreates paint)
        if (paint.getShader() == null && gradientShader != null) {
            paint.setShader(gradientShader);
        }

        for (int i = 0; i < BAR_COUNT; i++) {
            float h = heights[i];
            float x = startX + i * (barWidth + gap);
            
            // Draw rounded bar centered vertically
            // Radius = barWidth / 2 for full pill shape
            float radius = barWidth / 2f;
            canvas.drawRoundRect(x, centerY - h / 2, x + barWidth, centerY + h / 2, radius, radius, paint);
        }
    }
}
