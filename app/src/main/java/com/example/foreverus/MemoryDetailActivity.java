package com.example.foreverus;

import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.foreverus.databinding.ActivityMemoryDetailBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemoryDetailActivity extends BaseActivity {

    public static final String EXTRA_IMAGE_URL = "extra_image_url";
    public static final String EXTRA_TRANSITION_NAME = "extra_transition_name";

    private ActivityMemoryDetailBinding binding;
    private MemoriesViewModel memoriesViewModel;
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();
    private boolean isPlaying = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);
        binding = ActivityMemoryDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        supportPostponeEnterTransition();

        memoriesViewModel = new ViewModelProvider.AndroidViewModelFactory(getApplication())
                .create(MemoriesViewModel.class);

        String transitionName = getIntent().getStringExtra(EXTRA_TRANSITION_NAME);
        if (transitionName != null) {
            ViewCompat.setTransitionName(binding.viewPager, transitionName);
        }

        // Image Logic
        String singleImageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        ArrayList<String> mediaUrls = getIntent().getStringArrayListExtra("extra_media_urls");
        List<String> imagesToDisplay = new ArrayList<>();

        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            imagesToDisplay.addAll(mediaUrls);
        } else if (singleImageUrl != null) {
            imagesToDisplay.add(singleImageUrl);
        }

        ImageSliderAdapter adapter = new ImageSliderAdapter(imagesToDisplay);
        binding.viewPager.setAdapter(adapter);

        // Preload first image for transition smoothness
        if (!imagesToDisplay.isEmpty()) {
            // Trick: we are using ViewPager, but we want transition on the first item?
            // Shared Element Transition with ViewPager2 is tricky.
            // For now, we just start the transition once layout happens.
            binding.viewPager.post(() -> supportStartPostponedEnterTransition());
        } else {
            supportStartPostponedEnterTransition();
        }

        // Text Logic
        String title = getIntent().getStringExtra("extra_title");
        String description = getIntent().getStringExtra("extra_description");

        binding.detailTitle.setText(title != null ? title : "");
        binding.detailDescription.setText(description != null ? description : "");
        if (description == null || description.isEmpty())
            binding.detailDescription.setVisibility(View.GONE);

        // Audio Logic
        String audioUrl = getIntent().getStringExtra("extra_audio_url");
        if (audioUrl != null) {
            setupAudioPlayer(audioUrl);
        }

        // Delete Logic
        String memoryId = getIntent().getStringExtra("extra_memory_id");
        String relationshipId = getIntent().getStringExtra("extra_relationship_id");

        if (memoryId != null && relationshipId != null) {
            binding.deleteButton.setOnClickListener(v -> confirmDelete(memoryId, relationshipId));
        } else {
            binding.deleteButton.setVisibility(View.GONE);
        }

        // Spinner Replay Logic
        String spinnerCatId = getIntent().getStringExtra("extra_spinner_category_id");
        String spinnerItemId = getIntent().getStringExtra("extra_spinner_item_id");

        if (spinnerCatId != null && spinnerItemId != null) {
            binding.btnReplaySpin.setVisibility(View.VISIBLE);
            binding.btnReplaySpin.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, SpinnerActivity.class);
                intent.putExtra("EXTRA_REPLAY_MODE", true);
                intent.putExtra("EXTRA_CATEGORY_ID", spinnerCatId);
                intent.putExtra("EXTRA_ITEM_ID", spinnerItemId);
                startActivity(intent);
            });
        }

        binding.closeButton.setOnClickListener(v -> supportFinishAfterTransition());
    }

    private void setupAudioPlayer(String audioUrl) {
        binding.audioPlayerLayout.setVisibility(View.VISIBLE);
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                binding.audioSeekBar.setMax(mp.getDuration());
                updateTimeText(mp.getCurrentPosition(), mp.getDuration());
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                binding.audioPlayPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                binding.audioSeekBar.setProgress(0);
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading audio", Toast.LENGTH_SHORT).show();
        }

        binding.audioPlayPauseButton.setOnClickListener(v -> {
            if (isPlaying) {
                mediaPlayer.pause();
                binding.audioPlayPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
            } else {
                mediaPlayer.start();
                binding.audioPlayPauseButton.setImageResource(R.drawable.ic_baseline_pause_24);
                updateSeekBar();
            }
            isPlaying = !isPlaying;
        });

        binding.audioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
                if (mediaPlayer != null) {
                    updateTimeText(progress, mediaPlayer.getDuration());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void updateSeekBar() {
        if (mediaPlayer != null && isPlaying) {
            binding.audioSeekBar.setProgress(mediaPlayer.getCurrentPosition());
            handler.postDelayed(this::updateSeekBar, 1000);
        }
    }

    private void updateTimeText(int currentMs, int totalMs) {
        int currentSec = currentMs / 1000;
        int totalSec = totalMs / 1000;
        binding.audioTimeText.setText(String.format("%02d:%02d / %02d:%02d",
                currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60));
    }

    private void confirmDelete(String memoryId, String relationshipId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Memory")
                .setMessage("Are you sure you want to delete this memory? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    memoriesViewModel.deleteMemory(memoryId, relationshipId);
                    Toast.makeText(this, "Memory deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
