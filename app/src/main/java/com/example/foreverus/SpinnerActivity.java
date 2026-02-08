package com.example.foreverus;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class SpinnerActivity extends BaseActivity {

    private SpinnerViewModel viewModel;
    private ChipGroup chipGroupCategories;
    private MaterialCardView resultCard;
    private TextView tvResultEmoji, tvResultText, tvPrompt;
    private com.google.android.material.button.MaterialButton btnSpin;
    private View btnManage;

    private android.os.Vibrator vibrator;
    private android.media.ToneGenerator toneGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spinner);

        vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        try {
            toneGenerator = new android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 50); // 50% volume
        } catch (Exception e) {
            e.printStackTrace();
        }

        viewModel = new ViewModelProvider(this).get(SpinnerViewModel.class);
        initViews();
        observeViewModel();

        if (getIntent().getBooleanExtra("EXTRA_REPLAY_MODE", false)) {
            setupReplayMode();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }

    private List<SpinnerItem> currentCycleItems;

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnManage = findViewById(R.id.btnManage);
        btnManage.setOnClickListener(v -> {
            startActivity(new Intent(this, ManageSpinnerListsActivity.class));
        });

        chipGroupCategories = findViewById(R.id.chipGroupCategories);
        resultCard = findViewById(R.id.resultCard);
        tvResultEmoji = findViewById(R.id.tvResultEmoji);
        tvResultText = findViewById(R.id.tvResultText);
        tvPrompt = findViewById(R.id.tvPrompt);

        btnSpin = findViewById(R.id.btnSpin);
        btnSpin.setOnClickListener(v -> viewModel.spin());
    }

    private void observeViewModel() {
        // Current Items (Single Source)
        viewModel.currentCategoryItems.observe(this, items -> {
            this.currentCycleItems = items;
        });

        // Categories
        viewModel.getAllCategories().observe(this, categories -> {
            if (categories == null)
                return;

            // Preserve selection if possible, else select first
            int checkedId = chipGroupCategories.getCheckedChipId();
            String selectedId = null;
            if (checkedId != View.NO_ID) {
                Chip c = chipGroupCategories.findViewById(checkedId);
                if (c != null)
                    selectedId = (String) c.getTag();
            }

            chipGroupCategories.removeAllViews();

            boolean isSelectionValid = false;
            if (selectedId != null) {
                for (SpinnerCategory cat : categories) {
                    if (cat.getId().equals(selectedId)) {
                        isSelectionValid = true;
                        break;
                    }
                }
            }

            // If selection is invalid (deleted) or null, default to the first one
            if (!isSelectionValid && !categories.isEmpty()) {
                selectedId = categories.get(0).getId();
                // Ensure ViewModel is synced
                viewModel.setCurrentCategory(categories.get(0));
            }

            for (SpinnerCategory cat : categories) {
                Chip chip = new Chip(this);
                chip.setText(cat.getName());
                chip.setTag(cat.getId());
                chip.setCheckable(true);
                chip.setChipBackgroundColorResource(R.color.bg_chip_state_list);

                chip.setOnClickListener(v -> {
                    viewModel.setCurrentCategory(cat);
                    resetUI();
                });

                chipGroupCategories.addView(chip);

                if (selectedId != null && selectedId.equals(cat.getId())) {
                    chip.setChecked(true);
                    // Ensure ViewModel is synced if this was a preserved selection
                    if (viewModel.currentCategoryItems.getValue() == null) {
                        viewModel.setCurrentCategory(cat);
                    }
                }
            }

            if (viewModel.isSpinning.getValue() != null && viewModel.isSpinning.getValue()) {
                // If we get a category update WHILE spinning (unlikely but possible), ensure UI
                // stays locked
                // Actually, spin logic locks UI.
            }
        });

        // Spin State
        viewModel.isSpinning.observe(this, spinning -> {
            btnSpin.setEnabled(!spinning);
            btnManage.setEnabled(!spinning);

            for (int i = 0; i < chipGroupCategories.getChildCount(); i++) {
                chipGroupCategories.getChildAt(i).setEnabled(!spinning);
            }

            if (spinning) {
                runShuffleAnimation();
            }
        });

        // Result
        // Result
        viewModel.spinResult.observe(this, result -> {
            if (result != null) {
                showResult(result);
            }
        });

        // Toast
        viewModel.toastMessage.observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.clearToast();
            }
        });
    }

    private void setupReplayMode() {
        // Hide interactive elements
        btnSpin.setVisibility(View.GONE);
        btnManage.setVisibility(View.GONE);
        tvPrompt.setVisibility(View.GONE);

        String catId = getIntent().getStringExtra("EXTRA_CATEGORY_ID");
        String itemId = getIntent().getStringExtra("EXTRA_ITEM_ID");

        if (catId != null && itemId != null) {
            viewModel.loadReplayItem(catId, itemId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBreathingAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBreathingAnimation();
    }

    private ObjectAnimator breathingAnimator;

    private void startBreathingAnimation() {
        if (breathingAnimator == null) {
            breathingAnimator = ObjectAnimator.ofFloat(btnSpin, "scaleX", 1f, 1.05f);
            breathingAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            breathingAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            breathingAnimator.setDuration(2000); // 2 seconds pulse

            // Sync scale Y
            breathingAnimator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                btnSpin.setScaleY(val);
            });
        }
        if (!breathingAnimator.isStarted()) {
            breathingAnimator.start();
        }
    }

    private void stopBreathingAnimation() {
        if (breathingAnimator != null) {
            breathingAnimator.cancel();
            btnSpin.setScaleX(1f);
            btnSpin.setScaleY(1f);
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable shuffler;

    private void runShuffleAnimation() {
        stopBreathingAnimation();

        // UI Reset
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(1f);
        resultCard.setScaleX(1f);
        resultCard.setScaleY(1f);

        tvPrompt.setVisibility(View.GONE);

        tvResultEmoji.setText("🎲");
        tvResultText.setText("Spinning...");

        // Remove any existing callbacks
        if (shuffler != null)
            handler.removeCallbacks(shuffler);

        // Decelerating Spin Logic
        final long totalDuration = 3000; // Increased to 3s for better pacing
        final long startTime = System.currentTimeMillis();

        // Start very fast, then slow down
        final int initialInterval = 80;

        shuffler = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed())
                    return;

                long elapsed = System.currentTimeMillis() - startTime;

                if (elapsed > totalDuration) {
                    return; // Wait for ViewModel to trigger showResult
                }

                if (currentCycleItems != null && !currentCycleItems.isEmpty()) {
                    int randomIndex = (int) (Math.random() * currentCycleItems.size());
                    SpinnerItem randomItem = currentCycleItems.get(randomIndex);
                    tvResultEmoji.setText(randomItem.getEmoji());
                    tvResultText.setText(randomItem.getText());

                    triggerTickFeedback();
                }

                // Subtle Shuffle Animation (Tilt & Tiny Scale)
                float rotate = (float) (Math.random() * 4 - 2); // -2 to +2 degrees
                resultCard.setRotation(rotate);
                resultCard.animate()
                        .scaleX(0.98f).scaleY(0.98f)
                        .setDuration(50)
                        .withEndAction(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                resultCard.animate().scaleX(1f).scaleY(1f).setDuration(50).start();
                            }
                        }).start();

                // Calculate next interval based on progress (Ease-Out effect)
                float progress = (float) elapsed / totalDuration;
                // Linear increase: Start 80ms -> End ~400ms
                long nextDelay = (long) (initialInterval + (350 * progress));
                // Add some randomness to the delay for "ticking wheel" realism
                nextDelay += (long) (Math.random() * 20);

                handler.postDelayed(this, nextDelay);
            }
        };
        handler.post(shuffler);
    }

    private void showResult(SpinnerItem item) {
        // Stop shuffle just in case
        if (shuffler != null) {
            handler.removeCallbacks(shuffler);
        }

        // Ensure card is visible (critical for Replay Mode)
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(1f);

        tvResultEmoji.setText(item.getEmoji());
        tvResultText.setText(item.getText());

        // Final "Pop" Reveal
        resultCard.setRotation(0f); // Reset tilt
        resultCard.animate().cancel();

        resultCard.setScaleX(0.8f);
        resultCard.setScaleY(0.8f);

        resultCard.animate()
                .scaleX(1f).scaleY(1f)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                .setDuration(400)
                .withEndAction(() -> {
                    // Only resume game loop if "Spin" button is visible (Interactive Mode)
                    if (btnSpin.getVisibility() == View.VISIBLE) {
                        btnSpin.setText("Spin Again");
                        startBreathingAnimation();
                    }
                })
                .start();

        tvPrompt.setVisibility(View.GONE);
    }

    private void triggerTickFeedback() {
        // Haptic
        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        android.os.VibrationEffect.createOneShot(10, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(10);
            }
        }
        // Sound
        if (toneGenerator != null) {
            toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 50);
        }
    }

    private void triggerSuccessFeedback() {
        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }
    }

    private void resetUI() {
        resultCard.setVisibility(View.INVISIBLE);
        tvPrompt.setVisibility(View.VISIBLE);
        btnSpin.setText("Spin");
    }
}
