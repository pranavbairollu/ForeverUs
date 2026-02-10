package com.example.foreverus;

import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GuideAdapter extends RecyclerView.Adapter<GuideAdapter.GuideViewHolder> {

    private final List<GuideItem> guideItems;

    public GuideAdapter(List<GuideItem> guideItems) {
        this.guideItems = guideItems;
    }

    @NonNull
    @Override
    public GuideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_guide_topic, parent, false);
        return new GuideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GuideViewHolder holder, int position) {
        GuideItem item = guideItems.get(position);
        holder.bind(item);

        holder.itemView.setOnClickListener(v -> {
            boolean expanded = item.isExpanded();
            item.setExpanded(!expanded);
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return guideItems.size();
    }

    static class GuideViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final TextView descriptionTextView;
        private final ImageView iconImageView;
        private final ImageView arrowImageView;

        public GuideViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            descriptionTextView = itemView.findViewById(R.id.descriptionTextView);
            iconImageView = itemView.findViewById(R.id.iconImageView);
            arrowImageView = itemView.findViewById(R.id.arrowImageView);
        }

        public void bind(GuideItem item) {
            titleTextView.setText(item.getTitle());
            descriptionTextView.setText(item.getDescription());
            iconImageView.setImageResource(item.getIconResId());

            boolean isExpanded = item.isExpanded();
            descriptionTextView.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            // Rotate arrow: 180 (Right >) if collapsed, 270 (Down v) if expanded
            // Assuming base logic:
            // If ic_arrow_back is used (Left <), then
            // 180 deg = Right (>)
            // 270 deg = Down (v)
            float targetRotation = isExpanded ? 270f : 180f;
            arrowImageView.setRotation(targetRotation);

            // Accessibility
            String stateDescription = isExpanded ? "Expanded" : "Collapsed";
            itemView.setContentDescription(item.getTitle() + ", " + stateDescription + ". Double tap to toggle.");
            arrowImageView.setContentDescription(stateDescription);
        }
    }
}
