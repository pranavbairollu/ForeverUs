package com.example.foreverus;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AdventureSuggestionEngine {

    private static final String ASSET_FILE = "adventure_suggestions.json";

    public static class Suggestion {
        public String text;
        public String category; // TRAVEL, HOME, DATE, GOAL
        public String costType; // FREE, BUDGET, SAVING

        public Suggestion(String text, String category, String costType) {
            this.text = text;
            this.category = category;
            this.costType = costType;
        }
    }

    private List<Suggestion> allSuggestions;

    public AdventureSuggestionEngine(Context context) {
        loadSuggestions(context);
    }

    private void loadSuggestions(Context context) {
        allSuggestions = new ArrayList<>();
        try {
            InputStream is = context.getAssets().open(ASSET_FILE);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            JSONObject root = new JSONObject(json);
            JSONArray array = root.getJSONArray("suggestions");
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                allSuggestions.add(new Suggestion(
                        obj.getString("text"),
                        obj.getString("category"),
                        obj.optString("costType", "VARIES")));
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallbacks if file load fails
            allSuggestions.add(new Suggestion("Watch the sunset", "DATE", "FREE"));
            allSuggestions.add(new Suggestion("Cook a meal", "HOME", "BUDGET"));
        }
    }

    public Suggestion getSmartSuggestion(List<BucketItem> currentItems) {
        if (allSuggestions == null || allSuggestions.isEmpty())
            return null;

        // 1. Filter out already added items (by approximate text match)
        List<Suggestion> candidates = new ArrayList<>();
        for (Suggestion s : allSuggestions) {
            boolean exists = false;
            for (BucketItem item : currentItems) {
                if (item.getTitle().equalsIgnoreCase(s.text)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                candidates.add(s);
            }
        }

        if (candidates.isEmpty())
            return allSuggestions.get(new Random().nextInt(allSuggestions.size()));

        // 2. Identify under-represented categories
        Map<String, Integer> categoryCounts = new HashMap<>();
        categoryCounts.put("TRAVEL", 0);
        categoryCounts.put("HOME", 0);
        categoryCounts.put("DATE", 0);
        categoryCounts.put("GOAL", 0);

        for (BucketItem item : currentItems) {
            String type = item.getType() != null ? item.getType().toUpperCase() : "OTHER";
            // Map our JSON "HOME" to "OTHER" or "DATE" or keep separate?
            // "HOME" isn't a standard BucketItem type yet (types: TRAVEL, MOVIE, DATE,
            // GOAL, OTHER).
            // Let's assume HOME maps to DATE or OTHER.
            // Better: Let's map strict types.
            // If item type matches category key.
            if (categoryCounts.containsKey(type)) {
                categoryCounts.put(type, categoryCounts.get(type) + 1);
            }
        }

        // Find least used category among candidates
        // We actually want to prioritize categories that the USER HASN'T added yet.
        // So we score candidates.
        List<Suggestion> weightedCandidates = new ArrayList<>();
        for (Suggestion s : candidates) {
            String cat = s.category; // TRAVEL, HOME, DATE, GOAL
            // Convert 'HOME' to a known type if needed, or update BucketItem types later.
            // For now, let's treat HOME as 'DATE' or 'OTHER'.
            // Actually, let's just use the count of that exact string if it matches.

            int count = categoryCounts.getOrDefault(cat, 0);

            // Weight logic:
            // Count 0 -> Add 3 times
            // Count 1 -> Add 2 times
            // Count >2 -> Add 1 time
            int weight = 1;
            if (count == 0)
                weight = 4;
            else if (count <= 2)
                weight = 2;

            for (int i = 0; i < weight; i++) {
                weightedCandidates.add(s);
            }
        }

        return weightedCandidates.get(new Random().nextInt(weightedCandidates.size()));
    }
}
