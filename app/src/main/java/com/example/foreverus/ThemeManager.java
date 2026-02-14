package com.example.foreverus;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {

    // PREFERENCES
    private static final String PREFS_NAME = "app_preferences";
    public static final String KEY_THEME_COLOR = "selected_theme_color"; // Renamed for clarity, but using new key
    public static final String KEY_THEME_MODE = "selected_theme_mode";

    // Legacy Key for Migration
    private static final String LEGACY_KEY_SELECTED_THEME = "selected_theme";

    // COLOR STYLES
    public static final String THEME_DEFAULT = "DEFAULT";
    public static final String THEME_PINK_DREAM = "PINK_DREAM";
    public static final String THEME_MIDNIGHT_SKY = "MIDNIGHT_SKY";
    public static final String THEME_ROSE_GOLD = "ROSE_GOLD";

    // MODES
    public static final String MODE_LIGHT = "LIGHT";
    public static final String MODE_DARK = "DARK";
    public static final String MODE_SYSTEM = "SYSTEM";

    private ThemeManager() {
    }

    /**
     * Applies the selected theme mode (Light/Dark/System).
     * Call this in Application.onCreate() and BaseActivity.onCreate().
     */
    public static void applyTheme(Context context) {
        migrateLegacyTheme(context);

        String mode = getThemeMode(context);
        switch (mode) {
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private static void migrateLegacyTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 1. Check if we already migrated (Mode exists)
        if (prefs.contains(KEY_THEME_MODE)) {
            return;
        }

        // 2. Check if legacy theme exists
        if (!prefs.contains(LEGACY_KEY_SELECTED_THEME)) {
            // New user or no prev selection -> Set Defaults
            prefs.edit()
                    .putString(KEY_THEME_MODE, MODE_SYSTEM)
                    .putString(KEY_THEME_COLOR, THEME_DEFAULT)
                    .apply();
            return;
        }

        // Defensive: Check if migration is needed but legacy key is somehow missing
        // (redundant but safe)
        if (!prefs.contains(LEGACY_KEY_SELECTED_THEME)) {
            return;
        }

        // 3. Migrate
        String legacyTheme = prefs.getString(LEGACY_KEY_SELECTED_THEME, THEME_DEFAULT);
        String newMode;
        String newColor;

        switch (legacyTheme) {
            case THEME_MIDNIGHT_SKY:
                newMode = MODE_DARK;
                newColor = THEME_MIDNIGHT_SKY;
                break;
            case THEME_PINK_DREAM:
                newMode = MODE_LIGHT;
                newColor = THEME_PINK_DREAM;
                break;
            case THEME_ROSE_GOLD:
                newMode = MODE_LIGHT;
                newColor = THEME_ROSE_GOLD;
                break;
            default:
                newMode = MODE_SYSTEM;
                newColor = THEME_DEFAULT;
                break;
        }

        prefs.edit()
                .putString(KEY_THEME_MODE, newMode)
                .putString(KEY_THEME_COLOR, newColor)
                .remove(LEGACY_KEY_SELECTED_THEME) // Cleanup
                .apply();
    }

    // --- GETTERS & SETTERS ---

    public static void setThemeColor(Context context, String colorStyle) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME_COLOR, colorStyle).apply();
    }

    public static String getThemeColor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Fallback to legacy key if migration failed/skipped (shouldn't happen with
        // applyTheme called first)
        if (!prefs.contains(KEY_THEME_COLOR) && prefs.contains(LEGACY_KEY_SELECTED_THEME)) {
            return prefs.getString(LEGACY_KEY_SELECTED_THEME, THEME_DEFAULT);
        }
        return prefs.getString(KEY_THEME_COLOR, THEME_DEFAULT);
    }

    public static void setThemeMode(Context context, String mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME_MODE, mode).apply();
    }

    public static String getThemeMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME_MODE, MODE_SYSTEM);
    }

    public static int getThemeResId(Context context) {
        String themeColor = getThemeColor(context);

        switch (themeColor) {
            case THEME_PINK_DREAM:
                return R.style.Theme_ForeverUs_PinkDream;
            case THEME_MIDNIGHT_SKY:
                return R.style.Theme_ForeverUs_MidnightSky;
            case THEME_ROSE_GOLD:
                return R.style.Theme_ForeverUs_RoseGold;
            case THEME_DEFAULT:
            default:
                return R.style.Theme_ForeverUs;
        }
    }
}
