package com.example.foreverus;

import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.widget.EditText;

import java.util.LinkedList;

public class UndoRedoHelper {
    private boolean isUndoing = false;
    private final EditText editText;
    private final LinkedList<EditItem> history = new LinkedList<>();
    private final LinkedList<EditItem> redoHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 50;
    
    // Merging logic
    private static final long MERGE_WINDOW_MS = 2500;
    private long lastOpTime = 0;

    public UndoRedoHelper(EditText editText) {
        this.editText = editText;
        editText.addTextChangedListener(new TextWatcher() {
            private CharSequence beforeText;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!isUndoing) {
                    // Capture a snapshot including spans
                    beforeText = new SpannableStringBuilder(s);
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isUndoing && beforeText != null) {
                    // Only add to history if the text actually changed
                    if (!beforeText.toString().equals(s.toString())) {
                        long now = System.currentTimeMillis();
                        boolean isMergeable = (now - lastOpTime) < MERGE_WINDOW_MS;
                        
                        // Check if we can merge this edit into the previous one
                        // Simple heuristic: If typing continues (count=1), merge.
                        // For now, let's just use time-based merging for typing.
                        
                        if (isMergeable && !history.isEmpty()) {
                            EditItem lastItem = history.peekFirst();
                            // Update the "after" state of the last item, keeping its "before" state
                            lastItem.after = new SpannableStringBuilder(s);
                        } else {
                            // New edit item
                            history.addFirst(new EditItem(beforeText, new SpannableStringBuilder(s)));
                            if (history.size() > MAX_HISTORY_SIZE) {
                                history.removeLast();
                            }
                        }
                        
                        lastOpTime = now;
                        redoHistory.clear();
                    }
                }
            }
        });
    }

    public void undo() {
        if (!history.isEmpty()) {
            isUndoing = true;
            EditItem item = history.removeFirst();
            redoHistory.addFirst(item);
            
            // Restore text with spans
            int currentSelection = editText.getSelectionStart();
            editText.setText(item.before);
            
            if (editText.getText() instanceof Editable) {
               // Restore cursor end if possible, or keep approximate position
               int len = editText.getText().length();
               // Ensure we don't crash if restored text is shorter than previous selection start
               // Although 'before' text should match, safety first.
               int safeSelection = Math.min(len, Math.max(0, currentSelection));
               
               // Double check against new text length just to be 100% sure
               if (safeSelection <= len && safeSelection >= 0) {
                   try {
                       Selection.setSelection(editText.getText(), safeSelection);
                   } catch (IndexOutOfBoundsException e) {
                       // Fallback to end of text
                       Selection.setSelection(editText.getText(), len);
                   }
               }
            }
            isUndoing = false;
        }
    }

    public void redo() {
        if (!redoHistory.isEmpty()) {
            isUndoing = true;
            EditItem item = redoHistory.removeFirst();
            history.addFirst(item);
            
            editText.setText(item.after);
            Selection.setSelection(editText.getText(), item.after.length());
            isUndoing = false;
        }
    }
    
    // Explicitly add an undo checkpoint (e.g., after AI insertion)
    public void commitHistory() {
        lastOpTime = 0; // Force next edit to be a new item
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public boolean canRedo() {
        return !redoHistory.isEmpty();
    }
    
    // State Persistence
    public void saveState(android.os.Bundle outState) {
        // Saving rich text history to Bundle can be heavy. 
        // For simplicity/performance in this demo, strict persistence of history is elided
        // or we could serialize just strings. 
        // A better approach for production is a persistent local DB or file.
        // For now, we'll skip saving history on rotation to avoid TransactionTooLargeException
    }
    
    public void restoreState(android.os.Bundle savedInstanceState) {
       // No-op for now to be safe
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

        // We want to merge all items from [Top] down to [history.size() - checkpointHistorySize] items.
        // History is a LinkedList, addFirst/removeFirst.
        // So indices 0 to (NewCount - OldCount - 1) are the new items.
        // We want to collapse them into one.

        EditItem firstItem = history.peekFirst(); // Newest (Final After)
        EditItem lastItem = null; // We need to find the Oldest of the new items (Initial Before)

        int itemsToMerge = history.size() - checkpointHistorySize;
        if (itemsToMerge <= 1) {
            checkpointHistorySize = -1;
            return; // Nothing to merge
        }
        
        // Remove items until we match size, keeping track of the oldest's "before"
        CharSequence finalAfter = firstItem.after;
        CharSequence initialBefore = null;
        
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
        if (checkpointHistorySize == -1) return;
        
        while (history.size() > checkpointHistorySize) {
            history.removeFirst();
        }
        checkpointHistorySize = -1;
    }

    private static class EditItem {
        final CharSequence before;
        CharSequence after; // Mutable for merging

        EditItem(CharSequence before, CharSequence after) {
            this.before = before;
            this.after = after;
        }
    }
}
