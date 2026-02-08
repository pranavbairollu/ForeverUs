package com.example.foreverus;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ManageSpinnerListsActivity extends BaseActivity {

    private SpinnerViewModel viewModel;
    private SpinnerItemAdapter adapter;
    private com.google.android.material.chip.ChipGroup chipGroupCategories;
    private com.google.android.material.textfield.TextInputLayout inputLayoutEmoji, inputLayoutContent;
    private com.google.android.material.textfield.TextInputEditText etEmoji, etText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_spinner);

        viewModel = new ViewModelProvider(this).get(SpinnerViewModel.class);

        initViews();
        observeViewModel();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        chipGroupCategories = findViewById(R.id.chipGroupCategories);

        inputLayoutEmoji = findViewById(R.id.inputLayoutEmoji);
        inputLayoutContent = findViewById(R.id.inputLayoutContent);
        etEmoji = findViewById(R.id.etEmoji);
        etText = findViewById(R.id.etText);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewItems);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SpinnerItemAdapter(item -> {
            viewModel.deleteItem(item.getId());
        });
        recyclerView.setAdapter(adapter);

        com.google.android.material.button.MaterialButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> addItem());
    }

    private void addItem() {
        inputLayoutContent.setError(null);

        String text = etText.getText().toString().trim();
        String emoji = etEmoji.getText().toString().trim();

        if (TextUtils.isEmpty(text)) {
            inputLayoutContent.setError("Required");
            return;
        }

        if (TextUtils.isEmpty(emoji)) {
            // Default emoji if empty? Or required?
            // Let's use a generic generic star if empty
            emoji = "⭐";
        }

        viewModel.addItem(text, emoji);

        etText.setText("");
        etEmoji.setText("");
        // Keep focus
        etText.requestFocus();
    }

    private void observeViewModel() {
        // Items (Single Observer Source)
        viewModel.currentCategoryItems.observe(this, items -> {
            adapter.submitList(items);
        });

        // Categories
        viewModel.getAllCategories().observe(this, categories -> {
            if (categories == null)
                return;

            int checkedId = chipGroupCategories.getCheckedChipId();
            String selectedId = null;
            if (checkedId != View.NO_ID) {
                Chip c = chipGroupCategories.findViewById(checkedId);
                if (c != null)
                    selectedId = (String) c.getTag();
            }

            chipGroupCategories.removeAllViews();

            boolean isSelectionValid = false;

            // First pass: Check if previous selection still exists
            if (selectedId != null) {
                for (SpinnerCategory cat : categories) {
                    if (cat.getId().equals(selectedId)) {
                        isSelectionValid = true;
                        break;
                    }
                }
            }
            // If selection is invalid (deleted) or null, default to the first one
            if (!isSelectionValid && !categories.isEmpty()) {
                selectedId = categories.get(0).getId();
            }

            for (SpinnerCategory cat : categories) {
                Chip chip = new Chip(this);
                chip.setText(cat.getName());
                chip.setTag(cat.getId());
                chip.setCheckable(true);
                chip.setChipBackgroundColorResource(R.color.bg_chip_state_list);

                chip.setOnClickListener(v -> {
                    viewModel.setCurrentCategory(cat);
                });

                chipGroupCategories.addView(chip);

                // Auto-select match
                if (selectedId != null && selectedId.equals(cat.getId())) {
                    chip.setChecked(true);
                    viewModel.setCurrentCategory(cat);
                }

                // Delete logic for custom categories
                if (!cat.isDefault()) {
                    chip.setCloseIconVisible(true);
                    chip.setOnCloseIconClickListener(v -> showDeleteCategoryConfirmation(cat));
                }
            }

            // ADD "Custom" CHIP logic at the end
            Chip addChip = new Chip(this);
            addChip.setText("+ New");
            addChip.setChipBackgroundColorResource(R.color.bg_chip_state_list);
            addChip.setOnClickListener(v -> showAddCategoryDialog());
            chipGroupCategories.addView(addChip);
        });
    }

    private void showAddCategoryDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                this);
        builder.setTitle("New Category");
        builder.setIcon(R.drawable.ic_baseline_add_24); // Assuming this icon exists or similar, if not I remove it.
                                                        // Let's safe-check or omit for now.

        // Inflate custom view
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_spinner_category, null);
        builder.setView(dialogView);

        final com.google.android.material.textfield.TextInputEditText input = dialogView
                .findViewById(R.id.inputCategoryName);

        // Show keyboard automatically focus
        input.requestFocus();

        builder.setPositiveButton("Create", null); // Set null here
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Override listener to prevent auto-dismissal
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = "";
            if (input.getText() != null) {
                name = input.getText().toString().trim();
            }

            if (TextUtils.isEmpty(name)) {
                input.setError("Required");
                return;
            }
            viewModel.addCategory(name);
            dialog.dismiss();
        });
    }

    private void showDeleteCategoryConfirmation(SpinnerCategory category) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Delete Category?")
                .setMessage("Are you sure you want to delete '" + category.getName() + "' and all its items?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteCategory(category);
                    Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
