package com.example.foreverus;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.foreverus.databinding.ItemSpecialDateBinding;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SpecialDateAdapter extends ListAdapter<SpecialDate, SpecialDateAdapter.SpecialDateViewHolder> {

    public interface OnSpecialDateDeleteListener {
        void onSpecialDateDelete(SpecialDate specialDate);
    }

    private final SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
    private final OnSpecialDateDeleteListener deleteListener;

    public SpecialDateAdapter(OnSpecialDateDeleteListener deleteListener) {
        super(DIFF_CALLBACK);
        this.deleteListener = deleteListener;
    }

    private static final DiffUtil.ItemCallback<SpecialDate> DIFF_CALLBACK = new DiffUtil.ItemCallback<SpecialDate>() {
        @Override
        public boolean areItemsTheSame(@NonNull SpecialDate oldItem, @NonNull SpecialDate newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull SpecialDate oldItem, @NonNull SpecialDate newItem) {
            return oldItem.getTitle().equals(newItem.getTitle())
                    && oldItem.getDate().equals(newItem.getDate());
        }
    };

    @NonNull
    @Override
    public SpecialDateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSpecialDateBinding binding = ItemSpecialDateBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SpecialDateViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SpecialDateViewHolder holder, int position) {
        SpecialDate specialDate = getItem(position);
        holder.binding.specialDateTitleTextView.setText(specialDate.getTitle());
        holder.binding.specialDateDateTextView.setText(sdf.format(specialDate.getDate()));

        // Format date
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault());
        String dateStr = sdf.format(specialDate.getDate());

        // Append countdown logic
        long daysRemaining = specialDate.getDaysRemaining(); // Assuming SpecialDate has this method
        String countdownText;
        if (daysRemaining == 0) {
            countdownText = " (Today!)";
        } else if (daysRemaining == 1) {
            countdownText = " (Tomorrow)";
        } else if (daysRemaining < 0) { // Date is in the past
            countdownText = " (Past)";
        }
        else {
            countdownText = String.format(" (in %d days)", daysRemaining);
        }

        holder.binding.specialDateDateTextView.setText(dateStr + countdownText);

        // The original daysRemainingTextView logic is replaced by the countdownText in specialDateDateTextView
        // If daysRemainingTextView is still needed, its logic would need to be re-added or adapted.
        // For now, it's assumed the combined text in specialDateDateTextView is sufficient.
        holder.binding.daysRemainingTextView.setText(""); // Clear or set to empty if not used

        holder.binding.deleteSpecialDateButton.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onSpecialDateDelete(specialDate);
            }
        });
    }


    static class SpecialDateViewHolder extends RecyclerView.ViewHolder {
        private final ItemSpecialDateBinding binding;

        public SpecialDateViewHolder(ItemSpecialDateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
