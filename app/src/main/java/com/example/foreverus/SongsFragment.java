package com.example.foreverus;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.foreverus.databinding.FragmentSongsBinding;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SongsFragment extends Fragment {

    private FragmentSongsBinding binding;
    private SongAdapter songAdapter;
    private SongsViewModel songsViewModel;
    private AlertDialog addSongDialog;

    private static final long DEBOUNCE_DELAY_MS = 500;
    private final android.os.Handler debounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable debounceRunnable;

    // Robust Regex for ID extraction including Shorts, standard links, mobile
    // links, and short URLs
    // Captures 11-char ID
    // Robust Regex for ID extraction including Shorts, standard links, mobile
    // links, and short URLs
    // Captures 11-char ID
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> searchLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    String videoId = result.getData().getStringExtra(YouTubeSearchActivity.EXTRA_VIDEO_ID);
                    String title = result.getData().getStringExtra(YouTubeSearchActivity.EXTRA_VIDEO_TITLE);
                    if (addSongDialog != null && addSongDialog.isShowing()) {
                        EditText youtubeUrlEditText = addSongDialog.findViewById(R.id.youtubeUrlEditText);
                        EditText titleEditText = addSongDialog.findViewById(R.id.songTitleEditText);
                        if (youtubeUrlEditText != null && videoId != null) {
                            youtubeUrlEditText.setText("https://youtu.be/" + videoId);
                        }
                        if (titleEditText != null && title != null) {
                            titleEditText.setText(title);
                        }
                    }
                }
            });

    // Robust Regex removed - using SongAdapter's version
    // private static final Pattern VIDEO_ID_PATTERN = ...

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSongsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        songsViewModel = new ViewModelProvider(this).get(SongsViewModel.class);

        setupRecyclerView();
        observeViewModel();

        binding.fabAddSong.setOnClickListener(v -> showAddSongDialog());

        binding.fabShufflePlay.setOnClickListener(v -> {
            java.util.List<Song> currentList = songAdapter.getCurrentList();
            if (currentList == null || currentList.isEmpty()) {
                Toast.makeText(getContext(), "No songs to shuffle", Toast.LENGTH_SHORT).show();
                return;
            }

            android.content.Intent intent = new android.content.Intent(getContext(), MusicPlayerActivity.class);
            java.util.ArrayList<String> ids = new java.util.ArrayList<>();
            java.util.ArrayList<String> titles = new java.util.ArrayList<>();
            java.util.ArrayList<String> artists = new java.util.ArrayList<>();

            for (Song s : currentList) {
                String vId = s.getVideoId();
                if (vId == null)
                    vId = SongAdapter.extractVideoId(s.getYoutubeUrl());

                // Strict Filtering: Only add valid songs to the playlist
                if (vId != null && !vId.isEmpty()) {
                    ids.add(vId);
                    titles.add(s.getTitle() != null ? s.getTitle() : "Unknown Title");
                    artists.add(s.getArtist() != null ? s.getArtist() : "Unknown Artist");
                } else if (s.getYoutubeUrl() != null && !s.getYoutubeUrl().isEmpty()) {
                    // Fallback to URL if ID extraction failed but URL exists
                    ids.add(s.getYoutubeUrl());
                    titles.add(s.getTitle() != null ? s.getTitle() : "Unknown Title");
                    artists.add(s.getArtist() != null ? s.getArtist() : "Unknown Artist");
                }
            }

            if (ids.isEmpty()) {
                Toast.makeText(getContext(), "No valid songs to play", Toast.LENGTH_SHORT).show();
                return;
            }

            intent.putStringArrayListExtra(MusicPlayerActivity.EXTRA_VIDEO_IDS, ids);
            intent.putStringArrayListExtra(MusicPlayerActivity.EXTRA_VIDEO_TITLES, titles);
            intent.putStringArrayListExtra(MusicPlayerActivity.EXTRA_VIDEO_ARTISTS, artists);
            intent.putExtra(MusicPlayerActivity.EXTRA_START_INDEX, 0); // Start at beginning, shuffle will randomization
            intent.putExtra("EXTRA_FORCE_SHUFFLE", true);

            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        binding.songsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        songAdapter = new SongAdapter(getContext());
        binding.songsRecyclerView.setAdapter(songAdapter);

        // Swipe to Delete Logic
        androidx.recyclerview.widget.ItemTouchHelper itemTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0,
                        androidx.recyclerview.widget.ItemTouchHelper.LEFT
                                | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView,
                            @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                            @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                            int direction) {
                        int position = viewHolder.getBindingAdapterPosition();
                        if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION)
                            return;

                        // Safety Check: Bounds
                        if (position < 0 || position >= songAdapter.getCurrentList().size())
                            return;

                        Song songToDelete = songAdapter.getCurrentList().get(position);
                        if (songToDelete == null)
                            return;

                        String relationshipId = songToDelete.getRelationshipId();

                        // Delete
                        songsViewModel.deleteSong(relationshipId, songToDelete);

                        // Undo Snackbar
                        com.google.android.material.snackbar.Snackbar.make(binding.songsRecyclerView,
                                R.string.song_deleted,
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo, v -> {
                                    // RESTORE (The ID is inside songToDelete, ensuring identity preservation)
                                    songsViewModel.addSong(relationshipId, songToDelete);
                                })
                                .setAnchorView(binding.fabAddSong) // Float above FAB
                                .show();
                    }

                    @Override
                    public void onChildDraw(@NonNull android.graphics.Canvas c,
                            @NonNull androidx.recyclerview.widget.RecyclerView recyclerView,
                            @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {

                        // Premium Red Background with Icon
                        if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE) {
                            View itemView = viewHolder.itemView;
                            android.graphics.drawable.ColorDrawable background = new android.graphics.drawable.ColorDrawable(
                                    android.graphics.Color.RED);
                            android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(
                                    getContext(),
                                    R.drawable.ic_baseline_delete_24); // Reuse existing drawable

                            if (icon != null) {
                                icon.setTint(android.graphics.Color.WHITE);
                                int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                                int iconTop = itemView.getTop()
                                        + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                                int iconBottom = iconTop + icon.getIntrinsicHeight();

                                if (dX > 0) { // Swiping Right
                                    background.setBounds(itemView.getLeft(), itemView.getTop(),
                                            itemView.getLeft() + ((int) dX), itemView.getBottom());
                                    icon.setBounds(itemView.getLeft() + iconMargin, iconTop,
                                            itemView.getLeft() + iconMargin + icon.getIntrinsicWidth(), iconBottom);
                                } else if (dX < 0) { // Swiping Left
                                    background.setBounds(itemView.getRight() + ((int) dX), itemView.getTop(),
                                            itemView.getRight(), itemView.getBottom());
                                    icon.setBounds(itemView.getRight() - iconMargin - icon.getIntrinsicWidth(), iconTop,
                                            itemView.getRight() - iconMargin, iconBottom);
                                } else {
                                    background.setBounds(0, 0, 0, 0);
                                }

                                background.draw(c);
                                icon.draw(c);
                            }
                        }
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    }
                });

        itemTouchHelper.attachToRecyclerView(binding.songsRecyclerView);
    }

    private void observeViewModel() {
        RelationshipRepository.getInstance().getRelationshipId().observe(getViewLifecycleOwner(), id -> {
            songsViewModel.relationshipIdLiveData.setValue(id);
        });

        songsViewModel.getSongs().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null)
                return;

            switch (resource.status) {
                case LOADING:
                    showLoadingState(resource.data);
                    break;
                case SUCCESS:
                    showSuccessState(resource.data);
                    break;
                case ERROR:
                    showErrorState(resource.message, resource.data);
                    break;
            }
        });

        songsViewModel.getAddSongStatus().observe(getViewLifecycleOwner(), this::handleStatus);
        songsViewModel.getDeleteSongStatus().observe(getViewLifecycleOwner(), this::handleStatus);
        songsViewModel.getYoutubeVideoInfo().observe(getViewLifecycleOwner(), this::handleYoutubeVideoInfo);
    }

    private void handleStatus(Resource<Void> status) {
        if (status == null)
            return;
        switch (status.status) {
            case LOADING:
                binding.fabAddSong.setEnabled(false);
                break;
            case SUCCESS:
                binding.fabAddSong.setEnabled(true);
                if (addSongDialog != null && addSongDialog.isShowing()) {
                    addSongDialog.dismiss();
                }
                break;
            case ERROR:
                binding.fabAddSong.setEnabled(true);
                Toast.makeText(getContext(), status.message, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void handleYoutubeVideoInfo(Resource<YoutubeVideoInfo> resource) {
        if (resource == null || addSongDialog == null || !addSongDialog.isShowing())
            return;

        final EditText titleEditText = addSongDialog.findViewById(R.id.songTitleEditText);
        final EditText artistEditText = addSongDialog.findViewById(R.id.artistNameEditText);
        final View loadingIndicator = addSongDialog.findViewById(R.id.youtubeLoadingIndicator);

        switch (resource.status) {
            case LOADING:
                loadingIndicator.setVisibility(View.VISIBLE);
                break;
            case SUCCESS:
                loadingIndicator.setVisibility(View.GONE);
                if (resource.data != null) {
                    titleEditText.setText(resource.data.getTitle());
                    artistEditText.setText(resource.data.getChannelTitle());
                }
                break;
            case ERROR:
                loadingIndicator.setVisibility(View.GONE);
                Toast.makeText(getContext(), resource.message, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void showAddSongDialog() {
        addSongDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setView(R.layout.dialog_add_song)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel())
                .show();

        addSongDialog.findViewById(R.id.searchButton).setOnClickListener(v -> {
            searchLauncher.launch(new android.content.Intent(requireContext(), YouTubeSearchActivity.class));
        });

        EditText youtubeUrlEditText = addSongDialog.findViewById(R.id.youtubeUrlEditText);
        youtubeUrlEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (debounceRunnable != null) {
                    debounceHandler.removeCallbacks(debounceRunnable);
                }
                debounceRunnable = () -> {
                    String videoId = SongAdapter.extractVideoId(s.toString());
                    if (videoId != null) {
                        songsViewModel.fetchYoutubeVideoInfo(videoId);
                    }
                };
                debounceHandler.postDelayed(debounceRunnable, DEBOUNCE_DELAY_MS);
            }
        });

        addSongDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final EditText titleEditText = addSongDialog.findViewById(R.id.songTitleEditText);
            final EditText artistEditText = addSongDialog.findViewById(R.id.artistNameEditText);

            String title = titleEditText.getText().toString().trim();
            String artist = artistEditText.getText().toString().trim();
            String youtubeUrl = youtubeUrlEditText.getText().toString().trim();
            String relationshipId = RelationshipRepository.getInstance().getRelationshipId().getValue();

            // Check for valid user
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(getContext(), "You must be logged in.", Toast.LENGTH_SHORT).show();
                return;
            }
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(artist) || TextUtils.isEmpty(youtubeUrl)
                    || relationshipId == null) {
                Toast.makeText(getContext(), R.string.please_fill_out_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            // More permissive validation: If we can extract an ID, it's valid enough.
            // Or if the user just pasted an ID roughly.
            String extraction = SongAdapter.extractVideoId(youtubeUrl);
            if (extraction == null) {
                Toast.makeText(getContext(), R.string.please_enter_a_valid_youtube_url, Toast.LENGTH_SHORT).show();
                return;
            }

            Song song = new Song();
            song.setId(java.util.UUID.randomUUID().toString()); // GEN ID TO PREVENT CRASH
            song.setTitle(title);
            song.setArtist(artist);
            // extraction is guaranteed to be non-null here due to checks above
            song.setYoutubeUrl(extraction);
            song.setAddedBy(currentUserId);
            song.setRelationshipId(relationshipId);

            songsViewModel.addSong(relationshipId, song);
        });
    }

    // Helper removed, using SongAdapter.extractVideoId
    // private String extractVideoId(String youtubeUrl) { ... }

    private void showLoadingState(List<Song> data) {
        if (data == null || data.isEmpty()) {
            binding.shimmerViewContainer.setVisibility(View.VISIBLE);
            binding.shimmerViewContainer.startShimmer();
            binding.songsRecyclerView.setVisibility(View.GONE);
            binding.emptyView.getRoot().setVisibility(View.GONE);
            binding.errorView.getRoot().setVisibility(View.GONE);
        } else {
            showSuccessState(data);
        }
    }

    private void showSuccessState(List<Song> songs) {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);
        if (songs != null && !songs.isEmpty()) {
            binding.songsRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyView.getRoot().setVisibility(View.GONE);
            songAdapter.submitList(songs);
        } else {
            binding.songsRecyclerView.setVisibility(View.GONE);
            binding.emptyView.getRoot().setVisibility(View.VISIBLE);
            binding.emptyView.txtEmptyTitle.setText(R.string.songs_empty_soft);
            binding.emptyView.txtEmptyMessage.setVisibility(View.GONE);
        }
    }

    private void showErrorState(String message, List<Song> data) {
        if (data != null && !data.isEmpty()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            showSuccessState(data);
        } else {
            binding.shimmerViewContainer.stopShimmer();
            binding.shimmerViewContainer.setVisibility(View.GONE);
            binding.songsRecyclerView.setVisibility(View.GONE);
            binding.emptyView.getRoot().setVisibility(View.GONE);
            binding.errorView.getRoot().setVisibility(View.VISIBLE);

            binding.errorView.txtErrorTitle.setText(R.string.error_title_default);
            binding.errorView.txtErrorMessage
                    .setText(message != null ? message : getString(R.string.error_message_default));
            binding.errorView.btnErrorRetry.setOnClickListener(v -> RelationshipRepository.getInstance()
                    .getRelationshipId().observe(getViewLifecycleOwner(), id -> {
                        songsViewModel.relationshipIdLiveData.setValue(id);
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
        if (debounceHandler != null) {
            debounceHandler.removeCallbacksAndMessages(null);
        }
        if (addSongDialog != null) {
            addSongDialog.dismiss();
        }
        binding = null;
    }
}
