package com.example.foreverus;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.example.foreverus.databinding.FragmentAdventureBoardBinding;
import com.google.android.material.snackbar.Snackbar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.bumptech.glide.Glide;
import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.Position;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.core.emitter.EmitterConfig;
import nl.dionsegijn.konfetti.core.models.Size;
import nl.dionsegijn.konfetti.core.Rotation;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.UUID;
import com.example.foreverus.AddMemoryActivity;
import com.example.foreverus.FirestoreConstants;

public class AdventureBoardFragment extends Fragment {

    private FragmentAdventureBoardBinding binding;
    private BucketViewModel viewModel;
    private BucketAdapter adapter;
    private RelationshipRepository relationshipRepository;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private android.net.Uri tempImageUri;
    private android.widget.ImageView dialogImageView;
    private android.view.View dialogAddImageBtn;
    private android.view.View dialogRemoveImageBtn;
    private AdventureSuggestionEngine suggestionEngine;
    private android.view.View partnerAvatar; // For presence

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                tempImageUri = uri;
                if (dialogImageView != null) {
                    Glide.with(this).load(uri).into(dialogImageView);
                    dialogImageView.setVisibility(View.VISIBLE);
                }
                if (dialogAddImageBtn != null)
                    dialogAddImageBtn.setVisibility(View.GONE);
                if (dialogRemoveImageBtn != null)
                    dialogRemoveImageBtn.setVisibility(View.VISIBLE);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAdventureBoardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(BucketViewModel.class);
        relationshipRepository = RelationshipRepository.getInstance();
        adapter = new BucketAdapter();
        suggestionEngine = new AdventureSuggestionEngine(requireContext());

        setupRecyclerView();
        setupObservers();

