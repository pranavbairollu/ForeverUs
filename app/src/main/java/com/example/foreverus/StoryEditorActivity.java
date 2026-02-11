
package com.example.foreverus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.text.SpannableString;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.foreverus.databinding.ActivityStoryEditorBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import android.graphics.Typeface;
import android.text.Html;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.content.Intent;
import android.speech.RecognizerIntent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StoryEditorActivity extends BaseActivity implements StoryAppearanceBottomSheet.OnAppearanceChangedListener,
        StoryAiOptionsBottomSheet.OnAiOptionSelectedListener {

    private ActivityStoryEditorBinding binding;
    private StoryEditorViewModel storyEditorViewModel;
    private StoryAiViewModel storyAiViewModel;
    private MenuItem saveMenuItem;

    private String currentUserDisplayName;
    private int aiInitialLength = 0; // Track length before AI starts

    private android.speech.tts.TextToSpeech textToSpeech;
    private boolean isTtsInitialized = false;
    private boolean isSpeaking = false;
    private UndoRedoHelper undoRedoHelper;
    private android.text.style.BackgroundColorSpan ttsHighlightSpan;

    // AI Retry State
    private String lastAiPrompt;
    private String lastAiActionType;
    private String lastAiCustomInstruction;
    private boolean lastAiStream;
    private int lastAiPreviewLength = 0;

    // Ghost Text State
    private boolean isGhostMode = false;
    private int ghostStartIndex = -1;
    private android.text.style.ForegroundColorSpan ghostSpan;
    private boolean isAiUpdating = false; // Flag to prevent TextWatcher loops

    // Diff Track Changes
    private String pendingCleanAiResponse;

    private static final String PREF_KEY_THEME_MODE = "story_theme_mode";
    private static final String PREF_KEY_FONT_TYPE = "story_font_type";

    // Appearance Constants
    private static final String THEME_SYSTEM = "SYSTEM";
    private static final String THEME_PAPER = "PAPER";
    private static final String THEME_MIDNIGHT = "MIDNIGHT";
    private static final String FONT_SANS = "SANS";
    private static final String FONT_SERIF = "SERIF";
    private static final String FONT_CURSIVE = "CURSIVE";

    private final ActivityResultLauncher<Intent> speechRecognizerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    java.util.ArrayList<String> matches = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        insertTextAtCursor(spokenText);
                    }
                }
            });

    private static final int AUTOSAVE_DELAY_MS = 3000;
    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSaveRunnable = () -> {
        String title = binding.storyTitleEditText.getText().toString();
        String content = getStoryContentHtml();
        storyEditorViewModel.saveStory(title, content);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);
        binding = ActivityStoryEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (savedInstanceState != null) {
            aiInitialLength = savedInstanceState.getInt("aiInitialLength", 0);
        }

        storyEditorViewModel = new ViewModelProvider(this).get(StoryEditorViewModel.class);
        storyAiViewModel = new ViewModelProvider(this).get(StoryAiViewModel.class);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserDisplayName = currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                    ? currentUser.getDisplayName()
                    : getString(R.string.you);
        }

        String relationshipId = getIntent().getStringExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID);
        String storyId = getIntent().getStringExtra("storyId");

        storyEditorViewModel.init(relationshipId, storyId);

        undoRedoHelper = new UndoRedoHelper(binding.storyContentEditText,
                storyEditorViewModel.getUndoHistory(),
                storyEditorViewModel.getRedoHistory());

        if (savedInstanceState != null) {
            undoRedoHelper.restoreState(savedInstanceState);
        }

        setupObservers();
        restoreAppearancePreferences();
        setupTextWatchers();
        setupAiHelper();
        setupAiConfirmation();
        setupFormattingToolbar();
        setupOnBackPressed();
        if (savedInstanceState != null) {
            // Restore AI Variables
            lastAiPrompt = savedInstanceState.getString("lastAiPrompt");
            lastAiActionType = savedInstanceState.getString("lastAiActionType");
            lastAiCustomInstruction = savedInstanceState.getString("lastAiCustomInstruction");
            lastAiStream = savedInstanceState.getBoolean("lastAiStream");
            lastAiPreviewLength = savedInstanceState.getInt("lastAiPreviewLength");

            // Restore Diff Logic
            pendingCleanAiResponse = savedInstanceState.getString("pendingCleanAiResponse");

            // Restore Ghost Mode
            isGhostMode = savedInstanceState.getBoolean("isGhostMode", false);
            ghostStartIndex = savedInstanceState.getInt("ghostStartIndex", -1);

            if (isGhostMode) {
                // restore UI for Ghost Mode
                binding.fabAiHelper.hide();
                binding.fabStopAi.setVisibility(View.GONE); // Assuming stream stopped on rotation?
                // Actually, if we were streaming, rotation kills the stream in ViewModel
                // usually unless scoped?
                // ViewModel survives, but the Observer is re-attached.
                // If ViewModel is still streaming, we need to show Stop button.
                // Logic: Check ViewModel state? Or just default to visible if isGhostMode?
                // For safety, let's show controls.
                binding.ghostTextControlCard.setVisibility(View.VISIBLE);

                // Re-bind ghostSpan
                // We need to find the span in the text.
                Editable content = binding.storyContentEditText.getText();
                android.text.style.ForegroundColorSpan[] spans = content.getSpans(0, content.length(),
                        android.text.style.ForegroundColorSpan.class);
                // We assume the last one or the one matching our color logic is ours?
                // Or we just search for spans in general.
                // A safer way: we don't *need* the exact java object reference if we just
                // remove spans by range later.
                // But discardGhostText removes `ghostSpan` specifically.
                // Let's find a span that covers [ghostStartIndex, length]?
                for (android.text.style.ForegroundColorSpan s : spans) {
                    if (content.getSpanStart(s) == ghostStartIndex) {
                        ghostSpan = s;
                        break;
                    }
                }
            }

            // Restore Preview Card State if we have pending data
            // (If simple rotation happened while Preview was open)
            if (pendingCleanAiResponse != null || (binding.aiPreviewText.length() > 0)) {
                // Wait, binding is inflated new. We rely on ViewModel to repost data?
                // ViewModel LiveData will repost `aiStreamingResponse` or `aiFullResponse` if
                // they have values!
                // So the Observer will fire again.
                // We just need to ensure UI visibility is correct.
                // Observers are set up earlier.
            }
        }

        binding.buttonSaveMyVersion.setOnClickListener(v -> {
            String title = binding.storyTitleEditText.getText().toString();
            String content = getStoryContentHtml();
            storyEditorViewModel.resolveConflictByOverwriting(title, content);
        });

        textToSpeech = new android.speech.tts.TextToSpeech(this, status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.getDefault());
                if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                        result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(Locale.US);
                }

                textToSpeech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        // Optional: Scroll to start for deep chunks?
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        if (utteranceId.endsWith("_1") || (utteranceId.equals("StoryTitle")
                                && binding.storyContentEditText.getText().length() == 0)) {
                            runOnUiThread(() -> {
                                clearTtsHighlight();
                                isSpeaking = false;
                                Toast.makeText(StoryEditorActivity.this, "Finished reading", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        runOnUiThread(() -> {
                            clearTtsHighlight();
                            isSpeaking = false;
                            Toast.makeText(StoryEditorActivity.this, "Error reading story segment", Toast.LENGTH_SHORT)
                                    .show();
                        });
                    }

                    @Override
                    public void onRangeStart(String utteranceId, int start, int end, int frame) {
                        super.onRangeStart(utteranceId, start, end, frame);
                        if (utteranceId.startsWith("Content_")) {
                            try {
                                String[] parts = utteranceId.split("_");
                                if (parts.length >= 2) {
                                    int chunkOffset = Integer.parseInt(parts[1]);
                                    runOnUiThread(() -> highlightTtsRange(chunkOffset + start, chunkOffset + end));
                                }
                            } catch (Exception e) {
                                // Ignore parsing errors
                            }
                        }
                    }
                });

                isTtsInitialized = true;
            } else {
                Toast.makeText(this, "TTS Initialization failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.story_editor_menu, menu);
        saveMenuItem = menu.findItem(R.id.action_save_story);
        updateSaveMenuVisibility(false); // Initially hidden
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_appearance) {
            StoryAppearanceBottomSheet bottomSheet = new StoryAppearanceBottomSheet();
            bottomSheet.show(getSupportFragmentManager(), "AppearanceBottomSheet");
            return true;
        } else if (item.getItemId() == R.id.action_save_story) {
            String title = binding.storyTitleEditText.getText().toString();
            String content = binding.storyContentEditText.getText().toString();
            storyEditorViewModel.saveStory(title, content);
            return true;
        } else if (item.getItemId() == R.id.action_delete_story) {
            showDeleteConfirmationDialog();
            return true;
        } else if (item.getItemId() == R.id.action_export_to_pdf) {
            exportToPdf();
            return true;
        } else if (item.getItemId() == R.id.action_read_aloud) {
            toggleReadAloud();
            return true;
        } else if (item.getItemId() == R.id.action_dictate) {
            startVoiceDictation();
            return true;
        } else if (item.getItemId() == R.id.action_undo) {
            undoRedoHelper.undo();
            return true;
        } else if (item.getItemId() == R.id.action_redo) {
            undoRedoHelper.redo();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleGhostStreaming(String fullText) {
        if (!isGhostMode)
            return;

        isAiUpdating = true;
        Editable editable = binding.storyContentEditText.getText();

        int currentLength = editable.length();
        if (ghostStartIndex > currentLength) {
            ghostStartIndex = currentLength;
        }

        Spanned spannedText = parseAiMarkdown(fullText);

        editable.replace(ghostStartIndex, currentLength, spannedText);

        int newEnd = ghostStartIndex + spannedText.length();

        // Remove ALL existing foreground color spans in this range to be safe
        // This handles rotation restoration or duplicate spans cases
        android.text.style.ForegroundColorSpan[] existingSpans = editable.getSpans(ghostStartIndex, newEnd,
                android.text.style.ForegroundColorSpan.class);
        for (android.text.style.ForegroundColorSpan span : existingSpans) {
            editable.removeSpan(span);
        }

        // Resolve a high-contrast color based on theme using semantic attribute
        int color;
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (getTheme().resolveAttribute(R.attr.colorGhostText, typedValue, true)) {
            color = typedValue.data;
        } else {
            // Fallback
            int nightModeFlags = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            color = (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    ? android.graphics.Color.LTGRAY
                    : android.graphics.Color.GRAY;
        }

        ghostSpan = new android.text.style.ForegroundColorSpan(color);
        editable.setSpan(ghostSpan, ghostStartIndex, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.storyContentEditText.setSelection(newEnd);
        isAiUpdating = false;

        if (binding.ghostTextControlCard.getVisibility() != View.VISIBLE) {
            binding.ghostTextControlCard.setVisibility(View.VISIBLE);
        }
    }

    private void setupObservers() {
        storyEditorViewModel.getStory().observe(this, resource -> {
            if (resource == null)
                return;

            if (resource.status == Resource.Status.LOADING) {
                showInitialLoading(true);
            } else {
                showInitialLoading(false);
            }

            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                updateUiWithStoryData(resource.data);
            } else if (resource.status == Resource.Status.ERROR) {
                Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show();
            }
        });

        storyEditorViewModel.getIsSaving().observe(this, isSaving -> {
            showSaving(isSaving);
            if (!isSaving) {
                updateSaveMenuVisibility(false);
            }
        });

        storyEditorViewModel.getHasUnsavedChanges().observe(this, hasChanges -> {
            updateSaveMenuVisibility(hasChanges);
            if (hasChanges) {
                autoSaveHandler.removeCallbacks(autoSaveRunnable);
                autoSaveHandler.postDelayed(autoSaveRunnable, AUTOSAVE_DELAY_MS);
            }
        });

        storyEditorViewModel.getIsConflict().observe(this, isConflict -> {
            binding.conflictBanner.setVisibility(isConflict ? View.VISIBLE : View.GONE);
        });

        storyEditorViewModel.getStoryDeleted().observe(this, aVoid -> {
            Toast.makeText(this, R.string.story_deleted, Toast.LENGTH_SHORT).show();
            finish();
        });

        storyAiViewModel.aiState.observe(this, state -> {
            if (state == StoryAiViewModel.AiState.LOADING) {
                // Ensure we set it as EDITABLE so subsequent casts don't fail
                binding.aiPreviewText.setText("", android.widget.TextView.BufferType.EDITABLE);
                lastAiPreviewLength = 0; // Reset tracking

                if (!isGhostMode) {
                    binding.aiPreviewCard.setVisibility(View.VISIBLE);
                    // Scroll to bottom so card is visible
                    binding.storyEditorRoot.post(() -> {
                        // Scroll logic if needed
                    });
                }

                showAiStreamingUI(true);
            } else if (state == StoryAiViewModel.AiState.SUCCESS) {
                showAiStreamingUI(false);
                // Clear highlights on success for clean review
                if (binding.aiPreviewText.getText() instanceof Editable) {
                    Editable editable = (Editable) binding.aiPreviewText.getText();
                    android.text.style.BackgroundColorSpan[] spans = editable.getSpans(0, editable.length(),
                            android.text.style.BackgroundColorSpan.class);
                    for (android.text.style.BackgroundColorSpan span : spans) {
                        editable.removeSpan(span);
                    }
                }
            } else if (state == StoryAiViewModel.AiState.ERROR) {
                showAiStreamingUI(false);
                Toast.makeText(this, storyAiViewModel.errorMessage.getValue(), Toast.LENGTH_LONG).show();
            }
        });

        storyAiViewModel.aiStreamingResponse.observe(this, fullText -> {
            if (fullText != null) {
                if (isGhostMode) {
                    handleGhostStreaming(fullText);
                } else {
                    // Standard Preview Card Stream
                    Spanned formattedText = parseAiMarkdown(fullText);
                    binding.aiPreviewText.setText(formattedText, android.widget.TextView.BufferType.EDITABLE);

                    // Note: Highlighting removed for visual consistency.
                    // The Card UI is sufficient context.
                }
            }
        });

        storyAiViewModel.aiFullResponse.observe(this, response -> {
            if (response != null) {
                pendingCleanAiResponse = response;

                if (StoryAiOptionsBottomSheet.ACTION_FIX_GRAMMAR.equals(lastAiActionType)) {
                    // Generate Diff for Grammar changes
                    new Thread(() -> {
                        java.util.List<SimpleDiffUtils.Diff> diffs = SimpleDiffUtils.computeDiff(lastAiPrompt,
                                response);
                        boolean hasChanges = SimpleDiffUtils.hasChanges(diffs);
                        // Pass 'this' (Context) to resolve colors
                        CharSequence renderedDiff = SimpleDiffUtils.renderDiff(this, diffs);

                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed())
                                return;
                            if (!hasChanges) {
                                binding.aiPreviewText.setText("No grammar changes needed.");
                                binding.btnAiAccept.setEnabled(false);
                            } else {
                                binding.aiPreviewText.setText(renderedDiff);
                                binding.btnAiAccept.setEnabled(true);
                            }
                        });
                    }).start();
                } else {
                    // Standard Markdown Render
                    binding.aiPreviewText.setText(parseAiMarkdown(response));
                    binding.btnAiAccept.setEnabled(true);
                }
            }
        });
    }

    private void updateUiWithStoryData(Story story) {
        if (!binding.storyTitleEditText.getText().toString().equals(story.getTitle())) {
            binding.storyTitleEditText.setText(story.getTitle());
        }
        if (!binding.storyContentEditText.getText().toString().equals(story.getContent())) {
            // Check if content looks like HTML
            String currentContentHtml = getStoryContentHtml();
            // Prevent flickering: Only update if the content is materially different from
            // what we expect
            // Note: HTML comparison is tricky, but we can trust that if the user is typing,
            // we probably shouldn't blindly overwrite unless it's a conflict resolution.

            // If the incoming story content is exactly what we just saved (HTML-wise), do
            // nothing.
            if (currentContentHtml.equals(story.getContent())) {
                return;
            }

            // If user has unsaved changes, we might NOT want to overwrite unless it's a
            // reload/force update.
            // But this method is called when we receive an update from ViewModel.
            // If the VM determined it's a valid update (server version > local), we should
            // update.
            // The flicker happens because "Hello" becomes "<p>Hello</p>" on server, and we
            // reset.
            // Simple heuristic: If the raw text length is very different, or if we are not
            // "saving" right now...
            // Actually, best fix is:

            if (story.getContent().contains("<") && story.getContent().contains(">")) {
                if (undoRedoHelper != null)
                    undoRedoHelper.setEnabled(false);
                binding.storyContentEditText.setText(deserializeHtml(story.getContent()));
                if (undoRedoHelper != null)
                    undoRedoHelper.setEnabled(true);
            } else {
                if (undoRedoHelper != null)
                    undoRedoHelper.setEnabled(false);
                binding.storyContentEditText.setText(story.getContent());
                if (undoRedoHelper != null)
                    undoRedoHelper.setEnabled(true);
            }
        }
        updateTimestampAndEditor(story.getLastEditedBy(), story.getTimestamp());
    }

    private void updateTimestampAndEditor(String editorId, Timestamp timestamp) {
        if (timestamp != null) {
            Date date = timestamp.toDate();
            storyEditorViewModel.getPartnerName().observe(this, partnerName -> {
                String editorName;
                if (editorId != null && editorId.equals(storyEditorViewModel.getCurrentUserId())) {
                    editorName = currentUserDisplayName;
                } else {
                    editorName = partnerName;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy \'at\' hh:mm a", Locale.getDefault());
                binding.timestampTextView.setText(getString(R.string.last_saved_by, editorName, sdf.format(date)));
                binding.timestampTextView.setVisibility(View.VISIBLE);
            });
        } else {
            binding.timestampTextView.setVisibility(View.GONE);
        }
    }

    private void showInitialLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        setInputsEnabled(!isLoading);
    }

    private void showSaving(boolean isSaving) {
        if (saveMenuItem != null) {
            saveMenuItem.setEnabled(!isSaving);
            if (isSaving) {
                android.widget.ProgressBar loader = new android.widget.ProgressBar(this);
                loader.setIndeterminateDrawable(
                        androidx.core.content.ContextCompat.getDrawable(this, R.drawable.progress_indeterminate_warm));
                saveMenuItem.setActionView(loader);
            } else {
                saveMenuItem.setActionView(null);
            }
        }
        setInputsEnabled(!isSaving);
    }

    private void showAiStreamingUI(boolean isStreaming) {
        if (isGhostMode) {
            // In Ghost Mode, we DO NOT lock inputs (otherwise user can't type to interrupt)
            // We just toggle the Stop button.
            if (isStreaming) {
                binding.fabStopAi.setVisibility(View.VISIBLE);
            } else {
                binding.fabStopAi.setVisibility(View.GONE);
            }
            return;
        }

        setInputsEnabled(!isStreaming); // Lock main input while streaming

        if (isStreaming) {
            binding.fabAiHelper.hide();
            binding.fabStopAi.setVisibility(View.VISIBLE);

            // Disable Accept/Try Again while streaming, keep Discard
            binding.btnAiAccept.setEnabled(false);
            binding.btnAiTryAgain.setEnabled(false);
        } else {
            binding.fabStopAi.setVisibility(View.GONE);

            // Enable buttons
            binding.btnAiAccept.setEnabled(true);
            binding.btnAiTryAgain.setEnabled(true);
        }
    }

    private void setInputsEnabled(boolean enabled) {
        binding.storyTitleEditText.setEnabled(enabled);
        binding.storyContentEditText.setEnabled(enabled);
        // Also toolbar buttons could be possibly disabled, but let's keep it simple
    }

    private void updateSaveMenuVisibility(boolean visible) {
        if (saveMenuItem != null) {
            saveMenuItem.setVisible(visible);
        }
    }

    private void setupTextWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // If user deletes into the ghost text, or types...
                if (isGhostMode && !isAiUpdating) {
                    // We should discard immediately to be safe
                    // But we can't discard INSIDE beforeTextChanged safely sometimes.
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isGhostMode && !isAiUpdating) {
                    // User is typing or modifying text manually.
                    // We must discard the ghost session to avoid corruption.
                    // We post this to run AFTER the current text change is fully committed.
                    binding.storyContentEditText.post(() -> discardGhostText());
                } else {
                    storyEditorViewModel.onTextChanged();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        binding.storyTitleEditText.addTextChangedListener(textWatcher);
        binding.storyContentEditText.addTextChangedListener(textWatcher);
    }

    private void setupAiHelper() {
        binding.fabAiHelper.setOnClickListener(v -> showAiHelperDialog());
        binding.fabStopAi.setOnClickListener(v -> storyAiViewModel.cancelRequest());
    }

    private void showAiHelperDialog() {
        StoryAiOptionsBottomSheet bottomSheet = new StoryAiOptionsBottomSheet();
        bottomSheet.show(getSupportFragmentManager(), "AiOptionsBottomSheet");
    }

    @Override
    public void onAiOptionSelected(String actionType, String customInstruction) {
        String currentText = binding.storyContentEditText.getText().toString();
        if (currentText.trim().isEmpty()) {
            Toast.makeText(this, R.string.no_text_to_work_with, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean stream = true;
        // Disable streaming for replacement tasks where we might want to review the
        // whole block
        if (actionType.equals(StoryAiOptionsBottomSheet.ACTION_FIX_GRAMMAR) ||
                actionType.equals(StoryAiOptionsBottomSheet.ACTION_SHORTEN) ||
                actionType.equals(StoryAiOptionsBottomSheet.ACTION_REWRITE)) {
            stream = false;
        } else {
            // For creative tasks, streaming is better UX
            aiInitialLength = binding.storyContentEditText.length();
        }

        lastAiPrompt = currentText;
        lastAiActionType = actionType;
        lastAiCustomInstruction = customInstruction;
        lastAiStream = stream;

        // Ghost Mode only for Continue
        if (actionType.equals(StoryAiOptionsBottomSheet.ACTION_CONTINUE)) {
            isGhostMode = true;
            ghostStartIndex = binding.storyContentEditText.getSelectionEnd();
            // Ensure index is valid
            if (ghostStartIndex < 0)
                ghostStartIndex = binding.storyContentEditText.length();

            // Start Atomic Undo Checkpoint for the upcoming stream
            undoRedoHelper.startCheckpoint();

            // Just hide FAB, standard showAiStreamingUI logic will be bypassed or adapted
            binding.fabAiHelper.hide();
            binding.fabStopAi.setVisibility(View.VISIBLE);
            // Don't show Preview Card for ghost text
        } else {
            isGhostMode = false;
            ghostStartIndex = -1;
        }

        storyAiViewModel.generateResponse(currentText, actionType, customInstruction, stream);
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_story_title)
                .setMessage(R.string.delete_story_confirmation)
                .setPositiveButton(R.string.delete, (dialog, which) -> storyEditorViewModel.deleteStory())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupOnBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Boolean.TRUE.equals(storyEditorViewModel.getIsSaving().getValue())) {
                    return;
                }
                if (Boolean.TRUE.equals(storyEditorViewModel.getHasUnsavedChanges().getValue())) {
                    new AlertDialog.Builder(StoryEditorActivity.this)
                            .setTitle(R.string.unsaved_changes_title)
                            .setMessage(R.string.unsaved_changes_message)
                            .setPositiveButton(R.string.save, (dialog, which) -> {
                                String title = binding.storyTitleEditText.getText().toString();
                                String content = binding.storyContentEditText.getText().toString();
                                storyEditorViewModel.saveStory(title, content);
                                finish();
                            })
                            .setNegativeButton(R.string.discard, (dialog, which) -> {
                                finish();
                            })
                            .setNeutralButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                            .show();
                } else {
                    finish();
                }
            }
        });
    }

    private void exportToPdf() {
        String title = binding.storyTitleEditText.getText().toString().trim();
        String content = binding.storyContentEditText.getText().toString().trim();

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Title or content is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        // Create a temporary Story object for the exporter
        Story tempStory = new Story();
        tempStory.setTitle(title);
        tempStory.setContent(content);

        // PdfExporter expects a list
        java.util.List<Story> storiesToExport = java.util.Collections.singletonList(tempStory);

        new Thread(() -> {
            new PdfExporter().createPdf(this, storiesToExport, new PdfExporter.PdfCallback() {
                @Override
                public void onSuccess(android.net.Uri uri) {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(StoryEditorActivity.this, "PDF exported to Downloads", Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(StoryEditorActivity.this, "Error exporting: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }).start();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("aiInitialLength", aiInitialLength);

        // Persist AI Context
        outState.putString("lastAiPrompt", lastAiPrompt);
        outState.putString("lastAiActionType", lastAiActionType);
        outState.putString("lastAiCustomInstruction", lastAiCustomInstruction);
        outState.putBoolean("lastAiStream", lastAiStream);
        outState.putInt("lastAiPreviewLength", lastAiPreviewLength);

        // Persist Ghost Mode State
        outState.putBoolean("isGhostMode", isGhostMode);
        outState.putInt("ghostStartIndex", ghostStartIndex);

        // Persist Diff Logic State
        outState.putString("pendingCleanAiResponse", pendingCleanAiResponse);

        if (undoRedoHelper != null) {
            undoRedoHelper.saveState(outState);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        if (Boolean.TRUE.equals(storyEditorViewModel.getHasUnsavedChanges().getValue()) && !isFinishing()) {
            String title = binding.storyTitleEditText.getText().toString();
            String content = getStoryContentHtml();
            storyEditorViewModel.saveStory(title, content);
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private void toggleReadAloud() {
        if (!isTtsInitialized) {
            Toast.makeText(this, "TTS not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isSpeaking) {
            textToSpeech.stop();
            clearTtsHighlight();
            isSpeaking = false;
            Toast.makeText(this, "Stopped reading", Toast.LENGTH_SHORT).show();
        } else {
            String title = binding.storyTitleEditText.getText().toString();
            String content = binding.storyContentEditText.getText().toString();

            if (content.isEmpty() && title.isEmpty()) {
                Toast.makeText(this, "No content to read", Toast.LENGTH_SHORT).show();
                return;
            }

            isSpeaking = true;
            Toast.makeText(this, "Reading story...", Toast.LENGTH_SHORT).show();

            // Queue Title
            if (!title.isEmpty()) {
                textToSpeech.speak(title, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "StoryTitle");
                textToSpeech.playSilentUtterance(500, android.speech.tts.TextToSpeech.QUEUE_ADD, null);
            }

            // Queue Content in Chunks (Max ~3500 chars to be safe)
            if (!content.isEmpty()) {
                int textLength = content.length();
                int maxLength = 3500;
                int offset = 0;

                int queueMode = title.isEmpty() ? android.speech.tts.TextToSpeech.QUEUE_FLUSH
                        : android.speech.tts.TextToSpeech.QUEUE_ADD;

                while (offset < textLength) {
                    int end = Math.min(offset + maxLength, textLength);

                    // Attempt to split at a logical break (newline or space) near the end to avoid
                    // cutting words
                    if (end < textLength) {
                        int lastNewline = content.lastIndexOf('\n', end);
                        int lastSpace = content.lastIndexOf(' ', end);
                        int safeEnd = Math.max(lastNewline, lastSpace);

                        if (safeEnd > offset) {
                            end = safeEnd + 1; // Include the delimiter
                        }
                    }

                    String chunk = content.substring(offset, end);
                    boolean isLast = (end >= textLength);

                    // Encode Metadata in ID: "Content_<startOffset>_<isLast>"
                    String utterId = "Content_" + offset + "_" + (isLast ? "1" : "0");

                    Bundle params = new Bundle();
                    params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utterId);

                    textToSpeech.speak(chunk, queueMode, params, utterId);

                    // Next chunk should always ADD
                    queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD;
                    offset = end;
                }
            }
        }
    }

    private void highlightTtsRange(int start, int end) {
        // Only safely apply span if range is valid
        Editable editable = binding.storyContentEditText.getText();
        if (start < 0 || end > editable.length() || start >= end) {
            return; // Invalid range
        }

        clearTtsHighlight();

        ttsHighlightSpan = new android.text.style.BackgroundColorSpan(
                androidx.core.content.ContextCompat.getColor(this, R.color.colorReadAloudHighlight));
        editable.setSpan(ttsHighlightSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void clearTtsHighlight() {
        if (ttsHighlightSpan != null) {
            binding.storyContentEditText.getText().removeSpan(ttsHighlightSpan);
            ttsHighlightSpan = null;
        }
    }

    private void setupFormattingToolbar() {
        binding.btnFormatBold.setOnClickListener(v -> toggleStyle(StyleSpan.class, Typeface.BOLD));
        binding.btnFormatItalic.setOnClickListener(v -> toggleStyle(StyleSpan.class, Typeface.ITALIC));
        binding.btnFormatUnderline.setOnClickListener(v -> toggleStyle(UnderlineSpan.class, 0));
    }

    private void toggleStyle(Class<?> spanClass, int styleAttr) {
        int start = binding.storyContentEditText.getSelectionStart();
        int end = binding.storyContentEditText.getSelectionEnd();

        if (start < 0 || end < 0)
            return;

        if (start == end) {
            // Cursor mode (no selection) - Complex to handle "pending" style without a
            // span.
            // For now, let's focus on selection toggling as requested.
            // Ideally we'd set a flag for next typed character, but Android EditText
            // handles
            // some of this if we just set the span on 0 length? No, 0 length spans are
            // often removed.
            // Let's strictly handle selection for now to fix the reported bug.
            return;
        }

        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }

        Editable editable = binding.storyContentEditText.getText();

        if (isFullyStyled(editable, start, end, spanClass, styleAttr)) {
            removeStyle(editable, start, end, spanClass, styleAttr);
        } else {
            addStyle(editable, start, end, spanClass, styleAttr);
        }

        storyEditorViewModel.onTextChanged();
    }

    private boolean isFullyStyled(Editable editable, int start, int end, Class<?> spanClass, int styleAttr) {
        if (start >= end)
            return false;

        // Get all spans of type in range
        Object[] spans = editable.getSpans(start, end, spanClass);

        // Sort spans by start
        java.util.Arrays.sort(spans, (o1, o2) -> Integer.compare(editable.getSpanStart(o1), editable.getSpanStart(o2)));

        int currentPos = start;

        for (Object span : spans) {
            if (spanClass == StyleSpan.class) {
                if (((StyleSpan) span).getStyle() != styleAttr)
                    continue;
            }

            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);

            if (spanStart > currentPos) {
                // Gap found
                return false;
            }

            currentPos = Math.max(currentPos, spanEnd);

            if (currentPos >= end)
                return true;
        }

        return currentPos >= end;
    }

    private void removeStyle(Editable editable, int start, int end, Class<?> spanClass, int styleAttr) {
        Object[] spans = editable.getSpans(start, end, spanClass);

        for (Object span : spans) {
            if (spanClass == StyleSpan.class) {
                if (((StyleSpan) span).getStyle() != styleAttr)
                    continue;
            }

            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);

            // Remove the existing span
            editable.removeSpan(span);

            // Restore the part BEFORE the selection
            if (spanStart < start) {
                editable.setSpan(createSpan(spanClass, styleAttr), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Restore the part AFTER the selection
            if (spanEnd > end) {
                editable.setSpan(createSpan(spanClass, styleAttr), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private void addStyle(Editable editable, int start, int end, Class<?> spanClass, int styleAttr) {
        // First clean up any partial spans inside to avoid nesting mess
        // (Optional: standard editors often merge. Simple approach: just add on top?
        // No, best to remove inner ones to keep DOM clean, but "add on top" works
        // visually.)
        // But if we toggle OFF later, we want to remove the specific span.
        // Let's remove conflicting inner spans first so we have one clean span.
        removeStyle(editable, start, end, spanClass, styleAttr);

        editable.setSpan(createSpan(spanClass, styleAttr), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private Object createSpan(Class<?> spanClass, int styleAttr) {
        if (spanClass == StyleSpan.class) {
            return new StyleSpan(styleAttr);
        } else if (spanClass == UnderlineSpan.class) {
            return new UnderlineSpan();
        }
        return null; // Should not happen
    }

    private String getStoryContentHtml() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return Html.toHtml(binding.storyContentEditText.getText(), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
        } else {
            return Html.toHtml(binding.storyContentEditText.getText());
        }
    }

    private Spanned deserializeHtml(String html) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        } else {
            return Html.fromHtml(html);
        }
    }

    private void setupAiConfirmation() {
        binding.btnAiAccept.setOnClickListener(v -> {
            CharSequence textToInsert;
            if (pendingCleanAiResponse != null) {
                textToInsert = pendingCleanAiResponse;
            } else {
                textToInsert = binding.aiPreviewText.getText();
            }

            if (StoryAiOptionsBottomSheet.ACTION_CONTINUE.equals(lastAiActionType)) {
                insertTextAtCursor(textToInsert);
            } else {
                // For Rewrite, Grammar, etc. we are replacing the context we sent.
                // Currently we send the whole text, so we replace the whole text.
                replaceStoryContent(textToInsert);
            }
            discardAiPreview();
        });

        binding.btnAiDiscard.setOnClickListener(v -> discardAiPreview());

        binding.btnAiTryAgain.setOnClickListener(v -> {
            discardAiPreview();
            if (lastAiPrompt != null) {
                storyAiViewModel.generateResponse(lastAiPrompt, lastAiActionType, lastAiCustomInstruction,
                        lastAiStream);
            }
        });

        // Ghost Text Controls
        binding.btnGhostAccept.setOnClickListener(v -> acceptGhostText());
        binding.btnGhostDiscard.setOnClickListener(v -> discardGhostText());
    }

    private void discardAiPreview() {
        binding.aiPreviewCard.setVisibility(View.GONE);
        binding.aiPreviewText.setText("");
        pendingCleanAiResponse = null;
        binding.fabAiHelper.show();
        binding.fabStopAi.setVisibility(View.GONE);
        setInputsEnabled(true);
    }

    private void startVoiceDictation() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.story_content_hint));
        try {
            speechRecognizerLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice dictation not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void insertTextAtCursor(CharSequence text) {
        undoRedoHelper.commitHistory(); // Commit previous state before AI insertion
        int start = Math.max(binding.storyContentEditText.getSelectionStart(), 0);
        int end = Math.max(binding.storyContentEditText.getSelectionEnd(), 0);
        binding.storyContentEditText.getText().replace(Math.min(start, end), Math.max(start, end), text, 0,
                text.length());
        undoRedoHelper.commitHistory(); // Commit this insertion state
    }

    private void replaceStoryContent(CharSequence newText) {
        undoRedoHelper.commitHistory();
        binding.storyContentEditText.setText(newText);
        // setText removes spans? If newText is CharSequence with spans (like Diff), it
        // keeps them.
        // But pendingCleanAiResponse is likely a String (no spans) which is what we
        // want for "Accept".
        // Move cursor to end
        binding.storyContentEditText.setSelection(binding.storyContentEditText.length());
        undoRedoHelper.commitHistory();
    }

    // Helper to render AI Markdown (Bold/Italic)
    private Spanned parseAiMarkdown(String text) {
        if (text == null)
            return new android.text.SpannableString("");

        // Escape existing HTML entities first to avoid injection/confusion?
        // Actually, AI output is usually clean text + markdown.
        // But if user typed <, >, it's messy.
        // Simple regex replace for * and ** is safer if we trust the text isn't
        // malicious HTML.

        // 1. TextUtil.htmlEncode to escape raw HTML characters
        String safeText = TextUtils.htmlEncode(text);

        // 2. Restore newlines (htmlEncode converts them? No, typically preserves or
        // escapes chars)
        // TextUtils.htmlEncode does NOT convert \n to <br>.
        safeText = safeText.replace("\n", "<br>");

        // 3. Apply Markdown Bold (**...**)
        safeText = safeText.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");

        // 4. Apply Markdown Italic (*...*) (Be careful not to break <b> tags? Regex
        // matches non-greedy)
        // Only single asterisks not inside bold?
        // regex: (?<!\*)\*(?![*])(.*?)(?<!\*)\*(?![*]) is complex.
        // Let's simple-case: *text* -> <i>text</i>.
        safeText = safeText.replaceAll("\\*(.*?)\\*", "<i>$1</i>");

        // Return styled
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return Html.fromHtml(safeText, Html.FROM_HTML_MODE_COMPACT);
        } else {
            return Html.fromHtml(safeText);
        }
    }

    private void acceptGhostText() {
        if (!isGhostMode)
            return;

        // Remove span (make permanent)
        Editable editable = binding.storyContentEditText.getText();

        // Robustly remove ANY ForegroundColorSpan in the ghost range
        // This handles cases where ghostSpan reference was lost or multiple spans exist
        int end = editable.length();
        int safeStart = Math.min(Math.max(0, ghostStartIndex), end);

        if (safeStart < end) {
            android.text.style.ForegroundColorSpan[] spans = editable.getSpans(safeStart, end,
                    android.text.style.ForegroundColorSpan.class);
            for (android.text.style.ForegroundColorSpan span : spans) {
                editable.removeSpan(span);
            }
        }
        ghostSpan = null;

        binding.ghostTextControlCard.setVisibility(View.GONE);
        binding.fabStopAi.setVisibility(View.GONE);
        binding.fabAiHelper.show(); // Restore FAB

        // Merge all streaming chunks into one Atomic Undo action
        undoRedoHelper.finishCheckpointMerge();
        undoRedoHelper.commitHistory(); // Ensure next user action is separate

        isGhostMode = false;
        ghostStartIndex = -1;
        setInputsEnabled(true);
    }

    private void discardGhostText() {
        if (!isGhostMode)
            return;

        // Cancel AI
        storyAiViewModel.cancelRequest();

        Editable editable = binding.storyContentEditText.getText();

        // Determine range to delete using the Span if available (Most accurate)
        int startDel = -1;
        int endDel = -1;

        if (ghostSpan != null) {
            startDel = editable.getSpanStart(ghostSpan);
            endDel = editable.getSpanEnd(ghostSpan);
            editable.removeSpan(ghostSpan);
            ghostSpan = null;
        } else if (ghostStartIndex >= 0) {
            // Fallback to index if span is lost
            startDel = ghostStartIndex;
            endDel = editable.length();
        }

        // Safety check ranges
        if (startDel >= 0 && endDel > startDel && endDel <= editable.length()) {
            isAiUpdating = true;
            editable.delete(startDel, endDel);
            isAiUpdating = false;
        }

        // Discard the streaming history items so Undo doesn't bring them back
        undoRedoHelper.finishCheckpointDiscard();

        binding.ghostTextControlCard.setVisibility(View.GONE);
        binding.fabStopAi.setVisibility(View.GONE);
        binding.fabAiHelper.show();

        isGhostMode = false;
        ghostStartIndex = -1;
        setInputsEnabled(true);
    }

    private void restoreAppearancePreferences() {
        android.content.SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        String theme = prefs.getString(PREF_KEY_THEME_MODE, THEME_SYSTEM);
        String font = prefs.getString(PREF_KEY_FONT_TYPE, FONT_SANS);
        applyThemeMode(theme);
        applyFontType(font);
    }

    private void saveAppearancePreference(String key, String value) {
        getPreferences(Context.MODE_PRIVATE).edit().putString(key, value).apply();
    }

    public void onThemeChanged(String themeMode) {
        applyThemeMode(themeMode);
        saveAppearancePreference(PREF_KEY_THEME_MODE, themeMode);
    }

    public void onFontChanged(String fontType) {
        applyFontType(fontType);
        saveAppearancePreference(PREF_KEY_FONT_TYPE, fontType);
    }

    public void onFocusModeChanged(boolean enabled) {
        if (enabled) {
            binding.toolbar.setVisibility(View.GONE);
            binding.fabAiHelper.hide();
        } else {
            binding.toolbar.setVisibility(View.VISIBLE);
            binding.fabAiHelper.show();
        }
    }

    private void applyThemeMode(String themeMode) {
        View root = binding.getRoot(); // CoordinatorLayout

        int bgColor, textColor, hintColor, cursorColor, selectionColor;

        if (THEME_PAPER.equals(themeMode)) {
            try {
                root.setBackgroundResource(R.drawable.bg_paper_texture);
            } catch (Exception e) {
                // Fallback color if texture missing
                root.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.editor_paper_bg));
            }
            textColor = androidx.core.content.ContextCompat.getColor(this, R.color.editor_paper_text);
            hintColor = androidx.core.content.ContextCompat.getColor(this, R.color.editor_paper_hint);
            cursorColor = androidx.core.content.ContextCompat.getColor(this, R.color.editor_paper_cursor);
            selectionColor = androidx.core.content.ContextCompat.getColor(this, R.color.editor_paper_selection);
        } else if (THEME_MIDNIGHT.equals(themeMode)) {
            root.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.editor_midnight_bg));
            textColor = androidx.core.content.ContextCompat.getColor(this, R.color.editor_midnight_text);
            hintColor = androidx.core.content.ContextCompat.getColor(this, R.color.editor_midnight_hint);
            cursorColor = androidx.core.content.ContextCompat.getColor(this, R.color.editor_midnight_cursor);
            selectionColor = androidx.core.content.ContextCompat.getColor(this, R.color.editor_midnight_selection);
        } else {
            // SYSTEM - Resolve attributes
            android.util.TypedValue typedValue = new android.util.TypedValue();

            getTheme().resolveAttribute(R.attr.colorEditorBackground, typedValue, true);
            root.setBackgroundColor(typedValue.data);

            getTheme().resolveAttribute(R.attr.colorEditorText, typedValue, true);
            textColor = typedValue.data;

            getTheme().resolveAttribute(R.attr.colorEditorHint, typedValue, true);
            hintColor = typedValue.data;

            getTheme().resolveAttribute(R.attr.colorEditorCursor, typedValue, true);
            cursorColor = typedValue.data;

            getTheme().resolveAttribute(R.attr.colorEditorSelection, typedValue, true);
            selectionColor = typedValue.data;
        }

        // Apply Colors
        binding.storyContentEditText.setTextColor(textColor);
        binding.storyContentEditText.setHintTextColor(hintColor);
        binding.storyContentEditText.setHighlightColor(selectionColor);

        binding.storyTitleEditText.setTextColor(textColor);
        binding.storyTitleEditText.setHintTextColor(hintColor);
    }

    private void applyFontType(String fontType) {
        Typeface tf = Typeface.DEFAULT;
        switch (fontType) {
            case FONT_SERIF:
                tf = Typeface.SERIF;
                break;
            case FONT_CURSIVE:
                tf = Typeface.create("cursive", Typeface.NORMAL);
                break;
            default:
                tf = Typeface.SANS_SERIF;
                break;
        }
        binding.storyContentEditText.setTypeface(tf);
        binding.storyTitleEditText.setTypeface(tf, Typeface.BOLD);
    }
}
