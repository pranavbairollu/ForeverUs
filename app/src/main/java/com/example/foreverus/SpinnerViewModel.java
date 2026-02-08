package com.example.foreverus;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SpinnerViewModel extends AndroidViewModel {

    private final SpinnerRepository repository;
    private final MemoryRepository memoryRepository;

    private final LiveData<List<SpinnerCategory>> allCategories;

    private final MutableLiveData<SpinnerItem> _spinResult = new MutableLiveData<>();
    public final LiveData<SpinnerItem> spinResult = _spinResult;

    private final MutableLiveData<Boolean> _isSpinning = new MutableLiveData<>(false);
    public final LiveData<Boolean> isSpinning = _isSpinning;

    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public final LiveData<String> toastMessage = _toastMessage;

    private boolean hasRespun = false;
    private SpinnerCategory currentCategory;

    public SpinnerViewModel(@NonNull Application application) {
        super(application);
        repository = new SpinnerRepository(application);
        memoryRepository = new MemoryRepository(application);
        allCategories = repository.getAllCategories();

        currentCategoryItems = androidx.lifecycle.Transformations.switchMap(
                _currentCategoryId,
                id -> repository.getItemsForCategory(id));
    }

    public LiveData<List<SpinnerCategory>> getAllCategories() {
        return allCategories;
    }

    public LiveData<List<SpinnerItem>> getItemsForCategory(String categoryId) {
        return repository.getItemsForCategory(categoryId);
    }

    private final MutableLiveData<String> _currentCategoryId = new MutableLiveData<>();
    public final LiveData<List<SpinnerItem>> currentCategoryItems;

    public void setCurrentCategory(SpinnerCategory category) {
        this.currentCategory = category;
        if (category != null) {
            _currentCategoryId.setValue(category.getId());
        }
        resetGame();
    }

    public void resetGame() {
        hasRespun = false;
        _spinResult.setValue(null);
    }

    public void spin() {
        if (Boolean.TRUE.equals(_isSpinning.getValue())) {
            return;
        }

        if (currentCategory == null) {
            android.util.Log.e("SpinnerDebug", "Current category is null!");
            return;
        }

        if (_spinResult.getValue() != null && hasRespun) {
            _toastMessage.setValue("Hey 😄 no cheating fate.");
            return;
        }

        if (_spinResult.getValue() != null) {
            hasRespun = true;
        }

        _isSpinning.setValue(true);

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            android.util.Log.d("SpinnerDebug",
                    "Spinning for Category: " + currentCategory.getName() + " ID: " + currentCategory.getId());

            List<SpinnerItem> items = repository.getItemsForCategorySync(currentCategory.getId());

            if (items == null) {
                android.util.Log.d("SpinnerDebug", "Items list is null");
            } else {
                android.util.Log.d("SpinnerDebug", "Found " + items.size() + " items.");
                for (SpinnerItem item : items) {
                    android.util.Log.d("SpinnerDebug",
                            "Item: " + item.getText() + " (CatID: " + item.getCategoryId() + ")");
                }
            }

            if (items == null || items.isEmpty()) {
                _isSpinning.postValue(false);
                _toastMessage.postValue("This list is empty! Add some items first.");
                return;
            }

            Random random = new Random();
            SpinnerItem winner = items.get(random.nextInt(items.size()));
            android.util.Log.d("SpinnerDebug", "Winner: " + winner.getText());

            try {
                Thread.sleep(3200); // 3.2s to cover the 3s UI animation
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            _spinResult.postValue(winner);
            _isSpinning.postValue(false);
        });
    }

    // ... saveResultAsMemory ...
    public void saveResultAsMemory(String relationshipId) {
        SpinnerItem result = _spinResult.getValue();
        if (result == null || currentCategory == null)
            return;

        Memory memory = new Memory();
        memory.setTitle("Us-Time Decision");

        String description;
        String catName = currentCategory.getName().toLowerCase();
        if (catName.contains("food") || catName.contains("dinner") || catName.contains("eat")) {
            description = "We let fate choose dinner 🍽️ — " + result.getText() + " " + result.getEmoji();
        } else if (catName.contains("movie") || catName.contains("film")) {
            description = "The spinner decided movie night 🎬 — " + result.getText() + " " + result.getEmoji();
        } else if (catName.contains("weekend") || catName.contains("activity")) {
            description = "Tonight's plan was decided 🎡 — " + result.getText() + " " + result.getEmoji();
        } else {
            description = "We let fate choose " + currentCategory.getName() + " and it picked: " + result.getEmoji()
                    + " " + result.getText();
        }

        memory.setDescription(description);
        memory.setImageUrl("android.resource://com.example.foreverus/drawable/grad_auth_background");
        memory.setTimestamp(new com.google.firebase.Timestamp(new java.util.Date()));
        memory.setRelationshipId(relationshipId);

        // Deterministic Replay Data
        memory.setSpinnerCategoryId(currentCategory.getId());
        memory.setSpinnerItemId(result.getId());
        // Tag source if useful for filtering later
        // memory.setSource("SOURCE_SPINNER");

        memoryRepository.saveMemory(memory, null);
    }

    public void loadReplayItem(String categoryId, String itemId) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            List<SpinnerItem> items = repository.getItemsForCategorySync(categoryId);
            SpinnerItem foundItem = null;

            if (items != null) {
                for (SpinnerItem item : items) {
                    if (item.getId().equals(itemId)) {
                        foundItem = item;
                        break;
                    }
                }
            }

            if (foundItem != null) {
                _spinResult.postValue(foundItem);
            } else {
                // Edge Case: Item was deleted after memory was saved.
                // Fallback to a "Ghost" item so UI doesn't hang.
                SpinnerItem ghost = new SpinnerItem(categoryId, "Start a new spin!", "❓", 0);
                _spinResult.postValue(ghost);
                _toastMessage.postValue("Original item was deleted.");
            }
        });
    }

    // For List Management
    public void addItem(String text, String emoji) {
        if (currentCategory == null)
            return;
        // Simple append
        SpinnerItem item = new SpinnerItem(currentCategory.getId(), text, emoji, 999);
        repository.insertItem(item);
    }

    public void addCategory(String name) {
        List<SpinnerCategory> currentList = allCategories.getValue();
        if (currentList != null) {
            for (SpinnerCategory cat : currentList) {
                if (cat.getName().equalsIgnoreCase(name)) {
                    _toastMessage.setValue("Category '" + name + "' already exists!");
                    return;
                }
            }
        }
        SpinnerCategory newCat = new SpinnerCategory(name, false);
        repository.insertCategory(newCat);
        // Toast? maybe.
        _toastMessage.setValue("Category created!");
    }

    public void deleteItem(String itemId) {
        repository.deleteItem(itemId);
    }

    public void deleteCategory(SpinnerCategory category) {
        if (category == null)
            return;
        repository.deleteCategory(category.getId());
        if (currentCategory != null && currentCategory.getId().equals(category.getId())) {
            currentCategory = null;
            resetGame();
        }
    }

    public void clearToast() {
        _toastMessage.setValue(null);
    }
}
