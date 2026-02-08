package com.example.foreverus;

import static com.example.foreverus.FirestoreConstants.COLLECTION_USERS;
import static com.example.foreverus.FirestoreConstants.FIELD_RELATIONSHIP_ID;
import android.util.Log;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import android.widget.Toast;

public class SplashActivity extends BaseActivity {
    private static final String TAG = SplashActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The splash screen theme is applied here
        SplashScreen.installSplashScreen(this);
        // We then immediately set the real theme
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            checkIfPaired(currentUser.getUid());
        } else {
            navigateToLogin();
        }
    }

    private void checkIfPaired(String uid) {
        FirebaseFirestore.getInstance().collection(COLLECTION_USERS).document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String relationshipId = documentSnapshot.getString(FIELD_RELATIONSHIP_ID);
                    if (documentSnapshot.exists() && relationshipId != null && !relationshipId.isEmpty()) {
                        // Cache the relationshipId for offline use
                        getSharedPreferences("ForeverUsPrefs", MODE_PRIVATE).edit()
                                .putString("cached_relationship_id", relationshipId)
                                .apply();

                        Intent intent = new Intent(SplashActivity.this, DashboardActivity.class);
                        intent.putExtra(FIELD_RELATIONSHIP_ID, relationshipId);
                        startActivity(intent);
                    } else {
                        startActivity(new Intent(SplashActivity.this, PairActivity.class));
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to fetch profile (likely offline). Checking cache...");
                    
                    // Try to recover from cache
                    String cachedId = getSharedPreferences("ForeverUsPrefs", MODE_PRIVATE)
                            .getString("cached_relationship_id", null);

                    if (cachedId != null) {
                        Log.d(TAG, "Found cached relationshipId: " + cachedId);
                        Intent intent = new Intent(SplashActivity.this, DashboardActivity.class);
                        intent.putExtra(FIELD_RELATIONSHIP_ID, cachedId);
                        startActivity(intent);
                        finish();
                    } else {
                        // Truly no local data and no internet.
                        Log.e(TAG, "No cache found. Cannot start offline.");
                        Toast.makeText(this, "Offline and no local data. Please connect to internet.", Toast.LENGTH_LONG).show();
                        navigateToLogin(); 
                    }
                });
    }

    private void navigateToLogin() {
        startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        finish();
    }
}
