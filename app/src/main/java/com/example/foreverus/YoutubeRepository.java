package com.example.foreverus;

import android.os.Handler;
import android.os.Looper;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YoutubeRepository {

    private static final String API_KEY = BuildConfig.YOUTUBE_API_KEY;
    private final YouTube youtube;
    private final ExecutorService executorService;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public interface YoutubeRepositoryListener<T> {
        void onComplete(Resource<T> resource);
    }

    public YoutubeRepository() {
        youtube = new YouTube.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), null)
                .setApplicationName("ForeverUs")
                .build();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void fetchVideoInfo(String videoId, YoutubeRepositoryListener<YoutubeVideoInfo> listener) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            listener.onComplete(Resource.error("YouTube API Key is missing", null));
            return;
        }

        executorService.execute(() -> {
            try {
                YoutubeVideoInfo result = fetchVideoInfoSync(videoId);
                mainThreadHandler.post(() -> {
                    if (result != null) {
                        listener.onComplete(Resource.success(result));
                    } else {
                        listener.onComplete(Resource.error("Video not found", null));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainThreadHandler.post(() -> listener.onComplete(Resource.error("Error: " + e.getMessage(), null)));
            }
        });
    }

    private YoutubeVideoInfo fetchVideoInfoSync(String videoId) throws IOException {
        YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet"));
        request.setKey(API_KEY);
        request.setId(Collections.singletonList(videoId));
        VideoListResponse response = request.execute();
        if (response.getItems() == null || response.getItems().isEmpty()) {
            return null;
        }
        String title = response.getItems().get(0).getSnippet().getTitle();
        String channelTitle = response.getItems().get(0).getSnippet().getChannelTitle();
        return new YoutubeVideoInfo(title, channelTitle);
    }

    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
