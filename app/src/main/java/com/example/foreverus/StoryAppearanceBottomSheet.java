package com.example.foreverus;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.example.foreverus.databinding.BottomSheetStoryAppearanceBinding;

public class StoryAppearanceBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetStoryAppearanceBinding binding;
    private OnAppearanceChangedListener listener;

    public interface OnAppearanceChangedListener {
        void onThemeChanged(String themeMode); // "SYSTEM", "PAPER", "MIDNIGHT"
        void onFontChanged(String fontType);   // "SANS", "SERIF", "CURSIVE"
        void onFocusModeChanged(boolean enabled);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnAppearanceChangedListener) {
            listener = (OnAppearanceChangedListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetStoryAppearanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup listeners
        binding.chipThemeSystem.setOnClickListener(v -> notifyThemeChanged("SYSTEM"));
        binding.chipThemePaper.setOnClickListener(v -> notifyThemeChanged("PAPER"));
        binding.chipThemeMidnight.setOnClickListener(v -> notifyThemeChanged("MIDNIGHT"));

        binding.chipFontSans.setOnClickListener(v -> notifyFontChanged("SANS"));
        binding.chipFontSerif.setOnClickListener(v -> notifyFontChanged("SERIF"));
        binding.chipFontCursive.setOnClickListener(v -> notifyFontChanged("CURSIVE"));

        binding.switchFocusMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onFocusModeChanged(isChecked);
        });

        // Initialize state (We could pass current state via arguments to set correct selection)
        // For now, let's leave it neutral or default
    }

    private void notifyThemeChanged(String theme) {
        if (listener != null) listener.onThemeChanged(theme);
        dismiss(); // Optional: Close on selection? Or let user stay? Let's stay open for multi-edit.
    }

    private void notifyFontChanged(String font) {
        if (listener != null) listener.onFontChanged(font);
        // Don't dismiss for fonts, users like to try them out
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
