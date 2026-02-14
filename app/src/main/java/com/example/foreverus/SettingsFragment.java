package com.example.foreverus;

import static com.example.foreverus.FirestoreConstants.*;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.example.foreverus.databinding.FragmentSettingsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsFragment extends Fragment implements SpecialDateAdapter.OnSpecialDateDeleteListener {

    private static final String TAG = "SettingsFragment";
    private FragmentSettingsBinding binding;
    private SpecialDateAdapter specialDateAdapter;
    private SpecialDatesViewModel specialDatesViewModel;
    private SettingsViewModel settingsViewModel;
    private String relationshipId;
    private RelationshipRepository relationshipRepository;

    private final ActivityResultLauncher<String> pickAvatarLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    settingsViewModel.uploadAvatar(uri);
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showAddSpecialDateDialog();
                } else {
                    Toast.makeText(requireContext(), "Notifications needed for reminders", Toast.LENGTH_SHORT).show();
                    showAddSpecialDateDialog(); // Let them add anyway, just no notifs
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        relationshipRepository = RelationshipRepository.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        specialDatesViewModel = new ViewModelProvider(this).get(SpecialDatesViewModel.class);
        settingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        setupThemePicker();
        setupRecyclerView();
        observeSpecialDates();
        observeProfileData();

        relationshipRepository.getRelationshipId().observe(getViewLifecycleOwner(), id -> {
            this.relationshipId = id;
            if (relationshipId != null) {
                specialDatesViewModel.loadSpecialDates(relationshipId);
                settingsViewModel.loadProfileData(relationshipId);
                settingsViewModel.loadNickname(relationshipId);

                // loadSettings(); // Removed raw call
                binding.addSpecialDateButton.setEnabled(true);

                binding.unpairButton.setEnabled(true);
            } else {
                Toast.makeText(requireContext(), R.string.error_relationship_not_found, Toast.LENGTH_LONG).show();
                binding.addSpecialDateButton.setEnabled(false);
                binding.unpairButton.setEnabled(false);
            }
        });

        binding.addSpecialDateButton.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v) {
                checkPermissionsAndShowDialog();
            }
        });

        binding.logoutButton.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v) {
                showLogoutConfirmationDialog();
            }
        });

        binding.unpairButton.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v) {
                showUnpairConfirmationDialog();
            }
        });
        binding.testNotificationButton.setOnClickListener(v -> sendTestNotification());

        // Support Buttons (using SafeClickListener for debounce)
        binding.btnGuide.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v) {
                if (isAdded())
                    startActivity(new Intent(requireContext(), GuideActivity.class));
            }
        });

        binding.btnFeedback.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v) {
                if (isAdded())
                    sendFeedback();
            }
        });

        binding.btnAbout.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v) {
                if (isAdded())
                    showAboutDialog();
            }
        });

        // Fix: Enable Back Navigation
        binding.toolbar
                .setNavigationOnClickListener(v -> androidx.navigation.Navigation.findNavController(v).navigateUp());

        // Avatar Click
        binding.ivUserAvatar.setOnClickListener(v -> pickAvatarLauncher.launch("image/*"));

        // Auto-save nickname logic
        binding.coupleNicknameEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveNickname();
            }
        });

        // Dynamic Title on Scroll
        binding.settingsScrollView
                .setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener) (v, scrollX,
                        scrollY, oldScrollX, oldScrollY) -> {
                    boolean shouldShowTitle = scrollY > 100; // Threshold
                    binding.toolbar.setTitle(shouldShowTitle ? "Settings" : " ");
                });
    }

    private void observeProfileData() {
        settingsViewModel.getUserAvatarUrl().observe(getViewLifecycleOwner(), url -> {
            if (binding == null)
                return;
            if (url != null && !url.isEmpty()) {
                Glide.with(this)
                        .load(url)
                        .placeholder(R.drawable.ic_baseline_person_24)
                        .error(R.drawable.ic_baseline_person_24)
                        .circleCrop()
                        .into(binding.ivUserAvatar);
            }
        });

        settingsViewModel.getPartnerAvatarUrl().observe(getViewLifecycleOwner(), url -> {
            if (binding == null)
                return;
            if (url != null && !url.isEmpty()) {
                Glide.with(this)
                        .load(url)
                        .placeholder(R.drawable.ic_baseline_person_24)
                        .error(R.drawable.ic_baseline_person_24)
                        .circleCrop()
                        .into(binding.ivPartnerAvatar);
            }
        });

        settingsViewModel.getDaysTogether().observe(getViewLifecycleOwner(), days -> {
            if (binding == null)
                return;
            binding.tvDaysCount.setText(days);
            // Pulse the heart
            android.view.animation.Animation pulse = android.view.animation.AnimationUtils
                    .loadAnimation(requireContext(), R.anim.heartbeat);
            binding.ivHeartIcon.startAnimation(pulse);
        });

        settingsViewModel.getPartnerNickname().observe(getViewLifecycleOwner(), nickname -> {
            if (nickname != null && binding != null && !binding.coupleNicknameEditText.hasFocus()) {
                binding.coupleNicknameEditText.setText(nickname);
            }
        });

        settingsViewModel.getUploadState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null)
                return; // Crash prevention check
            View progressBar = binding.getRoot().findViewById(R.id.pbAvatarUpload);
            if (progressBar != null) {
                switch (state) {
                    case UPLOADING:
                        progressBar.setVisibility(View.VISIBLE);
                        binding.ivUserAvatar.setEnabled(false);
                        binding.ivUserAvatar.setAlpha(0.5f);
                        break;
                    case SUCCESS:
                        progressBar.setVisibility(View.GONE);
                        binding.ivUserAvatar.setEnabled(true);
                        binding.ivUserAvatar.setAlpha(1.0f);
                        Toast.makeText(requireContext(), "Profile photo updated!", Toast.LENGTH_SHORT).show();
                        break;
                    case ERROR:
                        progressBar.setVisibility(View.GONE);
                        binding.ivUserAvatar.setEnabled(true);
                        binding.ivUserAvatar.setAlpha(1.0f);
                        break;
                    default:
                        progressBar.setVisibility(View.GONE);
                        binding.ivUserAvatar.setEnabled(true);
                        binding.ivUserAvatar.setAlpha(1.0f);
                        break;
                }
            }
        });

        settingsViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && getContext() != null) { // Added context check
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupThemePicker() {
        try {
            // Setup Mode Toggle (Appearance)
            String currentMode = ThemeManager.getThemeMode(requireContext());
            if (ThemeManager.MODE_LIGHT.equals(currentMode)) {
                binding.toggleGroupAppearanceMode.check(R.id.btnModeLight);
            } else if (ThemeManager.MODE_DARK.equals(currentMode)) {
                binding.toggleGroupAppearanceMode.check(R.id.btnModeDark);
            } else {
                binding.toggleGroupAppearanceMode.check(R.id.btnModeSystem);
            }

            binding.toggleGroupAppearanceMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    String newMode = ThemeManager.MODE_SYSTEM;
                    if (checkedId == R.id.btnModeLight) {
                        newMode = ThemeManager.MODE_LIGHT;
                    } else if (checkedId == R.id.btnModeDark) {
                        newMode = ThemeManager.MODE_DARK;
                    }

                    String activeMode = ThemeManager.getThemeMode(requireContext());
                    if (!newMode.equals(activeMode)) {
                        ThemeManager.setThemeMode(requireContext(), newMode);
                        ThemeManager.applyTheme(requireContext());
                        requireActivity().recreate();
                        requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                }
            });

            // Setup Color Style Picker
            if (binding.themePickerContainer.getChildCount() > 0) {
                return;
            }
            String[] themeValues = getResources().getStringArray(R.array.theme_values);
            TypedArray themeColors = getResources().obtainTypedArray(R.array.theme_colors);

            if (themeValues.length != themeColors.length()) {
                Log.e(TAG, "Theme values and colors arrays have different lengths");
                themeColors.recycle();
                return;
            }

            String currentThemeColor = ThemeManager.getThemeColor(requireContext());

            for (int i = 0; i < themeValues.length; i++) {
                View swatchView = LayoutInflater.from(requireContext()).inflate(R.layout.item_theme_swatch,
                        binding.themePickerContainer, false);
                ImageView swatch = swatchView.findViewById(R.id.theme_swatch);
                ImageView checkmark = swatchView.findViewById(R.id.checkmark);
                swatch.setBackgroundColor(themeColors.getColor(i, 0));

                final String themeName = themeValues[i];
                if (currentThemeColor.equals(themeName)) {
                    checkmark.setVisibility(View.VISIBLE);
                }

                swatchView.setOnClickListener(v -> {
                    ThemeManager.setThemeColor(requireContext(), themeName);
                    // Just update preference and recreate to load new style
                    requireActivity().recreate();
                    requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                });
                binding.themePickerContainer.addView(swatchView);
            }
            themeColors.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up theme picker", e);
        }
    }

    private void setupRecyclerView() {
        binding.specialDatesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        specialDateAdapter = new SpecialDateAdapter(this);
        binding.specialDatesRecyclerView.setAdapter(specialDateAdapter);
    }

    private void observeSpecialDates() {
        specialDatesViewModel.getSpecialDates().observe(getViewLifecycleOwner(), specialDates -> {
            if (binding == null)
                return;
            if (specialDates != null) {
                specialDateAdapter.submitList(specialDates);

                // Toggle empty state
                if (specialDates.isEmpty()) {
                    binding.specialDatesRecyclerView.setVisibility(View.GONE);
                    binding.noSpecialDatesTextView.setVisibility(View.VISIBLE);
                } else {
                    binding.specialDatesRecyclerView.setVisibility(View.VISIBLE);
                    binding.noSpecialDatesTextView.setVisibility(View.GONE);
                }
            }
        });
    }

    private void checkPermissionsAndShowDialog() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }

        // Exact Alarm check (Android 12+) using explicit fully qualified class names to
        // avoid import ambiguity
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) requireContext()
                    .getSystemService(android.content.Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                // Ask user to grant exact alarm permission
                new AlertDialog.Builder(requireContext())
                        .setTitle("Permission Needed")
                        .setMessage(
                                "To get exact reminders for special dates, please allow 'Alarms & Reminders' in Settings.")
                        .setPositiveButton("Settings", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        showAddSpecialDateDialog();
    }

    private void showAddSpecialDateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_special_date, null);
        final EditText titleEditText = dialogView.findViewById(R.id.dateTitleEditText);
        final TextView selectedDateTextView = dialogView.findViewById(R.id.selectedDateTextView);
        final Calendar selectedDate = Calendar.getInstance();

        dialogView.findViewById(R.id.pickDateButton).setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(),
                    (datePicker, year, month, day) -> {
                        selectedDate.set(year, month, day);
                        selectedDateTextView.setText(String.format("%d/%d/%d", month + 1, day, year));
                    }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.show();
        });

        builder.setView(dialogView)
                .setPositiveButton(R.string.add, (dialog, id) -> {
                    String title = titleEditText.getText().toString();
                    if (!TextUtils.isEmpty(title) && selectedDateTextView.getText().length() > 0) {
                        SpecialDate newSpecialDate = new SpecialDate(title, selectedDate.getTime());
                        specialDatesViewModel.addSpecialDate(relationshipId, newSpecialDate);
                    } else {
                        Toast.makeText(requireContext(), R.string.please_enter_title_and_date, Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss());
        builder.create().show();
    }

    @Override
    public void onSpecialDateDelete(SpecialDate specialDate) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_special_date_title)
                .setMessage(getString(R.string.delete_special_date_message, specialDate.getTitle()))
                .setPositiveButton(R.string.delete,
                        (dialog, which) -> specialDatesViewModel.deleteSpecialDate(relationshipId, specialDate))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_confirmation_message)
                .setPositiveButton(R.string.logout, (dialog, which) -> logout())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void showUnpairConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.unpair_device_title)
                .setMessage(R.string.unpair_confirmation_message)
                .setPositiveButton(R.string.unpair, (dialog, which) -> unpair())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void unpair() {
        if (!isAdded())
            return;

        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) requireContext()
                .getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        boolean isOnline = cm != null && cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();

        if (!isOnline) {
            Toast.makeText(requireContext(), R.string.internet_required_to_unpair, Toast.LENGTH_LONG).show();
            return;
        }

        String currentUserId = FirebaseAuth.getInstance().getUid();
        if (relationshipId == null || currentUserId == null) {
            Toast.makeText(requireContext(), R.string.cannot_unpair_no_relationship, Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference relationshipRef = db.collection(COLLECTION_RELATIONSHIPS).document(relationshipId);

        relationshipRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!isAdded())
                return;
            if (!documentSnapshot.exists()) {
                Toast.makeText(requireContext(), R.string.already_unpaired, Toast.LENGTH_SHORT).show();
                logout();
                return;
            }

            @SuppressWarnings("unchecked")
            List<String> members = (List<String>) documentSnapshot.get(FIELD_MEMBERS);

            WriteBatch batch = db.batch();
            batch.delete(relationshipRef); // Delete the relationship document

            if (members != null) {
                for (String memberId : members) {
                    if (memberId != null) {
                        DocumentReference userRef = db.collection(COLLECTION_USERS).document(memberId);
                        batch.update(userRef, FIELD_RELATIONSHIP_ID, FieldValue.delete());
                    }
                }
            }

            // BULLETPROOF SAFETY: Ensure current user is explicitly cleaned up
            // even if they weren't in the members list (data corruption case)
            if (members == null || !members.contains(currentUserId)) {
                DocumentReference currentUserRef = db.collection(COLLECTION_USERS).document(currentUserId);
                batch.update(currentUserRef, FIELD_RELATIONSHIP_ID, FieldValue.delete());
            }

            batch.commit().addOnSuccessListener(aVoid -> {
                if (!isAdded())
                    return;
                Toast.makeText(requireContext(), R.string.unpaired_successfully, Toast.LENGTH_SHORT).show();
                logout(); // Logout after successful unpairing
            }).addOnFailureListener(e -> {
                if (!isAdded())
                    return;
                Log.e(TAG, "Error during unpairing commit", e);
                Toast.makeText(requireContext(), R.string.failed_to_finalize_unpairing, Toast.LENGTH_SHORT).show();
            });
        }).addOnFailureListener(e -> {
            if (!isAdded())
                return;
            Log.e(TAG, "Error fetching relationship for unpairing", e);
            Toast.makeText(requireContext(), R.string.error_starting_unpairing_process, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        saveNickname();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void saveNickname() {
        if (binding == null)
            return;
        String nickname = binding.coupleNicknameEditText.getText().toString().trim();

        if (relationshipId != null && !nickname.isEmpty()) {
            // Check if changed to avoid spamming updates/toasts
            String currentSaved = settingsViewModel.getPartnerNickname().getValue();
            if (currentSaved != null && currentSaved.equals(nickname)) {
                return;
            }

            settingsViewModel.updateNickname(relationshipId, nickname, success -> {
                if (!isAdded())
                    return;
                if (success) {
                    Toast.makeText(requireContext(), R.string.nickname_updated, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), R.string.failed_to_update_nickname, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (nickname.isEmpty()) {
            Toast.makeText(requireContext(), R.string.nickname_cannot_be_empty, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendTestNotification() {
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) requireContext()
                .getSystemService(android.content.Context.NOTIFICATION_SERVICE);

        Intent intent = new Intent(requireContext(), DashboardActivity.class);
        intent.putExtra(FIELD_RELATIONSHIP_ID, relationshipId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(requireContext(), 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(
                requireContext(), ForeverUsApplication.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Test Notification")
                .setContentText("This verifies your notifications are working!")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (notificationManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (notificationManager.getNotificationChannel(ForeverUsApplication.CHANNEL_ID) == null) {
                    // Create channel if missing (safety net)
                    android.app.NotificationChannel channel = new android.app.NotificationChannel(
                            ForeverUsApplication.CHANNEL_ID,
                            "Special Dates",
                            android.app.NotificationManager.IMPORTANCE_HIGH);
                    channel.setDescription("Reminders for special dates");
                    notificationManager.createNotificationChannel(channel);
                }
            }
            try {
                notificationManager.notify(999, builder.build());
                Toast.makeText(requireContext(), "Notification Sent! Check Status Bar.", Toast.LENGTH_SHORT).show();
            } catch (SecurityException e) {
                Toast.makeText(requireContext(), "Permission Missing!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendFeedback() {
        // Use a more specific URI to ensure it targets email apps
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:pranavbairollu@gmail.com"));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject));

        // Add device info for better support
        String deviceInfo = "\n\n\n--- Device Info ---\n" +
                "App Version: " + BuildConfig.VERSION_NAME + "\n" +
                "Device: " + android.os.Build.MODEL + " (" + android.os.Build.VERSION.RELEASE + ")";
        intent.putExtra(Intent.EXTRA_TEXT, deviceInfo);

        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "No email client installed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showAboutDialog() {
        if (!isAdded())
            return;
        startActivity(new Intent(requireContext(), AboutActivity.class));
    }
}
