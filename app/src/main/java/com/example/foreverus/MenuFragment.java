package com.example.foreverus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

public class MenuFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        NavController navController = Navigation.findNavController(view);

        view.findViewById(R.id.cardTimeline).setOnClickListener(v -> navController.navigate(R.id.timeline));

        view.findViewById(R.id.cardAdventure).setOnClickListener(v -> navController.navigate(R.id.adventure_board));

        view.findViewById(R.id.cardSettings).setOnClickListener(v -> navController.navigate(R.id.settings));

        view.findViewById(R.id.cardPolaroid).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), PolaroidMakerActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.cardSpinner).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), SpinnerActivity.class);
            startActivity(intent);
        });
    }
}
