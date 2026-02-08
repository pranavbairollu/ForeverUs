package com.example.foreverus;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.foreverus.databinding.FragmentMemoriesBinding;
import java.util.List;

public class MemoriesFragment extends Fragment {

    private FragmentMemoriesBinding binding;
    private MemoryAdapter memoryAdapter;
    private MemoriesViewModel memoriesViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentMemoriesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        memoriesViewModel = new ViewModelProvider(this).get(MemoriesViewModel.class);

        setupRecyclerView();
        observeViewModel();

        binding.addMemoryFab.setOnClickListener(v -> {
            String relationshipId = RelationshipRepository.getInstance().getRelationshipId().getValue();
            if (relationshipId != null) {
                Intent intent = new Intent(getActivity(), AddMemoryActivity.class);
                intent.putExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID, relationshipId);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Relationship not set up", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecyclerView() {
        androidx.recyclerview.widget.StaggeredGridLayoutManager layoutManager = new androidx.recyclerview.widget.StaggeredGridLayoutManager(
                2, androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(androidx.recyclerview.widget.StaggeredGridLayoutManager.GAP_HANDLING_NONE); // Prevent
                                                                                                                 // jumping
        binding.memoriesRecyclerView.setLayoutManager(layoutManager);
        memoryAdapter = new MemoryAdapter();
        binding.memoriesRecyclerView.setAdapter(memoryAdapter);
        binding.memoriesRecyclerView.setItemAnimator(null); // Fix potential blinking
    }

    private void observeViewModel() {
        RelationshipRepository.getInstance().getRelationshipId().observe(getViewLifecycleOwner(), id -> {
            memoriesViewModel.loadMemories(id);
        });

        memoriesViewModel.getMemories().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null)
                return;

            switch (resource.status) {
                case LOADING:
                    showLoading(resource.data);
                    break;
                case SUCCESS:
                    showContent(resource.data);
                    break;
                case ERROR:
                    showError(resource.message, resource.data);
                    break;
            }
        });
    }

    private void showLoading(List<Memory> data) {
        if (data == null || data.isEmpty()) {
            binding.shimmerViewContainer.startShimmer();
            binding.shimmerViewContainer.setVisibility(View.VISIBLE);
            binding.memoriesRecyclerView.setVisibility(View.GONE);
            binding.emptyView.getRoot().setVisibility(View.GONE);
            binding.errorView.getRoot().setVisibility(View.GONE);
        } else {
            showContent(data);
        }
    }

    private void showContent(List<Memory> memories) {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.memoriesRecyclerView.setVisibility(View.VISIBLE);
        binding.emptyView.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        if (memories != null && !memories.isEmpty()) {
            // Sort by Timestamp Descending
            java.util.Collections.sort(memories, (m1, m2) -> {
                if (m1.getTimestamp() == null || m2.getTimestamp() == null)
                    return 0;
                return m2.getTimestamp().compareTo(m1.getTimestamp());
            });

            java.util.List<MemoryListItem> listItems = new java.util.ArrayList<>();

            // "On This Day" Logic
            java.util.List<Memory> onThisDayMemories = new java.util.ArrayList<>();
            java.util.Calendar today = java.util.Calendar.getInstance();
            int currentMonth = today.get(java.util.Calendar.MONTH);
            int currentDay = today.get(java.util.Calendar.DAY_OF_MONTH);
            int currentYear = today.get(java.util.Calendar.YEAR);

            for (Memory m : memories) {
                if (m.getTimestamp() != null) {
                    java.util.Calendar memDate = java.util.Calendar.getInstance();
                    memDate.setTime(m.getTimestamp().toDate());
                    if (memDate.get(java.util.Calendar.MONTH) == currentMonth &&
                            memDate.get(java.util.Calendar.DAY_OF_MONTH) == currentDay &&
                            memDate.get(java.util.Calendar.YEAR) != currentYear) {
                        onThisDayMemories.add(m);
                    }
                }
            }

            if (!onThisDayMemories.isEmpty()) {
                listItems.add(new MemoryHeader("On This Day ❤️"));
                listItems.addAll(onThisDayMemories);
                listItems.add(new MemoryHeader("All Memories"));
            }

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());

            String lastHeader = "";

            for (Memory memory : memories) {
                if (memory.getTimestamp() != null) {
                    java.util.Date date = memory.getTimestamp().toDate();
                    String currentHeader = sdf.format(date);

                    if (!currentHeader.equals(lastHeader)) {
                        listItems.add(new MemoryHeader(currentHeader));
                        lastHeader = currentHeader;
                    }
                }
                listItems.add(memory);
            }

            memoryAdapter.submitList(listItems);
        } else {
            showEmpty();
        }
    }

    private void showEmpty() {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.memoriesRecyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.VISIBLE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        binding.emptyView.txtEmptyTitle.setText(R.string.memories_empty_soft);
        binding.emptyView.txtEmptyMessage.setVisibility(View.GONE);

        // Add Button Logic
        binding.emptyView.btnCreateFirstMemory.setVisibility(View.VISIBLE);
        binding.emptyView.btnCreateFirstMemory.setText("Create First Memory");
        binding.emptyView.btnCreateFirstMemory.setOnClickListener(v -> {
            String relationshipId = RelationshipRepository.getInstance().getRelationshipId().getValue();
            if (relationshipId != null) {
                Intent intent = new Intent(getActivity(), AddMemoryActivity.class);
                intent.putExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID, relationshipId);
                startActivity(intent);
            }
        });

        // Pulse Animation for the button
        android.view.animation.ScaleAnimation pulse = new android.view.animation.ScaleAnimation(
                1.0f, 1.05f, 1.0f, 1.05f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(1000);
        pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
        pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
        binding.emptyView.btnCreateFirstMemory.startAnimation(pulse);
    }

    private void showError(String message, List<Memory> data) {
        binding.errorView.btnErrorRetry.setOnClickListener(
                v -> RelationshipRepository.getInstance().getRelationshipId().observe(getViewLifecycleOwner(), id -> {
                    memoriesViewModel.loadMemories(id);
                }));
        if (data != null && !data.isEmpty()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            showContent(data);
        } else {
            binding.shimmerViewContainer.stopShimmer();
            binding.shimmerViewContainer.setVisibility(View.GONE);
            binding.memoriesRecyclerView.setVisibility(View.GONE);
            binding.emptyView.getRoot().setVisibility(View.GONE);
            binding.errorView.getRoot().setVisibility(View.VISIBLE);

            binding.errorView.txtErrorTitle.setText(R.string.error_title_default);
            binding.errorView.txtErrorMessage
                    .setText(message != null ? message : getString(R.string.memories_error_state_text));
            binding.errorView.btnErrorRetry.setOnClickListener(v -> RelationshipRepository.getInstance()
                    .getRelationshipId().observe(getViewLifecycleOwner(), id -> {
                        memoriesViewModel.loadMemories(id);
                    }));
        }
    }

    @Override
    public void onPause() {
        binding.shimmerViewContainer.stopShimmer();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
