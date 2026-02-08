package com.example.foreverus;

public class MemoryHeader implements MemoryListItem {
    private final String title;

    public MemoryHeader(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public int getItemType() {
        return TYPE_HEADER;
    }
}
