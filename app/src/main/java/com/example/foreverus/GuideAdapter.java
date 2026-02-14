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

public class GuideAdapter extends RecyclerView.Adapter<GuideAdapter.GuideViewHolder>
        implements android.widget.Filterable {

    private final List<GuideItem> originalGuideItems;
    private final List<GuideItem> filteredGuideItems;

    public GuideAdapter(List<GuideItem> guideItems) {
        this.originalGuideItems = new java.util.ArrayList<>(guideItems);
        this.filteredGuideItems = guideItems; // Initially same reference, but should be managed
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
        GuideItem item = filteredGuideItems.get(position);
        holder.bind(item);

        holder.itemView.setOnClickListener(v -> {
            boolean expanded = item.isExpanded();
            item.setExpanded(!expanded);
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return filteredGuideItems.size();
    }

    @Override
    public android.widget.Filter getFilter() {
        return new android.widget.Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String charString = (constraint == null) ? "" : constraint.toString();
                List<GuideItem> filteredList = new java.util.ArrayList<>();

                if (charString.isEmpty()) {
                    filteredList.addAll(originalGuideItems);
                } else {
                    for (GuideItem row : originalGuideItems) {
                        // Filter by title or description
                        if (row.getTitle().toLowerCase().contains(charString.toLowerCase()) ||
                                row.getDescription().toLowerCase().contains(charString.toLowerCase())) {
                            filteredList.add(row);
                        }
                    }
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = filteredList;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.values != null) {
                    filteredGuideItems.clear();
                    filteredGuideItems.addAll((List<GuideItem>) results.values);
                    notifyDataSetChanged();
                }
            }
        };
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

            float targetRotation = isExpanded ? 270f : 180f;
            arrowImageView.setRotation(targetRotation);

            View.AccessibilityDelegate accessibilityDelegate = new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                        android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    String stateDescription = isExpanded ? "Expanded" : "Collapsed";
                    info.setStateDescription(stateDescription);
                    info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, "Toggle"));
                }
            };
            itemView.setAccessibilityDelegate(accessibilityDelegate);
        }
    }
}
