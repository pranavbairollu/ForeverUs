package com.example.foreverus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.foreverus.databinding.FragmentTimelineBinding;
import java.util.List;

public class TimelineFragment extends Fragment {

    private FragmentTimelineBinding binding;
    private TimelineViewModel timelineViewModel;
    private TimelineAdapter timelineAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTimelineBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    private String currentRelationshipId;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        timelineViewModel = new ViewModelProvider(this).get(TimelineViewModel.class);

        setupRecyclerView();
        observeViewModel();

        binding.swipeRefreshLayout.setOnRefreshListener(() -> timelineViewModel.refresh());
        // Retry button specific binding moved to showError for shared layout
        // flexibility

        // Observe the relationshipId to load the timeline
        RelationshipRepository.getInstance().getRelationshipId().observe(getViewLifecycleOwner(), id -> {
            this.currentRelationshipId = id;
            timelineViewModel.loadTimeline(id);
        });

        binding.filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty())
                return;
            int id = checkedIds.get(0);
            String filter = "ALL";
            if (id == R.id.chipStories)
                filter = "STORIES";
            else if (id == R.id.chipMemories)
                filter = "MEMORIES";
            else if (id == R.id.chipLetters)
                filter = "LETTERS";

            timelineViewModel.setFilter(filter);
        });

        timelineAdapter.setOnItemClickListener((item, sharedView, transitionName) -> {
            if (!isAdded() || currentRelationshipId == null) {
                if (isAdded())
                    Toast.makeText(getContext(), "Relationship ID not loaded", Toast.LENGTH_SHORT).show();
                return;
            }

            if (item instanceof TimelineItem.StoryItem) {
                com.example.foreverus.Story story = ((TimelineItem.StoryItem) item).getStory();
                android.content.Intent intent = new android.content.Intent(getContext(), StoryEditorActivity.class);
                intent.putExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID, currentRelationshipId);
                intent.putExtra("storyId", story.getId());
                startActivity(intent);
            } else if (item instanceof TimelineItem.LetterItem) {
                com.example.foreverus.Letter letter = ((TimelineItem.LetterItem) item).getLetter();
                android.content.Intent intent = new android.content.Intent(getContext(), LetterViewActivity.class);
                intent.putExtra("letterId", letter.getLetterId());
                intent.putExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID, currentRelationshipId);
                startActivity(intent);
            } else if (item instanceof TimelineItem.MemoryItem) {
                com.example.foreverus.Memory memory = ((TimelineItem.MemoryItem) item).getMemory();
                if ("video".equals(memory.getMediaType())) {
                    // Handled in adapter
                } else {
                    android.content.Intent intent = new android.content.Intent(getContext(),
                            MemoryDetailActivity.class);
                    intent.putExtra(MemoryDetailActivity.EXTRA_IMAGE_URL, memory.getImageUrl());
                    intent.putExtra("extra_title", memory.getTitle());
                    intent.putExtra("extra_description", memory.getDescription());

                    if (sharedView != null && transitionName != null) {
                        intent.putExtra(MemoryDetailActivity.EXTRA_TRANSITION_NAME, transitionName);
                        androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat
                                .makeSceneTransitionAnimation(
                                        requireActivity(), sharedView, transitionName);
                        startActivity(intent, options.toBundle());
                    } else {
                        startActivity(intent);
                    }
                }
            }
        });
    }

    private void setupRecyclerView() {
        timelineAdapter = new TimelineAdapter();
        binding.timelineRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.timelineRecyclerView.setAdapter(timelineAdapter);
        binding.timelineRecyclerView.addItemDecoration(new TimelineDecoration(getContext()));
    }

    private void observeViewModel() {
        timelineViewModel.getTimelineItems().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null)
                return;

            binding.swipeRefreshLayout.setRefreshing(resource.status == Resource.Status.LOADING);

            if (resource.status == Resource.Status.SUCCESS || (resource.status == Resource.Status.ERROR
                    && resource.data != null && !resource.data.isEmpty())) {
                showSuccess(resource.data);
            } else if (resource.status == Resource.Status.LOADING
                    && (resource.data == null || resource.data.isEmpty())) {
                showLoading();
            } else if (resource.status == Resource.Status.ERROR) {
                showError(resource.message);
            }

            if (resource.status == Resource.Status.ERROR && resource.data != null) {
                Toast.makeText(getContext(), resource.message, Toast.LENGTH_LONG).show();
            }
        });

        timelineViewModel.getCoupleNickname().observe(getViewLifecycleOwner(), nickname -> {
            binding.coupleNicknameTextView
                    .setText(nickname != null && !nickname.isEmpty() ? nickname : getString(R.string.app_name));
        });
    }

    private void showLoading() {
        binding.shimmerViewContainer.setVisibility(View.VISIBLE);
        binding.shimmerViewContainer.startShimmer();
        binding.timelineRecyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);
    }

    private void showSuccess(List<TimelineItem> timelineItems) {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        if (timelineItems != null && !timelineItems.isEmpty()) {
            binding.timelineRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyView.getRoot().setVisibility(View.GONE);
            timelineAdapter.submitList(timelineItems);
        } else {
            showEmpty();
        }
    }

    private void showEmpty() {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.timelineRecyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.VISIBLE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        binding.emptyView.txtEmptyTitle.setText(R.string.timeline_empty_soft);
        binding.emptyView.txtEmptyMessage.setVisibility(View.GONE);
    }

    private void showError(String message) {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.timelineRecyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.VISIBLE);

        binding.errorView.txtErrorTitle.setText(R.string.timeline_error_title);
        binding.errorView.txtErrorMessage
                .setText(message != null ? message : getString(R.string.timeline_error_subtitle));
        binding.errorView.btnErrorRetry.setOnClickListener(v -> timelineViewModel.refresh());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
