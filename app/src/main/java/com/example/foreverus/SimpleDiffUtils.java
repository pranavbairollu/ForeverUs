package com.example.foreverus;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleDiffUtils {

    public enum Operation {
        EQUAL,
        DELETE,
        INSERT
    }

    public static class Diff {
        public Operation operation;
        public String text;

        public Diff(Operation operation, String text) {
            this.operation = operation;
            this.text = text;
        }
    }

    /**
     * Computes a word-level diff between original and new text.
     * Preserves punctuation and whitespace by tokenizing aggressively.
     */
    public static List<Diff> computeDiff(String text1, String text2) {
        List<String> tokens1 = tokenize(text1);
        List<String> tokens2 = tokenize(text2);

        // Standard LCS algorithm
        int[][] lcs = new int[tokens1.size() + 1][tokens2.size() + 1];

        for (int i = 0; i < tokens1.size(); i++) {
            for (int j = 0; j < tokens2.size(); j++) {
                if (tokens1.get(i).equals(tokens2.get(j))) {
                    lcs[i + 1][j + 1] = lcs[i][j] + 1;
                } else {
                    lcs[i + 1][j + 1] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        // Backtrack to generate diff
        List<Diff> diffs = new ArrayList<>();
        int i = tokens1.size();
        int j = tokens2.size();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && tokens1.get(i - 1).equals(tokens2.get(j - 1))) {
                diffs.add(0, new Diff(Operation.EQUAL, tokens1.get(i - 1)));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                diffs.add(0, new Diff(Operation.INSERT, tokens2.get(j - 1)));
                j--;
            } else if (i > 0 && (j == 0 || lcs[i][j - 1] < lcs[i - 1][j])) {
                diffs.add(0, new Diff(Operation.DELETE, tokens1.get(i - 1)));
                i--;
            }
        }

        return diffs;
    }

    // Tokenize by splitting on whitespace but keeping delimiters,
    // and also splitting on punctuation.
    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        // Regex to match words, whitespace, or punctuation
        // We want to keep everything.
        // Match: \s+ (whitespace), OR \w+ (word chars), OR . (anything else, single char punctuation)
        Pattern pattern = Pattern.compile("\\s+|\\w+|[^\\s\\w]");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    public static CharSequence renderDiff(android.content.Context context, List<Diff> diffs) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        
        // Resolve Diff Colors from Theme
        int insertColor = resolveColor(context, R.attr.colorDiffInsert, android.graphics.Color.GREEN);
        int deleteColor = resolveColor(context, R.attr.colorDiffDelete, android.graphics.Color.RED);
        
        for (Diff diff : diffs) {
            int start = ssb.length();
            if (diff.operation == Operation.EQUAL) {
                ssb.append(diff.text);
            } else if (diff.operation == Operation.DELETE) {
                ssb.append(diff.text);
                ssb.setSpan(new StrikethroughSpan(), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(deleteColor), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (diff.operation == Operation.INSERT) {
                ssb.append(diff.text);
                ssb.setSpan(new ForegroundColorSpan(insertColor), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return ssb;
    }
    
    private static int resolveColor(android.content.Context context, int attrId, int fallback) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attrId, typedValue, true)) {
            return typedValue.data;
        }
        return fallback;
    }

    public static boolean hasChanges(List<Diff> diffs) {
        for (Diff diff : diffs) {
            if (diff.operation != Operation.EQUAL) return true;
        }
        return false;
    }
}
