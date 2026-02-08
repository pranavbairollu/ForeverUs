package com.example.foreverus;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import java.util.Objects;

public abstract class TimelineItem {

    public abstract String getId();
    public abstract long getTimestamp();

    public static class StoryItem extends TimelineItem {
        private final Story story;

        public StoryItem(Story story) {
            this.story = story;
        }

        public Story getStory() {
            return story;
        }

        @Override
        public String getId() {
            return story.getId();
        }

        @Override
        public long getTimestamp() {
            return story.getTimestamp() != null ? story.getTimestamp().toDate().getTime() : 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StoryItem storyItem = (StoryItem) o;
            return Objects.equals(story, storyItem.story);
        }

        @Override
        public int hashCode() {
            return Objects.hash(story);
        }
    }

    public static class MemoryItem extends TimelineItem {
        private final Memory memory;

        public MemoryItem(Memory memory) {
            this.memory = memory;
        }

        public Memory getMemory() {
            return memory;
        }

        @Override
        public String getId() {
            return memory.getId();
        }

        @Override
        public long getTimestamp() {
            return memory.getTimestamp() != null ? memory.getTimestamp().toDate().getTime() : 0;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MemoryItem that = (MemoryItem) o;
            return Objects.equals(memory, that.memory);
        }

        @Override
        public int hashCode() {
            return Objects.hash(memory);
        }
    }

    public static class LetterItem extends TimelineItem {
        private final Letter letter;

        public LetterItem(Letter letter) {
            this.letter = letter;
        }

        public Letter getLetter() {
            return letter;
        }

        @Override
        public String getId() {
            return letter.getLetterId();
        }

        @Override
        public long getTimestamp() {
            return letter.getTimestamp() != null ? letter.getTimestamp().toDate().getTime() : 0;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LetterItem that = (LetterItem) o;
            return Objects.equals(letter, that.letter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(letter);
        }
    }

    public static class HeaderItem extends TimelineItem {
        private final String title;
        private final long timestamp; // For sorting

        public HeaderItem(String title, long timestamp) {
            this.title = title;
            this.timestamp = timestamp;
        }

        public String getTitle() {
            return title;
        }

        @Override
        public String getId() {
            return "header_" + title + "_" + timestamp;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HeaderItem that = (HeaderItem) o;
            return Objects.equals(title, that.title) && timestamp == that.timestamp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, timestamp);
        }
    }

    public static class TimelineDiffCallback extends DiffUtil.ItemCallback<TimelineItem> {
        @Override
        public boolean areItemsTheSame(@NonNull TimelineItem oldItem, @NonNull TimelineItem newItem) {
            return oldItem.getClass() == newItem.getClass() && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull TimelineItem oldItem, @NonNull TimelineItem newItem) {
            return oldItem.equals(newItem);
        }
    }
}
