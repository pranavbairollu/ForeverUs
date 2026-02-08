package com.example.foreverus;

import static com.example.foreverus.FirestoreConstants.*;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.foreverus.databinding.ActivityComposeLetterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ComposeLetterActivity extends BaseActivity {

    private static final String TAG = "ComposeLetterActivity";
    private ActivityComposeLetterBinding binding;
    private MenuItem sendMenuItem;
    private ComposeLetterViewModel viewModel;

    // Media Recorders - Kept in Activity for ease of Context/Lifecycle management,
    // but controlled by VM state flags.
    private android.media.MediaRecorder mediaRecorder;
    private android.media.MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // Lock to portrait for
                                                                                              // MVP audio stability
        binding = ActivityComposeLetterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ViewModel Setup
        viewModel = new ViewModelProvider(this).get(ComposeLetterViewModel.class);

        String relationshipId = getIntent().getStringExtra(FIELD_RELATIONSHIP_ID);
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (relationshipId != null && currentUserId != null) {
            viewModel.init(relationshipId, currentUserId);
        } else {
            Toast.makeText(this, "Error: Missing Data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupUI();
        setupTemplates();
        setupObservers(); // The Core of Bulletproof Logic
    }

    private void setupTemplates() {
        String[] templates = {
                "Open when you miss me",
                "Open when you're sad",
                "Open when we fight",
                "Open when it's our anniversary",
                "Open when you need a laugh",
                "Open whenever"
        };

        for (String template : templates) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(template);
            chip.setCheckable(true);
            chip.setOnClickListener(v -> {
                // REFINEMENT: Guidance only, do not auto-fill.
                binding.titleEditText.setHint(template);
                if (binding.titleEditText.getText().length() == 0) {
                    binding.titleEditText.requestFocus();
                }
                binding.templatesChipGroup.clearCheck();
            });
            binding.templatesChipGroup.addView(chip);
        }
    }

    private void setupUI() {
        binding.selectDateButton.setOnClickListener(v -> showDatePickerDialog());
        setupOnBackPressed();
        setupMediaListeners();

        // Theme Selection
        // Theme Selection
        binding.themeChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty())
                return;
            int id = checkedIds.get(0);
            if (id == R.id.chipClassic) {
                applyThemeColors("classic");
            } else if (id == R.id.chipMidnight) {
                applyThemeColors("midnight");
            } else if (id == R.id.chipRomance) {
                applyThemeColors("romance");
            }
        });

        // Initial Theme Apply
        applyThemeColors(viewModel.getTheme());
    }

    private void applyThemeColors(String theme) {
        viewModel.setTheme(theme);
        int textColor;
        int hintColor;
        int backgroundRes;

        switch (theme) {
            case "midnight":
                backgroundRes = R.drawable.stationery_midnight;
                textColor = getColor(android.R.color.white);
                hintColor = getColor(android.R.color.darker_gray);
                break;
            case "romance":
                backgroundRes = R.drawable.stationery_romance; // Light Pink
                textColor = getColor(android.R.color.black); // Dark text for contrast
                hintColor = getColor(android.R.color.darker_gray);
                break;
            case "classic":
            default:
                backgroundRes = R.drawable.stationery_classic;
                textColor = getColor(android.R.color.black);
                hintColor = getColor(android.R.color.darker_gray);
                break;
        }

        binding.letterContainer.setBackgroundResource(backgroundRes);

        // Apply to Title
        binding.titleEditText.setTextColor(textColor);
        binding.titleEditText.setHintTextColor(hintColor);

        // Apply to Body
        binding.contentEditText.setTextColor(textColor);
        binding.contentEditText.setHintTextColor(hintColor);

        // Apply to Voice Note Label (if visible or for future)
        // Check if binding has tvAudioLabel (referencing ID added in Step 740)
        // Since we are using View Binding, and we just added the ID to XML,
        // the binding class won't have it generated until build.
        // Safest approach without rebuild is finding by ID or assuming binding might
        // fail?
        // No, I can't use binding.tvAudioLabel until I rebuild.
        // BUT I can use findViewById since I'm in an Activity.
        android.widget.TextView tvAudioLabel = findViewById(R.id.tvAudioLabel);
        if (tvAudioLabel != null) {
            tvAudioLabel.setTextColor(textColor);
        }
    }

    private void setupObservers() {
        viewModel.getState().observe(this, viewState -> {
            // Loading Handling
            boolean isLoading = (viewState.status == ComposeLetterViewModel.Status.LOADING);
            showLoading(isLoading);

            if (isLoading && viewState.message != null) {
                // Optional: Update loading text
            }

            // Draft Restore
            if (viewState.message != null && viewState.message.equals("Draft Restored")) {
                if (viewState.draftTitle != null && !viewState.draftTitle.isEmpty()) {
                    binding.titleEditText.setText(viewState.draftTitle);
                }
                if (viewState.draftContent != null && !viewState.draftContent.isEmpty()) {
                    binding.contentEditText.setText(viewState.draftContent);
                }

                // REFINEMENT: Clear draft indication
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("Editing Draft");
                    getSupportActionBar().setSubtitle("Progress is saved locally");
                }
                com.google.android.material.snackbar.Snackbar
                        .make(binding.getRoot(), "Welcome back. We kept your letter safe.",
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .show();
            }

            // Image Removal Handling
            if (viewState.message != null && viewState.message.equals("Image Removed")) {
                binding.imageAttachmentContainer.setVisibility(View.GONE);
                binding.btnAddPhoto.setText("Add Photo");
            }

            // Success Handling
            if (viewState.status == ComposeLetterViewModel.Status.SUCCESS) {
                // Hiding keyboard if open
                View view = this.getCurrentFocus();
                if (view != null) {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(
                            android.content.Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                // Emotional Completion Flow
                // COMPLETION ANIMATION
                animateLetterSealing();
            }

            // Error Handling
            if (viewState.status == ComposeLetterViewModel.Status.ERROR) {
                Toast.makeText(this, viewState.message != null ? viewState.message : "Error", Toast.LENGTH_LONG)
                        .show();
            }

            // Audio UI State
            if (viewState.status == ComposeLetterViewModel.Status.RECORDING) {
                binding.btnRecordAudio.setText("Stop Recording");
                binding.btnRecordAudio.setIconResource(R.drawable.ic_baseline_stop_24);
                binding.btnRecordAudio.setBackgroundColor(getColor(android.R.color.holo_red_light));
                binding.audioPlayerLayout.setVisibility(View.GONE);
            } else if (viewState.status == ComposeLetterViewModel.Status.PLAYING) {
                binding.btnPlayAudio.setImageResource(R.drawable.ic_baseline_stop_24);
            } else {
                // IDLE - Reset Audio UIs
                // If we have audio file
                if (viewModel.getAudioFilePath() != null) {
                    binding.btnRecordAudio.setVisibility(View.GONE);
                    binding.audioPlayerLayout.setVisibility(View.VISIBLE);
                    binding.btnPlayAudio.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
                } else {
                    binding.btnRecordAudio.setVisibility(View.VISIBLE);
                    binding.btnRecordAudio.setText("Add Voice");
                    binding.btnRecordAudio.setIconResource(R.drawable.ic_baseline_mic_24);
                    binding.btnRecordAudio.setBackgroundColor(
                            getColor(com.google.android.material.R.color.m3_ref_palette_secondary90));
                    binding.audioPlayerLayout.setVisibility(View.GONE);
                }
            }
        });

        // Data Restoration (For Rotation)
        if (viewModel.getSelectedImageUri() != null)

        {
            binding.imageAttachmentContainer.setVisibility(View.VISIBLE);
            Glide.with(this).load(viewModel.getSelectedImageUri()).into(binding.attachmentImageView);
            binding.btnAddPhoto.setText("Change Photo");
        }

        if (viewModel.getOpenDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
            binding.selectedDateTextView.setText(getString(R.string.opens_on, sdf.format(viewModel.getOpenDate())));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.compose_letter_menu, menu);
        sendMenuItem = menu.findItem(R.id.action_send_letter);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_send_letter) {
            sendLetter();
            return true;
        } else if (item.getItemId() == R.id.action_new_letter) {
            // Clear current content but keep screen open
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Start New Letter?")
                    .setMessage("This will clear your current text. The sent letter is safe.")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        viewModel.clearDraft();
                        binding.titleEditText.setText("");
                        binding.contentEditText.setText("");
                        viewModel.removeImage();
                        viewModel.deleteAudio();
                        viewModel.setOpenDate(null);
                        binding.selectedDateTextView.setText("Select Date");
                        Toast.makeText(this, "Ready for a new letter.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- Actions ---

    private void showLoading(boolean isLoading) {
        if (sendMenuItem != null) {
            sendMenuItem.setEnabled(!isLoading);
            if (isLoading) {
                android.widget.ProgressBar loader = new android.widget.ProgressBar(this);
                loader.setIndeterminateDrawable(
                        androidx.core.content.ContextCompat.getDrawable(this, R.drawable.progress_indeterminate_warm));
                sendMenuItem.setActionView(loader);
            } else {
                sendMenuItem.setActionView(null);
            }
        }

        // Disable inputs instead of blocking screen
        binding.titleEditText.setEnabled(!isLoading);
        binding.contentEditText.setEnabled(!isLoading);
        binding.themeChipGroup.setEnabled(!isLoading);
        binding.btnAddPhoto.setEnabled(!isLoading);
        binding.btnRecordAudio.setEnabled(!isLoading);
        binding.selectDateButton.setEnabled(!isLoading);
    }

    // --- Media Listeners ---

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    viewModel.setImageFromGallery(uri); // Process & Cache for persistence
                    // Immediate UI update handled by Observer logic or manual here?
                    // Ideally Observer, but for simplicity we trigger update via Observers usually.
                    // Let's just update View manually to be snappy OR let Observer handle it on
                    // re-bind.
                    // For now:
                    binding.imageAttachmentContainer.setVisibility(View.VISIBLE);
                    Glide.with(this).load(uri).into(binding.attachmentImageView);
                    binding.btnAddPhoto.setText("Change Photo");
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted)
                    pickImageLauncher.launch("image/*");
                else
                    Toast.makeText(this, "Permission needed", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<String> audioPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted)
                    toggleRecording();
                else
                    Toast.makeText(this, "Permission needed", Toast.LENGTH_SHORT).show();
            });

    private void setupMediaListeners() {
        binding.btnAddPhoto.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                    pickImageLauncher.launch("image/*");
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
                }
            } else {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    pickImageLauncher.launch("image/*");
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }
        });

        binding.btnRecordAudio.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                toggleRecording();
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        });

        binding.btnPlayAudio.setOnClickListener(v -> togglePlaying());

        binding.btnDeleteAudio.setOnClickListener(v -> {
            if (viewModel.isPlaying())
                stopPlaying();
            viewModel.deleteAudio();
            // Observer handles UI reset
            binding.audioPlayerLayout.setVisibility(View.GONE);
            binding.btnRecordAudio.setVisibility(View.VISIBLE);
        });

        binding.btnRemoveImage.setOnClickListener(v -> viewModel.removeImage());
    }

    // --- Audio Logic Wrappers ---

    private void toggleRecording() {
        if (viewModel.isRecording())
            stopRecording();
        else
            startRecording();
    }

    private void startRecording() {
        // Use Internal Files Dir to prevent OS from deleting it (Fix for "File not
        // found" error)
        File audioDir = new File(getFilesDir(), "audio_drafts");
        if (!audioDir.exists())
            confirmDir(audioDir);

        File audioFile = new File(audioDir, "letter_audio_" + System.currentTimeMillis() + ".3gp");
        String path = audioFile.getAbsolutePath();
        viewModel.setAudioFilePath(path);

        mediaRecorder = new android.media.MediaRecorder();
        mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(path);
        mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            viewModel.setRecording(true);
        } catch (Exception e) {
            Log.e(TAG, "Record fail", e);
            Toast.makeText(this, "Could not start recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
            }
            mediaRecorder = null;
        }
        viewModel.setRecording(false);
    }

    private void togglePlaying() {
        if (viewModel.isPlaying())
            stopPlaying();
        else
            startPlaying();
    }

    private void startPlaying() {
        try {
            if (viewModel.getAudioFilePath() == null)
                return;
            mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setDataSource(viewModel.getAudioFilePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            viewModel.setPlaying(true);
            mediaPlayer.setOnCompletionListener(mp -> stopPlaying());
        } catch (Exception e) {
            Log.e(TAG, "Play fail", e);
            Toast.makeText(this, "Audio file unavailable", Toast.LENGTH_SHORT).show();
            // Update UI to reflect stopped state
            viewModel.setPlaying(false);
        }
    }

    private void stopPlaying() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        viewModel.setPlaying(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Check ViewModel state? No, Activity stopping means we should release
        // resources to be safe.
        // But if we are just rotating, we don't want to kill the recording process IF
        // we used a service.
        // Since we are using basic MediaRecorder in Activity, rotation WILL kill
        // recording. This is Android limitation without Service.
        // ACCEPTABLE LIMITATION: Rotation stops recording.
        if (viewModel.isRecording()) {
            stopRecording();
            Toast.makeText(this, "Recording interrupted", Toast.LENGTH_SHORT).show();
            viewModel.deleteAudio(); // Discard partial? Or keep? Discard is safer for partial corrupted files.
        }
        if (viewModel.isPlaying()) {
            stopPlaying();
        }

        // Auto-Save Draft
        String title = binding.titleEditText.getText() != null ? binding.titleEditText.getText().toString() : "";
        String content = binding.contentEditText.getText() != null ? binding.contentEditText.getText().toString() : "";
        viewModel.saveDraft(title, content);
    }

    // --- Date Picker ---
    private void showDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year1, monthOfYear, dayOfMonth) -> {
            Calendar selectedCalendar = Calendar.getInstance();
            selectedCalendar.set(year1, monthOfYear, dayOfMonth, 0, 0, 0);
            selectedCalendar.set(Calendar.MILLISECOND, 0);
            viewModel.setOpenDate(selectedCalendar.getTime()); // State

            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
            binding.selectedDateTextView.setText(getString(R.string.opens_on, sdf.format(viewModel.getOpenDate())));
        }, year, month, day);

        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    // NOTE: This interaction is emotionally complete. Avoid modification without UX
    // review.
    private void animateLetterSealing() {
        // 1. Setup Views
        View overlay = findViewById(R.id.layout_sealed_overlay);
        // View envBg = findViewById(R.id.env_bg); // Not used in code, just in layout
        // View envFront = findViewById(R.id.env_front); // Not used in code logic,
        // mainly we use flap/paper
        View envFlap = findViewById(R.id.env_flap);
        View paperProxy = findViewById(R.id.paper_proxy);
        View paperTop = findViewById(R.id.paper_top);
        View paperBottom = findViewById(R.id.paper_bottom);
        View seal = findViewById(R.id.img_seal);
        View textLayout = findViewById(R.id.layout_completion_text);

        if (overlay == null || envFlap == null) {
            // Fallback
            Toast.makeText(this, "Success", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // RESET STATE
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(1f);

        // Initial Positions
        // Use View dimensions
        float paperHeight = paperProxy.getHeight();
        if (paperHeight == 0)
            paperHeight = 600f; // Approx 240dp in pixels

        paperProxy.setTranslationY(-paperHeight * 0.75f);
        paperProxy.setScaleX(1f);
        paperProxy.setScaleY(1f);

        envFlap.setPivotY(0f);
        envFlap.setRotationX(180f);
        envFlap.setVisibility(View.VISIBLE);

        // ANIMATION SEQUENCE
        android.animation.AnimatorSet masterSet = new android.animation.AnimatorSet();

        // Phase 1: Fold Paper
        paperTop.setPivotY(paperTop.getHeight());
        paperBottom.setPivotY(0f);

        android.animation.ObjectAnimator foldTop = android.animation.ObjectAnimator.ofFloat(paperTop, "rotationX", 0f,
                180f);
        android.animation.ObjectAnimator foldBottom = android.animation.ObjectAnimator.ofFloat(paperBottom, "rotationX",
                0f, -180f);

        android.animation.AnimatorSet foldSet = new android.animation.AnimatorSet();
        foldSet.playTogether(foldTop, foldBottom);
        foldSet.setDuration(600);
        foldSet.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        // Phase 2: Slide into Envelope
        android.animation.ObjectAnimator slideDown = android.animation.ObjectAnimator.ofFloat(paperProxy,
                "translationY", 0f);
        slideDown.setDuration(500);
        slideDown.setStartDelay(400);
        slideDown.setInterpolator(new android.view.animation.AccelerateInterpolator());

        // Phase 3: Close Flap
        android.animation.ObjectAnimator closeFlap = android.animation.ObjectAnimator.ofFloat(envFlap, "rotationX",
                180f, 0f);
        closeFlap.setDuration(300);
        closeFlap.setStartDelay(900);

        // Phase 4: Seal Appears + Haptic
        android.animation.ObjectAnimator sealScaleX = android.animation.ObjectAnimator.ofFloat(seal, "scaleX", 0f, 1f);
        android.animation.ObjectAnimator sealScaleY = android.animation.ObjectAnimator.ofFloat(seal, "scaleY", 0f, 1f);
        android.animation.AnimatorSet sealAnim = new android.animation.AnimatorSet();
        sealAnim.playTogether(sealScaleX, sealScaleY);
        sealAnim.setDuration(250);
        sealAnim.setStartDelay(1200);
        sealAnim.setInterpolator(new android.view.animation.OvershootInterpolator());

        sealAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(android.animation.Animator animation) {
                seal.setVisibility(View.VISIBLE);
                // HAPTIC FEEDBACK TRIGGER POINT
                seal.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            }
        });

        // Phase 5: Text Fade In
        android.animation.ObjectAnimator textFade = android.animation.ObjectAnimator.ofFloat(textLayout, "alpha", 0f,
                1f);
        textFade.setDuration(500);
        textFade.setStartDelay(1500);

        // Run All
        masterSet.playTogether(foldSet, slideDown, closeFlap, sealAnim, textFade);
        masterSet.start();

        // Finish after delay
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }, 3500);
    }

    private void sendLetter() {
        String title = binding.titleEditText.getText() != null ? binding.titleEditText.getText().toString().trim() : "";
        String content = binding.contentEditText.getText() != null ? binding.contentEditText.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Please give your letter a title.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "The letter cannot be empty. Pour your heart out...", Toast.LENGTH_SHORT).show();
            return;
        }

        // CRITICAL FIX: Date is optional. If null, set to now.
        if (viewModel.getOpenDate() == null) {
            viewModel.setOpenDate(new Date());
        }

        // Check if recording is in progress and stop it cleanly to save the file
        if (viewModel.isRecording()) {
            stopRecording();
            Toast.makeText(this, "Recording saved.", Toast.LENGTH_SHORT).show();
        }

        viewModel.sendLetter(title, content);
    }

    private void confirmDir(java.io.File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private void setupOnBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewModel.getState().getValue() != null &&
                        viewModel.getState().getValue().status == ComposeLetterViewModel.Status.LOADING) {
                    Toast.makeText(ComposeLetterActivity.this, R.string.please_wait_for_letter_to_send,
                            Toast.LENGTH_SHORT).show();
                } else {
                    // Emotional Assurance
                    Toast.makeText(ComposeLetterActivity.this, "Draft saved. Your words are safe.", Toast.LENGTH_SHORT)
                            .show();
                    finish();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }
            }
        });
    }
}
