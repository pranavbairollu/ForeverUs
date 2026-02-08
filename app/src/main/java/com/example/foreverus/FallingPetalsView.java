package com.example.foreverus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FallingPetalsView extends View {

    private static class Petal {
        float x, y;
        float speed;
        float angle;
        float rotationSpeed;
        int color;
        int size;
    }

    private final List<Petal> petals = new ArrayList<>();
    private final Paint paint = new Paint();
    private final Random random = new Random();
    private boolean isAnimating = false;

    public FallingPetalsView(Context context) { super(context); init(); }
    public FallingPetalsView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
    }

    public void startShower() {
        if (!shouldAnimate()) return; // REFINEMENT: Accessibility Check
        
        petals.clear();
        int count = 50; // Number of petals
        for (int i = 0; i < count; i++) {
            Petal p = new Petal();
            p.x = random.nextInt(getResources().getDisplayMetrics().widthPixels);
            p.y = -random.nextInt(1000); // Start above screen
            p.speed = 5 + random.nextInt(10);
            p.angle = random.nextInt(360);
            p.rotationSpeed = -5 + random.nextInt(10);
            p.size = 20 + random.nextInt(20);
            
            // Randomize Colors: Pinks and Reds
            int r = 200 + random.nextInt(55);
            int g = random.nextInt(50); 
            int b = random.nextInt(50) + 50; 
            // Better Palette:
            // Deep Red: 180, 0, 0
            // Light Pink: 255, 182, 193
            if (random.nextBoolean()) {
                p.color = Color.rgb(255, 105 + random.nextInt(100), 180); // Pinkish
            } else {
                p.color = Color.rgb(220, 20, 60); // Crimson
            }
            petals.add(p);
        }
        isAnimating = true;
        setVisibility(VISIBLE);
        invalidate();
        
        // Auto stop after 5 seconds to save battery
        postDelayed(this::stopShower, 5000);
    }

    public void stopShower() {
        isAnimating = false;
        petals.clear();
        setVisibility(GONE);
    }
    
    private boolean shouldAnimate() {
        // Check 1: Animator Duration Scale (Developer Options / Battery Saver)
        float durationScale = android.provider.Settings.Global.getFloat(
            getContext().getContentResolver(), 
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        );
        if (durationScale == 0f) return false;
        
        // Check 2: Transition Scale (Often linked to "Remove Animations")
        float transitionScale = android.provider.Settings.Global.getFloat(
            getContext().getContentResolver(),
            android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
        );
        if (transitionScale == 0f) return false;

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isAnimating) return;

        for (Petal p : petals) {
            paint.setColor(p.color);
            
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.angle);
            // Draw Petal (Oval)
            canvas.drawOval(-p.size/2f, -p.size/2f, p.size/2f, p.size/2f, paint);
            canvas.restore();

            // Update Physics
            p.y += p.speed;
            p.angle += p.rotationSpeed;
            p.x += Math.sin(Math.toRadians(p.angle)) * 2; // Sway
            
            // Loop? No, user requested "Short lived".
            // If we wanted loop, we'd reset y. 
            // Here we just let them fall off screen.
        }

        invalidate();
    }
}