        // Presence Avatar (Dynamically added or found)
        // I'll assume I added it to XML or I can add it programmatically to the AppBar
        // or Toolbar.
        // Let's add it programmatically to the Toolbar for now to save an XML edit step
        // if simpler,
        // BUT XML is better for layout. I'll stick to editing XML.
        partnerAvatar = binding.getRoot().findViewById(R.id.partnerPresenceContainer);

        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        binding.fabAddBucketItem.setOnClickListener(v -> showAddDialog(null));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (relationshipRepository.getRelationshipId().getValue() != null) {
            viewModel.setPresence(relationshipRepository.getRelationshipId().getValue(), true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (relationshipRepository.getRelationshipId().getValue() != null) {
            viewModel.setPresence(relationshipRepository.getRelationshipId().getValue(), false);
        }
    }

    private void setupRecyclerView() {
        binding.bucketRecyclerView
                .setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        binding.bucketRecyclerView.setAdapter(adapter);

        // Drag and Drop
        androidx.recyclerview.widget.ItemTouchHelper itemTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.Callback() {
                    @Override
                    public int getMovementFlags(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView,
                            @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder) {
                        // StaggeredGrid drag flags
                        int dragFlags = androidx.recyclerview.widget.ItemTouchHelper.UP
                                | androidx.recyclerview.widget.ItemTouchHelper.DOWN
                                | androidx.recyclerview.widget.ItemTouchHelper.LEFT
                                | androidx.recyclerview.widget.ItemTouchHelper.RIGHT;
                        return makeMovementFlags(dragFlags, 0); // 0 swipe flags
                    }

                    @Override
                    public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView,
                            @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                            @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                        adapter.onItemMove(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                        recyclerView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); // Light
                                                                                                             // haptic
                                                                                                             // on move
                        return true;
                    }

                    @Override
                    public void onSwiped(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                            int direction) {
                        // No swipe
                    }

                    @Override
                    public void clearView(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView,
                            @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder) {
                        super.clearView(recyclerView, viewHolder);
                        // Drag ended, update priorities
                        // Re-calculate all priorities based on current list order
                        java.util.List<BucketItem> currentList = adapter.getItems();
                        for (int i = 0; i < currentList.size(); i++) {
                            currentList.get(i).setPriorityOrder(i);
                        }
                        if (relationshipRepository.getRelationshipId().getValue() != null) {
                            viewModel.updateItemPriorities(relationshipRepository.getRelationshipId().getValue(),
                                    currentList);
                        }
                        recyclerView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM); // Snap effect
                    }
                });
        itemTouchHelper.attachToRecyclerView(binding.bucketRecyclerView);

        adapter.setOnItemClickListener(new BucketAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BucketItem item) {
                showAddDialog(item);
            }

            @Override
            public void onCompleteClick(BucketItem item, boolean isChecked) {
                binding.getRoot().performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                item.setCompleted(isChecked);
                if (isChecked) {
                    item.setCompletedDate(new Date());

                    // Premium: Record Milestone
                    String relId = relationshipRepository.getRelationshipId().getValue();
                    if (relId != null) {
                        viewModel.recordMilestone(relId, item);
                    }

                    // Premium: Unlockable Delight
                    Snackbar.make(binding.getRoot(), "✨ Memory Unlocked! Capture it?", Snackbar.LENGTH_LONG)
                            .setAction("CREATE", v -> {
                                // ... existing intent logic ...
                                if (relId != null) {
                                    android.content.Intent intent = new android.content.Intent(requireContext(),
                                            AddMemoryActivity.class);
                                    intent.putExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID, relId);
                                    intent.putExtra("prefill_title", item.getTitle());
                                    startActivity(intent);
                                }
                            })
                            .show();

                    triggerConfetti();

                } else {
                    item.setCompletedDate(null);
                }
                viewModel.updateBucketItem(relationshipRepository.getRelationshipId().getValue(), item);
            }
        });

        setupMenu();
    }

    private void setupMenu() {
        binding.toolbar.inflateMenu(R.menu.adventure_board_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_surprise_me) {
                showSurpriseMe();
                return true;
            }
            return false;
        });
    }

    // Helper Factory for Party to avoid complex imports if PartyFactory is not
    // standard
    // I will use standard builder if PartyFactory doesn't exist.
    // Checking recent Konfetti API: It uses Party(emitter, ...) usually.
    // Let's use a simpler Party construction valid for 2.0.4
    /*
     * Party party = new Party(
     * 0,
     * 360,
     * 0f,
     * 30f,
     * 0.9f,
     * Arrays.asList(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
     * Arrays.asList(new Size(12, 5f)),
     * new Position.Relative(0.5, 0.3),
     * new Rotation(),
     * 0,
     * emitter
     * );
     */
    // To be safe I'll use the Builder pattern if available, or just omit if I can't
    // verify.
    // Wait, the safest is to NOT use PartyFactory if I didn't see it.
    // I'll use a simpler call or mock it for now to avoid compilation error,
    // OR BETTER: Use a known working snippet.
    // "new Party(emitter)" might work.
    // Since I can't verify, I'll use a simpler placeholder and fix compilation if
    // it fails.
    // Actually, I'll stick to basic Party logic.
    // Re-writing the onCompleteClick to be safer:
    /*
     * Emitter config = new Emitter(300, TimeUnit.MILLISECONDS).max(100);
     * binding.konfettiView.start(
     * new Party(
     * config,
     * 360,
     * 0f,
     * 30f,
     * 0.9f,
     * Arrays.asList(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
     * java.util.Collections.emptyList(), // sizes
     * new Position.Relative(0.5, 0.3),
     * new nl.dionsegijn.konfetti.core.models.Rotation(),
     * 0L,
     * false
     * )
     * );
     */
    // Too complex to guess. I'll just use a standard 'explode' if I can find one.
    // I'll try with PartyFactory, if it fails I'll fix.

    // UPDATED REPLACEMENT FOR THIS CHUNK:
    private void triggerConfetti() {
        EmitterConfig emitterConfig = new Emitter(100L, TimeUnit.MILLISECONDS).max(100);
        binding.konfettiView.start(
                new Party(
                        0,
                        360,
                        0f,
                        30f,
                        0.9f,
                        Arrays.asList(new Size(12, 5f, 2f)),
                        Arrays.asList(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                        Arrays.asList(nl.dionsegijn.konfetti.core.models.Shape.Square.INSTANCE,
                                nl.dionsegijn.konfetti.core.models.Shape.Circle.INSTANCE),
                        0L,
                        true,
                        new Position.Relative(0.5, 0.3),
                        0,
                        new Rotation(),
                        emitterConfig));
    }

    private void setupObservers() {
        relationshipRepository.getRelationshipId().observe(getViewLifecycleOwner(), id -> {
            if (id != null) {
                viewModel.loadBucketItems(id);

                // Presence
                viewModel.getPartnerPresence(id).observe(getViewLifecycleOwner(), isOnline -> {
                    if (isOnline) {
                        if (partnerAvatar.getVisibility() != View.VISIBLE) {
                            partnerAvatar.setAlpha(0f);
                            partnerAvatar.setVisibility(View.VISIBLE);
                            partnerAvatar.animate().alpha(1f).setDuration(300).start();
                        }
                    } else {
                        if (partnerAvatar.getVisibility() == View.VISIBLE) {
                            partnerAvatar.animate().alpha(0f).setDuration(300)
                                    .withEndAction(() -> partnerAvatar.setVisibility(View.GONE)).start();
                        }
                    }
                });
            }
        });

        viewModel.getBucketItems().observe(getViewLifecycleOwner(), items -> {
            if (items == null || items.isEmpty()) {
                binding.emptyView.getRoot().setVisibility(View.VISIBLE);
                binding.bucketRecyclerView.setVisibility(View.GONE);

                binding.emptyView.txtEmptyTitle.setText("Your Adventure Board is empty");
                binding.emptyView.txtEmptyMessage.setText("Add your first dream goal!");
                binding.emptyView.imgEmptyState.setVisibility(View.VISIBLE);
                binding.emptyView.imgEmptyState.setImageResource(R.drawable.ic_flight);

                binding.txtProgress.setText("0 / 0 Adventures");
            } else {
                binding.emptyView.getRoot().setVisibility(View.GONE);
                binding.bucketRecyclerView.setVisibility(View.VISIBLE);
                // Sort by priority if needed, but DAO usually handles it.
                // However, if we just dragged, local list might be different?
                // LiveData update should re-set items.
                adapter.setItems(items);

                // Progress Logic
                int total = items.size();
                int completed = 0;
                for (BucketItem i : items) {
                    if (i.isCompleted())
                        completed++;
                }
                binding.txtProgress.setText(completed + " / " + total + " Adventures");
            }
        });
    }

    private void showSurpriseMe() {
        AdventureSuggestionEngine.Suggestion suggestion = suggestionEngine.getSmartSuggestion(adapter.getItems());

        BucketItem template = new BucketItem();
        if (suggestion != null) {
            template.setTitle(suggestion.text);
            template.setType(suggestion.category);
            template.setEstimatedCost("FREE".equals(suggestion.costType) ? 0.0 : 50.0); // Simple default
        } else {
            template.setTitle("Go on a mystery drive");
            template.setType("TRAVEL");
        }

        // ID is null, so showAddDialog treats as new
        showAddDialog(template);
    }

    // Temp variable for DatePicker
    private java.util.Calendar tempTargetDate;

    private void showAddDialog(@Nullable BucketItem itemToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_add_bucket_item, null);

        EditText inputTitle = view.findViewById(R.id.editTitle);
        EditText inputDesc = view.findViewById(R.id.editDescription);
        // New Inputs
        com.google.android.material.chip.ChipGroup chipGroup = view.findViewById(R.id.chipGroupType);
        com.google.android.material.textfield.TextInputEditText inputDate = view.findViewById(R.id.inputTargetDate);
        com.google.android.material.textfield.TextInputEditText inputCost = view.findViewById(R.id.inputEstimatedCost);
        // Currency Toggle
        com.google.android.material.button.MaterialButtonToggleGroup toggleCurrency = view
                .findViewById(R.id.toggleCurrency);

        // Populate Chips
        String[] types = getResources().getStringArray(R.array.bucket_types_array);
        for (String type : types) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
            chip.setText(type);
            chip.setCheckable(true);
            chip.setId(android.view.View.generateViewId()); // CRITICAL: Generate ID for selection
            chipGroup.addView(chip);
        }

        // Date Picker Logic
        tempTargetDate = java.util.Calendar.getInstance();
        inputDate.setOnClickListener(v -> {
            new android.app.DatePickerDialog(requireContext(), (view1, year, month, dayOfMonth) -> {
                tempTargetDate.set(year, month, dayOfMonth);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy",
                        java.util.Locale.getDefault());
                inputDate.setText(sdf.format(tempTargetDate.getTime()));
            },
                    // Use current selection if available
                    (itemToEdit != null && itemToEdit.getTargetDate() != null)
                            ? getCalendarFromDate(itemToEdit.getTargetDate()).get(java.util.Calendar.YEAR)
                            : tempTargetDate.get(java.util.Calendar.YEAR),
                    (itemToEdit != null && itemToEdit.getTargetDate() != null)
                            ? getCalendarFromDate(itemToEdit.getTargetDate()).get(java.util.Calendar.MONTH)
                            : tempTargetDate.get(java.util.Calendar.MONTH),
                    (itemToEdit != null && itemToEdit.getTargetDate() != null)
                            ? getCalendarFromDate(itemToEdit.getTargetDate()).get(java.util.Calendar.DAY_OF_MONTH)
                            : tempTargetDate.get(java.util.Calendar.DAY_OF_MONTH))
                    .show();
        });

        // Add Clear functionality for Date
        // We only need to ensure the wrapper action works.
        com.google.android.material.textfield.TextInputLayout dateLayout = view.findViewById(R.id.layoutDateWrapper);
        if (dateLayout != null) {
            dateLayout.setEndIconOnClickListener(v -> {
                inputDate.setText("");
                tempTargetDate = java.util.Calendar.getInstance(); // Reset temp
            });
        }

        tempImageUri = null; // Reset

        // Find Views in Dialog
        dialogImageView = view.findViewById(R.id.imgAdventure);
        dialogAddImageBtn = view.findViewById(R.id.viewAddImageClickArea);
        View frameLayout = view.findViewById(R.id.imgAdventure).getParent() instanceof View
                ? (View) view.findViewById(R.id.imgAdventure).getParent()
                : null;
        dialogRemoveImageBtn = view.findViewById(R.id.btnRemoveImage);

        if (frameLayout != null) {
            frameLayout.setOnClickListener(v -> pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build()));
        }

        dialogRemoveImageBtn.setOnClickListener(v -> {
            tempImageUri = null;
            dialogImageView.setImageDrawable(null);
            dialogImageView.setVisibility(View.GONE);
            if (itemToEdit != null)
                itemToEdit.setImageUrl(null);
            ViewGroup parent = (ViewGroup) dialogImageView.getParent();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof android.widget.LinearLayout) {
                    child.setVisibility(View.VISIBLE);
                }
            }
            dialogImageView.setVisibility(View.INVISIBLE);
            dialogRemoveImageBtn.setVisibility(View.GONE);
        });

        boolean isRealEdit = itemToEdit != null && itemToEdit.getId() != null; // Check ID to distinguish template

        if (itemToEdit != null) {
            inputTitle.setText(itemToEdit.getTitle());
            inputDesc.setText(itemToEdit.getDescription());

            // Set Chip selection
            if (itemToEdit.getType() != null) {
                for (int i = 0; i < chipGroup.getChildCount(); i++) {
                    com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) chipGroup
                            .getChildAt(i);
                    if (chip.getText().toString().equalsIgnoreCase(itemToEdit.getType())) {
                        chip.setChecked(true);
                        break;
                    }
                }
            }

            // Set Date
            if (itemToEdit.getTargetDate() != null) {
                tempTargetDate.setTime(itemToEdit.getTargetDate());
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy",
                        java.util.Locale.getDefault());
                inputDate.setText(sdf.format(itemToEdit.getTargetDate()));
            } else {
                inputDate.setText("");
            }

            // Set Cost
            if (itemToEdit.getEstimatedCost() != null) {
                inputCost.setText(String.valueOf(itemToEdit.getEstimatedCost().intValue()));
            }

            // Set Currency
            if (itemToEdit.getCurrency() != null && itemToEdit.getCurrency().equals("USD")) {
                toggleCurrency.check(R.id.btnDollar);
            } else {
                toggleCurrency.check(R.id.btnRupee);
            }

            // Load Image
            if (itemToEdit.getImageUrl() != null) {
                Glide.with(this).load(itemToEdit.getImageUrl()).into(dialogImageView);
                dialogImageView.setVisibility(View.VISIBLE);
                dialogRemoveImageBtn.setVisibility(View.VISIBLE);
                ViewGroup parent = (ViewGroup) dialogImageView.getParent();
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (child instanceof android.widget.LinearLayout) {
                        child.setVisibility(View.GONE);
                    }
                }
            }
        }

        builder.setView(view)
                .setTitle(isRealEdit ? "Edit Adventure" : "Add New Adventure") // Use isRealEdit flag
                .setPositiveButton(isRealEdit ? "Save" : "Add", (dialog, which) -> {
                    String title = inputTitle.getText().toString().trim();
                    String desc = inputDesc.getText().toString().trim();

                    // Get Type
                    int chipId = chipGroup.getCheckedChipId();
                    String type = "GOAL"; // Default
                    if (chipId != View.NO_ID) {
                        com.google.android.material.chip.Chip chip = view.findViewById(chipId);
                        type = chip.getText().toString().toUpperCase();
                    }

                    // Get Date
                    Date tDate = null;
                    if (!inputDate.getText().toString().isEmpty()) {
                        tDate = tempTargetDate.getTime();
                    }

                    // Get Cost
                    Double eCost = null;
                    if (!inputCost.getText().toString().isEmpty()) {
                        try {
                            eCost = Double.parseDouble(inputCost.getText().toString());
                        } catch (NumberFormatException e) {
                            eCost = 0.0;
                        }
                    }

                    // Get Currency
                    String currencyCode = (toggleCurrency.getCheckedButtonId() == R.id.btnDollar) ? "USD" : "INR";

                    if (!title.isEmpty()) {
                        if (!isRealEdit) {
                            BucketItem newItem = new BucketItem();
                            newItem.setId(UUID.randomUUID().toString());
                            newItem.setTitle(title);
                            newItem.setDescription(desc);
                            newItem.setType(type);
                            newItem.setCompleted(false);
                            newItem.setTimestamp(new Date());
                            // Extensions
                            newItem.setTargetDate(tDate);
                            newItem.setEstimatedCost(eCost);
                            newItem.setCurrency(currencyCode);
                            // Priority: Add to end (size of list)
                            newItem.setPriorityOrder(adapter.getItemCount());

                            viewModel.addBucketItem(relationshipRepository.getRelationshipId().getValue(), newItem,
                                    tempImageUri);
                        } else {
                            itemToEdit.setTitle(title);
                            itemToEdit.setDescription(desc);
                            itemToEdit.setType(type);
                            itemToEdit.setTargetDate(tDate);
                            itemToEdit.setEstimatedCost(eCost);
                            itemToEdit.setCurrency(currencyCode);

                            viewModel.updateBucketItem(relationshipRepository.getRelationshipId().getValue(),
                                    itemToEdit, tempImageUri);
                        }
                    }
                })
                .setNegativeButton("Cancel", null);

        if (isRealEdit) {
            builder.setNeutralButton("Delete", (dialog, which) -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Adventure?")
                        .setMessage("Are you sure you want to delete this goal?")
                        .setPositiveButton("Delete", (d, w) -> {
                            viewModel.deleteBucketItem(relationshipRepository.getRelationshipId().getValue(),
                                    itemToEdit);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        builder.show();
    }

    private java.util.Calendar getCalendarFromDate(Date date) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        return cal;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
