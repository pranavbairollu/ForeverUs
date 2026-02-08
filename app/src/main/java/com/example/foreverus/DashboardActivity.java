package com.example.foreverus;

import static com.example.foreverus.FirestoreConstants.FIELD_RELATIONSHIP_ID;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.google.android.material.navigation.NavigationBarView;
import java.util.concurrent.TimeUnit;

public class DashboardActivity extends BaseActivity {

    private static final String TAG = "DashboardActivity";
    private static final String DAILY_REMINDER_WORK_NAME = "daily_reminder_work";
    private RelationshipRepository relationshipRepository;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted.");
                    relationshipRepository.getRelationshipId().observe(this, this::scheduleDailyReminderWorker);
                } else {
                    Log.d(TAG, "Notification permission denied.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            setTheme(ThemeManager.getThemeResId(this));
        } catch (Exception e) {
            Log.e(TAG, "Failed to set theme", e);
            setTheme(R.style.Theme_ForeverUs); // Fallback
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        relationshipRepository = RelationshipRepository.getInstance();

        String retrievedRelationshipId = null;
        if (savedInstanceState != null) {
            retrievedRelationshipId = savedInstanceState.getString(FIELD_RELATIONSHIP_ID);
        } else {
            retrievedRelationshipId = getIntent().getStringExtra(FIELD_RELATIONSHIP_ID);
        }

        // Fallback to SharedPrefs if Intent/Bundle failed
        if (retrievedRelationshipId == null) {
             retrievedRelationshipId = getSharedPreferences("ForeverUsPrefs", MODE_PRIVATE)
                    .getString("cached_relationship_id", null);
        }

        if (retrievedRelationshipId == null) {
            Log.e(TAG, "Relationship ID is null (Intent & Prefs). Showing Error Dialog.");
            new AlertDialog.Builder(this)
                .setTitle("Initialization Failed")
                .setMessage("Critical Error: Core Data Missing.\nThe app cannot find your relationship ID.\n\nPlease logout and login again.")
                .setCancelable(false)
                .setPositiveButton("Logout", (dialog, which) -> {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Retry", (dialog, which) -> {
                     Intent intent = new Intent(this, SplashActivity.class);
                     intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                     startActivity(intent);
                     finish();
                })
                .show();
            // Do NOT finish() here. Let the dialog block. 
            // We return to stop further execution of onCreate, leaving the Activity in a 'blank' state with the Dialog.
            return; 
        }
        relationshipRepository.setRelationshipId(retrievedRelationshipId);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        NavigationBarView bottomNav = findViewById(R.id.navigation_bar);
        NavigationUI.setupWithNavController(bottomNav, navController);

        askNotificationPermission();

        // Inject Smooth Transitions
        navHostFragment.getChildFragmentManager().registerFragmentLifecycleCallbacks(new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentCreated(@androidx.annotation.NonNull androidx.fragment.app.FragmentManager fm, @androidx.annotation.NonNull androidx.fragment.app.Fragment f, Bundle savedInstanceState) {
                f.setEnterTransition(new com.google.android.material.transition.MaterialFadeThrough());
                f.setExitTransition(new com.google.android.material.transition.MaterialFadeThrough());
            }
        }, true);

        // Fix: "Settings" is conceptually under "More".
        // Instead of hiding the nav (which feels disjointed), we keep it visible 
        // and force the "More" tab to be highlighted.
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.settings) {
                bottomNav.setVisibility(android.view.View.VISIBLE);
                bottomNav.getMenu().findItem(R.id.menu_more).setChecked(true);
            } else {
                bottomNav.setVisibility(android.view.View.VISIBLE);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(FIELD_RELATIONSHIP_ID, relationshipRepository.getRelationshipId().getValue());
    }

    public String getRelationshipId() {
        return relationshipRepository.getRelationshipId().getValue();
    }

    private void scheduleDailyReminderWorker(String relationshipId) {
        if (relationshipId != null && !relationshipId.isEmpty()) {
            Data inputData = new Data.Builder()
                    .putString(FIELD_RELATIONSHIP_ID, relationshipId)
                    .build();

            PeriodicWorkRequest dailyReminderWork = new PeriodicWorkRequest.Builder(DailyReminderWorker.class, 1, TimeUnit.DAYS)
                    .setInputData(inputData)
                    .build();
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(DAILY_REMINDER_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, dailyReminderWork);
            Log.d(TAG, "Daily reminder worker scheduled for relationship: " + relationshipId);
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                relationshipRepository.getRelationshipId().observe(this, this::scheduleDailyReminderWorker);
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.permission_needed)
                        .setMessage(R.string.notification_permission_rationale)
                        .setPositiveButton(R.string.ok, (dialog, which) -> {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                        })
                        .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // For older versions, schedule the worker directly
            relationshipRepository.getRelationshipId().observe(this, this::scheduleDailyReminderWorker);
        }
    }
}
