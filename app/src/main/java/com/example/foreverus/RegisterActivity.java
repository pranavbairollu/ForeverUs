package com.example.foreverus;

import static com.example.foreverus.FirestoreConstants.COLLECTION_USERS;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.foreverus.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends BaseActivity {

    private static final String TAG = "RegisterActivity";

    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private android.net.Uri selectedImageUri;

    private final androidx.activity.result.ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    com.bumptech.glide.Glide.with(this)
                            .load(uri)
                            .circleCrop()
                            .into((android.widget.ImageView) findViewById(R.id.ivAvatar));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Setup Avatar Click
        findViewById(R.id.ivAvatar).setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        findViewById(R.id.tvAddPhoto).setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        binding.btnRegister.setOnClickListener(v -> validateAndRegisterUser());
        binding.btnLogin.setOnClickListener(v -> startActivity(new Intent(RegisterActivity.this, LoginActivity.class)));

        if (savedInstanceState != null) {
            String uriString = savedInstanceState.getString("selectedImageUri");
            if (uriString != null) {
                selectedImageUri = android.net.Uri.parse(uriString);
                com.bumptech.glide.Glide.with(this)
                        .load(selectedImageUri)
                        .circleCrop()
                        .into((android.widget.ImageView) findViewById(R.id.ivAvatar));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedImageUri != null) {
            outState.putString("selectedImageUri", selectedImageUri.toString());
        }
    }

    private void validateAndRegisterUser() {
        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        if (name.isEmpty()) {
            binding.etName.setError(getString(R.string.name_required));
            binding.etName.requestFocus();
            return;
        }

        if (email.isEmpty()) {
            binding.etEmail.setError(getString(R.string.email_required));
            binding.etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError(getString(R.string.invalid_email));
            binding.etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            binding.etPassword.setError(getString(R.string.password_required));
            binding.etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            binding.etPassword.setError(getString(R.string.password_min_length));
            binding.etPassword.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.setError(getString(R.string.confirm_password_required));
            binding.etConfirmPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            binding.etConfirmPassword.setError(getString(R.string.passwords_do_not_match));
            binding.etConfirmPassword.requestFocus();
            return;
        }

        registerUser(name, email, password);
    }

    private void registerUser(String name, String email, String password) {
        setLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            if (selectedImageUri != null) {
                                uploadAvatarAndSaveUser(user, name, email);
                            } else {
                                saveUserToFirestore(user, name, email, null);
                            }
                        }
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthWeakPasswordException) {
                            Toast.makeText(RegisterActivity.this, R.string.password_too_weak, Toast.LENGTH_SHORT)
                                    .show();
                        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(RegisterActivity.this, R.string.invalid_email_format, Toast.LENGTH_SHORT)
                                    .show();
                        } else if (exception instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(RegisterActivity.this, R.string.email_already_registered, Toast.LENGTH_SHORT)
                                    .show();
                        } else {
                            Toast.makeText(RegisterActivity.this, R.string.authentication_failed, Toast.LENGTH_SHORT)
                                    .show();
                        }
                        setLoading(false);
                    }
                });
    }

    private void initCloudinary() {
        try {
            com.cloudinary.android.MediaManager.get();
        } catch (IllegalStateException e) {
            java.util.Map<String, Object> config = new java.util.HashMap<>();
            config.put("cloud_name", "dsuwyan5m");
            config.put("secure", true);
            com.cloudinary.android.MediaManager.init(getApplication(), config);
        }
    }

    private void uploadAvatarAndSaveUser(FirebaseUser user, String name, String email) {
        initCloudinary();

        com.cloudinary.android.MediaManager.get().upload(selectedImageUri)
                .unsigned("foreverus_memories")
                .option("resource_type", "image")
                .callback(new com.cloudinary.android.callback.UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, java.util.Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        saveUserToFirestore(user, name, email, url);
                    }

                    @Override
                    public void onError(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                        Log.e(TAG, "Avatar upload failed: " + error.getDescription());
                        // Proceed without avatar if upload fails
                        saveUserToFirestore(user, name, email, null);
                    }

                    @Override
                    public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                    }
                })
                .dispatch();
    }

    private void saveUserToFirestore(FirebaseUser firebaseUser, String name, String email, String avatarUrl) {
        String userId = firebaseUser.getUid();
        User user = new User(name, email);
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }

        db.collection(COLLECTION_USERS).document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile created for " + userId);
                    Toast.makeText(RegisterActivity.this, R.string.registration_successful, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(RegisterActivity.this, PairActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error adding document", e);
                    firebaseUser.delete().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "User account deleted after profile creation failure.");
                        }
                    });
                    mAuth.signOut();
                    Toast.makeText(RegisterActivity.this, R.string.error_creating_user_profile, Toast.LENGTH_LONG)
                            .show();
                    setLoading(false);
                });
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.etName.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.etConfirmPassword.setEnabled(!isLoading);
        binding.btnRegister.setEnabled(!isLoading);
        binding.btnLogin.setEnabled(!isLoading);
        findViewById(R.id.ivAvatar).setEnabled(!isLoading);
        findViewById(R.id.tvAddPhoto).setEnabled(!isLoading);
    }
}
