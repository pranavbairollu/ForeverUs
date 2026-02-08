package com.example.foreverus;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PolaroidAdapter extends RecyclerView.Adapter<PolaroidAdapter.PolaroidViewHolder> {

    private List<PolaroidEntity> polaroids = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PolaroidEntity polaroid);
    }

    public PolaroidAdapter(Context context, OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setPolaroids(List<PolaroidEntity> polaroids) {
        this.polaroids = polaroids;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PolaroidViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.polaroid_item, parent, false);

        // Force square-ish aspect ratio by setting height roughly based on width
        // context if needed,
        // but LinearLayout in XML handles wrapping.
        // For a true grid, we let the CardView Wrap Content.

        return new PolaroidViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PolaroidViewHolder holder, int position) {
        PolaroidEntity item = polaroids.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return polaroids != null ? polaroids.size() : 0;
    }

    static class PolaroidViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail;
        TextView txtCaption;
        TextView txtDate;

        public PolaroidViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.imgPolaroidThumbnail);
            txtCaption = itemView.findViewById(R.id.txtCaption);
            txtDate = itemView.findViewById(R.id.txtDate);
        }

        public void bind(final PolaroidEntity item, final OnItemClickListener listener) {
            if (item.caption != null && !item.caption.isEmpty()) {
                txtCaption.setText(item.caption);
                txtCaption.setVisibility(View.VISIBLE);
            } else {
                txtCaption.setVisibility(View.GONE);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            txtDate.setText(sdf.format(new Date(item.createdAt)));

            // Load Image efficiently
            if (item.imagePath != null) {
                // Determine image orientation or aspect based on creation logic?
                // Polaroids generated are portrait (2400x3000), so centerCrop is good.
                Glide.with(itemView.getContext())
                        .load(item.imagePath)
                        .centerCrop()
                        .into(imgThumbnail);
            }

            // Scatter Effect (Random Rotation)
            // Use item hash or position to keep rotation consistent per item (so it doesn't
            // flicker on scroll)
            float rotation = (item.id.hashCode() % 5) - 2.5f; // simple pseudo-random based on id
            itemView.setRotation(rotation);

            itemView.setOnClickListener(v -> {
                if (listener != null)
                    listener.onItemClick(item);
            });
        }
    }
}
