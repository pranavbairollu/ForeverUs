package com.example.foreverus;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.Collections;
import java.util.List;

public class MemoriesViewModel extends AndroidViewModel {

    private final MemoryRepository repository;
    private final MutableLiveData<String> relationshipIdLiveData = new MutableLiveData<>();
    private final LiveData<Resource<List<Memory>>> memories;

    public MemoriesViewModel(@NonNull Application application) {
        super(application);
        repository = new MemoryRepository(application);
        memories = Transformations.switchMap(relationshipIdLiveData, id -> {
            if (id == null || id.isEmpty()) {
                MutableLiveData<Resource<List<Memory>>> emptyResult = new MutableLiveData<>();
                emptyResult.setValue(Resource.success(Collections.emptyList()));
                return emptyResult;
            }
            return repository.getAllMemories(id);
        });
    }

    public void loadMemories(String relationshipId) {
        if (relationshipId != null && !relationshipId.equals(relationshipIdLiveData.getValue())) {
            relationshipIdLiveData.setValue(relationshipId);
        }
    }

    public LiveData<Resource<List<Memory>>> getMemories() {
        return memories;
    }

    public void deleteMemory(String memoryId, String relationshipId) {
        repository.deleteMemory(memoryId, relationshipId, null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.cleanup();
    }
}
