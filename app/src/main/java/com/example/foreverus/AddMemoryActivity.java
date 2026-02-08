package com.example.foreverus;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.foreverus.databinding.ActivityAddMemoryBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AddMemoryActivity extends BaseActivity {

    private ActivityAddMemoryBinding binding;
    private AddMemoryViewModel viewModel;
    private RelationshipRepository relationshipRepository;

    private java.util.List<Uri> selectedImageUris = new java.util.ArrayList<>();
    private String selectedMimeType;
    private String relationshipId;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddMemoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Components
        relationshipRepository = RelationshipRepository.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Initialize ViewModel
        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(AddMemoryViewModel.class);

        // Get Intent Data
        relationshipId = getIntent().getStringExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID);
        // Fallback to repository if intent is null (Safety)
        if (relationshipId == null && relationshipRepository.getRelationshipId().getValue() != null) {
            relationshipId = relationshipRepository.getRelationshipId().getValue();
        }

        // Handle "Adventure Board" Integration (Prefill)
        String prefillTitle = getIntent().getStringExtra("prefill_title");
        if (prefillTitle != null) {
            binding.memoryTitleEditText.setText(prefillTitle);
        }

        // Setup UI
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("New Memory");
        }
        
        setupOnBackPressed();
        setupAudioViews();

        binding.selectImageButton.setOnClickListener(v -> checkPermissionAndOpenFileChooser());
        binding.saveMemoryFab.setOnClickListener(v -> saveMemory());

        binding.imagesRecyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));

        // Observers
        viewModel.uploadState.observe(this, state -> {
            switch (state) {
                case IDLE:
                    showLoading(false);
                    break;
                case COMPRESSING:
                    showLoading(true);
                    binding.progressTextView.setText("Compressing Media...");
                    break;
                case UPLOADING:
                    showLoading(true);
                    binding.progressTextView.setText("Uploading to Cloud...");
                    break;
                case SAVING:
                    binding.progressTextView.setText("Saving Memory...");
                    break;
                case SUCCESS:
                    showLoading(false);
                    Toast.makeText(this, "Memory Saved!", Toast.LENGTH_SHORT).show();
                    finish();
                    break;
                case ERROR:
                    showLoading(false);
                    String error = viewModel.errorMessage.getValue();
                    Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
                    break;
            }
        });

        // Initial State Check
        updateSaveButtonState();
        
        // Listen to text changes for validation
        binding.memoryTitleEditText.addTextChangedListener(new android.text.TextWatcher() {
             public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
             public void onTextChanged(CharSequence s, int start, int before, int count) { updateSaveButtonState(); }
             public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // Use GetMultipleContents to allow selecting multiple images
    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris != null && !uris.isEmpty()) {
                    selectedImageUris = uris;
                    
                    // Show RecyclerView, Hide Placeholder logic
                    binding.imagePlaceholderCard.setVisibility(View.GONE);
                    binding.imagesRecyclerView.setVisibility(View.VISIBLE);
                    
                    // Setup Adapter
                    ImagePreviewAdapter adapter = new ImagePreviewAdapter(uris);
                    binding.imagesRecyclerView.setAdapter(adapter);
                    
                    // Check MIME Type for Video (using first one for now)
                    if (!uris.isEmpty()) {
                        selectedMimeType = getContentResolver().getType(uris.get(0));
                    }
                    
                    binding.selectImageButton.setVisibility(View.GONE);
                    updateSaveButtonState();
                }
            }
    );

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean allGranted = true;
                for (Boolean isGranted : result.values()) {
                    if (!isGranted) {
                        allGranted = false;
                        break;
                    }
                }
                
                if (allGranted) {
                    pickImageLauncher.launch("image/*,video/*");
                } else {
                    Toast.makeText(this, R.string.permission_needed_to_select_image, Toast.LENGTH_LONG).show();
                }
            }
    );

    private void checkPermissionAndOpenFileChooser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean imagesGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
            boolean videoGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
            
            if (imagesGranted && videoGranted) {
                 pickImageLauncher.launch("image/*,video/*");
            } else {
                 requestPermissionsLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO});
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                pickImageLauncher.launch("image/*,video/*");
            } else {
                requestPermissionsLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
            }
        }
    }

    private void saveMemory() {
        if (viewModel.uploadState.getValue() == AddMemoryViewModel.UploadState.IDLE) {
            String title = binding.memoryTitleEditText.getText() != null ? binding.memoryTitleEditText.getText().toString().trim() : "";
            String description = binding.memoryDescriptionEditText.getText() != null ? binding.memoryDescriptionEditText.getText().toString().trim() : "";
            String location = binding.memoryLocationEditText.getText() != null ? binding.memoryLocationEditText.getText().toString().trim() : "";

            if (title.isEmpty()) {
                binding.memoryTitleInputLayout.setError(getString(R.string.title_is_required));
                return;
            } else {
                binding.memoryTitleInputLayout.setError(null);
            }

            viewModel.saveMemory(selectedImageUris, selectedMimeType, title, description, location, relationshipId, currentUser, audioFilePath);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupOnBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewModel.uploadState.getValue() == AddMemoryViewModel.UploadState.COMPRESSING ||
                        viewModel.uploadState.getValue() == AddMemoryViewModel.UploadState.UPLOADING ||
                        viewModel.uploadState.getValue() == AddMemoryViewModel.UploadState.SAVING) {
                    Toast.makeText(AddMemoryActivity.this, R.string.please_wait_for_upload_to_complete, Toast.LENGTH_SHORT).show();
                } else {
                    if (!isEnabled()) {
                        return;
                    }
                    setEnabled(false);
                    AddMemoryActivity.this.getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void updateSaveButtonState() {
        boolean isReadyToSave = selectedImageUris != null && !selectedImageUris.isEmpty() && relationshipId != null && !relationshipId.isEmpty();
        binding.saveMemoryFab.setEnabled(isReadyToSave);
    }
    
    // Audio Recorder
    private android.media.MediaRecorder mediaRecorder;
    private android.media.MediaPlayer mediaPlayer;
    private String audioFilePath = null;
    private boolean isRecording = false;
    private boolean isPlaying = false;

    private final ActivityResultLauncher<String> requestAudioPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startRecording();
                } else {
                    Toast.makeText(this, "Permission needed to record audio", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private void setupAudioViews() {
        binding.btnRecordAudio.setOnClickListener(v -> toggleRecording());
        binding.btnPlayAudio.setOnClickListener(v -> togglePlaying());
        binding.btnDeleteAudio.setOnClickListener(v -> deleteAudio());
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        }
    }

    private void startRecording() {
         audioFilePath = getExternalCacheDir().getAbsolutePath() + "/audiorecordtest.3gp";
         mediaRecorder = new android.media.MediaRecorder();
         mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
         mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
         mediaRecorder.setOutputFile(audioFilePath);
         mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);

         try {
             mediaRecorder.prepare();
             mediaRecorder.start();
             isRecording = true;
             binding.btnRecordAudio.setText("Stop Recording");
             binding.btnRecordAudio.setIconResource(R.drawable.ic_baseline_stop_24);
             binding.btnRecordAudio.setBackgroundColor(getColor(android.R.color.holo_red_light));
         } catch (java.io.IOException e) {
             Log.e("AudioRecord", "prepare() failed", e);
         }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (RuntimeException stopException) {
                // Handling called stop() immediately after start()
            }
            mediaRecorder = null;
            isRecording = false;
            
            // UI Update
            binding.btnRecordAudio.setText("Add Voice Note");
            binding.btnRecordAudio.setIconResource(R.drawable.ic_baseline_mic_24);
            binding.btnRecordAudio.setBackgroundColor(getColor(com.google.android.material.R.color.m3_ref_palette_secondary90)); // Reset color (approx)
            binding.btnRecordAudio.setVisibility(View.GONE);
            binding.layoutAudioPlayer.setVisibility(View.VISIBLE);
        }
    }

    private void togglePlaying() {
        if (isPlaying) {
            stopPlaying();
        } else {
            startPlaying();
        }
    }

    private void startPlaying() {
        mediaPlayer = new android.media.MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            binding.btnPlayAudio.setImageResource(R.drawable.ic_baseline_stop_24); // Reuse stop icon for pause/stop
            
            mediaPlayer.setOnCompletionListener(mp -> stopPlaying());
            
        } catch (java.io.IOException e) {
            Log.e("AudioPlay", "prepare() failed");
        }
    }

    private void stopPlaying() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
            isPlaying = false;
            binding.btnPlayAudio.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
        }
    }

    private void deleteAudio() {
        if (isRecording) stopRecording();
        if (isPlaying) stopPlaying();
        audioFilePath = null;
        binding.layoutAudioPlayer.setVisibility(View.GONE);
        binding.btnRecordAudio.setVisibility(View.VISIBLE);
    }
    
    // Override cleanup
    @Override
    protected void onStop() {
        super.onStop();
        if (mediaRecorder != null) {
            try {
                // If we are recording, save what we have? Or just cancel?
                // Standard behavior: Stop cleanly so we don't corrupt the file.
                if (isRecording) {
                    mediaRecorder.stop();
                }
            } catch (RuntimeException e) {
                // Ignored
            }
            mediaRecorder.release();
            mediaRecorder = null;
        }
        
        // Reset Recording State so returning doesn't cause NPE
        if (isRecording) {
            isRecording = false;
            // Don't update UI here as it might not be visible/safe. 
            // Better to rely on onResume?
            // Actually, if we just set isRecording=false, the button will still say "Stop".
            // So when user clicks it, it calls toggleRecording -> if(!isRecording) -> startRecording().
            // This is "Okay" (user thinks they stopped it, but it starts a new one).
            // BETTER: Reset UI in onStop or onResume.
            // Let's reset UI here to be safe (View updates in onStop are generally frowned upon but fields are fine).
            binding.btnRecordAudio.setText("Add Voice Note");
            binding.btnRecordAudio.setIconResource(R.drawable.ic_baseline_mic_24);
            binding.btnRecordAudio.setBackgroundColor(getColor(com.google.android.material.R.color.m3_ref_palette_secondary90)); 
            
            // If we successfully stopped, maybe we should show the player?
            // For safety/simplicity in this "No Crash" request, let's just reset to initial state.
            // But if we have a file, we should show player?
            if (audioFilePath != null) {
                binding.btnRecordAudio.setVisibility(View.GONE);
                binding.layoutAudioPlayer.setVisibility(View.VISIBLE);
            }
        }
        
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (isPlaying) {
            isPlaying = false;
            binding.btnPlayAudio.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Persist Lists (Need to cast to ArrayList for Parcelable if implementation is ArrayList)
        if (selectedImageUris instanceof java.util.ArrayList) {
            outState.putParcelableArrayList("selected_uris", (java.util.ArrayList<Uri>) selectedImageUris);
        } else {
             // Fallback if it's some other list implementation
             outState.putParcelableArrayList("selected_uris", new java.util.ArrayList<>(selectedImageUris));
        }
        
        outState.putString("selected_mime", selectedMimeType);
        outState.putString("audio_path", audioFilePath);
    }
    
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        java.util.ArrayList<Uri> restoredUris = savedInstanceState.getParcelableArrayList("selected_uris");
        if (restoredUris != null) {
            selectedImageUris = restoredUris;
            if (!selectedImageUris.isEmpty()) {
                // Restore UI State
                binding.imagePlaceholderCard.setVisibility(View.GONE);
                binding.imagesRecyclerView.setVisibility(View.VISIBLE);
                ImagePreviewAdapter adapter = new ImagePreviewAdapter(selectedImageUris);
                binding.imagesRecyclerView.setAdapter(adapter);
                binding.selectImageButton.setVisibility(View.GONE);
            }
        }
        
        selectedMimeType = savedInstanceState.getString("selected_mime");
        
        // Restore Audio State (Path only, not playback state)
        audioFilePath = savedInstanceState.getString("audio_path");
        if (audioFilePath != null) {
            binding.layoutAudioPlayer.setVisibility(View.VISIBLE);
            binding.btnRecordAudio.setVisibility(View.GONE);
        }
        
        updateSaveButtonState();
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            // Soft Fade Out FAB
            binding.saveMemoryFab.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(300)
                .withEndAction(() -> binding.saveMemoryFab.setVisibility(View.GONE))
                .start();
                
            // Soft Fade In Loader
            binding.loadingLayout.setAlpha(0f);
            binding.loadingLayout.setVisibility(View.VISIBLE);
            binding.loadingLayout.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
        } else {
            // Soft Fade In FAB
            binding.saveMemoryFab.setVisibility(View.VISIBLE);
            binding.saveMemoryFab.setAlpha(0f);
            binding.saveMemoryFab.setScaleX(0.8f);
            binding.saveMemoryFab.setScaleY(0.8f);
            
            binding.saveMemoryFab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .start();
                
            // Soft Fade Out Loader
            binding.loadingLayout.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> binding.loadingLayout.setVisibility(View.GONE))
                .start();
        }
        
        // Disable inputs
        binding.memoryTitleInputLayout.setEnabled(!isLoading);
        binding.memoryDescriptionInputLayout.setEnabled(!isLoading);
        binding.memoryLocationInputLayout.setEnabled(!isLoading);
        binding.btnRecordAudio.setEnabled(!isLoading);
        binding.selectImageButton.setEnabled(!isLoading);
    }
}
