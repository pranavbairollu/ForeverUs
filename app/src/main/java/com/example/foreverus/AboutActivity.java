package com.example.foreverus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.foreverus.databinding.ActivityAboutBinding;

public class AboutActivity extends AppCompatActivity {

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        // Set Version Name
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            binding.versionTextView.setText("Version " + versionName);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            binding.versionTextView.setText("Version 1.0");
        }

        binding.btnInstagram.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(android.view.View v) {
                openInstagram();
            }
        });

        binding.btnPrivacyPolicy.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(android.view.View v) {
                openLegalDoc(getString(R.string.privacy_policy_title), getString(R.string.privacy_policy_content));
            }
        });

        binding.btnTermsOfService.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(android.view.View v) {
                openLegalDoc(getString(R.string.terms_of_service_title), getString(R.string.terms_of_service_content));
            }
        });
    }

    private void openLegalDoc(String title, String content) {
        Intent intent = new Intent(this, LegalActivity.class);
        intent.putExtra(LegalActivity.EXTRA_TITLE, title);
        intent.putExtra(LegalActivity.EXTRA_CONTENT, content);
        startActivity(intent);
    }

    private void openInstagram() {
        // Replace with actual Instagram URL or Profile
        // For now, let's open a generic one or the user's if known
        String url = "https://www.instagram.com/pranav_bairollu/";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Could not open link.", Toast.LENGTH_SHORT).show();
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
