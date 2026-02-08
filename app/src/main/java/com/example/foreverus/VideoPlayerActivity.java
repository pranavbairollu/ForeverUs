package com.example.foreverus;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.example.foreverus.databinding.ActivityVideoPlayerBinding;

public class VideoPlayerActivity extends BaseActivity {

    private ActivityVideoPlayerBinding binding;
    private ExoPlayer player;
    private String videoUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVideoPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        videoUrl = getIntent().getStringExtra("videoUrl");

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "Video URL is missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.closeButton.setOnClickListener(v -> finish());
    }
    
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializePlayer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    private void initializePlayer() {
        if (player == null) {
            androidx.media3.datasource.cache.CacheDataSource.Factory cacheDataSourceFactory = 
                new androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(ForeverUsApplication.simpleCache)
                .setUpstreamDataSourceFactory(new androidx.media3.datasource.DefaultHttpDataSource.Factory());

            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheDataSourceFactory))
                    .build();
            binding.playerView.setPlayer(player);

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        binding.loadingProgressBar.setVisibility(View.VISIBLE);
                    } else {
                        binding.loadingProgressBar.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    binding.loadingProgressBar.setVisibility(View.GONE);
                    Toast.makeText(VideoPlayerActivity.this, "Error playing video: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
