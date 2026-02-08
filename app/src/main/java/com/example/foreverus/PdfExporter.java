package com.example.foreverus;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfExporter {

    // Constants for A4 size (approximate at 72 DPI)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final int CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);

    public interface PdfCallback {
        void onSuccess(Uri uri);
        void onFailure(Exception e);
    }

    // Run this on a background thread!
    @SuppressWarnings("deprecation")
    public void createPdf(Context context, List<Story> stories, PdfCallback callback) {
        PdfDocument document = new PdfDocument();
        int pageNumber = 1;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();

        // Start the first page
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        int currentY = MARGIN;

        // Setup Paints
        TextPaint titlePaint = new TextPaint();
        titlePaint.setTextSize(24);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setColor(Color.BLACK);

        TextPaint contentPaint = new TextPaint();
        contentPaint.setTextSize(14); // Slightly larger for readability
        contentPaint.setColor(Color.DKGRAY);

        TextPaint footerPaint = new TextPaint();
        footerPaint.setTextSize(10);
        footerPaint.setColor(Color.GRAY);

        try {
            for (Story story : stories) {
                String titleSafe = story.getTitle() != null ? story.getTitle() : "Untitled";
                String contentSafe = story.getContent() != null ? story.getContent() : "";

                // --- DRAW TITLE ---
                StaticLayout titleLayout = createLayout(titleSafe, titlePaint);

                // Check if title fits, if not, new page
                if (currentY + titleLayout.getHeight() > PAGE_HEIGHT - MARGIN) {
                    drawFooter(canvas, footerPaint, pageNumber++);
                    document.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    currentY = MARGIN;
                }

                canvas.save();
                canvas.translate(MARGIN, currentY);
                titleLayout.draw(canvas);
                canvas.restore();
                currentY += titleLayout.getHeight() + 20; // Gap after title

                // --- DRAW CONTENT (With Splitting Logic) ---
                CharSequence fullContent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    fullContent = android.text.Html.fromHtml(contentSafe, android.text.Html.FROM_HTML_MODE_COMPACT);
                } else {
                    fullContent = android.text.Html.fromHtml(contentSafe);
                }

                // We assume content might be huge, so we process it iteratively
                while (fullContent.length() > 0) {
                    StaticLayout contentLayout = createLayout(fullContent, contentPaint);
                    int availableHeight = PAGE_HEIGHT - MARGIN - currentY;

                    if (contentLayout.getHeight() <= availableHeight) {
                        // It fits entirely
                        canvas.save();
                        canvas.translate(MARGIN, currentY);
                        contentLayout.draw(canvas);
                        canvas.restore();
                        currentY += contentLayout.getHeight() + 40; // Gap after story
                        break; // Done with this story
                    } else {
                        // It doesn't fit. We need to find how many lines fit in availableHeight
                        int linesToDraw = 0;
                        int heightAccumulator = 0;

                        // Calculate lines that fit
                        for (int i = 0; i < contentLayout.getLineCount(); i++) {
                            int lineHeight = contentLayout.getLineBottom(i) - contentLayout.getLineTop(i);
                            if (heightAccumulator + lineHeight > availableHeight) {
                                break;
                            }
                            heightAccumulator += lineHeight;
                            linesToDraw++;
                        }

                        // If NO lines fit (e.g., just margin left), force new page immediately
                        if (linesToDraw == 0) {
                            drawFooter(canvas, footerPaint, pageNumber++);
                            document.finishPage(page);
                            pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                            page = document.startPage(pageInfo);
                            canvas = page.getCanvas();
                            currentY = MARGIN;
                            
                            // Safety Check: If we just created a new page and STILL nothing fits, 
                            // we must force progress to avoid infinite loop.
                            // This happens if a single line is taller than the page (unlikely for text, but safer to handle).
                            int newLinesCheck = 0;
                             for (int j = 0; j < contentLayout.getLineCount(); j++) {
                                int lh = contentLayout.getLineBottom(j) - contentLayout.getLineTop(j);
                                if (lh > (PAGE_HEIGHT - (MARGIN * 2))) {
                                    // Line is taller than page. Force it to print anyway (it will clip)
                                    // or just break. Let's force draw 1 line to progress.
                                     break; 
                                }
                                newLinesCheck++;
                            }
                            
                            if (newLinesCheck == 0) {
                                // Forced progress: Draw at least one line (clipping it)
                                int charEndForced = contentLayout.getLineEnd(0);
                                CharSequence textToDrawForced = fullContent.subSequence(0, charEndForced);
                                StaticLayout chunkLayoutForced = createLayout(textToDrawForced, contentPaint);
                                canvas.save();
                                canvas.translate(MARGIN, currentY);
                                chunkLayoutForced.draw(canvas);
                                canvas.restore();
                                
                                fullContent = fullContent.subSequence(charEndForced, fullContent.length());
                                
                                drawFooter(canvas, footerPaint, pageNumber++);
                                document.finishPage(page);
                                pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                                page = document.startPage(pageInfo);
                                canvas = page.getCanvas();
                                currentY = MARGIN;
                            }
                            
                            continue; // Re-evaluate loop with full fresh page (or after forced progress)
                        }

                        // Determine where to cut the text
                        int charEnd = contentLayout.getLineEnd(linesToDraw - 1);
                        CharSequence textToDraw = fullContent.subSequence(0, charEnd);

                        // Draw the chunk that fits
                        StaticLayout chunkLayout = createLayout(textToDraw, contentPaint);
                        canvas.save();
                        canvas.translate(MARGIN, currentY);
                        chunkLayout.draw(canvas);
                        canvas.restore();

                        // Prepare for next page
                        fullContent = fullContent.subSequence(charEnd, fullContent.length()); // Remaining text
                        drawFooter(canvas, footerPaint, pageNumber++);
                        document.finishPage(page);
                        pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                        currentY = MARGIN;
                    }
                }
            }

            // Finish the last page
            drawFooter(canvas, footerPaint, pageNumber);
            document.finishPage(page);

            // Save file
            Uri uri = savePdfToFile(context, document);
            if (callback != null) callback.onSuccess(uri);

        } catch (Exception e) {
            if (callback != null) callback.onFailure(e);
        } finally {
            document.close();
        }
    }

    // Helper to create StaticLayout (Compatible with older APIs if needed)
    private StaticLayout createLayout(CharSequence text, TextPaint paint) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0.0f, 1.0f)
                .setIncludePad(false)
                .build();
    }

    private void drawFooter(Canvas canvas, Paint paint, int pageNumber) {
        String footerText = "Page " + pageNumber;
        canvas.drawText(footerText, PAGE_WIDTH / 2f, PAGE_HEIGHT - 20, paint);
    }

    @SuppressWarnings("deprecation")
    private Uri savePdfToFile(Context context, PdfDocument document) throws IOException {
        String fileName = "ForeverUs_Story_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
        Uri pdfUri = null;
        OutputStream fos = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                pdfUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (pdfUri != null) {
                    fos = context.getContentResolver().openOutputStream(pdfUri);
                }
            } else {
                String downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
                java.io.File file = new java.io.File(downloadsPath, fileName);
                fos = new java.io.FileOutputStream(file);
                pdfUri = Uri.fromFile(file);
            }
            if (fos != null) {
                document.writeTo(fos);
            }
            // Trigger Media Scanner for older APIs
            if (pdfUri != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(pdfUri);
                context.sendBroadcast(mediaScanIntent);
            }
            return pdfUri;
        } finally {
            if (fos != null) fos.close();
        }
    }
}
