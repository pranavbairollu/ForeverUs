package com.example.foreverus;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class LetterViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final String relationshipId;
    private final String letterId;

    public LetterViewModelFactory(@NonNull Application application, String relationshipId, String letterId) {
        this.application = application;
        this.relationshipId = relationshipId;
        this.letterId = letterId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(LetterViewModel.class)) {
            return (T) new LetterViewModel(application, relationshipId, letterId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
