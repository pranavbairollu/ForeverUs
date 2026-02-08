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
import java.util.Objects;

public class LettersViewModel extends AndroidViewModel {

    private final LetterRepository repository;
    private final MediatorLiveData<String> refreshTrigger = new MediatorLiveData<>();
    private final LiveData<Resource<List<Letter>>> letters;

    public LettersViewModel(@NonNull Application application) {
        super(application);
        repository = new LetterRepository(application);
        letters = Transformations.switchMap(refreshTrigger, id -> {
            if (id == null || id.isEmpty()) {
                MutableLiveData<Resource<List<Letter>>> emptyResult = new MutableLiveData<>();
                emptyResult.setValue(Resource.success(Collections.emptyList()));
                return emptyResult;
            }
            return repository.getAllLetters(id);
        });
    }

    public void setRelationshipId(String relationshipId) {
        if (!Objects.equals(relationshipId, refreshTrigger.getValue())) {
            refreshTrigger.setValue(relationshipId);
        }
    }

    public void refresh() {
        refreshTrigger.setValue(refreshTrigger.getValue());
    }

    public LiveData<Resource<List<Letter>>> getAllLetters() {
        return letters;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.cleanup();
    }
}
