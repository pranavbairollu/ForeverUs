package com.example.foreverus;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.foreverus.databinding.ActivityLetterViewBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LetterViewActivity extends BaseActivity {

    private static final String TAG = "LetterViewActivity";
    private ActivityLetterViewBinding binding;
    private LetterViewModel letterViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);
        binding = ActivityLetterViewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        String relationshipId = getIntent().getStringExtra("relationshipId");
        String letterId = getIntent().getStringExtra("letterId");

        if (relationshipId == null || letterId == null) {
            Log.e(TAG, "RelationshipId or LetterId is null.");
            showErrorState(getString(R.string.error_loading_letter));
            return;
        }

        LetterViewModelFactory factory = new LetterViewModelFactory(getApplication(), relationshipId, letterId);
        letterViewModel = new ViewModelProvider(this, factory).get(LetterViewModel.class);

        letterViewModel.getLetter().observe(this, resource -> {
            if (resource == null)
                return;

            switch (resource.status) {
                case LOADING:
                    // Show a loading indicator if needed
                    break;
                case SUCCESS:
                    if (resource.data != null) {
                        handleLetter(resource.data);
                    } else {
                        showErrorState(getString(R.string.error_letter_not_found));
                    }
                    break;
                case ERROR:
                    showErrorState(resource.message);
                    break;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleLetter(Letter letter) {
        Date openDate = letter.getOpenDate();
        Date now = new Date();

        // 1. Apply Theme
        String theme = letter.getTheme();
        int themeResId = R.drawable.stationery_classic;
        int textColor = android.R.color.black;

        if ("midnight".equals(theme)) {
            themeResId = R.drawable.stationery_midnight;
            textColor = android.R.color.white;
        } else if ("romance".equals(theme)) {
            themeResId = R.drawable.stationery_romance;
            textColor = R.color.text_primary;
        }

        binding.letterStationeryContainer.setBackgroundResource(themeResId);
        binding.letterMessageTextView.setTextColor(getColor(textColor));
        binding.letterTitleTextView.setTextColor(getColor(textColor));

        // 2. Lock Logic
        boolean isLocked = openDate != null && now.before(openDate);

        if (isLocked) {
            // LOCKED STATE
            binding.letterContentGroup.setVisibility(View.GONE);
            binding.envelopeOverlay.setVisibility(View.VISIBLE);

            SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_month_day_year),
                    Locale.getDefault());
            binding.timeCapsuleTitle.setText("Sealed until " + sdf.format(openDate));
            binding.btnOpenSeal.setVisibility(View.GONE);
            binding.waxSealImage.setAlpha(1.0f);
        } else {
            // UNLOCKED STATE - Animation
            // Show envelope first, then allow opening
            binding.envelopeOverlay.setVisibility(View.VISIBLE);
            binding.letterContentGroup.setVisibility(View.GONE); // Hidden initially

            binding.timeCapsuleTitle.setText("For Your Eyes Only");
            binding.timeCapsuleSubtitle.setText("The time has come.");
            binding.waxSealImage.setImageResource(R.drawable.ic_baseline_lock_open_24);
            binding.btnOpenSeal.setVisibility(View.VISIBLE);
            // VISIBILITY FIX: Enforce Red Background + White Text
            binding.btnOpenSeal.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_red_dark)));
            binding.btnOpenSeal.setTextColor(getColor(android.R.color.white));

            View.OnClickListener openListener = v -> {
                // ANIMATION
                binding.waxSealImage.animate()
                        .scaleX(1.5f).scaleY(1.5f).alpha(0f)
                        .setDuration(500)
                        .withEndAction(() -> {
                            binding.envelopeOverlay.animate()
                                    .translationY(binding.envelopeOverlay.getHeight())
                                    .alpha(0f)
                                    .setDuration(800)
                                    .withEndAction(() -> {
                                        binding.envelopeOverlay.setVisibility(View.GONE);
                                        binding.letterContentGroup.setVisibility(View.VISIBLE);
                                        binding.letterContentGroup.setAlpha(0f);
                                        binding.letterContentGroup.animate().alpha(1f).setDuration(500).start();

                                        binding.letterContentGroup.animate().alpha(1f).setDuration(500).start();

                                        // Romantic Animation
                                        FallingPetalsView petals = findViewById(R.id.fallingPetalsView);
                                        if (petals != null)
                                            petals.startShower();

                                        // Mark as opened in DB
                                        if (!letter.isOpened()) {
                                            letterViewModel.markLetterAsOpened(letter);
                                        }
                                    }).start();
                        }).start();
            };

            binding.btnOpenSeal.setOnClickListener(openListener);
            binding.waxSealImage.setOnClickListener(openListener);

            // Populate Content
            binding.letterTitleTextView.setText(letter.getTitle());
            binding.letterMessageTextView.setText(letter.getMessage());

            if (letter.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_month_day_year),
                        Locale.getDefault());
                binding.letterDateTextView
                        .setText(getString(R.string.sent_on_date, sdf.format(letter.getTimestamp().toDate())));
            }

            // --- Media Rendering ---
            if (letter.getMediaUrl() != null && !letter.getMediaUrl().isEmpty()) {
                binding.letterImageCard.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(this).load(letter.getMediaUrl()).into(binding.letterImageView);
                binding.letterImageView.setOnClickListener(v -> showFullScreenImage(letter.getMediaUrl()));
            } else {
                binding.letterImageCard.setVisibility(View.GONE);
            }

            if (letter.getAudioUrl() != null && !letter.getAudioUrl().isEmpty()) {
                binding.letterAudioPlayer.setVisibility(View.VISIBLE);
                setupAudioPlayer(letter.getAudioUrl());
            } else {
                binding.letterAudioPlayer.setVisibility(View.GONE);
            }

            handleIntimacyFeatures(letter);
        }
    }

    // Audio Logic
    private android.media.MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private boolean isPreparing = false;

    private void setupAudioPlayer(String url) {
        binding.btnPlayLetterAudio.setOnClickListener(v -> {
            if (isPreparing)
                return; // Prevent double taps

            if (isPlaying) {
                stopPlaying();
            } else {
                startPlaying(url);
            }
        });
    }

    private void startPlaying(String url) {
        try {
            if (mediaPlayer == null)
                mediaPlayer = new android.media.MediaPlayer();
            else
                mediaPlayer.reset();

            isPreparing = true;
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                if (isFinishing() || isDestroyed() || mediaPlayer == null)
                    return;
                isPreparing = false;
                mp.start();
                isPlaying = true;
                binding.btnPlayLetterAudio.setImageResource(R.drawable.ic_baseline_stop_24);
            });
            mediaPlayer.setOnCompletionListener(mp -> stopPlaying());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer Error: " + what + ", " + extra);
                isPreparing = false;
                stopPlaying();
                return true; // Handled
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to play letter audio", e);
            Toast.makeText(this, "Could not play audio", Toast.LENGTH_SHORT).show();
            isPreparing = false;
            stopPlaying();
        }
    }

    private void stopPlaying() {
        if (mediaPlayer != null) {
            try {
                if (isPlaying || isPreparing) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
            } catch (IllegalStateException e) {
                // Ignore, player might be in bad state
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
        isPlaying = false;
        isPreparing = false;
        binding.btnPlayLetterAudio.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isPlaying = false;
        isPreparing = false;
        binding.btnPlayLetterAudio.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
    }

    // --- Intimacy Features ---

    private void handleIntimacyFeatures(Letter letter) {
        // 1. Identity Check
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser();
        if (currentUser == null)
            return;
        String myId = currentUser.getUid();

        boolean isReceiver = myId.equals(letter.getToId());
        boolean isSender = myId.equals(letter.getFromId());

        // 2. Read Receipt (For Receiver)
        if (isReceiver && letter.getReadTimestamp() == null) {
            letterViewModel.markAsRead(letter.getRelationshipId(), letter.getLetterId());
        }

        // 3. UI Setup

        // REACTION SETUP
        setupReactions(letter, isReceiver, isSender);

        // REPLY SETUP
        setupReply(letter, isReceiver, isSender);

        // READ STATUS (For Sender)
        if (isSender && letter.getReadTimestamp() != null) {
            // Example: "Opened just now" or "Opened on [Date]"
            // For simplicity, just appending to subtitle or date text, or Toast?
            // User requested poetic status. Let's append to Date for now as there isn't a
            // dedicated ReadStatus View in my XML edit yet.
            // Actually, I didn't add a ReadStatus TextView in XML. I'll add it
            // programmatically or append to existing.
            // Let's use the Subtitle "For Your Eyes Only" -> "Opened by Partner"
            binding.timeCapsuleSubtitle.setText(getPoeticReadTime(letter.getReadTimestamp()));
        }
    }

    private void setupReactions(Letter letter, boolean isReceiver, boolean isSender) {
        if (isReceiver) {
            binding.reactionLabel.setVisibility(View.VISIBLE);
            binding.reactionChipGroup.setVisibility(View.VISIBLE);
            binding.reactionDisplay.setVisibility(View.GONE);
            binding.reactionStatusText.setVisibility(View.GONE);

            // Populate Chips if empty
            if (binding.reactionChipGroup.getChildCount() == 0) {
                String[] emojis = { "❤️", "😢", "😊", "😭", "🫂" };
                for (String emoji : emojis) {
                    com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
                    chip.setText(emoji);
                    chip.setCheckable(true);
                    chip.setClickable(true);
                    binding.reactionChipGroup.addView(chip);

                    chip.setOnClickListener(v -> {
                        letterViewModel.updateReaction(letter.getRelationshipId(), letter.getLetterId(), emoji);
                    });
                }
            }

            // Set Selection
            if (letter.getReaction() != null) {
                for (int i = 0; i < binding.reactionChipGroup.getChildCount(); i++) {
                    com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) binding.reactionChipGroup
                            .getChildAt(i);
                    chip.setChecked(chip.getText().toString().equals(letter.getReaction()));
                }
            }

        } else if (isSender) {
            binding.reactionLabel.setVisibility(View.GONE);
            binding.reactionChipGroup.setVisibility(View.GONE);

            if (letter.getReaction() != null) {
                binding.reactionDisplay.setText(letter.getReaction());
                binding.reactionDisplay.setVisibility(View.VISIBLE);
                binding.reactionStatusText.setVisibility(View.VISIBLE);
                // "Partner reacted"
                binding.reactionStatusText.setText(getString(R.string.partner_reacted_status, "Partner")); // Use string
                                                                                                           // resource
                                                                                                           // or
                                                                                                           // hardcode
                                                                                                           // for now
                binding.reactionStatusText.setText("Partner reacted");
            } else {
                binding.reactionDisplay.setVisibility(View.GONE);
                binding.reactionStatusText.setVisibility(View.GONE);
            }
        }
    }

    private void showFullScreenImage(String imageUrl) {
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_fullscreen_image);

        android.widget.ImageView fullscreenView = dialog.findViewById(R.id.fullscreenImageView);
        if (fullscreenView != null) {
            com.bumptech.glide.Glide.with(this)
                    .load(imageUrl)
                    .fitCenter()
                    .into(fullscreenView);

            fullscreenView.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void setupReply(Letter letter, boolean isReceiver, boolean isSender) {
        if (isReceiver) {
            if (letter.getReplyContent() != null) {
                // Already replied -> Show Card, Hide Button
                binding.btnWriteReply.setVisibility(View.GONE);
                binding.replyCard.setVisibility(View.VISIBLE);
                binding.replyContentText.setText(letter.getReplyContent());
            } else {
                // Can reply -> Show Button, Hide Card
                binding.btnWriteReply.setVisibility(View.VISIBLE);
                binding.replyCard.setVisibility(View.GONE);

                binding.btnWriteReply.setOnClickListener(v -> showReplyDialog(letter));
            }
        } else if (isSender) {
            binding.btnWriteReply.setVisibility(View.GONE);
            if (letter.getReplyContent() != null) {
                binding.replyCard.setVisibility(View.VISIBLE);
                binding.replyContentText.setText(letter.getReplyContent());
            } else {
                binding.replyCard.setVisibility(View.GONE);
            }
        }
    }

    private void showReplyDialog(Letter letter) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Write your reply...");
        input.setMinLines(3);
        input.setGravity(android.view.Gravity.TOP); // Start text at top

        // Wrap in container for margin
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 50;
        params.rightMargin = 50;
        input.setLayoutParams(params);
        container.addView(input);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Write a Reply")
                .setMessage("You can only send one reply. Make it count.")
                .setView(container)
                .setPositiveButton("Send", (dialog, which) -> {
                    String reply = input.getText().toString().trim();
                    if (!reply.isEmpty()) {
                        letterViewModel.updateReply(letter.getRelationshipId(), letter.getLetterId(), reply);
                        Toast.makeText(this, "Reply sent", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getPoeticReadTime(Date date) {
        if (date == null)
            return "";
        long diff = new Date().getTime() - date.getTime();
        long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff);

        if (minutes < 1)
            return "Opened just now \uD83D\uDC9E"; // 💞
        if (minutes < 60)
            return "Opened " + minutes + "m ago";
        if (minutes < 1440)
            return "Opened today";
        return "Opened on " + new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
    }

    private void showErrorState(String errorMessage) {
        binding.letterContentGroup.setVisibility(View.GONE);
        binding.envelopeOverlay.setVisibility(View.GONE);
        binding.errorGroup.setVisibility(View.VISIBLE);
        binding.errorText.setText(errorMessage);
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }
}
