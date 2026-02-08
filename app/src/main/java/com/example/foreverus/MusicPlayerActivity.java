package com.example.foreverus;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.foreverus.databinding.ActivityMusicPlayerBinding;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;

public class MusicPlayerActivity extends BaseActivity {

    // Helper for visual feedback
    private void showStatus(String message) {
        if (binding != null && binding.getRoot() != null) {
            com.google.android.material.snackbar.Snackbar
                    .make(binding.getRoot(), message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDynamicBackground(String videoId) {
        if (videoId == null || videoId.isEmpty())
            return;
        String thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/0.jpg";

        com.bumptech.glide.Glide.with(this)
                .asBitmap()
                .load(thumbnailUrl)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.Bitmap resource,
                            @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        androidx.palette.graphics.Palette.from(resource).generate(palette -> {
                            if (palette == null)
                                return;
                            // Get premium colors
                            int defaultColor = android.graphics.Color.parseColor("#121212");
                            int colorTop = palette.getDominantColor(defaultColor);
                            int colorBottom = android.graphics.Color.BLACK;

                            // Create Gradient
                            android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
                                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                                    new int[] { colorTop, colorBottom });

                            if (binding != null && binding.getRoot() != null) {
                                binding.getRoot().setBackground(gradient);
                            }
                        });
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                    }
                });
    }

    public static final String EXTRA_VIDEO_IDS = "EXTRA_VIDEO_IDS";
    public static final String EXTRA_VIDEO_TITLES = "EXTRA_VIDEO_TITLES";
    public static final String EXTRA_VIDEO_ARTISTS = "EXTRA_VIDEO_ARTISTS";
    public static final String EXTRA_START_INDEX = "EXTRA_START_INDEX";

    // Legacy single-video constants (kept for fallback)
    public static final String EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID";
    public static final String EXTRA_VIDEO_TITLE = "EXTRA_VIDEO_TITLE";
    public static final String EXTRA_VIDEO_ARTIST = "EXTRA_VIDEO_ARTIST";

    private ActivityMusicPlayerBinding binding;
    private java.util.ArrayList<String> videoIds;
    private java.util.ArrayList<String> titles;
    private java.util.ArrayList<String> artists;
    private int currentIndex = 0;
    private YouTubePlayer currentPlayer;

    // Enhancements
    private MusicVisualizerView visualizerView;
    private boolean isShuffleOn = false;
    private int loopMode = 0; // 0=OFF, 1=ONE, 2=ALL (User: OFF->ALL->ONE->OFF)
    private android.content.SharedPreferences prefs;
    private static final String PREFS_NAME = "MusicPrefs";

    // Playback Logic
    private java.util.ArrayList<Integer> shuffleIndices = new java.util.ArrayList<>();
    private int shufflePos = 0;
    private boolean isPlaying = false; // Track playback state for PiP
    private java.util.Stack<Integer> playHistory = new java.util.Stack<>();
    private java.util.Random random = new java.util.Random();

    // Audio Focus
    private android.media.AudioManager audioManager;
    private android.media.AudioFocusRequest audioFocusRequest;
    private android.media.AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    private static final String ACTION_MEDIA_CONTROL = "media_control";
    private static final String EXTRA_CONTROL_TYPE = "control_type";
    private static final int CONTROL_TYPE_PLAY = 1;
    private static final int CONTROL_TYPE_PAUSE = 2;

    private final android.content.BroadcastReceiver pipReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if (ACTION_MEDIA_CONTROL.equals(intent.getAction())) {
                int controlType = intent.getIntExtra(EXTRA_CONTROL_TYPE, 0);
                if (controlType == CONTROL_TYPE_PLAY) {
                    playVideo();
                } else if (controlType == CONTROL_TYPE_PAUSE) {
                    pauseVideo();
                }
            }
        }
    };

    @Override
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onCreate(Bundle savedInstanceState) {
        try {
            setTheme(ThemeManager.getThemeResId(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onCreate(savedInstanceState);
        binding = ActivityMusicPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets
                    .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, new android.content.IntentFilter(ACTION_MEDIA_CONTROL),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pipReceiver, new android.content.IntentFilter(ACTION_MEDIA_CONTROL));
        }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isShuffleOn = prefs.getBoolean("isShuffleOn", false);
        loopMode = prefs.getInt("loopMode", 0);

        if (savedInstanceState != null) {
            // Restore State for Robustness
            videoIds = savedInstanceState.getStringArrayList("videoIds");
            titles = savedInstanceState.getStringArrayList("titles");
            artists = savedInstanceState.getStringArrayList("artists");
            currentIndex = savedInstanceState.getInt("currentIndex", 0);
            shuffleIndices = savedInstanceState.getIntegerArrayList("shuffleIndices");
            shufflePos = savedInstanceState.getInt("shufflePos", 0);

            // Restore Stack safely
            try {
                java.util.ArrayList<Integer> historyList = savedInstanceState.getIntegerArrayList("playHistory");
                if (historyList != null) {
                    playHistory = new java.util.Stack<>();
                    playHistory.addAll(historyList);
                }
            } catch (Exception e) {
                playHistory = new java.util.Stack<>();
            }

            currentVideoTime = savedInstanceState.getFloat("currentVideoTime", 0f);
        } else {
            handleIntent(getIntent());
        }

        if (videoIds == null || videoIds.isEmpty()) {
            showStatus("Error: No Songs to Play");
            finish();
            return;
        }

        setupUI();
        setupPlayer();
    }

    @Override
    public void onBackPressed() {
        // Feature: Back button triggers PiP if playing, allowing background listening
        if (isPlaying && !isInPictureInPictureMode()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                boolean pipSupported = getPackageManager()
                        .hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
                if (pipSupported) {
                    try {
                        android.app.PictureInPictureParams params = updatePipParams(true);
                        if (params != null) {
                            enterPictureInPictureMode(params);
                        } else {
                            // Fallback
                            android.app.PictureInPictureParams.Builder builder = new android.app.PictureInPictureParams.Builder()
                                    .setAspectRatio(new android.util.Rational(16, 9));
                            enterPictureInPictureMode(builder.build());
                        }
                        return; // Prevent closing
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList("videoIds", videoIds);
        outState.putStringArrayList("titles", titles);
        outState.putStringArrayList("artists", artists);
        outState.putInt("currentIndex", currentIndex);
        outState.putIntegerArrayList("shuffleIndices", shuffleIndices);
        outState.putInt("shufflePos", shufflePos);

        // Save Originals
        outState.putStringArrayList("originalVideoIds", originalVideoIds);
        outState.putStringArrayList("originalTitles", originalTitles);
        outState.putStringArrayList("originalArtists", originalArtists);

        // Stack to ArrayList for Parcelable
        java.util.ArrayList<Integer> historyList = new java.util.ArrayList<>(playHistory);
        outState.putIntegerArrayList("playHistory", historyList);

        outState.putFloat("currentVideoTime", currentVideoTime);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        // If we are already running, we need to manually trigger load because
        // setupPlayer() only runs once
        if (currentPlayer != null) {
            loadCurrentSong();
        }
    }

    private void handleIntent(android.content.Intent intent) {
        if (intent.hasExtra(EXTRA_VIDEO_IDS)) {
            videoIds = intent.getStringArrayListExtra(EXTRA_VIDEO_IDS);
            titles = intent.getStringArrayListExtra(EXTRA_VIDEO_TITLES);
            artists = intent.getStringArrayListExtra(EXTRA_VIDEO_ARTISTS);
            currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0);

            // Capture Originals IMMEDIATELY
            if (videoIds != null) {
                originalVideoIds = new java.util.ArrayList<>(videoIds);
                originalTitles = new java.util.ArrayList<>(titles);
                originalArtists = new java.util.ArrayList<>(artists);
            }

            if (intent.getBooleanExtra("EXTRA_FORCE_SHUFFLE", false)) {
                isShuffleOn = true;
                prefs.edit().putBoolean("isShuffleOn", true).apply();

                // Perform Shuffle NOW
                if (videoIds != null && videoIds.size() > 1) {
                    // Create indices to shuffle
                    java.util.List<Integer> indices = new java.util.ArrayList<>();
                    for (int i = 0; i < videoIds.size(); i++) {
                        indices.add(i);
                    }
                    java.util.Collections.shuffle(indices);

                    // Rebuild lists based on shuffled indices
                    java.util.ArrayList<String> shuffledVideos = new java.util.ArrayList<>();
                    java.util.ArrayList<String> shuffledTitles = new java.util.ArrayList<>();
                    java.util.ArrayList<String> shuffledArtists = new java.util.ArrayList<>();

                    for (Integer index : indices) {
                        shuffledVideos.add(videoIds.get(index));
                        shuffledTitles.add(titles.get(index));
                        shuffledArtists.add(artists.get(index));
                    }

                    videoIds = shuffledVideos;
                    titles = shuffledTitles;
                    artists = shuffledArtists;
                    currentIndex = 0; // Start at the new first song (random)
                }
            }
        } else {
            // Fallback for single video legacy calls
            String videoId = intent.getStringExtra(EXTRA_VIDEO_ID);
            String title = intent.getStringExtra(EXTRA_VIDEO_TITLE);
            String artist = intent.getStringExtra(EXTRA_VIDEO_ARTIST);
            if (videoId != null) {
                videoIds = new java.util.ArrayList<>();
                videoIds.add(videoId);
                titles = new java.util.ArrayList<>();
                titles.add(title);
                artists = new java.util.ArrayList<>();
                artists.add(artist);
            }
        }
    }

    private int consecutiveErrors = 0;
    private android.webkit.WebView webView;
    private boolean isMuted = false;

    // Original Lists for Un-Shuffle
    private java.util.ArrayList<String> originalVideoIds;
    private java.util.ArrayList<String> originalTitles;
    private java.util.ArrayList<String> originalArtists;

    private void setupUI() {
        if (currentIndex >= 0 && currentIndex < titles.size()) {
            binding.playerSongTitle.setText(titles.get(currentIndex));
            binding.playerSongArtist.setText(artists.get(currentIndex));
        }
        binding.btnClose.setOnClickListener(v -> finish());
        // PiP Button
        binding.btnPip.setOnClickListener(v -> {
            boolean pipSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                    && getPackageManager()
                            .hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);

            if (pipSupported) {
                try {
                    android.app.PictureInPictureParams params = new android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(new android.util.Rational(16, 9))
                            .build();
                    enterPictureInPictureMode(params);
                } catch (Exception e) {
                    showStatus("Error entering PiP");
                }
            } else {
                showStatus("PiP not supported on this device");
            }
        });

        binding.btnNext.setOnClickListener(v -> playNextSong());
        binding.btnPrevious.setOnClickListener(v -> playPreviousSong());

        binding.btnPlayPause.setOnClickListener(v -> togglePlayPause());

        binding.btnShuffle.setOnClickListener(v -> toggleShuffle());
        binding.btnLoop.setOnClickListener(v -> toggleLoop());
        updateControlsUI();

        visualizerView = binding.visualizerView;
        setupWebView();
    }

    private void togglePlayPause() {
        if (isPlaying) {
            pauseVideo();
        } else {
            playVideo();
        }
    }

    private void pauseVideo() {
        isPlaying = false;
        if (currentPlayer != null) {
            currentPlayer.pause();
        }
        if (webView != null) {
            webView.evaluateJavascript("var v = document.querySelector('video'); if(v) v.pause();", null);
        }
        updateControlsUI();
        updateVisualizerState(false);
        updatePipParams(false);
    }

    private void playVideo() {
        requestAudioFocus(); // Request Focus
        isPlaying = true;
        if (currentPlayer != null) {
            currentPlayer.play();
        }
        if (webView != null) {
            webView.evaluateJavascript("var v = document.querySelector('video'); if(v) v.play();", null);
        }
        updateControlsUI();
        updateVisualizerState(true);
        updatePipParams(true);
    }

    private void updateControlsUI() {
        // Play/Pause Icon Update
        if (isPlaying) {
            binding.btnPlayPause.setImageResource(R.drawable.ic_baseline_pause_24);
        } else {
            binding.btnPlayPause.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        }
        // Shuffle UI
        float alphaShuffle = isShuffleOn ? 1.0f : 0.5f;
        int colorShuffle = android.graphics.Color.WHITE; // Always white, alpha indicates state
        binding.btnShuffle.setAlpha(alphaShuffle);
        binding.btnShuffle.setColorFilter(colorShuffle);

        // Loop UI
        float alphaLoop = (loopMode != 0) ? 1.0f : 0.5f;
        int colorLoop = android.graphics.Color.WHITE; // Always white
        binding.btnLoop.setAlpha(alphaLoop);
        binding.btnLoop.setColorFilter(colorLoop);

        // Update Icons based on state if possible, currently we rely on Tint/Alpha +
        // Toasts
        // Could swap Loop icon for 'repeat_one' if mode == 1
    }

    private float currentVideoTime = 0f;

    // Old toggleShuffle removed

    private void toggleMute() {
        isMuted = !isMuted;

        // Native Player Control
        if (currentPlayer != null) {
            if (isMuted) {
                currentPlayer.mute();
            } else {
                currentPlayer.unMute();
            }
        }

        // WebView Player Control
        if (webView != null && webView.getVisibility() == View.VISIBLE) {
            webView.evaluateJavascript("window.isAppMuted = " + isMuted + ";", null);
        }

        if (isMuted) {
            showStatus("Muted");
        } else {
            showStatus("Unmuted");
        }
        updateControlsUI();
    }

    private void toggleShuffle() {
        isShuffleOn = !isShuffleOn;
        prefs.edit().putBoolean("isShuffleOn", isShuffleOn).apply();

        if (isShuffleOn) {
            showStatus("Shuffle On");
            // Perform Physical Shuffle
            if (videoIds != null && videoIds.size() > 1) {
                // Keep current song
                String currentId = videoIds.get(currentIndex);
                String currentTitle = titles.get(currentIndex);
                String currentArtist = artists.get(currentIndex);

                // Create list of OTHER indices
                java.util.List<Integer> indices = new java.util.ArrayList<>();
                for (int i = 0; i < videoIds.size(); i++) {
                    if (i != currentIndex)
                        indices.add(i);
                }
                java.util.Collections.shuffle(indices);

                // Rebuild lists: Current Song First + Shuffled Others
                java.util.ArrayList<String> newVideos = new java.util.ArrayList<>();
                java.util.ArrayList<String> newTitles = new java.util.ArrayList<>();
                java.util.ArrayList<String> newArtists = new java.util.ArrayList<>();

                // Add Current
                newVideos.add(currentId);
                newTitles.add(currentTitle);
                newArtists.add(currentArtist);

                // Add Others
                for (Integer index : indices) {
                    newVideos.add(videoIds.get(index));
                    newTitles.add(titles.get(index));
                    newArtists.add(artists.get(index));
                }

                // Apply
                videoIds = newVideos;
                titles = newTitles;
                artists = newArtists;
                currentIndex = 0; // Current song is now at top
            }
        } else {
            showStatus("Shuffle Off");
            // Restore Original Order
            if (originalVideoIds != null && !originalVideoIds.isEmpty()) {
                String currentVideoId = videoIds.get(currentIndex);

                // Restore Lists
                videoIds = new java.util.ArrayList<>(originalVideoIds);
                titles = new java.util.ArrayList<>(originalTitles);
                artists = new java.util.ArrayList<>(originalArtists);

                // Find Current Song in Original List
                int newIndex = -1;
                for (int i = 0; i < videoIds.size(); i++) {
                    if (videoIds.get(i).equals(currentVideoId)) {
                        newIndex = i;
                        break;
                    }
                }

                // Update Index
                if (newIndex != -1) {
                    currentIndex = newIndex;
                } else {
                    currentIndex = 0; // Fallback
                }
            }
        }
        updateControlsUI();
    }

    private void toggleLoop() {
        // Cycle: 0 (OFF) -> 2 (ALL) -> 1 (ONE) -> 0 (OFF)
        // STRICT RULE: If Shuffle is ON, skip ONE. Cycle becomes: 0 -> 2 -> 0.

        if (loopMode == 0) {
            loopMode = 2; // OFF -> ALL
        } else if (loopMode == 2) {
            if (isShuffleOn) {
                loopMode = 0; // ALL -> OFF (Skip ONE)
            } else {
                loopMode = 1; // ALL -> ONE
            }
        } else if (loopMode == 1) {
            loopMode = 0; // ONE -> OFF
        }

        prefs.edit().putInt("loopMode", loopMode).apply();
        updateControlsUI();

        String msg = "Loop Off";
        if (loopMode == 1)
            msg = "Loop One";
        if (loopMode == 2)
            msg = "Loop All";
        showStatus(msg);
    }

    private long lastActionTime = 0;
    private static final long DEBOUNCE_DELAY = 500;

    private void playNextSong() {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < DEBOUNCE_DELAY)
            return;
        lastActionTime = now;

        if (!isNetworkAvailable()) {
            binding.statusMessageText.setVisibility(View.VISIBLE);
            binding.statusMessageText.setText("No internet connection");
            return;
        } else {
            binding.statusMessageText.setVisibility(View.GONE);
        }

        if (videoIds == null || videoIds.isEmpty())
            return;

        // Auto-Replay Check for Loop ONE
        if (loopMode == 1) {
            loadCurrentSong();
            return;
        }

        // Push to History
        playHistory.push(currentIndex);

        // Linear Logic (Works for Shuffle too because list is physically shuffled)
        if (currentIndex < videoIds.size() - 1) {
            currentIndex++;
        } else {
            if (loopMode == 2) { // Loop All
                currentIndex = 0;
            } else {
                playHistory.pop(); // Revert
                showStatus("Playlist finished");
                return;
            }
        }

        currentVideoTime = 0f;
        loadCurrentSong();
    }

    private void playPreviousSong() {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < DEBOUNCE_DELAY)
            return;
        lastActionTime = now;

        // Logic: > 3s -> Restart. Else -> Prev.
        if (currentVideoTime > 3.0f) {
            if (currentPlayer != null) {
                currentPlayer.seekTo(0f);
                currentVideoTime = 0f;
                return;
            }
        }

        if (!isNetworkAvailable()) {
            binding.statusMessageText.setVisibility(View.VISIBLE);
            binding.statusMessageText.setText("No internet connection");
            return;
        } else {
            binding.statusMessageText.setVisibility(View.GONE);
        }

        // Proceed to Previous Track
        if (!playHistory.isEmpty()) {
            currentIndex = playHistory.pop();
            // No need to sync shuffleIndices, purely index based now
        } else {
            if (currentIndex > 0) {
                currentIndex--;
            } else {
                if (loopMode == 2) {
                    currentIndex = videoIds.size() - 1;
                } else {
                    showStatus("Start of playlist");
                    return;
                }
            }
        }

        currentVideoTime = 0f;
        loadCurrentSong();
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = binding.fallbackWebView;
        android.webkit.WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36");

        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // Interaction Lock: Disable all touch events to prevent scrolling/clicking on
        // YouTube UI
        webView.setOnTouchListener((v, event) -> true);

        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                // Use evaluateJavascript for cleaner execution, falling back if needed
                view.evaluateJavascript(getAdSkipperScript(), null);
            }
        });
        webView.setWebChromeClient(new android.webkit.WebChromeClient());
    }

    public class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void onVideoPlaying() {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed())
                    return;
                // stopWatchdog(); // REMOVED: Watchdog caused issues on slow networks
                consecutiveErrors = 0; // Reset error count on success
                isPlaying = true;
                updateControlsUI();
                updateVisualizerState(true);
                updatePipParams(true);
            });
        }

        @android.webkit.JavascriptInterface
        public void onVideoPaused() {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed())
                    return;
                isPlaying = false;
                updateControlsUI();
                updateVisualizerState(false);
                updatePipParams(false);
            });
        }

        @android.webkit.JavascriptInterface
        public void onVideoEnded() {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed())
                    return;
                playNextSong();
            });
        }

        @android.webkit.JavascriptInterface
        public void onVideoUnavailable() {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed())
                    return;
                if (consecutiveErrors < 3) {
                    consecutiveErrors++;
                    showStatus("Skipping unavailable song...");
                    playNextSong();
                } else {
                    showStatus("Playback stopped: Too many errors.");
                    consecutiveErrors = 0;
                    // stopWatchdog(); // REMOVED
                }
            });
        }

    }

    private String getAdSkipperScript() {
        return "javascript:(function() {" +
                "    /* GUARD: Prevent Multiple Injections */" +
                "    if (window.ForeverUsSkipperRunning) return;" +
                "    window.ForeverUsSkipperRunning = true;" +
                "    " +
                "    /* STATE: Track if we have successfully started playing once */" +
                "    /* var initialPlayDone = false;  <-- REMOVED, using attributes now */" +
                "    " +
                "    /* CSS: Hide known elements immediately */" +
                "    var css = ` " +
                "       /* HIDE HEADER & TOP BAR */" +
                "       header, #header-bar, .header-bar, .mobile-topbar-header, " +
                "       ytm-header-bar-renderer, ytm-mobile-topbar-renderer, " +
                "       .ytm-pivot-bar-renderer, .topbar-icons, #topbar-menu-button, " +
                "       /* HIDE APP PROMOS & ADS */" +
                "       ytm-pwa-install-banner, .ytp-app-banner, .open-app-button, " +
                "       ytm-promoted-sparkles-web-renderer, ytm-companion-slot, " +
                "       .ad-container, .player-ads, #player-control-overlay " +
                "       { display: none !important; opacity: 0 !important; height: 0 !important; visibility: hidden !important; pointer-events: none !important; }"
                +
                "       " +
                "       /* FORCE VIDEO FULLSCREEN */" +
                "       video { " +
                "           position: fixed !important; " +
                "           top: 0 !important; left: 0 !important; " +
                "           width: 100vw !important; height: 100vh !important; " +
                "           z-index: 2147483647 !important; " +
                "           object-fit: contain !important; " +
                "           background: #000 !important;" +
                "       }" +
                "       body, html { background: #000 !important; overflow: hidden !important; }" +
                "    `;" +
                "    var style = document.createElement('style');" +
                "    style.type = 'text/css';" +
                "    style.appendChild(document.createTextNode(css));" +
                "    document.head.appendChild(style);" +
                "    " +
                "    /* MUTATION OBSERVER: The Permanent Fix */" +
                "    var observer = new MutationObserver(function(mutations) {" +
                "        /* 1. Check for 'Open App' Buttons and DESTROY them */" +
                "        var buttons = document.querySelectorAll('button, a, div[role=\"button\"]');" +
                "        buttons.forEach(function(btn) {" +
                "            if (btn.innerText && (btn.innerText === 'Open App' || btn.innerText === 'Get the app')) {"
                +
                "                btn.remove(); /* Completely delete from DOM */" +
                "            }" +
                "        });" +
                "        " +
                "        /* 2. Enforce Video Rules & SYNC */" +
                "        var video = document.querySelector('video');" +
                "        if (video) {" +
                "            /* Force Unmute */" +
                "            if (video.muted) video.muted = false;" +
                "            " +
                "            /* Update State if Already Playing */" +
                "            /* Initial Play: Use Attribute to track state on the ELEMENT itself */" +
                "            var hasStarted = video.getAttribute('data-has-started') === 'true';" +
                "            " +
                "            if (!video.paused) {" +
                "                 if (!hasStarted) video.setAttribute('data-has-started', 'true');" +
                "            } else if (!hasStarted) {" +
                "                 video.play().catch(e => {});" +
                "            }" +
                "            " +
                "            /* Initial Play: Keep trying to play until it successfully starts (initialPlayDone becomes true) */"
                +
                "            /* This ensures it Auto-Plays on load, but stops interfering once the user (or auto) has played it once. */"
                +
                "            " +
                "            " +
                "            /* SYNC: Attach Listeners to sync state with Android */" +
                "            if (!video.hasAttribute('data-listeners-attached')) {" +
                "                video.setAttribute('data-listeners-attached', 'true');" +
                "                video.addEventListener('play', function() { " +
                "                    video.setAttribute('data-has-started', 'true');" +
                "                    if(window.Android) window.Android.onVideoPlaying(); " +
                "                });" +
                "                video.addEventListener('pause', function() { if(window.Android) window.Android.onVideoPaused(); });"
                +
                "                video.addEventListener('ended', function() { if(window.Android) window.Android.onVideoEnded(); });"
                +
                "            }" +
                "        }" +
                "        " +
                "        /* 3. Skip Ads */" +
                "        var skip = document.querySelector('.ytp-ad-skip-button') || document.querySelector('.ytp-ad-skip-button-modern');"
                +
                "        if (skip) skip.click();" +
                "    });" +
                "    " +
                "    /* Start Observing the entire body for changes */" +
                "    observer.observe(document.body, { childList: true, subtree: true });" +
                "})()";
    }

    private void injectAdSkipper(View view) {
        if (view instanceof android.webkit.WebView) {
            ((android.webkit.WebView) view).loadUrl(getAdSkipperScript());
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                injectAdSkipper(group.getChildAt(i));
            }
        }
    }

    private void setupPlayer() {
        // Lifecycle observer to support PiP/Background properly
        // Note: We might need to handle onLocalLifecycle events manually if PiP
        // interferes,
        // but generally adding observer is recommended standard practice.
        getLifecycle().addObserver(binding.youtubePlayerView);

        // Configure Player to hide default UI (controls=0) and enable JS API
        com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions options = new com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions.Builder()
                .controls(0) // Hide default controls and title
                .rel(0) // Don't show related videos at end
                .ivLoadPolicy(3) // Hide annotations
                .ccLoadPolicy(1) // Show captions if available (optional)
                .build();

        binding.youtubePlayerView.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer youTubePlayer) {
                currentPlayer = youTubePlayer;
                // Force Unmute on Start (Player internal state only)
                currentPlayer.unMute();

                // Removed setVolume(100) to respect system volume
                loadCurrentSong();

                // Hack: Try to inject AdSkipper into Native Player (WebView wrapper)
                binding.youtubePlayerView.postDelayed(() -> {
                    injectAdSkipper(binding.youtubePlayerView);
                }, 2000); // Wait for IFrame load
            }

            @Override
            public void onCurrentSecond(@NonNull YouTubePlayer youTubePlayer, float second) {
                currentVideoTime = second;
            }

            @Override
            public void onStateChange(@NonNull YouTubePlayer youTubePlayer,
                    @NonNull com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState state) {
                if (state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.ENDED) {
                    consecutiveErrors = 0;
                    updateVisualizerState(false);
                    updatePipParams(false);
                    updateControlsUI();
                    playNextSong();
                } else if (state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.PLAYING) {
                    consecutiveErrors = 0;
                    isPlaying = true;
                    // Force Unmute during playback transitions
                    youTubePlayer.unMute();
                    isMuted = false; // Strict: Reset internal state
                    showNativePlayer();
                    updateVisualizerState(true);
                    updatePipParams(true);
                    updateControlsUI(); // Sync Button
                } else if (state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.PAUSED) {
                    isPlaying = false;
                    updateVisualizerState(false);
                    updatePipParams(false);
                    updateControlsUI(); // Sync Button
                } else {
                    isPlaying = false;
                    updateVisualizerState(false); // Strict: Stop visualizer on Buffer/Unknown
                    updateControlsUI();
                }
            }

            @Override
            public void onError(@NonNull YouTubePlayer youTubePlayer,
                    @NonNull com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError error) {
                super.onError(youTubePlayer, error);
                updateVisualizerState(false);

                if (error == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER) {
                    playEffectivelyWithWebView();
                    return;
                }

                if (consecutiveErrors < 3) {
                    consecutiveErrors++;
                    showStatus("Skipping unavailable song...");
                    playNextSong();
                } else {
                    showStatus("Playback stopped: Too many errors.");
                    consecutiveErrors = 0;
                }
            }
        }, true, options);
    }

    private void playEffectivelyWithWebView() {
        if (currentIndex >= 0 && currentIndex < videoIds.size()) {
            String vid = videoIds.get(currentIndex);
            // Hide Native, Show Web
            binding.youtubePlayerView.setVisibility(View.GONE);
            binding.fallbackWebView.setVisibility(View.VISIBLE);

            // Stop Native to prevent double audio (though it errored, so likely stopped,
            // but safety first)
            if (currentPlayer != null)
                currentPlayer.pause();

            String playUrl;
            if (vid.startsWith("http://") || vid.startsWith("https://")) {
                playUrl = vid;
            } else {
                playUrl = "https://m.youtube.com/watch?v=" + vid;
            }
            webView.loadUrl(playUrl);

            // Initialize Mute State in JS environment after load (via injection
            // onPageFinished, but also here safely)

            // Fix Sync Issue: Explicitly mark as playing since we just loaded a URL that
            // auto-plays
            isPlaying = true; // Optimistically set true
            // startWatchdog(); // REMOVED: Watchdog caused issues on slow networks

            updateControlsUI();
            updatePipParams(true);
            updateVisualizerState(true);
        }
    }

    // --- WATCHDOG REMOVED ---
    // User feedback: Watchdog caused skipping on slow networks.
    // relying on WebView internal error reporting + native player error handling.
    /*
     * private final android.os.Handler watchdogHandler = new
     * android.os.Handler(android.os.Looper.getMainLooper());
     * private final Runnable watchdogRunnable = () -> {
     * showStatus("Video stalled, skipping...");
     * playNextSong();
     * };
     * 
     * private void startWatchdog() {
     * stopWatchdog();
     * watchdogHandler.postDelayed(watchdogRunnable, 10000);
     * }
     * 
     * private void stopWatchdog() {
     * watchdogHandler.removeCallbacks(watchdogRunnable);
     * }
     */
    // -------------------------

    private void showNativePlayer() {
        // stopWatchdog(); // REMOVED
        binding.youtubePlayerView.setVisibility(View.VISIBLE);
        binding.fallbackWebView.setVisibility(View.GONE);
        webView.loadUrl("about:blank"); // Stop web audio
    }

    private void loadCurrentSong() {
        // stopWatchdog(); // REMOVED

        if (isFinishing() || isDestroyed())
            return; // Crash Protection

        if (!isNetworkAvailable()) {
            binding.statusMessageText.setVisibility(View.VISIBLE);
            binding.statusMessageText.setText("No internet connection");
            // Do not return, allow user to potentially see cached UI or behavior,
            // but actually we should probably return to avoid loading failure.
            // However, the requested fix is an INDICATOR.
            // Preventing load might be better UX than failing.
            return;
        } else {
            binding.statusMessageText.setVisibility(View.GONE);
        }

        // Reset to Native preferred
        showNativePlayer();

        if (currentPlayer == null)
            return;
        if (currentIndex >= 0 && currentIndex < videoIds.size()) {
            String vid = videoIds.get(currentIndex);
            if (vid != null && !vid.isEmpty()) {
                binding.btnOpenYoutube.setVisibility(View.GONE);

                // Smart Logic: Try to extract ID. If we have ID, use Native. If not, use Web.
                String extractedId = SongAdapter.extractVideoId(vid);

                // Update Background Dynamic
                if (extractedId != null) {
                    updateDynamicBackground(extractedId);
                } else if (vid.matches("[a-zA-Z0-9_-]{11}")) {
                    updateDynamicBackground(vid);
                }

                if (extractedId != null) {
                    // Check if it's likely a valid ID (11 chars) just to be sure
                    if (extractedId.length() == 11) {
                        float startTime = currentVideoTime;
                        if (Float.isNaN(startTime) || startTime < 0)
                            startTime = 0f;

                        currentPlayer.loadVideo(extractedId, startTime);
                        currentPlayer.unMute(); // Strict: Ensure unmute on new video load
                        isMuted = false; // Strict: Reset internal state
                        setupUI();
                        return;
                    }
                }

                // Fallback to Web if extraction failed but we have a URL string
                if (vid.startsWith("http")) {
                    playEffectivelyWithWebView();
                    setupUI();
                } else {
                    // Last attempt: maybe it IS an ID but extractVideoId failed for some reason?
                    // Or it's garbage. Try Native as last resort if it looks like ID.
                    if (vid.matches("[a-zA-Z0-9_-]{11}")) {
                        currentPlayer.loadVideo(vid, currentVideoTime);
                        currentPlayer.unMute(); // Strict: Ensure unmute
                        isMuted = false; // Strict: Reset internal state
                        setupUI();
                    } else {
                        playNextSong(); // Skip invalid
                    }
                }
            } else {
                playNextSong(); // Skip invalid
            }
        }
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(
                android.content.Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetworkInfo = connectivityManager != null
                ? connectivityManager.getActiveNetworkInfo()
                : null;
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void updateVisualizerState(boolean playing) {
        if (visualizerView == null)
            return;

        if (isInPictureInPictureMode()) {
            visualizerView.stop();
            return;
        }

        if (playing) {
            visualizerView.setVisibility(View.VISIBLE);
            try {
                visualizerView.start();
            } catch (Exception e) {
                // Fail silently if RECORD_AUDIO permission is missing or Visualizer fails
                // This prevents a crash and just shows a static view (or invisible)
            }
        } else {
            visualizerView.stop();
        }
    }

    private android.app.PictureInPictureParams updatePipParams(boolean isPlaying) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                java.util.ArrayList<android.app.RemoteAction> actions = new java.util.ArrayList<>();

                int iconId = isPlaying ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24;
                String title = isPlaying ? "Pause" : "Play";
                int controlType = isPlaying ? CONTROL_TYPE_PAUSE : CONTROL_TYPE_PLAY;

                android.app.PendingIntent intent = android.app.PendingIntent.getBroadcast(
                        this,
                        controlType,
                        new android.content.Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, controlType)
                                .setPackage(getPackageName()),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

                actions.add(new android.app.RemoteAction(
                        android.graphics.drawable.Icon.createWithResource(this, iconId),
                        title,
                        title,
                        intent));

                android.app.PictureInPictureParams params = new android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(new android.util.Rational(16, 9))
                        .setActions(actions)
                        .build();

                setPictureInPictureParams(params);
                return params;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    protected void onUserLeaveHint() {
        // STRICT RULE: Enter PiP only if media is actively playing (or paused but
        // active).
        // Robust Null Check: Ensure binding and player exist before attempting PiP
        if (binding != null && currentPlayer != null && !isFinishing()) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // Generate Params WITH Actions based on CURRENT state
                    android.app.PictureInPictureParams params = updatePipParams(isPlaying);

                    // Android 12+ handles auto-enter, but for older versions or explicit cases:
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                        if (params != null) {
                            enterPictureInPictureMode(params);
                        }
                    }

                    // Force continue playback if it was playing (Fixes stopping issue)
                    if (isPlaying) {
                        // Double tap: Ensure native player isn't paused by system
                        if (currentPlayer != null)
                            currentPlayer.play();

                        // Ensure WebView isn't paused
                        if (webView != null) {
                            webView.evaluateJavascript(
                                    "window.shouldPlay = true; var v = document.querySelector('video'); if(v) v.play();",
                                    null);
                        }
                    }
                }
            } catch (Exception e) {
                // Fail silently to prevent crash on exit
                e.printStackTrace();
            }
        }
        super.onUserLeaveHint();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
            android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        if (binding == null)
            return; // Crash Protection

        android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) binding.playerContainer
                .getLayoutParams();

        if (isInPictureInPictureMode) {
            // Hide non-video elements
            binding.songInfoContainer.setVisibility(View.GONE);
            binding.btnClose.setVisibility(View.GONE);
            binding.btnPip.setVisibility(View.GONE); // Hide PiP button itself
            binding.btnOpenYoutube.setVisibility(View.GONE); // Ensure this is hidden
            visualizerView.stop();
            visualizerView.setVisibility(View.GONE);

            // FORCE JS Injection again to ensure UI is clean for PiP
            if (webView != null) {
                webView.evaluateJavascript(getAdSkipperScript(), null);
            }

            // Remove Margin for Full Window Video
            params.topMargin = 0;
            binding.playerContainer.setLayoutParams(params);
        } else {
            // Restore UI
            binding.songInfoContainer.setVisibility(View.VISIBLE);
            binding.btnClose.setVisibility(View.VISIBLE);
            binding.btnPip.setVisibility(View.VISIBLE);
            // btnOpenYoutube logic is handled by setupPlayer/state, so don't blindly show
            // it
            updateVisualizerState(isPlaying);

            // Restore Margin (32dp approx 84px - using resource or converting would be
            // better but hardcoded matches XML for now)
            params.topMargin = (int) android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());
            binding.playerContainer.setLayoutParams(params);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateVisualizerState(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            setPictureInPictureParams(new android.app.PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(true)
                    .build());
        }
    }

    @Override
    protected void onDestroy() {
        try {
            // stopWatchdog(); // REMOVED
            unregisterReceiver(pipReceiver);
        } catch (Exception e) {
        }

        if (binding != null) {
            if (binding.youtubePlayerView != null) {
                binding.youtubePlayerView.release();
            }
        }

        // Critical: Release WebView locally, binding might be null or view detached
        if (webView != null) {
            try {
                webView.removeJavascriptInterface("Android");
                webView.loadUrl("about:blank");
                webView.destroy();
            } catch (Exception e) {
            }
            webView = null;
        }

        abandonAudioFocus(); // Release Focus
        super.onDestroy();
    }

    private void initAudioFocus() {
        audioManager = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
        audioFocusChangeListener = new android.media.AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch (focusChange) {
                    case android.media.AudioManager.AUDIOFOCUS_LOSS:
                        pauseVideo(); // Permanent loss
                        break;
                    case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        pauseVideo(); // Temporary loss (call)
                        break;
                    case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        pauseVideo(); // Pause even for ducking (safe)
                        break;
                    case android.media.AudioManager.AUDIOFOCUS_GAIN:
                        if (!isPlaying)
                            playVideo(); // Regained focus
                        break;
                }
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();
        }
    }

    private void requestAudioFocus() {
        if (audioManager == null)
            initAudioFocus();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(audioFocusChangeListener,
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null)
            return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
    }
}
