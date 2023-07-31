package by.instruction.papera;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnDrawListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FullView extends AppCompatActivity {

    String fileName;
    PDFView pdfView;

    private final Paint highlightPaint = new Paint();
    private final Map<Integer, List<RectF>> pageToHighlights = new HashMap<>();
    private SearchTask currentTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_view);

        pdfView = findViewById(R.id.pdfView);

        highlightPaint.setColor(Color.YELLOW);
        highlightPaint.setAlpha(120);
        highlightPaint.setStyle(Paint.Style.FILL);

        fileName = getIntent().getStringExtra("fileName");

        // Инициализация PDFBox (tom-roush)
        PDFBoxResourceLoader.init(getApplicationContext());

        pdfView.fromAsset(fileName + ".pdf")
                .enableAnnotationRendering(true)
                .enableAntialiasing(true)
                .scrollHandle(new DefaultScrollHandle(this))
                .onDraw(new OnDrawListener() {
                    @Override
                    public void onLayerDrawn(Canvas canvas, float pageWidth, float pageHeight, int displayedPage) {
                        List<RectF> rects = pageToHighlights.get(displayedPage);
                        if (rects == null || rects.isEmpty()) return;
                        for (RectF rn : rects) {
                            canvas.drawRect(
                                    rn.left * pageWidth,
                                    rn.top * pageHeight,
                                    rn.right * pageWidth,
                                    rn.bottom * pageHeight,
                                    highlightPaint
                            );
                        }
                    }
                })
                .load();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.actionbar_menu, menu);
        MenuItem item = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) item.getActionView();
        searchView.setQueryHint("Поиск в PDF");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { runSearch(q); return true; }
            @Override public boolean onQueryTextChange(String s) { return false; }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (currentTask != null) currentTask.cancel(true);
        super.onDestroy();
    }

    private void runSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            pageToHighlights.clear();
            pdfView.invalidate();
            return;
        }
        if (currentTask != null) currentTask.cancel(true);
        currentTask = new SearchTask(query.trim());
        currentTask.execute();
    }

    private class SearchTask extends AsyncTask<Void, Void, Map<Integer, List<RectF>>> {
        private final String query;
        private Exception error;

        SearchTask(String query) { this.query = query; }

        @Override
        protected Map<Integer, List<RectF>> doInBackground(Void... voids) {
            Map<Integer, List<RectF>> result = new HashMap<>();
            PDDocument document = null;
            try {
                InputStream is = getAssets().open(fileName + ".pdf");
                document = PDDocument.load(is);
                is.close();

                int pageCount = document.getNumberOfPages();
                for (int pageIndex = 0; pageIndex < pageCount && !isCancelled(); pageIndex++) {
                    PDPage page = document.getPage(pageIndex);
                    PDRectangle crop = page.getCropBox();
                    float pageW = crop.getWidth();
                    float pageH = crop.getHeight();
                    float offsetX = crop.getLowerLeftX();
                    float offsetY = crop.getLowerLeftY();

                    MatchCollector collector = new MatchCollector(
                            query.toLowerCase(Locale.getDefault()),
                            pageW, pageH, offsetX, offsetY
                    );
                    collector.setSortByPosition(true);
                    collector.setStartPage(pageIndex + 1);
                    collector.setEndPage(pageIndex + 1);
                    collector.getText(document);

                    List<RectF> rects = collector.getRectsNormalized();
                    if (!rects.isEmpty()) result.put(pageIndex, rects);
                }
            } catch (Exception e) {
                error = e;
            } finally {
                try { if (document != null) document.close(); } catch (Exception ignored) {}
            }
            return result;
        }

        @Override
        protected void onPostExecute(Map<Integer, List<RectF>> highlights) {
            if (error != null) {
                Toast.makeText(FullView.this, "Ошибка поиска: " + error.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            pageToHighlights.clear();
            pageToHighlights.putAll(highlights);
            pdfView.invalidate();

            int first = -1;
            for (int i = 0; i < 10000; i++) {
                if (pageToHighlights.containsKey(i) && !pageToHighlights.get(i).isEmpty()) { first = i; break; }
            }
            if (first >= 0) pdfView.jumpTo(first, true);
            else Toast.makeText(FullView.this, "Ничего не найдено", Toast.LENGTH_SHORT).show();
        }
    }

    private static class MatchCollector extends PDFTextStripper {
        private final String queryLower;
        private final float pageWidth, pageHeight;
        private final float offsetX, offsetY;

        // Подстройте при желании (0f — максимально «впритык»)
        private static final float PAD_X_FACTOR = 0.00f; // 0..0.02
        private static final float PAD_Y_FACTOR = 0.00f; // 0..0.02
        private static final float PAD_X_MAX = 0.5f;
        private static final float PAD_Y_MAX = 0.5f;

        private final List<RectF> rectsNormalized = new ArrayList<>();

        MatchCollector(String queryLower, float pageWidth, float pageHeight,
                       float offsetX, float offsetY) throws java.io.IOException {
            super();
            this.queryLower = queryLower;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        @Override
        protected void writeString(String text, List<TextPosition> tps) throws java.io.IOException {
            if (text == null || text.isEmpty() || tps == null || tps.isEmpty()) return;

            String lower = text.toLowerCase(Locale.getDefault());
            int from = 0;

            while (true) {
                int idx = lower.indexOf(queryLower, from);
                if (idx < 0) break;

                int start = idx, end = idx + queryLower.length() - 1;
                if (end >= tps.size()) { from = idx + 1; continue; }

                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                float avgH = 0f;

                for (int i = start; i <= end; i++) {
                    TextPosition tp = tps.get(i);
                    float xBL = tp.getXDirAdj() - offsetX;
                    float yBL = tp.getYDirAdj() - offsetY;
                    float w = tp.getWidthDirAdj();
                    float h = tp.getHeightDir();

                    if (xBL < minX) minX = xBL;
                    if (yBL < minY) minY = yBL;
                    if (xBL + w > maxX) maxX = xBL + w;
                    if (yBL + h > maxY) maxY = yBL + h;
                    avgH += h;
                }
                avgH /= (end - start + 1);

                float padX = Math.min(avgH * PAD_X_FACTOR, PAD_X_MAX);
                float padY = Math.min(avgH * PAD_Y_FACTOR, PAD_Y_MAX);

                float x0 = Math.max(0f, minX - padX);
                float y0 = Math.max(0f, minY - padY);
                float x1 = Math.min(pageWidth,  maxX + padX);
                float y1 = Math.min(pageHeight, maxY + padY);

                // bottom-left -> top-left
                float leftTL   = x0;
                float rightTL  = x1;
                float topTL    = pageHeight - y1;
                float bottomTL = pageHeight - y0;

                rectsNormalized.add(new RectF(
                        leftTL / pageWidth,
                        topTL / pageHeight,
                        rightTL / pageWidth,
                        bottomTL / pageHeight
                ));

                from = idx + 1;
            }
        }

        List<RectF> getRectsNormalized() { return rectsNormalized; }
    }
}