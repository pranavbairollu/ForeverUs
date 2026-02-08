package com.example.foreverus;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import java.util.List;

public class SongsViewModel extends AndroidViewModel {

    private final SongRepository songRepository;
    private final YoutubeRepository youtubeRepository;
    public final MutableLiveData<String> relationshipIdLiveData = new MutableLiveData<>();
    private final LiveData<Resource<List<Song>>> songs;
    private final MutableLiveData<Resource<YoutubeVideoInfo>> youtubeVideoInfo = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> addSongStatus = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> deleteSongStatus = new MutableLiveData<>();

    public SongsViewModel(@NonNull Application application) {
        super(application);
        songRepository = new SongRepository(application);
        youtubeRepository = new YoutubeRepository();
        songs = Transformations.switchMap(relationshipIdLiveData, id -> {
            if (id == null || id.isEmpty()) {
                MutableLiveData<Resource<List<Song>>> emptyResult = new MutableLiveData<>();
                emptyResult.setValue(Resource.success(null));
                return emptyResult;
            }
            return songRepository.getAllSongs(id);
        });
    }

    public LiveData<Resource<List<Song>>> getSongs() {
        return songs;
    }

    public LiveData<Resource<YoutubeVideoInfo>> getYoutubeVideoInfo() {
        return youtubeVideoInfo;
    }

    public LiveData<Resource<Void>> getAddSongStatus() {
        return addSongStatus;
    }

    public LiveData<Resource<Void>> getDeleteSongStatus() {
        return deleteSongStatus;
    }

    public void addSong(String relationshipId, Song song) {
        addSongStatus.setValue(Resource.loading(null));
        songRepository.addSong(relationshipId, song, resource -> addSongStatus.postValue(resource));
    }

    public void deleteSong(String relationshipId, Song song) {
        deleteSongStatus.setValue(Resource.loading(null));
        songRepository.deleteSong(relationshipId, song, resource -> deleteSongStatus.postValue(resource));
    }

    public void fetchYoutubeVideoInfo(String videoId) {
        youtubeVideoInfo.setValue(Resource.loading(null));
        youtubeRepository.fetchVideoInfo(videoId, resource -> youtubeVideoInfo.postValue(resource));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        songRepository.cleanup();
        youtubeRepository.cleanup();
    }
}
