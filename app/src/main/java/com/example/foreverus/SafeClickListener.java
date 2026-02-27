package com.example.foreverus;

import android.os.SystemClock;
import android.view.View;

public abstract class SafeClickListener implements View.OnClickListener {

    private static final long DEFAULT_INTERVAL = 1000;
    private final long clickInterval;
    private long lastTimeClicked = 0;

    public SafeClickListener() {
        this(DEFAULT_INTERVAL);
    }

    public SafeClickListener(long customInterval) {
        this.clickInterval = customInterval;
    }

    @Override
    public void onClick(View v) {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < clickInterval) {
            return;
        }
        lastTimeClicked = SystemClock.elapsedRealtime();
        onSafeClick(v);
    }

    public abstract void onSafeClick(View v);
}
