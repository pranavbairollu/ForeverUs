package com.example.foreverus;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import android.util.Log;
import androidx.lifecycle.Transformations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TimelineViewModel extends AndroidViewModel {

    private final StoryRepository storyRepository;
    private final MemoryRepository memoryRepository;
    private final LetterRepository letterRepository;
    private final RelationshipRepository relationshipRepository;

    private final MutableLiveData<String> relationshipIdLiveData = new MutableLiveData<>();
    private final MediatorLiveData<Resource<List<TimelineItem>>> timelineItems = new MediatorLiveData<>();
    private final LiveData<String> coupleNickname;

    private LiveData<Resource<List<Story>>> storiesSource;
    private LiveData<Resource<List<Memory>>> memoriesSource;
    private LiveData<Resource<List<Letter>>> lettersSource;

    public TimelineViewModel(@NonNull Application application) {
        super(application);
        storyRepository = new StoryRepository(application);
        memoryRepository = new MemoryRepository(application);
        letterRepository = new LetterRepository(application);
        relationshipRepository = RelationshipRepository.getInstance();

        coupleNickname = Transformations.switchMap(relationshipIdLiveData, id -> relationshipRepository.getCoupleNickname(id));

        timelineItems.addSource(relationshipIdLiveData, this::onRelationshipIdChanged);
    }

    private void onRelationshipIdChanged(String relationshipId) {
        // Remove old sources if they exist
        if (storiesSource != null) timelineItems.removeSource(storiesSource);
        if (memoriesSource != null) timelineItems.removeSource(memoriesSource);
        if (lettersSource != null) timelineItems.removeSource(lettersSource);

        if (relationshipId == null || relationshipId.isEmpty()) {
            timelineItems.setValue(Resource.success(Collections.emptyList()));
            return;
        }

        // Get new sources
        storiesSource = storyRepository.getAllStories(relationshipId);
        memoriesSource = memoryRepository.getAllMemories(relationshipId);
        lettersSource = letterRepository.getAllLetters(relationshipId);

        // Add new sources
        timelineItems.addSource(storiesSource, storyResource -> combineData());
        timelineItems.addSource(memoriesSource, memoryResource -> combineData());
        timelineItems.addSource(lettersSource, letterResource -> combineData());
    }

    private String currentFilter = "ALL";

    public void setFilter(String filter) {
        this.currentFilter = filter;
        if (storiesSource != null && memoriesSource != null && lettersSource != null) {
            combineData();
        }
    }

    private final java.util.concurrent.ExecutorService backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private void combineData() {
        // Capture current values (LiveData.getValue() must be on Main Thread, safe here)
        Resource<List<Story>> storyResource = storiesSource != null ? storiesSource.getValue() : null;
        Resource<List<Memory>> memoryResource = memoriesSource != null ? memoriesSource.getValue() : null;
        Resource<List<Letter>> letterResource = lettersSource != null ? lettersSource.getValue() : null;

        backgroundExecutor.execute(() -> {
            // 1. Loading State Check
            boolean isStoryLoading = (storyResource == null || (storyResource.status == Resource.Status.LOADING && storyResource.data == null));
            boolean isMemoryLoading = (memoryResource == null || (memoryResource.status == Resource.Status.LOADING && memoryResource.data == null));
            boolean isLetterLoading = (letterResource == null || (letterResource.status == Resource.Status.LOADING && letterResource.data == null));

            if (isStoryLoading || isMemoryLoading || isLetterLoading) {
                 timelineItems.postValue(Resource.loading(null));
                 return;
            }

            List<Object> combinedList = new ArrayList<>();
            
            // Filter Logic
            if (currentFilter.equals("ALL") || currentFilter.equals("STORIES")) {
                if (storyResource != null && storyResource.data != null) combinedList.addAll(storyResource.data);
            }
            if (currentFilter.equals("ALL") || currentFilter.equals("MEMORIES")) {
                 if (memoryResource != null && memoryResource.data != null) combinedList.addAll(memoryResource.data);
            }
            if (currentFilter.equals("ALL") || currentFilter.equals("LETTERS")) {
                 if (letterResource != null && letterResource.data != null) combinedList.addAll(letterResource.data);
            }
            
            boolean hasError = (storyResource != null && storyResource.status == Resource.Status.ERROR) ||
                               (memoryResource != null && memoryResource.status == Resource.Status.ERROR) ||
                               (letterResource != null && letterResource.status == Resource.Status.ERROR);

            if (hasError) {
                 StringBuilder errorBuilder = new StringBuilder("Sync issue: ");
                 if (storyResource != null && storyResource.status == Resource.Status.ERROR) errorBuilder.append("Stories ");
                 if (memoryResource != null && memoryResource.status == Resource.Status.ERROR) errorBuilder.append("Memories ");
                 if (letterResource != null && letterResource.status == Resource.Status.ERROR) errorBuilder.append("Letters ");
                 
                 if (combinedList.isEmpty()) {
                     timelineItems.postValue(Resource.error(errorBuilder.toString().trim(), new ArrayList<TimelineItem>()));
                     return;
                 }
            }

            // Sort by Time (Descending)
            Collections.sort(combinedList, (o1, o2) -> {
                long t1 = getTimestamp(o1);
                long t2 = getTimestamp(o2);
                return Long.compare(t2, t1);
            });

            List<TimelineItem> finalItemsWithHeaders = generateTimelineWithHeaders(combinedList);
            
            timelineItems.postValue(Resource.success(finalItemsWithHeaders));
        });
    }

    private List<TimelineItem> generateTimelineWithHeaders(List<Object> sortedList) {
        List<TimelineItem> finalTimelineItems = new ArrayList<>();
        java.text.SimpleDateFormat monthYearFormat = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
        java.util.Calendar cal = java.util.Calendar.getInstance();
        long now = System.currentTimeMillis();
        cal.setTimeInMillis(now);
        int currentMonth = cal.get(java.util.Calendar.MONTH);
        int currentDay = cal.get(java.util.Calendar.DAY_OF_MONTH);
        int currentYear = cal.get(java.util.Calendar.YEAR);

        List<Object> onThisDayItems = new ArrayList<>();
        List<Object> otherItems = new ArrayList<>();

        for (Object item : sortedList) {
            long ts = getTimestamp(item);
            cal.setTimeInMillis(ts);
            
            // "On This Day" Logic: Same Month/Day, Different Year
            if (cal.get(java.util.Calendar.MONTH) == currentMonth &&
                cal.get(java.util.Calendar.DAY_OF_MONTH) == currentDay &&
                cal.get(java.util.Calendar.YEAR) != currentYear) {
                onThisDayItems.add(item);
            } else {
                otherItems.add(item);
            }
        }
        
        if (!onThisDayItems.isEmpty()) {
            finalTimelineItems.add(new TimelineItem.HeaderItem("On This Day ❤️", now));
            for (Object item : onThisDayItems) {
                finalTimelineItems.add(convertToTimelineItem(item));
            }
        }

        String lastHeader = "";
        for (Object item : otherItems) {
            long ts = getTimestamp(item);
            if (ts <= 0) continue; 
            
            String headerTitle = monthYearFormat.format(new java.util.Date(ts));
            
            if (!headerTitle.equals(lastHeader)) {
                finalTimelineItems.add(new TimelineItem.HeaderItem(headerTitle, ts));
                lastHeader = headerTitle;
            }
            finalTimelineItems.add(convertToTimelineItem(item));
        }

        return finalTimelineItems;
    }
    
    private TimelineItem convertToTimelineItem(Object item) {
        if (item instanceof Story) {
            return new TimelineItem.StoryItem((Story) item);
        } else if (item instanceof Memory) {
            return new TimelineItem.MemoryItem((Memory) item);
        } else if (item instanceof Letter) {
            return new TimelineItem.LetterItem((Letter) item);
        }
        return null;
    }

    public void loadTimeline(String relationshipId) {
        if (relationshipId != null && !relationshipId.equals(relationshipIdLiveData.getValue())) {
            relationshipIdLiveData.setValue(relationshipId);
        }
    }

    public LiveData<Resource<List<TimelineItem>>> getTimelineItems() {
        return timelineItems;
    }

    public LiveData<String> getCoupleNickname() {
        return coupleNickname;
    }

    public void refresh() {
        String currentId = relationshipIdLiveData.getValue();
        if (currentId != null) {
             onRelationshipIdChanged(currentId);
        }
    }

    private long getTimestamp(Object object) {
        com.google.firebase.Timestamp ts = null;
        if (object instanceof Story) ts = ((Story) object).getTimestamp();
        if (object instanceof Memory) ts = ((Memory) object).getTimestamp();
        if (object instanceof Letter) ts = ((Letter) object).getTimestamp();
        return ts != null ? ts.toDate().getTime() : 0;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        storyRepository.cleanup();
        memoryRepository.cleanup();
        letterRepository.cleanup();
        backgroundExecutor.shutdown();
    }
}
