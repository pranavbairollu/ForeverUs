package com.example.foreverus;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class RelationshipRepository {

    private static volatile RelationshipRepository instance;

    private final MutableLiveData<String> relationshipId = new MutableLiveData<>();

    private ListenerRegistration nicknameListener;
    private final MutableLiveData<String> coupleNicknameLiveData = new MutableLiveData<>();

    private RelationshipRepository() {
    }

    public static RelationshipRepository getInstance() {
        if (instance == null) {
            synchronized (RelationshipRepository.class) {
                if (instance == null) {
                    instance = new RelationshipRepository();
                }
            }
        }
        return instance;
    }

    public LiveData<String> getRelationshipId() {
        return relationshipId;
    }

    public void setRelationshipId(String id) {
        relationshipId.postValue(id);
    }

    public LiveData<String> getCoupleNickname(String id) {
        if (id == null) {
             coupleNicknameLiveData.setValue("You & I");
             return coupleNicknameLiveData;
        }

        if (nicknameListener != null) {
            nicknameListener.remove();
        }

        nicknameListener = FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_RELATIONSHIPS)
                .document(id)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("RelationshipRepo", "Listen failed.", error);
                        return;
                    }
                    if (value != null && value.exists()) {
                        String nickname = value.getString(FirestoreConstants.FIELD_COUPLE_NICKNAME);
                        if (nickname != null && !nickname.isEmpty()) {
                            coupleNicknameLiveData.postValue(nickname);
                        } else {
                            coupleNicknameLiveData.postValue("You & I");
                        }
                    } else {
                         coupleNicknameLiveData.postValue("You & I");
                    }
                });
        
        return coupleNicknameLiveData;
    }

    public void clear() {
        relationshipId.postValue(null);
        if (nicknameListener != null) {
            nicknameListener.remove();
            nicknameListener = null;
        }
    }
}
