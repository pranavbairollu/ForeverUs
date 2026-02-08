package com.example.foreverus;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.foreverus.databinding.ItemImageSliderBinding;
import java.util.List;

public class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.SliderViewHolder> {

    private final List<String> imageUrls;

    public ImageSliderAdapter(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    @NonNull
    @Override
    public SliderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemImageSliderBinding binding = ItemImageSliderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SliderViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SliderViewHolder holder, int position) {
        holder.bind(imageUrls.get(position));
    }

    @Override
    public int getItemCount() {
        return imageUrls != null ? imageUrls.size() : 0;
    }

    static class SliderViewHolder extends RecyclerView.ViewHolder {
        private final ItemImageSliderBinding binding;

        public SliderViewHolder(ItemImageSliderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(String imageUrl) {
            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_baseline_photo_library_24) // Fallback
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(binding.sliderImageView);
        }
    }
}
