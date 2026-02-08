package com.example.foreverus;

import static com.example.foreverus.FirestoreConstants.COLLECTION_USERS;
import static com.example.foreverus.FirestoreConstants.FIELD_RELATIONSHIP_ID;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.foreverus.databinding.ActivityLoginBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            showLoadingScreen();
            checkUserPairingStatus(currentUser.getUid());
        } else {
            showLoginUi();
        }
    }

    private void validateAndLoginUser() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError(getString(R.string.invalid_email));
            binding.etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            binding.etPassword.setError(getString(R.string.password_required));
            binding.etPassword.requestFocus();
            return;
        }
        loginUser(email, password);
    }

    private void loginUser(String email, String password) {
        setLoading(true);
        // If already signed in (and we are here due to a retry), check status directly
        if (mAuth.getCurrentUser() != null && email.equals(mAuth.getCurrentUser().getEmail())) {
             checkUserPairingStatus(mAuth.getCurrentUser().getUid());
             return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            checkUserPairingStatus(user.getUid());
                        }
                    } else {
                        setLoading(false);
                        handleLoginFailure(task.getException());
                    }
                });
    }

    private void checkUserPairingStatus(String userId) {
        setLoading(true);
        db.collection(COLLECTION_USERS).document(userId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        String relationshipId = snapshot.getString(FIELD_RELATIONSHIP_ID);
                        if (relationshipId != null && !relationshipId.isEmpty()) {
                            navigateToDashboard(relationshipId);
                        } else {
                            navigateToPairing();
                        }
                    } else {
                        navigateToPairing();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check user profile", e);
                    setLoading(false);
                    // Do NOT sign out automatically on network error. 
                    // Show a localized error and let user retry or manually sign out.
                    Toast.makeText(this, "Network error: Unable to verify profile. Please retry.", Toast.LENGTH_LONG).show();
                    
                    // Show UI with Retry button (We can reuse the login button for retry if we change text, 
                    // or just show the form again but keep user logged in? 
                    // Actually, if we show the form, they might try to login again.
                    // Let's just show the login UI so they can try clicking 'Login' again (which calls validateAndLogin -> loginUser).
                    // But wait, loginUser calls signInWithEmail. If already signed in, we should skip that.
                    
                    showLoginUi(); 
                });
    }

    private void navigateToDashboard(String relationshipId) {
        // Cache the relationshipId for offline use
        getSharedPreferences("ForeverUsPrefs", MODE_PRIVATE).edit()
                .putString("cached_relationship_id", relationshipId)
                .apply();

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.putExtra(FIELD_RELATIONSHIP_ID, relationshipId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToPairing() {
        Intent intent = new Intent(this, PairActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void handleLoginFailure(Exception exception) {
        Log.w(TAG, "signInWithEmail:failure", exception);
        if (exception instanceof FirebaseAuthInvalidUserException) {
            Toast.makeText(LoginActivity.this, R.string.no_account_found, Toast.LENGTH_SHORT).show();
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            Toast.makeText(LoginActivity.this, R.string.incorrect_password, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(LoginActivity.this, R.string.authentication_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);
        final EditText emailEditText = dialogView.findViewById(R.id.emailEditText);
        builder.setView(dialogView)
                .setTitle(R.string.reset_password_title)
                .setPositiveButton(R.string.send, (dialog, which) -> {
                    String email = emailEditText.getText().toString().trim();
                    if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        Toast.makeText(this, R.string.invalid_email, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendPasswordResetEmail(email);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.create().show();
    }

    private void sendPasswordResetEmail(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, R.string.password_reset_email_sent, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(LoginActivity.this, R.string.failed_to_send_reset_email, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        if (binding == null) return;
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.btnLogin.setEnabled(!isLoading);
        binding.btnRegister.setEnabled(!isLoading);
    }

    private void showLoadingScreen() {
        if (binding == null) return;
        binding.etEmail.setVisibility(View.GONE);
        binding.etPassword.setVisibility(View.GONE);
        binding.btnLogin.setVisibility(View.GONE);
        binding.btnRegister.setVisibility(View.GONE);
        binding.forgotPasswordTextView.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.VISIBLE);
    }

    private void showLoginUi() {
        if (binding == null) return;
        binding.etEmail.setVisibility(View.VISIBLE);
        binding.etPassword.setVisibility(View.VISIBLE);
        binding.btnLogin.setVisibility(View.VISIBLE);
        binding.btnRegister.setVisibility(View.VISIBLE);
        binding.forgotPasswordTextView.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);
        setLoading(false);
        binding.btnLogin.setOnClickListener(v -> validateAndLoginUser());
        binding.btnRegister.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        binding.forgotPasswordTextView.setOnClickListener(v -> showForgotPasswordDialog());
    }
}
