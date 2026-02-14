package com.example.foreverus;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.foreverus.GuideAdapter;
import com.example.foreverus.GuideItem;
import java.util.ArrayList;
import java.util.List;

public class GuideActivity extends AppCompatActivity {

        private List<GuideItem> guideItems;
        private GuideAdapter adapter;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_guide);

                RecyclerView recyclerView = findViewById(R.id.guideRecyclerView);
                recyclerView.setLayoutManager(new LinearLayoutManager(this));

                guideItems = new ArrayList<>();

                // Memories
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_memories),
                                getString(R.string.guide_desc_memories),
                                R.drawable.ic_baseline_photo_library_24));

                // Letters
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_letters),
                                getString(R.string.guide_desc_letters),
                                R.drawable.ic_baseline_email_24));

                // Music
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_music),
                                getString(R.string.guide_desc_music),
                                R.drawable.ic_baseline_music_note_24));

                // Timeline
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_timeline),
                                getString(R.string.guide_desc_timeline),
                                R.drawable.ic_baseline_timeline_24));

                // Story
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_story),
                                getString(R.string.guide_desc_story),
                                R.drawable.ic_baseline_book_24));

                // Adventure Board
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_adventure),
                                getString(R.string.guide_desc_adventure),
                                R.drawable.ic_flag // Using flag icon for adventure/goals
                ));

                // Polaroid Maker
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_polaroid),
                                getString(R.string.guide_desc_polaroid),
                                R.drawable.ic_camera // Using camera icon
                ));

                // Decision Spinner
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_spinner),
                                getString(R.string.guide_desc_spinner),
                                R.drawable.ic_baseline_refresh_24 // Using refresh for spinner
                ));

                // Special Dates
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_dates),
                                getString(R.string.guide_desc_dates),
                                R.drawable.ic_baseline_calendar_today_24));

                // Profile
                guideItems.add(new GuideItem(
                                getString(R.string.guide_topic_profile),
                                getString(R.string.guide_desc_profile),
                                R.drawable.ic_baseline_person_24));

                adapter = new GuideAdapter(guideItems);
                recyclerView.setAdapter(adapter);

                if (savedInstanceState != null) {
                        boolean[] expandedStates = savedInstanceState.getBooleanArray("expanded_states");
                        if (expandedStates != null && expandedStates.length == guideItems.size()) {
                                for (int i = 0; i < guideItems.size(); i++) {
                                        guideItems.get(i).setExpanded(expandedStates[i]);
                                }
                                adapter.notifyDataSetChanged();
                        }
                }

                // Search Setup
                androidx.appcompat.widget.SearchView searchView = findViewById(R.id.searchView);
                android.widget.TextView emptyView = findViewById(R.id.emptyView);

                // Custom Filter Listener wrapper to handle empty state
                adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                        @Override
                        public void onChanged() {
                                super.onChanged();
                                checkEmpty();
                        }

                        @Override
                        public void onItemRangeInserted(int positionStart, int itemCount) {
                                checkEmpty();
                        }

                        @Override
                        public void onItemRangeRemoved(int positionStart, int itemCount) {
                                checkEmpty();
                        }

                        void checkEmpty() {
                                if (emptyView != null) {
                                        boolean isEmpty = adapter.getItemCount() == 0;
                                        emptyView.setVisibility(
                                                        isEmpty ? android.view.View.VISIBLE : android.view.View.GONE);
                                        recyclerView.setVisibility(
                                                        isEmpty ? android.view.View.GONE : android.view.View.VISIBLE);
                                }
                        }
                });

                searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                        @Override
                        public boolean onQueryTextSubmit(String query) {
                                adapter.getFilter().filter(query);
                                searchView.clearFocus(); // Hide keyboard
                                return true;
                        }

                        @Override
                        public boolean onQueryTextChange(String newText) {
                                adapter.getFilter().filter(newText);
                                return true;
                        }
                });
        }

        @Override
        protected void onSaveInstanceState(@NonNull Bundle outState) {
                super.onSaveInstanceState(outState);
                if (guideItems != null) {
                        boolean[] expandedStates = new boolean[guideItems.size()];
                        for (int i = 0; i < guideItems.size(); i++) {
                                expandedStates[i] = guideItems.get(i).isExpanded();
                        }
                        outState.putBooleanArray("expanded_states", expandedStates);
                }

                androidx.appcompat.widget.SearchView searchView = findViewById(R.id.searchView);
                if (searchView != null && searchView.getQuery() != null) {
                        outState.putString("search_query", searchView.getQuery().toString());
                }
        }

        @Override
        protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
                super.onRestoreInstanceState(savedInstanceState);
                String savedQuery = savedInstanceState.getString("search_query");
                if (savedQuery != null && !savedQuery.isEmpty()) {
                        androidx.appcompat.widget.SearchView searchView = findViewById(R.id.searchView);
                        if (searchView != null) {
                                // Post to ensure menu/view is ready
                                searchView.post(() -> {
                                        searchView.setQuery(savedQuery, true);
                                        searchView.clearFocus(); // Hide keyboard
                                });
                        }
                }
        }
}
