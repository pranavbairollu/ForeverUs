package com.example.foreverus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

public class PolaroidGalleryFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView txtEmptyState;
    private PolaroidAdapter adapter;
    private PolaroidDao dao;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_polaroid_gallery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewPolaroids);
        txtEmptyState = view.findViewById(R.id.txtEmptyState);

        // 2 Columns with better spacing
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        // Simple manual padding/margin since we don't have a dimens file handy for this
        // unique feature
        recyclerView.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                    @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int spacing = 16;
                outRect.left = spacing;
                outRect.right = spacing;
                outRect.bottom = spacing;
                outRect.top = spacing;
            }
        });

        adapter = new PolaroidAdapter(requireContext(), polaroid -> {
            // Fullscreen Viewer (Dialog)
            // Fullscreen Viewer (Dialog)
            if (polaroid.imagePath != null) {
                if (!isAdded() || getContext() == null)
                    return;

                android.app.Dialog dialog = new android.app.Dialog(requireContext(),
                        android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                dialog.setContentView(R.layout.dialog_fullscreen_image);

                ImageView fullImg = dialog.findViewById(R.id.fullscreenImageView);
                com.bumptech.glide.Glide.with(this)
                        .load(polaroid.imagePath)
                        .fitCenter()
                        .into(fullImg);

                // Close on tap
                fullImg.setOnClickListener(v -> dialog.dismiss());

                dialog.show();
            }
        });

        recyclerView.setAdapter(adapter);

        // Load Data
        dao = AppDatabase.getDatabase(requireContext()).polaroidDao();
        dao.getAllPolaroids().observe(getViewLifecycleOwner(), polaroids -> {
            if (polaroids == null || polaroids.isEmpty()) {
                txtEmptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                txtEmptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.setPolaroids(polaroids);
                // Subtle fade in for list if first load
                if (recyclerView.getAlpha() == 0f) {
                    recyclerView.setAlpha(0f);
                    recyclerView.animate().alpha(1f).setDuration(200).start();
                }
            }
        });
    }
}
