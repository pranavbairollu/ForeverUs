package com.example.foreverus;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Base class for ALL Activities in the application.
 *
 * <p><strong>CRITICAL:</strong> All new Activities MUST extend this class to ensure
 * proper theme application and dynamic switching behavior.
 * Failure to extend BaseActivity will result in inconsistent theming.</p>
 */
public abstract class BaseActivity extends AppCompatActivity {

    private String currentThemeColor;
    private String currentThemeMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Ensure the correct mode (Light/Dark) is applied before layout inflation
        ThemeManager.applyTheme(this);
        
        currentThemeColor = ThemeManager.getThemeColor(this);
        currentThemeMode = ThemeManager.getThemeMode(this);
        
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if theme or mode changed while activity was paused (e.g., from Settings)
        String storedColor = ThemeManager.getThemeColor(this);
        String storedMode = ThemeManager.getThemeMode(this);
        
        boolean colorChanged = !currentThemeColor.equals(storedColor);
        boolean modeChanged = !currentThemeMode.equals(storedMode);
        
        if (colorChanged || modeChanged) {
            recreate();
        }
    }
}
