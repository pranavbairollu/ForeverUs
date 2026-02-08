package com.example.foreverus;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.foreverus.databinding.ItemStoryBinding;

public class StoryAdapter extends ListAdapter<Story, StoryAdapter.StoryViewHolder> {

    private final OnItemClickListener listener;
    private String currentUserId;

    public StoryAdapter(OnItemClickListener listener) {
        super(new StoryDiffCallback());
        this.listener = listener;
    }
    
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull StoryViewHolder holder, int position) {
        holder.bind(getItem(position), listener, currentUserId);
    }

    @NonNull
    @Override
    public StoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStoryBinding binding = ItemStoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new StoryViewHolder(binding);
    }

    public interface OnItemClickListener {
        void onItemClick(Story story);
    }

    static class StoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemStoryBinding binding;

        public StoryViewHolder(ItemStoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final Story story, final OnItemClickListener listener, String currentUserId) {
            binding.storyTitleTextView.setText(story.getTitle());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                binding.storyContentPreviewTextView.setText(android.text.Html.fromHtml(story.getContent(), android.text.Html.FROM_HTML_MODE_COMPACT));
            } else {
                binding.storyContentPreviewTextView.setText(android.text.Html.fromHtml(story.getContent()));
            }
            
            if (story.getTimestamp() != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault());
                String dateStr = sdf.format(story.getTimestamp().toDate());
                
                String editor;
                if (currentUserId != null && currentUserId.equals(story.getAuthorId())) {
                    editor = "You";
                } else {
                    editor = "Partner";
                }
                
                binding.storyLastEditedTextView.setText("Last edited by " + editor + " on " + dateStr);
                binding.storyLastEditedTextView.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.storyLastEditedTextView.setVisibility(android.view.View.GONE);
            }
            
            itemView.setOnClickListener(v -> listener.onItemClick(story));
        }
    }

    private static class StoryDiffCallback extends DiffUtil.ItemCallback<Story> {
        @Override
        public boolean areItemsTheSame(@NonNull Story oldItem, @NonNull Story newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Story oldItem, @NonNull Story newItem) {
            return oldItem.getTitle().equals(newItem.getTitle()) &&
                    oldItem.getContent().equals(newItem.getContent());
        }
    }
}
