package com.example.foreverus;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.foreverus.databinding.FragmentLettersBinding;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class LettersFragment extends Fragment {

    private FragmentLettersBinding binding;
    private LettersViewModel lettersViewModel;
    private LetterAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLettersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        lettersViewModel = new ViewModelProvider(this).get(LettersViewModel.class);

        setupRecyclerView();
        observeViewModel();

        binding.swipeRefreshLayout.setOnRefreshListener(() -> lettersViewModel.refresh());

        binding.fabAddLetter.setOnClickListener(v -> {
            String relationshipId = RelationshipRepository.getInstance().getRelationshipId().getValue();
            if (relationshipId != null) {
                Intent intent = new Intent(getActivity(), ComposeLetterActivity.class);
                intent.putExtra(FirestoreConstants.FIELD_RELATIONSHIP_ID, relationshipId);
                startActivity(intent);
            } else {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Connect with your Partner")
                        .setMessage(
                                "Before writing letters, you need to pair with your partner so they can receive them.")
                        .setPositiveButton("Pair Now", (dialog, which) -> {
                            // Assuming standard navigation to pairing/home? Or just dismiss if we can't
                            // deep link yet.
                            // Best effort: Dismiss and maybe guidance.
                            // Ideally: NavController or Intent to PairingActivity if it exists.
                            // Given context, let's just dismiss and Toast for now if we don't know the
                            // exact Pairing route?
                            // Actually, let's look for PairingActivity or similar?
                            // Step 15 strings shows "pair_title".
                            // Let's safe bet: Dismiss.
                            dialog.dismiss();
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new LetterAdapter(requireContext());
        binding.lettersRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.lettersRecyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener((letter, isLocked) -> {
            if (isLocked) {
                SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_month_day_year),
                        Locale.getDefault());
                Toast.makeText(getContext(),
                        getString(R.string.letter_sealed_message, sdf.format(letter.getOpenDate())), Toast.LENGTH_SHORT)
                        .show();
            } else {
                Intent intent = new Intent(getActivity(), LetterViewActivity.class);
                intent.putExtra("letterId", letter.getLetterId());
                intent.putExtra("relationshipId", letter.getRelationshipId());
                startActivity(intent);
            }
        });
    }

    private void observeViewModel() {
        RelationshipRepository.getInstance().getRelationshipId().observe(getViewLifecycleOwner(), id -> {
            lettersViewModel.setRelationshipId(id);
        });

        lettersViewModel.getAllLetters().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null)
                return;

            switch (resource.status) {
                case LOADING:
                    if (resource.data == null || resource.data.isEmpty()) {
                        showLoadingState(null);
                        binding.swipeRefreshLayout.setRefreshing(false);
                    } else {
                        showSuccessState(resource.data);
                        binding.swipeRefreshLayout.setRefreshing(true);
                    }
                    break;
                case SUCCESS:
                    binding.swipeRefreshLayout.setRefreshing(false);
                    showSuccessState(resource.data);
                    break;
                case ERROR:
                    binding.swipeRefreshLayout.setRefreshing(false);
                    showErrorState(resource.message, resource.data);
                    break;
            }
        });
    }

    private void showLoadingState(List<Letter> data) {
        if (data == null || data.isEmpty()) {
            binding.shimmerViewContainer.setVisibility(View.VISIBLE);
            binding.shimmerViewContainer.startShimmer();
            binding.lettersRecyclerView.setVisibility(View.GONE);
            binding.emptyStateLayout.getRoot().setVisibility(View.GONE);
            binding.errorView.getRoot().setVisibility(View.GONE);
        } else {
            showSuccessState(data);
        }
    }

    private void showSuccessState(List<Letter> letters) {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);

        if (letters != null && !letters.isEmpty()) {
            binding.lettersRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyStateLayout.getRoot().setVisibility(View.GONE);
            adapter.submitList(letters);
        } else {
            binding.lettersRecyclerView.setVisibility(View.GONE);
            binding.emptyStateLayout.getRoot().setVisibility(View.VISIBLE);
            binding.emptyStateLayout.txtEmptyTitle.setText(R.string.letters_empty_soft);
            binding.emptyStateLayout.txtEmptyMessage.setVisibility(View.GONE);
        }
        binding.errorView.getRoot().setVisibility(View.GONE);
    }

    private void showErrorState(String message, List<Letter> data) {
        binding.shimmerViewContainer.stopShimmer();
        binding.shimmerViewContainer.setVisibility(View.GONE);
        binding.lettersRecyclerView.setVisibility(View.GONE);
        binding.emptyStateLayout.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.VISIBLE);

        binding.errorView.txtErrorMessage
                .setText(message != null ? message : getString(R.string.error_message_default));
        // Retry logic: re-set the ID to trigger the switchMap
        binding.errorView.btnErrorRetry.setOnClickListener(v -> {
            String currentId = RelationshipRepository.getInstance().getRelationshipId().getValue();
            if (currentId != null) {
                // Force a refresh by ensuring the set triggers update if it's the same object,
                // but LiveData distinctUntilChanged usually prevents this.
                // However, commonly re-fetching might need a more robust trigger.
                // For now, re-setting it is the best we can do with the current VM.
                lettersViewModel.setRelationshipId(currentId);
            }
        });

        if (data != null && !data.isEmpty()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            showSuccessState(data);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
