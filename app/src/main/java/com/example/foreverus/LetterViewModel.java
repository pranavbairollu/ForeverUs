package com.example.foreverus;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

public class LetterViewModel extends AndroidViewModel {
    private final LetterRepository letterRepository;
    private final LiveData<Resource<Letter>> letter;

    public LetterViewModel(@NonNull Application application, String relationshipId, String letterId) {
        super(application);
        letterRepository = new LetterRepository(application);
        letter = letterRepository.getLetter(relationshipId, letterId);
    }

    public LiveData<Resource<Letter>> getLetter() {
        return letter;
    }

    public void markLetterAsOpened(Letter letter) {
        letterRepository.markLetterAsOpened(letter);
    }
    
    public void updateReaction(String relationshipId, String letterId, String reaction) {
        letterRepository.updateReaction(relationshipId, letterId, reaction);
    }

    public void updateReply(String relationshipId, String letterId, String replyContent) {
        letterRepository.updateReply(relationshipId, letterId, replyContent);
    }
    
    public void markAsRead(String relationshipId, String letterId) {
        letterRepository.markAsRead(relationshipId, letterId);
    }
}
