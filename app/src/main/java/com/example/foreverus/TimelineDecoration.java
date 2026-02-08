package com.example.foreverus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

public class TimelineDecoration extends RecyclerView.ItemDecoration {

    private final Paint linePaint;
    private final Paint dotPaint;
    private final int dotRadius;
    private final int lineThickness;

    private final Paint textPaint;
    private final Paint pillPaint;
    private final Rect textBounds = new Rect();

    public TimelineDecoration(Context context) {
        linePaint = new Paint();
        linePaint.setColor(ContextCompat.getColor(context, com.google.android.material.R.color.material_dynamic_neutral60));
        lineThickness = (int) (2 * context.getResources().getDisplayMetrics().density);
        linePaint.setStrokeWidth(lineThickness);

        dotPaint = new Paint();
        dotPaint.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);
        dotRadius = (int) (4 * context.getResources().getDisplayMetrics().density);

        pillPaint = new Paint();
        pillPaint.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(android.graphics.Color.WHITE);
        textPaint.setTextSize(10 * context.getResources().getDisplayMetrics().scaledDensity);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private final java.util.Calendar calendar = java.util.Calendar.getInstance();
    private final java.util.Calendar prevCalendar = java.util.Calendar.getInstance();
    private final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault());

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int childCount = parent.getChildCount();
        int width = parent.getWidth();
        int centerX = width / 2;

        c.drawLine(centerX, 0, centerX, parent.getHeight(), linePaint);

        TimelineAdapter adapter = (TimelineAdapter) parent.getAdapter();
        if (adapter == null) return;

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) continue;
            
            int viewType = adapter.getItemViewType(position);
            if (viewType == 0) continue; // Skip Headers

            TimelineItem currentItem = adapter.getItemAt(position);
            long currentDate = currentItem.getTimestamp();
            
            if (currentDate == 0) {
                 // Skip drawing or use default? If 0, likely metadata missing.
                 // Draw dot by default to be safe.
                 int top = child.getTop();
                 int cy = top + (int)(24 * parent.getResources().getDisplayMetrics().density);
                 c.drawCircle(centerX, cy, dotRadius, dotPaint);
                 continue;
            }

            boolean isNewDay = true;
            if (position > 0) {
                TimelineItem prevItem = adapter.getItemAt(position - 1);
                long prevDate = prevItem.getTimestamp();
                
                if (prevDate != 0) {
                    calendar.setTimeInMillis(currentDate);
                    prevCalendar.setTimeInMillis(prevDate);
                    
                    if (calendar.get(java.util.Calendar.YEAR) == prevCalendar.get(java.util.Calendar.YEAR) &&
                        calendar.get(java.util.Calendar.DAY_OF_YEAR) == prevCalendar.get(java.util.Calendar.DAY_OF_YEAR)) {
                        isNewDay = false;
                    }
                }
            }

            int top = child.getTop();
            // Align pill with the "Top" of the content roughly, simply top + padding
            int cy = top + (int)(24 * parent.getResources().getDisplayMetrics().density); // approximate center of card header

            if (isNewDay) {
                // Draw Pill
                String dayText = sdf.format(new java.util.Date(currentDate));
                float textWidth = textPaint.measureText(dayText);
                float pillW = textWidth + 40;
                float pillH = 40;
                
                android.graphics.RectF pillRect = new android.graphics.RectF(centerX - pillW/2, cy - pillH/2, centerX + pillW/2, cy + pillH/2);
                c.drawRoundRect(pillRect, 20, 20, pillPaint);
                
                // Draw Text centered vertically
                float textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2);
                c.drawText(dayText, centerX, textY, textPaint);
            } else {
                // Draw small dot
               c.drawCircle(centerX, cy, dotRadius, dotPaint);
            }
        }
    }
}
