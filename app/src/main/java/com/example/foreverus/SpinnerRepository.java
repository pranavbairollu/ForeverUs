package com.example.foreverus;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpinnerRepository {

    private final SpinnerDao spinnerDao;
    private final ExecutorService executorService;
    private final LiveData<List<SpinnerCategory>> allCategories;

    public SpinnerRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        spinnerDao = db.spinnerDao();
        executorService = Executors.newSingleThreadExecutor();
        allCategories = spinnerDao.getAllCategories();

        // Pre-populate if empty
        executorService.execute(() -> {
            if (spinnerDao.getCategoryCount() == 0) {
                prePopulateData();
            }
        });
    }

    public LiveData<List<SpinnerCategory>> getAllCategories() {
        return allCategories;
    }

    public LiveData<List<SpinnerItem>> getItemsForCategory(String categoryId) {
        return spinnerDao.getItemsForCategory(categoryId);
    }

    // Synchronous fetch for Game Logic (to avoid LiveData observation issues during
    // animations)
    public List<SpinnerItem> getItemsForCategorySync(String categoryId) {
        // Note: calling query on main thread is bad, but for small lists in VM
        // background logic it's okay.
        // Better to wrap this in a customized Callable if we want strict background
        // thread enforcement.
        // But for simplicity/speed in VM, we usually just run this in VM's background
        // scope.
        // However, DAO queries usually block if not suspend.
        // Since we don't have Kotlin coroutines here (Java project), we rely on VM
        // executors.
        return spinnerDao.getItemsForCategorySync(categoryId);
    }

    public void insertCategory(SpinnerCategory category) {
        executorService.execute(() -> spinnerDao.insertCategory(category));
    }

    public void insertItem(SpinnerItem item) {
        executorService.execute(() -> spinnerDao.insertItem(item));
    }

    public void deleteItem(String itemId) {
        executorService.execute(() -> spinnerDao.deleteItem(itemId));
    }

    public void deleteCategory(String categoryId) {
        executorService.execute(() -> spinnerDao.deleteCategoryWithItems(categoryId));
    }

    private void prePopulateData() {
        // Default Categories
        SpinnerCategory food = new SpinnerCategory("Food 🍽️", true);
        SpinnerCategory movie = new SpinnerCategory("Movie 🎬", true);
        SpinnerCategory weekend = new SpinnerCategory("Weekend 🌤️", true);
        SpinnerCategory dateNight = new SpinnerCategory("Date Night 🌙", true);
        SpinnerCategory dessert = new SpinnerCategory("Dessert 🍰", true);
        SpinnerCategory chores = new SpinnerCategory("Chores 🧹", true);

        spinnerDao.insertCategory(food);
        spinnerDao.insertCategory(movie);
        spinnerDao.insertCategory(weekend);
        spinnerDao.insertCategory(dateNight);
        spinnerDao.insertCategory(dessert);
        spinnerDao.insertCategory(chores);

        // Food Items
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Pizza", "🍕", 1));
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Biryani", "🍛", 2));
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Sushi", "🍣", 3));
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Burgers", "🍔", 4));
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Home Cooked", "👨‍🍳", 5));
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Tacos", "🌮", 6));
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Chinese", "🥡", 7));
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Italian", "🍝", 8));
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Street Food", "🌭", 9));
        spinnerDao.insertItem(new SpinnerItem(food.getId(), "Salad", "🥗", 10));

        // Movie Items
        spinnerDao.insertItem(new SpinnerItem(movie.getId(), "Romance", "💕", 1));
        spinnerDao.insertItem(new SpinnerItem(movie.getId(), "Action", "💥", 2));
        spinnerDao.insertItem(new SpinnerItem(movie.getId(), "Comedy", "😂", 3));
        spinnerDao.insertItem(new SpinnerItem(movie.getId(), "Horror", "👻", 4));
        spinnerDao.insertItem(new SpinnerItem(movie.getId(), "Rewatch Fav", "🍿", 5));
        spinnerDao.insertItem(new SpinnerItem(movie.getId(), "Sci-Fi", "👽", 6));
        spinnerDao.insertItem(new SpinnerItem(movie.getId(), "Thriller", "🕵️", 7));
        spinnerDao.insertItem(new SpinnerItem(movie.getId(), "Animation", "🦁", 8));
        spinnerDao.insertItem(new SpinnerItem(movie.getId(), "Fantasy", "🧙‍♂️", 9));

        // Weekend Items
        spinnerDao.insertItem(new SpinnerItem(weekend.getId(), "Long Walk", "🚶", 1));
        spinnerDao.insertItem(new SpinnerItem(weekend.getId(), "Movie Night", "🎬", 2));
        spinnerDao.insertItem(new SpinnerItem(weekend.getId(), "Try New Cafe", "☕", 3));
        spinnerDao.insertItem(new SpinnerItem(weekend.getId(), "Gaming", "🎮", 4));
        spinnerDao.insertItem(new SpinnerItem(weekend.getId(), "Cook Together", "🥘", 5));
        spinnerDao.insertItem(new SpinnerItem(weekend.getId(), "Museum", "🖼️", 6));
        spinnerDao.insertItem(new SpinnerItem(weekend.getId(), "Picnic", "🧺", 7));
        spinnerDao.insertItem(new SpinnerItem(weekend.getId(), "Bowling", "🎳", 8));
        spinnerDao.insertItem(new SpinnerItem(weekend.getId(), "Drive", "🚗", 9));

        // Date Night Items
        spinnerDao.insertItem(new SpinnerItem(dateNight.getId(), "Fancy Dinner", "🍷", 1));
        spinnerDao.insertItem(new SpinnerItem(dateNight.getId(), "Stargazing", "✨", 2));
        spinnerDao.insertItem(new SpinnerItem(dateNight.getId(), "Karaoke", "🎤", 3));
        spinnerDao.insertItem(new SpinnerItem(dateNight.getId(), "Arcade", "🕹️", 4));
        spinnerDao.insertItem(new SpinnerItem(dateNight.getId(), "Massage", "💆", 5));
        spinnerDao.insertItem(new SpinnerItem(dateNight.getId(), "Dance Class", "💃", 6));

        // Dessert Items
        spinnerDao.insertItem(new SpinnerItem(dessert.getId(), "Ice Cream", "🍦", 1));
        spinnerDao.insertItem(new SpinnerItem(dessert.getId(), "Chocolates", "🍫", 2));
        spinnerDao.insertItem(new SpinnerItem(dessert.getId(), "Donuts", "🍩", 3));
        spinnerDao.insertItem(new SpinnerItem(dessert.getId(), "Cheesecake", "🍰", 4));
        spinnerDao.insertItem(new SpinnerItem(dessert.getId(), "Fruit Salad", "🍇", 5));

        // Chore Items (Playful)
        spinnerDao.insertItem(new SpinnerItem(chores.getId(), "Dishes", "🍽️", 1));
        spinnerDao.insertItem(new SpinnerItem(chores.getId(), "Laundry", "🧺", 2));
        spinnerDao.insertItem(new SpinnerItem(chores.getId(), "Vacuum", "🧹", 3));
        spinnerDao.insertItem(new SpinnerItem(chores.getId(), "Groceries", "🛒", 4));
        spinnerDao.insertItem(new SpinnerItem(chores.getId(), "Cooking", "🍳", 5));
    }
}
