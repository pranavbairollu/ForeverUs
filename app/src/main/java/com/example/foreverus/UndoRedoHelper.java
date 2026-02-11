package com.example.foreverus;

import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.LinkedList;

public class UndoRedoHelper {
    private boolean isUndoing = false;
    private boolean isEnabled = true;
    private final EditText editText;
    private final LinkedList<EditItem> history;
    private final LinkedList<EditItem> redoHistory;
    private static final int MAX_HISTORY_SIZE = 50;

    // Merging logic
    private static final long MERGE_WINDOW_MS = 1000;
    private long lastOpTime = 0;

    private int currentEditStart = 0;
    private int currentEditCount = 0;
    private int currentEditBefore = 0;

    private int lastEditStart = -1;
    private int lastEditCount = -1;

    public UndoRedoHelper(EditText editText) {
        this(editText, new LinkedList<>(), new LinkedList<>());
    }

    public UndoRedoHelper(EditText editText, LinkedList<EditItem> history, LinkedList<EditItem> redoHistory) {
        this.editText = editText;
        this.history = history != null ? history : new LinkedList<>();
        this.redoHistory = redoHistory != null ? redoHistory : new LinkedList<>();

        editText.addTextChangedListener(new TextWatcher() {
            private CharSequence beforeText;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (isEnabled && !isUndoing) {
                    // Capture a snapshot including spans
                    beforeText = new SpannableStringBuilder(s);
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isEnabled && !isUndoing) {
                    currentEditStart = start;
                    currentEditCount = count;
                    currentEditBefore = before;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isEnabled || isUndoing || beforeText == null)
                    return;

                // Only add to history if the text actually changed
                if (!beforeText.toString().equals(s.toString())) {
                    long now = System.currentTimeMillis();
                    boolean isTimeMergeable = (now - lastOpTime) < MERGE_WINDOW_MS;

                    // Improved Merge Logic:
                    // 1. Time must be within window
                    // 2. Edit must be contiguous (e.g. typing forward)
                    // 3. Edit must NOT contain a newline change (standard behavior: Enter = new
                    // undo)

                    boolean hasNewline = s.toString().contains("\n") && !beforeText.toString().contains("\n");
                    // Actually, more robust: did the *change* involve a newline?
                    // If simple typing, count is usually 1.
                    boolean isNewlineParams = (currentEditCount == 1 && s.length() > 0 && currentEditStart < s.length()
                            && s.charAt(currentEditStart) == '\n');

                    // Allow simple check: if length differs by 1 and the char is newline

                    boolean isContiguous = false;
                    if (lastEditStart != -1) {
                        // 1. Forward typing (Append)
                        if (currentEditStart == lastEditStart + lastEditCount) {
                            isContiguous = true;
                        }
                        // 2. Composing Replacement (Extending the current word)
                        // The range we are replacing (start to start+before) was exactly what we added
                        // last time (lastStart to lastStart+lastCount).
                        // This handles Android keyboards expanding the composing region (e.g. "H" ->
                        // "He")
                        else if (currentEditStart == lastEditStart && currentEditBefore == lastEditCount) {
                            isContiguous = true;
                        }
                        // 3. Backspacing (simplified check - deleting char before cursor)
                        else if (currentEditStart == lastEditStart - 1 && currentEditCount == 0) {
                            isContiguous = true;
                        }
                        // 4. Backspacing / Deleting selection
                        else if (currentEditStart == lastEditStart && currentEditCount == 0) {
                            isContiguous = true;
                        }
                    }

                    // Force new step if newline involved
                    if (isNewlineParams) {
                        isContiguous = false;
                    }

                    // Always treat rapid typing as mergeable if contiguous
                    if (isTimeMergeable && isContiguous && !UndoRedoHelper.this.history.isEmpty()) {
                        EditItem lastItem = UndoRedoHelper.this.history.peekFirst();
                        if (lastItem != null) {
                            // Update the "after" state of the last item
                            lastItem.after = new SpannableStringBuilder(s);
                        }
                    } else {
                        // New edit item
                        UndoRedoHelper.this.history.addFirst(new EditItem(beforeText, new SpannableStringBuilder(s)));
                        if (UndoRedoHelper.this.history.size() > MAX_HISTORY_SIZE) {
                            UndoRedoHelper.this.history.removeLast();
                        }
                    }

                    lastOpTime = now;
                    lastEditStart = currentEditStart;
                    lastEditCount = currentEditCount;
                    UndoRedoHelper.this.redoHistory.clear();
                }
            }
        });
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            // Reset merge state so when we re-enable, we don't merge with old edits
            lastOpTime = 0;
            lastEditStart = -1;
            lastEditCount = -1;
        }
    }

    public void clearHistory() {
        history.clear();
        redoHistory.clear();
        lastOpTime = 0;
        lastEditStart = -1;
        lastEditCount = -1;
    }

    public void undo() {
        try {
            if (!history.isEmpty()) {
                isUndoing = true;
                EditItem item = history.removeFirst();
                redoHistory.addFirst(item);

                // Restore text with spans
                int currentSelection = editText.getSelectionStart();
                editText.setText(item.before);

                if (editText.getText() instanceof Editable) {
                    // Try to restore cursor intelligently
                    int len = editText.getText().length();
                    // Standard undo behavior usually puts cursor at end of UNDONE change.
                    int safeSelection = Math.min(len, Math.max(0, currentSelection));

                    if (safeSelection <= len && safeSelection >= 0) {
                        try {
                            Selection.setSelection(editText.getText(), safeSelection);
                        } catch (IndexOutOfBoundsException e) {
                            Selection.setSelection(editText.getText(), len);
                        }
                    }
                }
                isUndoing = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            isUndoing = false;
        }
    }

    public void redo() {
        try {
            if (!redoHistory.isEmpty()) {
                isUndoing = true;
                EditItem item = redoHistory.removeFirst();
                history.addFirst(item);

                editText.setText(item.after);
                Selection.setSelection(editText.getText(), item.after.length());
                isUndoing = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            isUndoing = false;
        }
    }

    // Explicitly add an undo checkpoint (e.g., after AI insertion)
    public void commitHistory() {
        lastOpTime = 0; // Force next edit to be a new item
        lastEditStart = -1;
        lastEditCount = -1;
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public boolean canRedo() {
        return !redoHistory.isEmpty();
    }

    // State Persistence
    public void saveState(android.os.Bundle outState) {
    }

    public void restoreState(android.os.Bundle savedInstanceState) {
    }

    // Checkpoint system for Atomic Undo of streams
    private int checkpointHistorySize = -1;

    public void startCheckpoint() {
        checkpointHistorySize = history.size();
    }

    public void finishCheckpointMerge() {
        if (checkpointHistorySize == -1 || history.size() <= checkpointHistorySize) {
            checkpointHistorySize = -1;
            return;
        }

        EditItem firstItem = history.peekFirst(); // Newest (Final After)
        if (firstItem == null) {
            checkpointHistorySize = -1;
            return;
        }

        // Remove items until we match size, keeping track of the oldest's "before"
        CharSequence finalAfter = firstItem.after;
        CharSequence initialBefore = null;

        int itemsToMerge = history.size() - checkpointHistorySize;
        if (itemsToMerge <= 1) {
            checkpointHistorySize = -1;
            return;
        }

        EditItem lastItem = null;
        for (int i = 0; i < itemsToMerge; i++) {
            lastItem = history.removeFirst();
            initialBefore = lastItem.before;
        }

        // Add merged item
        history.addFirst(new EditItem(initialBefore, finalAfter));
        redoHistory.clear();
        checkpointHistorySize = -1;
    }

    public void finishCheckpointDiscard() {
        if (checkpointHistorySize == -1)
            return;

        while (history.size() > checkpointHistorySize) {
            history.removeFirst();
        }
        checkpointHistorySize = -1;
    }

    public static class EditItem {
        final CharSequence before;
        CharSequence after; // Mutable for merging

        public EditItem(CharSequence before, CharSequence after) {
            this.before = before;
            this.after = after;
        }
    }
}
