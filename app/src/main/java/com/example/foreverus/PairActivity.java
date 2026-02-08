package com.example.foreverus;

import static com.example.foreverus.FirestoreConstants.*;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.foreverus.databinding.ActivityPairBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class PairActivity extends BaseActivity {

    private static final String TAG = "PairActivity";
    private ActivityPairBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String currentUserPairingCode;
    private ListenerRegistration userListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeManager.getThemeResId(this)); // Ensure ThemeManager exists in your project
        super.onCreate(savedInstanceState);
        binding = ActivityPairBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            // Safety check: if user is somehow null, go back to login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        binding.btnCopyCode.setOnClickListener(v -> copyCodeToClipboard());
        binding.btnPair.setOnClickListener(v -> handlePairingAttempt());

        generateAndShowPairCode();
        setupOnBackPressed();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (currentUser != null) {
            listenForPairingChanges();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userListener != null) {
            userListener.remove();
        }
    }

    private void setupOnBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new AlertDialog.Builder(PairActivity.this)
                        .setTitle(R.string.exit_application_title)
                        .setMessage(R.string.exit_application_message)
                        .setPositiveButton(R.string.exit_button_text, (dialog, which) -> {
                            finishAffinity();
                        })
                        .setNegativeButton(R.string.cancel_button_text, null)
                        .show();
            }
        });
    }

    // --- PAIRING CODE GENERATION ---

    private void generateAndShowPairCode() {
        setUiLoading(true);
        binding.tvPairCode.setText(R.string.generating_code);

        // Retry logic or ensuring unique code could go here,
        // but for simplicity, we assume random(6) is sufficient.
        String newPairingCode = generateRandomCode(6);

        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put(FIELD_PAIRING_CODE, newPairingCode);
        // Ensure we don't accidentally keep an old relationship ID if we are regenerating
        userUpdate.put(FIELD_RELATIONSHIP_ID, FieldValue.delete());

        db.collection(COLLECTION_USERS).document(currentUser.getUid())
                .set(userUpdate, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    currentUserPairingCode = newPairingCode;
                    binding.tvPairCode.setText(currentUserPairingCode);
                    setUiLoading(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to generate pairing code", e);
                    binding.tvPairCode.setText(R.string.error_generating_code);
                    handleFirestoreError(e, getString(R.string.error_generating_code));
                    setUiLoading(false);
                });
    }

    // --- PAIRING LOGIC ---

    private void handlePairingAttempt() {
        String partnerPairCode = binding.etPairCode.getText().toString().trim().toUpperCase();

        // 1. Local Validation
        if (partnerPairCode.isEmpty()) {
            binding.etPairCode.setError(getString(R.string.enter_a_code));
            return;
        }
        if (partnerPairCode.length() != 6) {
            binding.etPairCode.setError(getString(R.string.code_must_be_6_characters));
            return;
        }
        if (currentUserPairingCode != null && partnerPairCode.equals(currentUserPairingCode)) {
            Toast.makeText(this, R.string.cannot_pair_with_yourself, Toast.LENGTH_SHORT).show();
            return;
        }

        setUiLoading(true);

        // 2. Query to find the partner
        db.collection(COLLECTION_USERS)
                .whereEqualTo(FIELD_PAIRING_CODE, partnerPairCode)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        setUiLoading(false);
                        Toast.makeText(this, R.string.invalid_code, Toast.LENGTH_LONG).show();
                        return;
                    }
                    // Partner found, get their ID
                    String partnerId = querySnapshot.getDocuments().get(0).getId();
                    performPairingTransaction(currentUser.getUid(), partnerId, partnerPairCode);
                })
                .addOnFailureListener(e -> {
                    setUiLoading(false);
                    handleFirestoreError(e, getString(R.string.failed_to_search_for_partner));
                });
    }

    private void performPairingTransaction(String currentUserId, String partnerId, String partnerPairCode) {
        DocumentReference currentUserRef = db.collection(COLLECTION_USERS).document(currentUserId);
        DocumentReference partnerUserRef = db.collection(COLLECTION_USERS).document(partnerId);
        DocumentReference newRelationshipRef = db.collection(COLLECTION_RELATIONSHIPS).document();

        db.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot currentUserSnapshot = transaction.get(currentUserRef);
            DocumentSnapshot partnerUserSnapshot = transaction.get(partnerUserRef);

            // Validation 1: Documents must exist
            if (!currentUserSnapshot.exists()) {
                throw new FirebaseFirestoreException(getString(R.string.your_user_profile_was_not_found),
                        FirebaseFirestoreException.Code.ABORTED);
            }
            if (!partnerUserSnapshot.exists()) {
                throw new FirebaseFirestoreException(getString(R.string.partner_profile_disappeared),
                        FirebaseFirestoreException.Code.ABORTED);
            }

            // Validation 2: Already paired?
            if (currentUserSnapshot.get(FIELD_RELATIONSHIP_ID) != null) {
                throw new FirebaseFirestoreException(getString(R.string.you_are_already_in_a_relationship),
                        FirebaseFirestoreException.Code.ABORTED);
            }
            if (partnerUserSnapshot.get(FIELD_RELATIONSHIP_ID) != null) {
                throw new FirebaseFirestoreException(getString(R.string.partner_is_already_paired),
                        FirebaseFirestoreException.Code.ABORTED);
            }

            // Validation 3: Verify Code integrity (Race condition check)
            // The partner might have regenerated their code just now
            String partnerCurrentCode = partnerUserSnapshot.getString(FIELD_PAIRING_CODE);
            if (partnerCurrentCode == null || !partnerCurrentCode.equals(partnerPairCode)) {
                throw new FirebaseFirestoreException(getString(R.string.partner_code_changed),
                        FirebaseFirestoreException.Code.ABORTED);
            }

            // --- WRITE OPERATIONS ---

            // 1. Create Relationship
            Map<String, Object> relationshipData = new HashMap<>();
            relationshipData.put(FIELD_MEMBERS, Arrays.asList(currentUserId, partnerId));
            relationshipData.put(FIELD_CREATED_AT, FieldValue.serverTimestamp());
            transaction.set(newRelationshipRef, relationshipData);

            // 2. Update Both Users
            transaction.update(currentUserRef, FIELD_RELATIONSHIP_ID, newRelationshipRef.getId());
            transaction.update(partnerUserRef, FIELD_RELATIONSHIP_ID, newRelationshipRef.getId());

            // 3. Remove Pairing Codes
            transaction.update(currentUserRef, FIELD_PAIRING_CODE, FieldValue.delete());
            transaction.update(partnerUserRef, FIELD_PAIRING_CODE, FieldValue.delete());

            return newRelationshipRef.getId();

        }).addOnSuccessListener(relationshipId -> {
            setUiLoading(false);
            navigateToDashboard(relationshipId, false);

        }).addOnFailureListener(e -> {
            setUiLoading(false);
            // This handles the specific exceptions thrown above
            handleFirestoreError(e, getString(R.string.pairing_transaction_failed));
        });
    }

    // --- HELPER METHODS ---

    /**
     * Centralized error handling for Firestore exceptions.
     * Extracts user-friendly messages from error codes.
     */
    private void handleFirestoreError(Exception e, String defaultMsg) {
        String message = defaultMsg;

        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException ffe = (FirebaseFirestoreException) e;

            // Log the technical error for debugging
            Log.w(TAG, "Firestore Error: " + ffe.getCode(), e);

            switch (ffe.getCode()) {
                case UNAVAILABLE:
                    message = getString(R.string.network_unavailable);
                    break;
                case PERMISSION_DENIED:
                    message = getString(R.string.permission_denied);
                    break;
                case ABORTED:
                    // If we threw a specific message in the Transaction, use it.
                    if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                        message = e.getMessage();
                    } else {
                        message = getString(R.string.pairing_cancelled_due_to_conflict);
                    }
                    break;
                default:
                    message = defaultMsg + ": " + e.getLocalizedMessage();
                    break;
            }
        } else {
            Log.e(TAG, "Unknown Error", e);
            message = defaultMsg + ": " + getString(R.string.unknown_error_occurred);
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void listenForPairingChanges() {
        if (userListener != null) userListener.remove();

        userListener = db.collection(COLLECTION_USERS).document(currentUser.getUid())
                .addSnapshotListener(this, (snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        String relationshipId = snapshot.getString(FIELD_RELATIONSHIP_ID);
                        // Only navigate if we actually have an ID and the activity is valid
                        if (relationshipId != null && !relationshipId.isEmpty() && !isFinishing()) {
                            navigateToDashboard(relationshipId, true);
                        }
                    }
                });
    }

    private void navigateToDashboard(String relationshipId, boolean wasPairedByPartner) {
        if (isFinishing()) return;

        // Cache the relationshipId for offline use
        getSharedPreferences("ForeverUsPrefs", MODE_PRIVATE).edit()
                .putString("cached_relationship_id", relationshipId)
                .apply();

        String msg = wasPairedByPartner ? getString(R.string.youve_been_paired) : getString(R.string.successfully_paired);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(PairActivity.this, DashboardActivity.class);
        intent.putExtra(FIELD_RELATIONSHIP_ID, relationshipId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void copyCodeToClipboard() {
        String code = binding.tvPairCode.getText().toString();
        if (!code.isEmpty() && !code.equals(getString(R.string.generating_code)) && !code.equals(getString(R.string.error))) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Pairing Code", code);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void setUiLoading(boolean isLoading) {
        if (binding == null) return;
        binding.pairingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnPair.setEnabled(!isLoading);
        binding.etPairCode.setEnabled(!isLoading);
        binding.btnCopyCode.setEnabled(!isLoading);
    }
}
