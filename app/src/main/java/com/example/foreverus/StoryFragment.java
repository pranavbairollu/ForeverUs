package com.example.foreverus;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.foreverus.databinding.FragmentStoryBinding;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoryFragment extends Fragment implements StoryAdapter.OnItemClickListener {

    private FragmentStoryBinding binding;
    private StoryAdapter storyAdapter;
    private StoriesViewModel storiesViewModel;
    private PdfExporter pdfExporter;

    private List<Story> originalList;
    private String currentQuery = "";

    private ExecutorService executorService;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    exportToPdf();
                } else {
                    Toast.makeText(requireContext(), R.string.story_export_permission_needed, Toast.LENGTH_SHORT)
                            .show();
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pdfExporter = new PdfExporter();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        executorService = Executors.newSingleThreadExecutor();
        storiesViewModel = new ViewModelProvider(this).get(StoriesViewModel.class);

        setupRecyclerView();
        observeViewModel();
        setupMenu();

        binding.swipeRefreshLayout.setOnRefreshListener(() -> storiesViewModel.refresh());

        binding.fabAddStory.setOnClickListener(v -> {
            String relationshipId = storiesViewModel.getRelationshipId().getValue();
            if (relationshipId == null) {
                showError(getString(R.string.error_relationship_id_missing));
                return;
            }
            Intent intent = new Intent(requireActivity(), StoryEditorActivity.class);
            intent.putExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID, relationshipId);
            startActivity(intent);
        });
    }

    private void setupMenu() {
        binding.toolbar.inflateMenu(R.menu.story_fragment_menu);

        MenuItem searchItem = binding.toolbar.getMenu().findItem(R.id.action_search);
        androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem
                .getActionView();

        if (searchView != null) {
            searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    currentQuery = newText;
                    filterStories(newText);
                    return true;
                }
            });
        }

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_export_to_pdf) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                } else {
                    exportToPdf();
                }
                return true;
            }
            return false;
        });
    }

    private void filterStories(String query) {
        if (originalList == null)
            return;

        if (query == null || query.trim().isEmpty()) {
            storyAdapter.submitList(originalList);
            // Re-check empty state locally
            if (originalList.isEmpty())
                showEmptyState();
        } else {
            String lowerCaseQuery = query.toLowerCase();
            List<Story> filteredList = new java.util.ArrayList<>();
            for (Story story : originalList) {
                if (story.getTitle().toLowerCase().contains(lowerCaseQuery) ||
                        story.getContent().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(story);
                }
            }
            storyAdapter.submitList(filteredList);
        }
    }

    private void setupRecyclerView() {
        binding.storiesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        storyAdapter = new StoryAdapter(this);

        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser();
        if (user != null) {
            storyAdapter.setCurrentUserId(user.getUid());
        }

        binding.storiesRecyclerView.setAdapter(storyAdapter);
    }

    private void observeViewModel() {
        if (storiesViewModel == null)
            return;

        storiesViewModel.getStories().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null)
                return;

            switch (resource.status) {
                case LOADING:
                    // Show shimmer only if we have no data to display.
                    if (resource.data == null || resource.data.isEmpty()) {
                        showLoadingState();
                        binding.swipeRefreshLayout.setRefreshing(false);
                    } else {
                        // If we have stale data, show it. The shimmer is distracting if data is
                        // present.
                        showSuccessState(resource.data);
                        // Show swipe spinner to indicate refresh
                        binding.swipeRefreshLayout.setRefreshing(true);
                    }
                    break;
                case SUCCESS:
                    binding.swipeRefreshLayout.setRefreshing(false);
                    if (resource.data != null && !resource.data.isEmpty()) {
                        showSuccessState(resource.data);
                    } else {
                        // This handles the case where all stories are deleted.
                        showEmptyState();
                    }
                    break;
                case ERROR:
                    binding.swipeRefreshLayout.setRefreshing(false);
                    // If we have cached data from Room, show it and display a non-blocking error
                    // message.
                    if (resource.data != null && !resource.data.isEmpty()) {
                        showSuccessState(resource.data);
                        Toast.makeText(requireContext(), "Sync failed: " + resource.message, Toast.LENGTH_LONG).show();
                    } else {
                        // Only show a full-screen error if there's nothing at all to show.
                        showErrorState(resource.message);
                    }
                    break;
            }
            // Invalidate menu to enable/disable the 'Export' button based on data
            // availability.
            // requireActivity().invalidateOptionsMenu(); // No-op since we use local
            // toolbar
        });
    }

    @Override
    public void onItemClick(Story story) {
        String relationshipId = storiesViewModel.getRelationshipId().getValue();
        if (relationshipId == null) {
            showError(getString(R.string.error_relationship_id_missing));
            return;
        }
        Intent intent = new Intent(requireActivity(), StoryEditorActivity.class);
        intent.putExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID, relationshipId);
        intent.putExtra("storyId", story.getId());
        startActivity(intent);
    }

    private void exportToPdf() {
        Resource<List<Story>> resource = storiesViewModel.getStories().getValue();
        if (resource == null || resource.status != Resource.Status.SUCCESS || resource.data == null
                || resource.data.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_stories_to_export, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            pdfExporter.createPdf(requireContext(), resource.data, new PdfExporter.PdfCallback() {
                @Override
                public void onSuccess(Uri uri) {
                    handler.post(() -> {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), R.string.story_exported_to_downloads, Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    handler.post(() -> {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), R.string.error_exporting_story, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });
    }

    private void showError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void showLoadingState() {
        binding.shimmerViewContainer.setVisibility(View.VISIBLE);
        binding.shimmerViewContainer.startShimmer();
        binding.storiesRecyclerView.setVisibility(View.GONE);
        binding.emptyView.setVisibility(View.GONE);
        binding.errorView.setVisibility(View.GONE);
    }

    private void showSuccessState(List<Story> stories) {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.storiesRecyclerView.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);
        binding.errorView.setVisibility(View.GONE);

        this.originalList = stories;
        filterStories(currentQuery);
    }

    private void showEmptyState() {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.storiesRecyclerView.setVisibility(View.GONE);
        binding.emptyView.setVisibility(View.VISIBLE);
        binding.errorView.setVisibility(View.GONE);
    }

    private void showErrorState(String message) {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.storiesRecyclerView.setVisibility(View.GONE);
        binding.emptyView.setVisibility(View.GONE);
        binding.errorView.setVisibility(View.VISIBLE);
        binding.errorMessageTextView.setText(message);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
