package com.example.foreverus;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.foreverus.databinding.ItemTimelineLetterBinding;
import com.example.foreverus.databinding.ItemTimelineMemoryBinding;
import com.example.foreverus.databinding.ItemTimelineStoryBinding;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimelineAdapter extends ListAdapter<TimelineItem, RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_STORY = 1;
    private static final int TYPE_MEMORY = 2;
    private static final int TYPE_LETTER = 3;

    public TimelineAdapter() {
        super(new TimelineItem.TimelineDiffCallback());
    }

    @Override
    public int getItemViewType(int position) {
        TimelineItem item = getItem(position);
        if (item instanceof TimelineItem.HeaderItem) return TYPE_HEADER; // 0
        if (item instanceof TimelineItem.StoryItem) return TYPE_STORY;
        if (item instanceof TimelineItem.MemoryItem) return TYPE_MEMORY;
        if (item instanceof TimelineItem.LetterItem) return TYPE_LETTER;
        return -1;
    }

    public TimelineItem getItemAt(int position) {
        return getItem(position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                // Reusing memory header layout or create new one. Using item_memory_header for now.
                // Assuming R.layout.item_memory_header exists and binds to simple TextView
                // Note: item_memory_header might be specific to Memory. 
                // Let's create a generic item_timeline_header layout later.
                // For now, I'll assume we use a simple layout.
                android.view.View view = inflater.inflate(R.layout.item_timeline_header, parent, false);
                return new HeaderViewHolder(view);
            case TYPE_STORY:
                ItemTimelineStoryBinding storyBinding = ItemTimelineStoryBinding.inflate(inflater, parent, false);
                return new StoryViewHolder(storyBinding);
            case TYPE_MEMORY:
                ItemTimelineMemoryBinding memoryBinding = ItemTimelineMemoryBinding.inflate(inflater, parent, false);
                return new MemoryViewHolder(memoryBinding);
            case TYPE_LETTER:
                ItemTimelineLetterBinding letterBinding = ItemTimelineLetterBinding.inflate(inflater, parent, false);
                return new LetterViewHolder(letterBinding);
            default:
                throw new IllegalArgumentException("Invalid view type");
        }
    }


    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TimelineItem item, android.view.View sharedView, String transitionName);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TimelineItem item = getItem(position);
        
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder header = (HeaderViewHolder) holder;
            header.title.setText(((TimelineItem.HeaderItem) item).getTitle());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            params.setMargins(0, 0, 0, 0);
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            holder.itemView.setLayoutParams(params);
            return; 
        }

        
        applyZigZagLayout(holder.itemView, position);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null && position != RecyclerView.NO_POSITION) {
                // Default click for non-special types (though currently all types handle their own clicks below)
                // But just in case:
                listener.onItemClick(item, null, null);
            }
        });

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        
        switch (holder.getItemViewType()) {
             case TYPE_STORY:
                StoryViewHolder storyHolder = (StoryViewHolder) holder;
                Story story = ((TimelineItem.StoryItem) item).getStory();
                storyHolder.binding.titleTextView.setText(story.getTitle());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    storyHolder.binding.previewTextView.setText(android.text.Html.fromHtml(story.getContent(), android.text.Html.FROM_HTML_MODE_COMPACT));
                } else {
                    storyHolder.binding.previewTextView.setText(android.text.Html.fromHtml(story.getContent()));
                }
                if (item.getTimestamp() > 0) {
                    storyHolder.binding.dateTextView.setText(sdf.format(new Date(item.getTimestamp())));
                }
                break;
            case TYPE_MEMORY:
                MemoryViewHolder memoryHolder = (MemoryViewHolder) holder;
                Memory memory = ((TimelineItem.MemoryItem) item).getMemory();
                memoryHolder.binding.timelineMemoryTitleTextView.setText(memory.getTitle());
                
                String memUrl = memory.getImageUrl();
                boolean isMemVideo = "video".equals(memory.getMediaType());
                boolean isMemAudio = "audio".equals(memory.getMediaType());
                
                if (isMemVideo) {
                    memoryHolder.binding.playIcon.setVisibility(android.view.View.VISIBLE);
                    memoryHolder.binding.playIcon.setImageResource(R.drawable.ic_play_circle_filled_48); // Use explicit 48 if available or generic play
                    
                    if (memUrl != null && !memUrl.endsWith(".jpg")) {
                         if (memUrl.contains("cloudinary")) {
                            int lastDot = memUrl.lastIndexOf(".");
                            if (lastDot != -1) {
                                 memUrl = memUrl.substring(0, lastDot) + ".jpg";
                            }
                        }
                    }
                } else if (isMemAudio) {
                    memoryHolder.binding.playIcon.setVisibility(android.view.View.VISIBLE);
                    memoryHolder.binding.playIcon.setImageResource(R.drawable.ic_baseline_mic_24); // Show mic for audio
                    // Maybe use a placeholder image for Audio background?
                    // For now, Glide loads audioUrl expecting image? No.
                    // If audio, use a gradient or specific bg.
                    memUrl = null; // Don't load audio as image
                    memoryHolder.binding.timelineMemoryImageView.setImageResource(R.drawable.bg_paper_texture); // or some gradient
                } else {
                    memoryHolder.binding.playIcon.setVisibility(android.view.View.GONE);
                }

                String transitionName = "memory_image_" + position;
                androidx.core.view.ViewCompat.setTransitionName(memoryHolder.binding.timelineMemoryImageView, transitionName);

                if (memUrl != null) {
                    Glide.with(holder.itemView.getContext()).load(memUrl).into(memoryHolder.binding.timelineMemoryImageView);
                }
                
                if (item.getTimestamp() > 0) {
                    memoryHolder.binding.timelineMemoryDateTextView.setText(sdf.format(new Date(item.getTimestamp())));
                }
                
                holder.itemView.setOnClickListener(v -> {
                    if (isMemVideo) {
                         android.content.Intent intent = new android.content.Intent(holder.itemView.getContext(), VideoPlayerActivity.class);
                         intent.putExtra("videoUrl", memory.getImageUrl());
                         holder.itemView.getContext().startActivity(intent);
                    } else if (isMemAudio) {
                          // Audio item click -> Open Detail View
                          if (listener != null) listener.onItemClick(item, null, null);
                    } else {
                         if (listener != null) listener.onItemClick(item, memoryHolder.binding.timelineMemoryImageView, transitionName);
                    }
                });
                break;
            case TYPE_LETTER:
                LetterViewHolder letterHolder = (LetterViewHolder) holder;
                Letter letter = ((TimelineItem.LetterItem) item).getLetter();
                letterHolder.binding.timelineLetterTitleTextView.setText(letter.getTitle());
                letterHolder.binding.timelineLetterContentTextView.setText(letter.getMessage());
                if (item.getTimestamp() > 0) {
                    letterHolder.binding.timelineLetterDateTextView.setText(sdf.format(new Date(item.getTimestamp())));
                }
                break;
        }

        setAnimation(holder.itemView, position);
    }
    
    private void applyZigZagLayout(android.view.View view, int position) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int parentWidth = view.getResources().getDisplayMetrics().widthPixels;
        int halfWidth = parentWidth / 2;
        int gutter = (int) (16 * view.getResources().getDisplayMetrics().density); // Space for line
        
        // We want Card Width = (Half Width) - Gutter
        // Left Card: MarginLeft = ? MarginRight = Half + Gutter?
        
        // Let's say Line is at 50%.
        // Left Card: End at 50% - padding.
        // Right Card: Start at 50% + padding.
        
        int cardWidth = (parentWidth / 2) - (gutter * 2);
        
        if (position % 2 != 0) { 
            // Odd -> LEFT (Arbitrary choice, just alternating)
            // Left Margin: Normal
            // Right Margin: 50% + gutter
            params.setMargins(gutter, gutter / 2, halfWidth + gutter, gutter / 2);
        } else {
            // Even -> RIGHT
            // Left Margin: 50% + gutter
            // Right Margin: Normal
            params.setMargins(halfWidth + gutter, gutter / 2, gutter, gutter / 2);
        }
        
        view.setLayoutParams(params);
    }
    


    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        android.widget.TextView title;
        HeaderViewHolder(android.view.View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.headerTitle);
        }
    }

   
    private int lastPosition = -1;

    private void setAnimation(android.view.View viewToAnimate, int position) {
        if (position > lastPosition) {
            android.view.animation.Animation animation = android.view.animation.AnimationUtils.loadAnimation(viewToAnimate.getContext(), android.R.anim.fade_in);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }
    

    static class StoryViewHolder extends RecyclerView.ViewHolder {
        final ItemTimelineStoryBinding binding;
        StoryViewHolder(ItemTimelineStoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class MemoryViewHolder extends RecyclerView.ViewHolder {
        final ItemTimelineMemoryBinding binding;
        MemoryViewHolder(ItemTimelineMemoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class LetterViewHolder extends RecyclerView.ViewHolder {
        final ItemTimelineLetterBinding binding;
        LetterViewHolder(ItemTimelineLetterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
