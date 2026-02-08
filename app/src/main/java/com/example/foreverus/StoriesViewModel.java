package com.example.foreverus;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.Collections;
import java.util.List;

public class StoriesViewModel extends AndroidViewModel {

    private final StoryRepository storyRepository;
    private final RelationshipRepository relationshipRepository;
    private final LiveData<Resource<List<Story>>> stories;
    private final MediatorLiveData<String> refreshTrigger = new MediatorLiveData<>();

    public StoriesViewModel(@NonNull Application application) {
        super(application);
        storyRepository = new StoryRepository(application);
        relationshipRepository = RelationshipRepository.getInstance();

        // Pass relationship ID updates to the trigger
        refreshTrigger.addSource(relationshipRepository.getRelationshipId(), refreshTrigger::setValue);

        stories = Transformations.switchMap(refreshTrigger, id -> {
            if (id == null || id.isEmpty()) {
                MutableLiveData<Resource<List<Story>>> emptyResult = new MutableLiveData<>();
                // To avoid showing an error state when the user is just not paired yet.
                emptyResult.setValue(new Resource<>(Resource.Status.SUCCESS, Collections.emptyList(), null));
                return emptyResult;
            }
            return storyRepository.getAllStories(id);
        });
    }

    public void refresh() {
        refreshTrigger.setValue(refreshTrigger.getValue());
    }

    public LiveData<Resource<List<Story>>> getStories() {
        return stories;
    }

    public LiveData<String> getRelationshipId() {
        return refreshTrigger;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        storyRepository.cleanup();
    }
}
