package com.example.foreverus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class SpinnerItemAdapter extends ListAdapter<SpinnerItem, SpinnerItemAdapter.ViewHolder> {

    private final OnItemDeleteListener deleteListener;

    public interface OnItemDeleteListener {
        void onDelete(SpinnerItem item);
    }

    public SpinnerItemAdapter(OnItemDeleteListener deleteListener) {
        super(new DiffUtil.ItemCallback<SpinnerItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull SpinnerItem oldItem, @NonNull SpinnerItem newItem) {
                return oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull SpinnerItem oldItem, @NonNull SpinnerItem newItem) {
                return oldItem.getText().equals(newItem.getText()) &&
                        oldItem.getEmoji().equals(newItem.getEmoji());
            }
        });
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_spinner_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvEmoji;
        private final TextView tvText;
        private final ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji = itemView.findViewById(R.id.tvEmoji);
            tvText = itemView.findViewById(R.id.tvText);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        public void bind(SpinnerItem item) {
            tvEmoji.setText(item.getEmoji());
            tvText.setText(item.getText());

            btnDelete.setOnClickListener(v -> {
                if (deleteListener != null)
                    deleteListener.onDelete(item);
            });
        }
    }
}
