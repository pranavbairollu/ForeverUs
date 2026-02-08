package com.example.foreverus;

public class YoutubeVideoInfo {
    private final String title;
    private final String channelTitle;

    public YoutubeVideoInfo(String title, String channelTitle) {
        this.title = title;
        this.channelTitle = channelTitle;
    }

    public String getTitle() {
        return title;
    }

    public String getChannelTitle() {
        return channelTitle;
    }
}
