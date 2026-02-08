package com.example.foreverus;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import androidx.core.app.ActivityOptionsCompat;
import androidx.core.view.ViewCompat;
import android.app.Activity;

import com.bumptech.glide.Glide;
import com.example.foreverus.databinding.ItemMemoryBinding;
import com.example.foreverus.databinding.ItemMemoryHeaderBinding; // Using standard ViewBinding naming

public class MemoryAdapter extends ListAdapter<MemoryListItem, RecyclerView.ViewHolder> {

    public MemoryAdapter() {
        super(new MemoryDiffCallback());
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getItemType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == MemoryListItem.TYPE_HEADER) {
            // Check if we have the binding, otherwise fallback
            // Ideally ItemMemoryHeaderBinding is generated from item_memory_header.xml
            // If the binding is not resolvable in this snippet context, we assume standard
            // generation.
            ItemMemoryHeaderBinding binding = ItemMemoryHeaderBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new HeaderViewHolder(binding);
        } else {
            ItemMemoryBinding binding = ItemMemoryBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new MemoryViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MemoryListItem item = getItem(position);

        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((MemoryHeader) item);

            // Full Span for Headers in Staggered Grid
            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof StaggeredGridLayoutManager.LayoutParams) {
                ((StaggeredGridLayoutManager.LayoutParams) layoutParams).setFullSpan(true);
            }

        } else if (holder instanceof MemoryViewHolder) {
            ((MemoryViewHolder) holder).bind((Memory) item);
        }
    }

    // --- ViewHolders ---

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemMemoryHeaderBinding binding;

        public HeaderViewHolder(ItemMemoryHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(MemoryHeader header) {
            binding.headerTitleTextView.setText(header.getTitle());
        }
    }

    static class MemoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemMemoryBinding binding;

        public MemoryViewHolder(ItemMemoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Memory model) {
            String imageUrl = model.getImageUrl();
            boolean isVideo = "video".equals(model.getMediaType());

            // Cloudinary Thumbnail Logic
            if (isVideo && imageUrl != null && !imageUrl.endsWith(".jpg")) {
                if (imageUrl.contains("cloudinary") && imageUrl.contains("/upload/")) {
                    imageUrl = imageUrl.replace("/upload/", "/upload/w_400,c_scale,q_auto,f_auto/");
                    int lastDot = imageUrl.lastIndexOf(".");
                    if (lastDot != -1) {
                        imageUrl = imageUrl.substring(0, lastDot) + ".jpg";
                    }
                }
            }

            // Assign unique transition name for shared element transition
            ViewCompat.setTransitionName(binding.memoryImageView, "transition_" + model.getId());

            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_baseline_photo_library_24)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(binding.memoryImageView);

            itemView.setOnClickListener(v -> {
                if (isVideo) {
                    Intent intent = new Intent(itemView.getContext(), VideoPlayerActivity.class);
                    intent.putExtra("videoUrl", model.getImageUrl());
                    itemView.getContext().startActivity(intent);
                } else {
                    Intent intent = new Intent(itemView.getContext(), MemoryDetailActivity.class);
                    intent.putExtra(MemoryDetailActivity.EXTRA_IMAGE_URL, model.getImageUrl());
                    intent.putExtra(MemoryDetailActivity.EXTRA_TRANSITION_NAME, "transition_" + model.getId());

                    // Pass multiple images
                    if (model.getMediaUrls() != null && !model.getMediaUrls().isEmpty()) {
                        intent.putStringArrayListExtra("extra_media_urls",
                                new java.util.ArrayList<>(model.getMediaUrls()));
                    }

                    // Pass audio URL
                    if (model.getAudioUrl() != null) {
                        intent.putExtra("extra_audio_url", model.getAudioUrl());
                    }

                    // Pass ID for deletion
                    intent.putExtra("extra_memory_id", model.getId());
                    intent.putExtra("extra_relationship_id", model.getRelationshipId());

                    intent.putExtra("extra_title", model.getTitle());
                    intent.putExtra("extra_description", model.getDescription());

                    // Pass Spinner Data
                    if (model.getSpinnerCategoryId() != null && !model.getSpinnerCategoryId().isEmpty() &&
                            model.getSpinnerItemId() != null && !model.getSpinnerItemId().isEmpty()) {
                        intent.putExtra("extra_spinner_category_id", model.getSpinnerCategoryId());
                        intent.putExtra("extra_spinner_item_id", model.getSpinnerItemId());
                    }

                    ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            (Activity) itemView.getContext(), binding.memoryImageView, "transition_" + model.getId());
                    itemView.getContext().startActivity(intent, options.toBundle());
                }
            });

            binding.memoryTitleTextView.setText(model.getTitle());

            // Toggle visibility logic
            boolean hasDescription = model.getDescription() != null && !model.getDescription().isEmpty();
            boolean hasLocation = model.getLocation() != null && !model.getLocation().isEmpty();

            if (hasDescription) {
                binding.memoryDescriptionTextView.setText(model.getDescription());
                binding.memoryDescriptionTextView.setVisibility(View.VISIBLE);
            } else {
                binding.memoryDescriptionTextView.setVisibility(View.GONE);
            }

            if (hasLocation) {
                binding.memoryLocationTextView.setText(model.getLocation());
                binding.locationLayout.setVisibility(View.VISIBLE);
            } else {
                binding.locationLayout.setVisibility(View.GONE);
            }

            binding.detailsLayout.setVisibility((hasDescription || hasLocation) ? View.VISIBLE : View.GONE);
        }
    }

    // --- DiffCallback ---

    private static class MemoryDiffCallback extends DiffUtil.ItemCallback<MemoryListItem> {
        @Override
        public boolean areItemsTheSame(@NonNull MemoryListItem oldItem, @NonNull MemoryListItem newItem) {
            if (oldItem.getItemType() != newItem.getItemType())
                return false;

            if (oldItem.getItemType() == MemoryListItem.TYPE_HEADER) {
                return ((MemoryHeader) oldItem).getTitle().equals(((MemoryHeader) newItem).getTitle());
            } else {
                return ((Memory) oldItem).getId().equals(((Memory) newItem).getId());
            }
        }

        @Override
        public boolean areContentsTheSame(@NonNull MemoryListItem oldItem, @NonNull MemoryListItem newItem) {
            if (oldItem.getItemType() == MemoryListItem.TYPE_HEADER) {
                return true; // Title checked in areItemsTheSame
            } else {
                return oldItem.equals(newItem);
            }
        }
    }
}
