package com.example.foreverus;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.example.foreverus.databinding.BottomSheetAiOptionsBinding;

public class StoryAiOptionsBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetAiOptionsBinding binding;
    private OnAiOptionSelectedListener listener;

    public interface OnAiOptionSelectedListener {
        void onAiOptionSelected(String actionType, String customInstruction); 
    }
    
    // Action Constants
    public static final String ACTION_CONTINUE = "CONTINUE";
    public static final String ACTION_FIX_GRAMMAR = "FIX_GRAMMAR";
    public static final String ACTION_REWRITE = "REWRITE";
    public static final String ACTION_EXPAND = "EXPAND";
    public static final String ACTION_SHORTEN = "SHORTEN";
    public static final String ACTION_FUNNY = "FUNNY";
    public static final String ACTION_DRAMATIC = "DRAMATIC";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnAiOptionSelectedListener) {
            listener = (OnAiOptionSelectedListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAiOptionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupListener(binding.chipAiContinue, ACTION_CONTINUE);
        setupListener(binding.chipAiFixGrammar, ACTION_FIX_GRAMMAR);
        setupListener(binding.chipAiRewrite, ACTION_REWRITE);
        setupListener(binding.chipAiExpand, ACTION_EXPAND);
        setupListener(binding.chipAiShorten, ACTION_SHORTEN);
        setupListener(binding.chipAiMakeFunny, ACTION_FUNNY);
        setupListener(binding.chipAiMakeDramatic, ACTION_DRAMATIC);
    }
    
    private void setupListener(View view, String action) {
        view.setOnClickListener(v -> {
            if (listener != null) {
                String instruction = binding.etCustomInstruction.getText().toString().trim();
                listener.onAiOptionSelected(action, instruction);
            }
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
