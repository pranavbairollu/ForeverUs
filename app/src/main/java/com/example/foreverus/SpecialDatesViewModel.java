package com.example.foreverus;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import java.util.List;

public class SpecialDatesViewModel extends AndroidViewModel {

    private final SpecialDateRepository repository;
    private final MutableLiveData<String> relationshipIdLiveData = new MutableLiveData<>();
    private final LiveData<List<SpecialDate>> specialDates;
    private final LiveData<String> firestoreError;

    public SpecialDatesViewModel(@NonNull Application application) {
        super(application);
        repository = new SpecialDateRepository(application);
        specialDates = Transformations.map(
                Transformations.switchMap(relationshipIdLiveData, id -> repository.getAllSpecialDates(id)),
                list -> {
                    if (list != null) {
                        List<SpecialDate> sortedList = new java.util.ArrayList<>(list);
                        java.util.Collections.sort(sortedList);
                        return sortedList;
                    }
                    return list;
                });
        firestoreError = repository.getFirestoreError();
    }

    public void loadSpecialDates(String relationshipId) {
        if (relationshipId != null && !relationshipId.equals(relationshipIdLiveData.getValue())) {
            relationshipIdLiveData.setValue(relationshipId);
        }
    }

    public LiveData<List<SpecialDate>> getSpecialDates() {
        return specialDates;
    }

    public LiveData<String> getFirestoreError() {
        return firestoreError;
    }

    public void addSpecialDate(String relationshipId, SpecialDate specialDate) {
        repository.addSpecialDate(relationshipId, specialDate);
    }

    public void deleteSpecialDate(String relationshipId, SpecialDate specialDate) {
        repository.deleteSpecialDate(relationshipId, specialDate);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.cleanup();
    }
}
