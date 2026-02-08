package com.example.foreverus;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.foreverus.databinding.ItemLetterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class LetterAdapter extends ListAdapter<Letter, LetterAdapter.LetterViewHolder> {

    private OnItemClickListener listener;
    private final Context context;

    public LetterAdapter(Context context) {
        super(DIFF_CALLBACK);
        this.context = context;
    }

    private static final DiffUtil.ItemCallback<Letter> DIFF_CALLBACK = new DiffUtil.ItemCallback<Letter>() {
        @Override
        public boolean areItemsTheSame(@NonNull Letter oldItem, @NonNull Letter newItem) {
            return oldItem.getLetterId().equals(newItem.getLetterId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Letter oldItem, @NonNull Letter newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public LetterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLetterBinding binding = ItemLetterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new LetterViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LetterViewHolder holder, int position) {
        Letter letter = getItem(position);
        holder.bind(letter, listener);
    }

    class LetterViewHolder extends RecyclerView.ViewHolder {
        private final ItemLetterBinding binding;

        public LetterViewHolder(ItemLetterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final Letter letter, final OnItemClickListener listener) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                return;
            }
            String currentUserId = currentUser.getUid();
            boolean isSentByMe = currentUserId.equals(letter.getFromId());

            binding.letterTitle.setText(letter.getTitle());
            binding.letterContentPreview.setText(isSentByMe ? context.getString(R.string.sent_by_you) : context.getString(R.string.letter_from_love));

            boolean isLocked = letter.getOpenDate() != null && letter.getOpenDate().after(new Date());

            binding.lockedIcon.setVisibility(isLocked ? View.VISIBLE : View.GONE);
            binding.unlockedIcon.setVisibility(isLocked ? View.GONE : View.VISIBLE);
            binding.letterIcon.setImageResource(isLocked ? R.drawable.ic_baseline_lock_24 : R.drawable.ic_baseline_email_24);
            binding.letterOpenDate.setVisibility(isLocked ? View.VISIBLE : View.GONE);

            if (isLocked) {
                SimpleDateFormat sdf = new SimpleDateFormat(context.getString(R.string.date_format_month_day_year), Locale.getDefault());
                binding.letterOpenDate.setText(context.getString(R.string.opens_on, sdf.format(letter.getOpenDate())));
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(letter, isLocked);
                }
            });
        }
    }

    public interface OnItemClickListener {
        void onItemClick(Letter letter, boolean isLocked);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
}
