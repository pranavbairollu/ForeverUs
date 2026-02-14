package com.example.foreverus;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.foreverus.databinding.ActivityLegalBinding;

public class LegalActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CONTENT = "extra_content";

    private ActivityLegalBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLegalBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String content = getIntent().getStringExtra(EXTRA_CONTENT);

        if (title == null && content == null) {
            // Invalid state, close activity
            finish();
            return;
        }

        if (title != null) {
            getSupportActionBar().setTitle(title);

            // Set icon based on title
            if (title.contains("Privacy")) {
                binding.headerIcon.setImageResource(R.drawable.ic_baseline_lock_24);
            } else if (title.contains("Terms")) {
                binding.headerIcon.setImageResource(R.drawable.ic_baseline_description_24);
            } else {
                // Fallback icon or hide
                binding.headerIcon.setVisibility(android.view.View.GONE);
            }
        }

        if (content != null) {
            try {
                // Render HTML using LEGACY mode to support block tags like <h3> and <p>
                binding.contentTextView
                        .setText(android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY));
                // Make links clickable
                binding.contentTextView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            } catch (Exception e) {
                // Fallback for html parsing error
                binding.contentTextView.setText(content);
            }
        } else {
            binding.contentTextView.setText("No content available.");
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
