package com.example.foreverus;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.foreverus.databinding.ItemSongBinding;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SongAdapter extends ListAdapter<Song, SongAdapter.SongViewHolder> {

    private static final String YOUTUBE_VIDEO_ID_REGEX = "^[a-zA-Z0-9_-]{11}$";
    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile(
            "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*");

    private final Context context;

    public SongAdapter(Context context) {
        super(new SongDiffCallback());
        this.context = context;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongBinding binding = ItemSongBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SongViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = getItem(position);
        holder.bind(song);
    }

    public static String extractVideoId(String youtubeUrl) {
        if (youtubeUrl == null) {
            return null;
        }
        Matcher matcher = YOUTUBE_VIDEO_ID_PATTERN.matcher(youtubeUrl);
        if (matcher.find()) {
            return matcher.group();
        }
        // Fallback: Check if it IS an ID (11 chars)
        if (youtubeUrl.trim().matches("^[a-zA-Z0-9_-]{11}$")) {
            return youtubeUrl.trim();
        }
        return null;
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private final ItemSongBinding binding;

        public SongViewHolder(ItemSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Song song = getItem(position);
                    // Attempt to get ID, but if null, we will pass the URL to the player as
                    // fallback
                    String videoId = song.getVideoId();
                    if (videoId == null)
                        videoId = extractVideoId(song.getYoutubeUrl());

                    if (videoId != null || (song.getYoutubeUrl() != null && !song.getYoutubeUrl().isEmpty())) {
                        Intent intent = new Intent(context, MusicPlayerActivity.class);

                        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                        java.util.ArrayList<String> titles = new java.util.ArrayList<>();
                        java.util.ArrayList<String> artists = new java.util.ArrayList<>();

                        java.util.List<Song> currentList = SongAdapter.this.getCurrentList();

                        // Pass FULL list for correct Previous/Next/Shuffle behavior
                        // Assuming list size < 2000 to avoid TransactionTooLargeException
                        for (Song s : currentList) {
                            // Ensure ID is extracted if not cached
                            String vId = s.getVideoId();
                            if (vId == null)
                                vId = extractVideoId(s.getYoutubeUrl());

                            // Safe fallback: Use ID if available, otherwise use Full URL
                            if (vId != null) {
                                ids.add(vId);
                            } else if (s.getYoutubeUrl() != null) {
                                ids.add(s.getYoutubeUrl()); // Pass full URL as "ID" for player to handle
                            } else {
                                ids.add("");
                            }

                            titles.add(s.getTitle() != null ? s.getTitle() : "");
                            artists.add(s.getArtist() != null ? s.getArtist() : "");
                        }

                        intent.putStringArrayListExtra("EXTRA_VIDEO_IDS", ids);
                        intent.putStringArrayListExtra("EXTRA_VIDEO_TITLES", titles);
                        intent.putStringArrayListExtra("EXTRA_VIDEO_ARTISTS", artists);
                        intent.putExtra("EXTRA_START_INDEX", position);

                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, R.string.invalid_youtube_url, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        public void bind(Song song) {
            binding.songTitleTextView.setText(song.getTitle());
            binding.songArtistTextView.setText(song.getArtist());

            if (song.getVideoId() == null) {
                song.setVideoId(extractVideoId(song.getYoutubeUrl()));
            }

            if (song.getVideoId() != null) {
                String thumbnailUrl = "https://img.youtube.com/vi/" + song.getVideoId() + "/0.jpg";
                Glide.with(context)
                        .load(thumbnailUrl)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_error_outline)
                        .into(binding.songThumbnailImageView);
            } else {
                binding.songThumbnailImageView.setImageResource(R.drawable.ic_error_outline);
            }
        }
    }

    private static class SongDiffCallback extends DiffUtil.ItemCallback<Song> {
        @Override
        public boolean areItemsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.equals(newItem);
        }
    }
}
