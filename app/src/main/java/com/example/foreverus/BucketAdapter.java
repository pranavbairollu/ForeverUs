package com.example.foreverus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class BucketAdapter extends RecyclerView.Adapter<BucketAdapter.BucketViewHolder> {

    private List<BucketItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(BucketItem item);

        void onCompleteClick(BucketItem item, boolean isChecked);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<BucketItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BucketViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bucket, parent, false);
        return new BucketViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BucketViewHolder holder, int position) {
        BucketItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                java.util.Collections.swap(items, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                java.util.Collections.swap(items, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);

        // Update priority indices in the list objects immediately?
        // We defer specific priority value update to the ViewModel save call,
        // but the list state 'items' is now consistent with UI.
    }

    public List<BucketItem> getItems() {
        return items;
    }

    class BucketViewHolder extends RecyclerView.ViewHolder {
        TextView title, description, date, cost;
        ImageView image, icon;
        CheckBox checkBox;

        public BucketViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textTitle);
            description = itemView.findViewById(R.id.textDescription);
            image = itemView.findViewById(R.id.itemImage);
            icon = itemView.findViewById(R.id.iconType);
            checkBox = itemView.findViewById(R.id.checkboxComplete);

            // Extensions
            date = itemView.findViewById(R.id.textDate);
            cost = itemView.findViewById(R.id.textCost);

            itemView.setOnClickListener(v -> {
                if (listener != null && getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onItemClick(items.get(getBindingAdapterPosition()));
                }
            });

            // ... checkBox listener same as before ...
            checkBox.setOnClickListener(v -> {
                if (listener != null && getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onCompleteClick(items.get(getBindingAdapterPosition()), checkBox.isChecked());
                }
            });
        }

        void bind(BucketItem item) {
            title.setText(item.getTitle());
            description.setText(item.getDescription());
            checkBox.setChecked(item.isCompleted());

            // Date & Cost logic
            if (item.getTargetDate() != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM yyyy",
                        java.util.Locale.getDefault());
                date.setText(sdf.format(item.getTargetDate()));
                date.setVisibility(View.VISIBLE);
            } else {
                date.setVisibility(View.GONE);
            }

            if (item.getEstimatedCost() != null && item.getEstimatedCost() > 0) {
                cost.setVisibility(View.VISIBLE);
                String symbol = "₹";
                if (item.getCurrency() != null && item.getCurrency().equals("USD")) {
                    symbol = "$";
                }
                cost.setText(symbol + String.format("%.0f", item.getEstimatedCost()));
            } else {
                cost.setVisibility(View.GONE);
            }

            // Icon Logic
            if ("TRAVEL".equalsIgnoreCase(item.getType())) {
                icon.setImageResource(R.drawable.ic_flight);
            } else if ("MOVIE".equalsIgnoreCase(item.getType())) {
                icon.setImageResource(R.drawable.ic_movie);
            } else if ("DATE".equalsIgnoreCase(item.getType())) {
                // Using calendar or heart if available. Given drawables list, calendar is safe.
                icon.setImageResource(R.drawable.ic_baseline_calendar_today_24);
            } else if ("GOAL".equalsIgnoreCase(item.getType())) {
                icon.setImageResource(R.drawable.ic_flag);
            } else if ("OTHER".equalsIgnoreCase(item.getType())) {
                icon.setImageResource(R.drawable.ic_baseline_auto_awesome_24);
            } else {
                icon.setImageResource(R.drawable.ic_baseline_check_circle_24);
            }

            // Image Logic
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(item.getImageUrl())
                        .placeholder(R.drawable.placeholder_image)
                        .centerCrop()
                        .into(image);
            } else {
                image.setImageResource(R.drawable.placeholder_image);
            }

            // Alpha/Strikethrough
            itemView.setAlpha(item.isCompleted() ? 0.6f : 1.0f);
        }
    }
}
