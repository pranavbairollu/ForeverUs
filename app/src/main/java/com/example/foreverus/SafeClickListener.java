package com.example.foreverus;

import android.os.SystemClock;
import android.view.View;

public abstract class SafeClickListener implements View.OnClickListener {

    private static final long DEFAULT_INTERVAL = 1000;
    private long lastTimeClicked = 0;

    @Override
    public void onClick(View v) {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < DEFAULT_INTERVAL) {
            return;
        }
        lastTimeClicked = SystemClock.elapsedRealtime();
        onSafeClick(v);
    }

    public abstract void onSafeClick(View v);
}
