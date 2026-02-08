package com.example.foreverus;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeSearchActivity extends BaseActivity {

    public static final String EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID";
    public static final String EXTRA_VIDEO_TITLE = "EXTRA_VIDEO_TITLE";
    public static final String EXTRA_PLAY_VIDEO_ID = "EXTRA_PLAY_VIDEO_ID";

    private WebView webView;
    private ProgressBar progressBar;
    private static final String YOUTUBE_MOBILE_URL = "https://m.youtube.com";

    // Regex to detect video clicks (Legacy). Now we use SongAdapter.extractVideoId
    // for robustness.
    // Kept for reference or quick regex checks if needed, but primary logic
    // delegates to SongAdapter.
    private static final Pattern VIDEO_WATCH_PATTERN = Pattern.compile(
            "(?:watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\\?v=|youtu.be%2F|%2Fv%2F|shorts/)([a-zA-Z0-9_-]{11})");

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_search);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        setupWebView();

        setupWebView();

        if (getIntent().hasExtra(EXTRA_PLAY_VIDEO_ID)) {
            // Play Mode: Hide instructions
            findViewById(R.id.instructionText).setVisibility(View.GONE);

            String videoId = getIntent().getStringExtra(EXTRA_PLAY_VIDEO_ID);
            String playUrl = "https://m.youtube.com/watch?v=" + videoId;
            webView.loadUrl(playUrl);
            // Update title if provided
            if (getIntent().hasExtra(EXTRA_VIDEO_TITLE)) {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(getIntent().getStringExtra(EXTRA_VIDEO_TITLE));
                }
            }
        } else {
            webView.loadUrl(YOUTUBE_MOBILE_URL);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); // Attempt to auto-play
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"); // Mobile
                                                                                                                                    // UA

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (checkUrlForVideo(url, view.getTitle())) {
                    return true; // Handled
                }
                return false; // Let WebView load it
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!checkUrlForVideo(url, view.getTitle())) {
                    super.onPageStarted(view, url, favicon);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Double check in case we missed the load event
                checkUrlForVideo(url, view.getTitle());
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(YouTubeSearchActivity.this, "Error loading page: " + description, Toast.LENGTH_SHORT)
                        .show();
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                // Critical for SPA: Catch URL changes that don't trigger full reload
                checkUrlForVideo(url, view.getTitle());
            }
        });
    }

    private boolean checkUrlForVideo(String url, String pageTitle) {
        // If we are in "Play Mode", providing a playback experience, existing checks
        // might
        // prematurely close the activity. We want to allow navigation within YouTube.
        // However, the original intent of this method was to 'Capture' a video ID for
        // selection.

        if (getIntent().hasExtra(EXTRA_PLAY_VIDEO_ID)) {
            // In Play Mode, we just let the user browse/watch. We don't "capture" and
            // finish.
            return false;
        }

        // Use centralized extraction logic to ensure consistency with Player
        String videoId = SongAdapter.extractVideoId(url);

        // Matcher backup (legacy local check) if Adapter fails but Pattern matches?
        // Actually SongAdapter should be the source of truth.
        if (videoId == null) {
            Matcher matcher = VIDEO_WATCH_PATTERN.matcher(url);
            if (matcher.find()) {
                videoId = matcher.group(1);
            }
        }

        if (videoId != null && videoId.length() == 11) {
            Log.d("YouTubeSearch", "Detected Video ID: " + videoId);

            // Clean up title (YouTube titles often end with " - YouTube")
            String title = pageTitle;
            if (title != null && title.endsWith(" - YouTube")) {
                title = title.substring(0, title.length() - 10);
            }
            if (title == null || title.isEmpty() || title.equals("YouTube")) {
                title = "New Song"; // Fallback
            }

            // Return result
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_VIDEO_ID, videoId);
            resultIntent.putExtra(EXTRA_VIDEO_TITLE, title);
            setResult(RESULT_OK, resultIntent);
            finish();
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // User pressed the top-left back arrow.
            // We treat this as "Cancel Search" and strictly close the activity,
            // ignoring WebView history.
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
