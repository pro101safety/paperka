package by.instruction.papera;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

// Класс для хранения информации о найденном результате поиска
class DocSearchResult {
    final int index;
    final String text;
    final int position;
    final int page;
    final String snippet;

    DocSearchResult(int index, String text, int position, int page, String snippet) {
        this.index = index;
        this.text = text;
        this.position = position;
        this.page = page;
        this.snippet = snippet != null ? snippet : text;
    }

    public int getIndex() {
        return index;
    }

    public String getText() {
        return text;
    }

    public int getPosition() {
        return position;
    }

    public int getPage() {
        return page;
    }

    public String getSnippet() {
        return snippet;
    }
}

public class FullView extends AppCompatActivity {

    private String fileName;
    private String docTitle;
    private int jumpToPage = -1; // Страница для перехода при открытии из закладки
    private WebView webView;
    
    // Панель поиска в документе
    private LinearLayout searchPanel;
    private EditText searchInput;
    private TextView searchCountText;
    private ImageButton btnSearchPrev;
    private ImageButton btnSearchNext;
    private ImageButton btnSearchResultsToggle;
    private ImageButton btnSearchClose;
    private CheckBox checkCaseSensitive;
    private CheckBox checkWholeWords;
    private ListView searchResultsList;
    private SearchSnippetAdapter searchSnippetAdapter;

    // Индикатор страниц
    private TextView pageIndicator;
    private int totalPages = 1;
    private int currentPage = 1;
    private long lastScrollTime = 0;
    private static final long SCROLL_THROTTLE_MS = 100;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    // Настройки поиска
    private boolean caseSensitive = false;
    private boolean wholeWordsOnly = false;
    private boolean searchPanelVisible = false;
    private boolean searchResultsListVisible = false;
    private boolean documentPageReady = false;
    private String pendingInitialSearchQuery;

    // Навигация по результатам поиска
    private final List<DocSearchResult> searchResults = new ArrayList<>();
    private int currentResultIndex = -1;
    private String lastSearchQuery = "";
    private String documentContent = "";
    private String documentHtml = "";
    private String tempHtmlFilePath = null;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchRunnable;
    private final AtomicInteger searchGeneration = new AtomicInteger(0);
    private boolean searchApiInjected = false;

    // Прогресс загрузки
    private ProgressBar loadingProgress;

    private MaterialToolbar toolbar;

    private static final class CachedDocument {
        final String text;
        final String html;

        CachedDocument(String text, String html) {
            this.text = text;
            this.html = html;
        }
    }

    // Кэш для оптимизации (LRU, не более 5 документов: текст + HTML при возможности)
    private static final int DOCUMENT_CACHE_MAX_ENTRIES = 5;
    private static final Map<String, CachedDocument> documentCache = Collections.synchronizedMap(
            new LinkedHashMap<String, CachedDocument>(DOCUMENT_CACHE_MAX_ENTRIES + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedDocument> eldest) {
                    return size() > DOCUMENT_CACHE_MAX_ENTRIES;
                }
            }
    );

    /** DOCX >= 256 KB — только XmlPullParser (POI раздувает heap и блокирует GC). */
    private static final long LIGHTWEIGHT_DOCX_THRESHOLD_BYTES = 256L * 1024L;
    /** Картинки больше порога пишем во временный файл, а не inline base64. */
    private static final int MAX_INLINE_IMAGE_BYTES = 32 * 1024;
    private static final int TEXT_CAP = 500_000;

    private ExecutorService docLoadExecutor;
    private volatile boolean loadCancelled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);
        setContentView(R.layout.activity_full_view);

        webView = findViewById(R.id.webView);

        setupSearchPanel();

        // Инициализация индикатора страниц
        pageIndicator = findViewById(R.id.pageIndicator);
        initializePageIndicator();

        // Настройка обработчика касаний для индикатора
        setupPageIndicatorTouchListener();

        // Инициализация прогресса загрузки
        loadingProgress = findViewById(R.id.loadingProgress);

        // Подключаем Toolbar как ActionBar, чтобы отрисовать меню (лупу/закладку)
        toolbar = findViewById(R.id.toolbar);
        EdgeToEdgeHelper.applyToolbarScreenInsets(toolbar, webView);
        if (toolbar != null && getSupportActionBar() == null) {
            setSupportActionBar(toolbar);
            // Устанавливаем кастомный заголовок с уменьшенным шрифтом
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.app_name));
                getSupportActionBar().setDisplayShowTitleEnabled(true);
            }
            toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);
        }

        fileName = getIntent().getStringExtra("fileName");
        docTitle = getIntent().getStringExtra("docTitle");
        jumpToPage = getIntent().getIntExtra("jumpToPage", -1);
        pendingInitialSearchQuery = getIntent().getStringExtra("initialSearchQuery");

        android.util.Log.d("BookmarkJump", "Получены параметры - fileName: " + fileName + ", jumpToPage: " + jumpToPage);

        if (fileName == null) {
            Toast.makeText(this, R.string.fullview_no_filename, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchPanelVisible) {
                    closeSearchPanel(true);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        // Загружаем DOC файл
        loadDocFile();
    }

    private void setupSearchPanel() {
        searchPanel = findViewById(R.id.searchPanel);
        searchInput = findViewById(R.id.searchInput);
        searchCountText = findViewById(R.id.searchCountText);
        btnSearchPrev = findViewById(R.id.btnSearchPrev);
        btnSearchNext = findViewById(R.id.btnSearchNext);
        btnSearchResultsToggle = findViewById(R.id.btnSearchResultsToggle);
        btnSearchClose = findViewById(R.id.btnSearchClose);
        checkCaseSensitive = findViewById(R.id.checkCaseSensitive);
        checkWholeWords = findViewById(R.id.checkWholeWords);
        searchResultsList = findViewById(R.id.searchResultsList);

        searchSnippetAdapter = new SearchSnippetAdapter();
        searchResultsList.setAdapter(searchSnippetAdapter);
        searchResultsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < searchResults.size()) {
                currentResultIndex = position;
                navigateToResult(position);
                setSearchResultsListVisible(false);
            }
        });

        btnSearchPrev.setOnClickListener(v -> navigateToPreviousResult());
        btnSearchNext.setOnClickListener(v -> navigateToNextResult());
        btnSearchClose.setOnClickListener(v -> closeSearchPanel(true));
        btnSearchResultsToggle.setOnClickListener(v ->
                setSearchResultsListVisible(!searchResultsListVisible));

        checkCaseSensitive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            caseSensitive = isChecked;
            if (searchPanelVisible) {
                scheduleSearch(searchInput.getText() != null ? searchInput.getText().toString() : "");
            }
        });
        checkWholeWords.setOnCheckedChangeListener((buttonView, isChecked) -> {
            wholeWordsOnly = isChecked;
            if (searchPanelVisible) {
                scheduleSearch(searchInput.getText() != null ? searchInput.getText().toString() : "");
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSearch = actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (isSearch) {
                String query = v.getText() != null ? v.getText().toString() : "";
                String normalized = DocumentSearchMatcher.normalizeQuery(query);
                if (!normalized.isEmpty()
                        && normalized.equals(lastSearchQuery)
                        && !searchResults.isEmpty()) {
                    navigateToNextResult();
                } else {
                    if (pendingSearchRunnable != null) {
                        searchHandler.removeCallbacks(pendingSearchRunnable);
                        pendingSearchRunnable = null;
                    }
                    runSearch(query, true);
                }
                return true;
            }
            return false;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleSearch(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        updateNavigationButtons();
    }

    private void loadDocFile() {
        showLoadingProgress();
        loadCancelled = false;

        if (docLoadExecutor == null || docLoadExecutor.isShutdown()) {
            docLoadExecutor = Executors.newSingleThreadExecutor();
        }

        docLoadExecutor.execute(() -> {
            DocLoadResult result;
            try {
                if (documentCache.containsKey(fileName)) {
                    CachedDocument cached = documentCache.get(fileName);
                    result = new DocLoadResult();
                    result.text = cached != null ? cached.text : "";
                    if (cached != null && cached.html != null && !cached.html.isEmpty()) {
                        result.html = cached.html;
                    } else {
                        result.html = convertToHtmlOptimized(result.text);
                    }
                } else {
                    result = loadDocumentFromAssets();
                }
            } catch (Exception e) {
                android.util.Log.e("DocRender", "Ошибка загрузки " + fileName, e);
                result = new DocLoadResult();
                result.text = "Ошибка загрузки файла: " + e.getMessage();
                result.html = convertToHtmlOptimized(result.text);
            }

            final DocLoadResult loadedResult = result;
            if (loadCancelled || isFinishing()) {
                deleteTempHtmlFile(loadedResult.htmlFilePath);
                return;
            }

            runOnUiThread(() -> applyDocLoadResult(loadedResult));
        });
    }

    private static final class DocLoadResult {
        String text = "";
        String html;
        String htmlFilePath;
    }

    private void applyDocLoadResult(DocLoadResult result) {
        if (isFinishing()) {
            deleteTempHtmlFile(result.htmlFilePath);
            return;
        }

        documentContent = result.text != null ? result.text : "";
        documentPageReady = false;

        if (result.htmlFilePath != null) {
            tempHtmlFilePath = result.htmlFilePath;
            documentHtml = null;
            if (documentContent != null
                    && !documentContent.startsWith("Ошибка загрузки")
                    && documentContent.length() < TEXT_CAP) {
                // HTML на диске с временными картинками — кэшируем только текст
                documentCache.put(fileName, new CachedDocument(documentContent, null));
            }
        } else {
            tempHtmlFilePath = null;
            if (result.html != null && !result.html.isEmpty()) {
                documentHtml = result.html;
            } else if (documentContent != null && !documentContent.isEmpty()) {
                documentHtml = convertToHtmlOptimized(documentContent);
            }
            if (documentContent != null
                    && !documentContent.startsWith("Ошибка загрузки")
                    && documentContent.length() < TEXT_CAP) {
                documentCache.put(fileName, new CachedDocument(documentContent, documentHtml));
            }
        }

        displayDocument();
        hideLoadingProgress();
        if (!searchPanelVisible) {
            restoreDocumentTitle();
        }
    }

    private DocLoadResult loadDocumentFromAssets() throws IOException, XmlPullParserException {
        DocLoadResult result = new DocLoadResult();
        InputStream is = getAssets().open(fileName);

        if (fileName.endsWith(".docx")) {
            java.io.File tempDocx = java.io.File.createTempFile("doc_src_", ".docx", getCacheDir());
            try (java.io.FileOutputStream fosCopy = new java.io.FileOutputStream(tempDocx)) {
                byte[] bufCopy = new byte[1 << 16];
                int rCopy;
                long totalBytes = 0L;
                while ((rCopy = is.read(bufCopy)) != -1) {
                    fosCopy.write(bufCopy, 0, rCopy);
                    totalBytes += rCopy;
                }
                is.close();

                if (totalBytes >= LIGHTWEIGHT_DOCX_THRESHOLD_BYTES) {
                    DocLoadResult light = renderDocxLightweight(tempDocx);
                    try { tempDocx.delete(); } catch (Throwable ignored) {}
                    return light;
                }

                try (java.io.FileInputStream fis = new java.io.FileInputStream(tempDocx)) {
                    DocLoadResult rich = renderDocxRich(fis);
                    try { tempDocx.delete(); } catch (Throwable ignored) {}
                    return rich;
                }
            }
        }

        HWPFDocument document = new HWPFDocument(is);
        Range range = document.getRange();
        result.text = range.text();
        result.html = convertToHtmlOptimized(result.text);
        document.close();
        is.close();
        return result;
    }

    private DocLoadResult renderDocxRich(java.io.InputStream fis) throws IOException {
        DocLoadResult result = new DocLoadResult();
        XWPFDocument document = new XWPFDocument(fis);
        java.io.File temp = java.io.File.createTempFile("doc_render_", ".html", getCacheDir());
        java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(temp), StandardCharsets.UTF_8);
        StringBuilder textContent = new StringBuilder();

        writer.write("<html><head><meta charset='UTF-8'>");
        writeDocHtmlStyles(writer);
        writer.write("</head><body>");

        try {
            renderBodyElements(document.getBodyElements(), writer, textContent, '\n');
        } finally {
            document.close();
        }

        writer.write("</body></html>");
        writer.flush();
        writer.close();

        result.text = textContent.toString();
        result.htmlFilePath = temp.getAbsolutePath();
        return result;
    }

    private void writeDocHtmlStyles(java.io.Writer writer) throws IOException {
        writer.write("<style>");
        writer.write("body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; font-size: 34px; }");
        writer.write("p { margin: 0 0 12px 0; }");
        writer.write(".doc-table-wrap { overflow-x: auto; -webkit-overflow-scrolling: touch; margin: 12px 0; }");
        writer.write("table { border-collapse: collapse; width: 100%; min-width: max-content; }");
        writer.write("td, th { border: 1px solid #ccc; padding: 8px; vertical-align: top; word-wrap: break-word; }");
        writer.write("td p { margin: 0 0 4px 0; }");
        writer.write("td p:last-child { margin-bottom: 0; }");
        writer.write("th { background: #f5f5f5; font-weight: bold; }");
        writer.write(".doc-image { margin: 20px 0; text-align: center; }");
        writer.write(".doc-image img { max-width: 100%; height: auto; }");
        writer.write("</style>");
    }

    private void renderBodyElements(List<IBodyElement> elements, java.io.Writer writer,
                                    StringBuilder textContent, char textSeparator) throws IOException {
        for (IBodyElement element : elements) {
            if (element.getElementType() == BodyElementType.PARAGRAPH) {
                appendParagraphToHtml((XWPFParagraph) element, writer, textContent, TEXT_CAP, textSeparator);
            } else if (element.getElementType() == BodyElementType.TABLE) {
                renderTableToHtml((XWPFTable) element, writer, textContent);
            }
        }
    }

    private void renderTableToHtml(XWPFTable table, java.io.Writer writer,
                                   StringBuilder textContent) throws IOException {
        writer.write("<div class='doc-table-wrap'><table>");
        for (XWPFTableRow row : table.getRows()) {
            writer.write("<tr>");
            for (XWPFTableCell cell : row.getTableCells()) {
                writer.write("<td>");
                renderBodyElements(cell.getBodyElements(), writer, textContent, '\t');
                writer.write("</td>");
            }
            writer.write("</tr>");
            if (textContent.length() < TEXT_CAP) {
                textContent.append('\n');
            }
        }
        writer.write("</table></div>");
    }

    private DocLoadResult renderDocxLightweight(java.io.File tempDocx) throws IOException, XmlPullParserException {
        DocLoadResult result = new DocLoadResult();
        java.io.File tempHtml = java.io.File.createTempFile("doc_light_", ".html", getCacheDir());
        StringBuilder textContent = new StringBuilder();

        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(tempDocx);
             java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                     new java.io.FileOutputStream(tempHtml), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) {
                throw new IOException("word/document.xml not found");
            }

            writer.write("<html><head><meta charset='UTF-8'>");
            writeDocHtmlStyles(writer);
            writer.write("</head><body>");

            try (java.io.InputStream xmlIn = zipFile.getInputStream(entry)) {
                XmlPullParser parser = android.util.Xml.newPullParser();
                parser.setInput(xmlIn, "UTF-8");

                final String wordNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
                StringBuilder curText = new StringBuilder();
                boolean inTextTag = false;
                int tableDepth = 0;
                int cellDepth = 0;

                for (int event = parser.getEventType();
                     event != XmlPullParser.END_DOCUMENT;
                     event = parser.next()) {
                    if (event == XmlPullParser.START_TAG) {
                        if (isWordTag(parser, wordNs, "tbl")) {
                            tableDepth++;
                            writer.write("<div class='doc-table-wrap'><table>");
                        } else if (isWordTag(parser, wordNs, "tr")) {
                            writer.write("<tr>");
                        } else if (isWordTag(parser, wordNs, "tc")) {
                            cellDepth++;
                            writer.write("<td>");
                        } else if (isWordTag(parser, wordNs, "p")) {
                            curText.setLength(0);
                        } else if (isWordTag(parser, wordNs, "t")) {
                            inTextTag = true;
                        } else if (isWordTag(parser, wordNs, "tab")) {
                            curText.append('\t');
                        } else if (isWordTag(parser, wordNs, "br")) {
                            curText.append('\n');
                        }
                    } else if (event == XmlPullParser.TEXT && inTextTag) {
                        curText.append(parser.getText());
                    } else if (event == XmlPullParser.END_TAG) {
                        if (isWordTag(parser, wordNs, "t")) {
                            inTextTag = false;
                        } else if (isWordTag(parser, wordNs, "p")) {
                            char separator = cellDepth > 0 ? '\t' : '\n';
                            appendLightweightParagraph(curText, writer, textContent, separator);
                        } else if (isWordTag(parser, wordNs, "tc")) {
                            writer.write("</td>");
                            cellDepth = Math.max(0, cellDepth - 1);
                        } else if (isWordTag(parser, wordNs, "tr")) {
                            writer.write("</tr>");
                            if (tableDepth > 0 && textContent.length() < TEXT_CAP) {
                                textContent.append('\n');
                            }
                        } else if (isWordTag(parser, wordNs, "tbl")) {
                            writer.write("</table></div>");
                            tableDepth = Math.max(0, tableDepth - 1);
                        }
                    }
                }
            }

            writer.write("</body></html>");
        }

        result.text = textContent.toString();
        result.htmlFilePath = tempHtml.getAbsolutePath();
        return result;
    }

    private boolean isWordTag(XmlPullParser parser, String wordNs, String localName) {
        if (!wordNs.equals(parser.getNamespace())) {
            return false;
        }
        String name = parser.getName();
        return localName.equals(name) || ("w:" + localName).equals(name);
    }

    private void appendLightweightParagraph(StringBuilder curText, java.io.Writer writer,
                                            StringBuilder textContent, char textSeparator) throws IOException {
        if (curText.length() == 0) {
            return;
        }
        String raw = curText.toString().trim();
        if (raw.isEmpty()) {
            return;
        }
        writer.write("<p>");
        writer.write(escapeHtml(raw));
        writer.write("</p>");
        if (textContent.length() < TEXT_CAP) {
            int remain = TEXT_CAP - textContent.length();
            String toAdd = raw;
            if (toAdd.length() > remain) {
                toAdd = toAdd.substring(0, remain);
            }
            textContent.append(toAdd).append(textSeparator);
        }
    }

    private void deleteTempHtmlFile(String path) {
        if (path == null) {
            return;
        }
        try {
            new java.io.File(path).delete();
        } catch (Throwable ignored) {
        }
    }

    private void cleanupRenderTempFiles() {
        java.io.File cacheDir = getCacheDir();
        if (cacheDir == null) {
            return;
        }
        String[] prefixes = {"docimg_", "doc_light_", "doc_src_"};
        java.io.File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }
        for (java.io.File file : files) {
            String name = file.getName();
            for (String prefix : prefixes) {
                if (name.startsWith(prefix)) {
                    try {
                        file.delete();
                    } catch (Throwable ignored) {
                    }
                    break;
                }
            }
        }
    }

    private String convertToHtmlOptimized(String text) {
        // Оптимизированное преобразование текста в HTML
        StringBuilder html = new StringBuilder(text.length() + 1000); // Предварительное выделение памяти
        
        html.append("<html><head><meta charset='UTF-8'>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; font-size: 34px; }")
            .append(".highlight { background-color: #FFEB3B !important; color: #000000 !important; padding: 2px 4px; border-radius: 3px; font-weight: bold; display: inline; }")
            .append(".highlight.active { background-color: #4CAF50 !important; color: #FFFFFF !important; padding: 3px 6px !important; border-radius: 5px !important; font-weight: bold !important; display: inline !important; border: 3px solid #2E7D32 !important; box-shadow: 0 2px 4px rgba(0,0,0,0.3) !important; }")
            .append(".doc-image { margin: 20px 0; text-align: center; }")
            .append(".doc-image img { max-width: 100%; height: auto; }")
            .append("</style></head><body>");
        
        // Оптимизированная обработка параграфов
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                html.append("<p>")
                    .append(escapeHtml(trimmed))
                    .append("</p>");
            }
        }
        
        html.append("</body></html>");
        return html.toString();
    }
    
    private String escapeHtml(String text) {
        // Оптимизированное экранирование HTML символов
        StringBuilder result = new StringBuilder(text.length() + text.length() / 4);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&': result.append("&amp;"); break;
                case '<': result.append("&lt;"); break;
                case '>': result.append("&gt;"); break;
                case '"': result.append("&quot;"); break;
                case '\'': result.append("&#39;"); break;
                default: result.append(c); break;
            }
        }
        return result.toString();
    }

    private void appendParagraphToHtml(XWPFParagraph paragraph, java.io.Writer writer,
                                       StringBuilder textContent, int textCap, char textSeparator) throws IOException {
        String paragraphText = paragraph.getText();
        if (paragraphText != null && !paragraphText.trim().isEmpty() && textContent.length() < textCap) {
            int toAppend = Math.min(paragraphText.length(), textCap - textContent.length());
            textContent.append(paragraphText, 0, toAppend).append(textSeparator);
        }
        writer.write("<p>");
        for (XWPFRun run : paragraph.getRuns()) {
            appendPictures(run, writer);
        }
        if (paragraphText != null && !paragraphText.isEmpty()) {
            writer.write(escapeHtml(paragraphText));
        }
        writer.write("</p>");
    }

    private void appendPictures(XWPFRun run, java.io.Writer writer) throws IOException {
        List<XWPFPicture> pictures = run.getEmbeddedPictures();
        if (pictures == null || pictures.isEmpty()) {
            return;
        }
        for (XWPFPicture picture : pictures) {
            String imageSrc = buildImageSrc(picture);
            if (imageSrc == null) {
                continue;
            }
            writer.write("<div class='doc-image'><img src='");
            writer.write(imageSrc);
            writer.write("' alt='' /></div>");
        }
    }

    private String buildImageSrc(XWPFPicture picture) {
        if (picture == null) {
            return null;
        }
        try {
            XWPFPictureData pictureData = picture.getPictureData();
            if (pictureData == null) {
                return null;
            }
            byte[] bytes = pictureData.getData();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            String ext = pictureData.suggestFileExtension();
            if (ext == null || ext.isEmpty()) {
                ext = "bin";
            }
            if (bytes.length <= MAX_INLINE_IMAGE_BYTES) {
                String mime = guessMimeType(ext);
                if (mime == null || mime.trim().isEmpty()) {
                    mime = "application/octet-stream";
                }
                return "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
            }
            java.io.File imgFile = java.io.File.createTempFile("docimg_", "." + ext, getCacheDir());
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(imgFile)) {
                fos.write(bytes);
            }
            return "file://" + imgFile.getAbsolutePath();
        } catch (Exception e) {
            android.util.Log.e("DocRender", "Не удалось подготовить изображение", e);
            return null;
        }
    }

    private String guessMimeType(String extension) {
        if (extension == null) {
            return null;
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        switch (ext) {
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            case "svg":
            case "svgz":
                return "image/svg+xml";
            default:
                return null;
        }
    }

    private void displayDocument() {
        documentPageReady = false;
        searchApiInjected = false;
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                android.util.Log.d("PageIndicator", "Страница загружена");
                documentPageReady = true;

                // Инициализируем индикатор страниц после загрузки
                initializePageIndicator();

                // Если нужно перейти к определенной странице (из закладки)
                if (jumpToPage >= 0) {
                    android.util.Log.d("BookmarkJump", "Переход к закладке на странице: " + jumpToPage);
                    jumpToBookmarkPage(jumpToPage);
                }

                if (pendingInitialSearchQuery != null && !pendingInitialSearchQuery.trim().isEmpty()) {
                    String query = pendingInitialSearchQuery;
                    pendingInitialSearchQuery = null;
                    openSearchPanel(query);
                } else if (searchPanelVisible && lastSearchQuery != null && !lastSearchQuery.isEmpty()) {
                    applyHighlightsInWebView(lastSearchQuery, true);
                }
            }
        });
        if (tempHtmlFilePath != null) {
            webView.loadUrl("file://" + tempHtmlFilePath);
        } else {
            webView.loadDataWithBaseURL(null, documentHtml, "text/html", "UTF-8", null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.actionbar_menu, menu);
        
        // Инициализация кнопок навигации
        updateNavigationButtons();
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.search) {
            openSearchPanel(lastSearchQuery);
            return true;
        } else if (id == R.id.add_bookmark) {
            // Получаем текущую позицию прокрутки и небольшой текстовый сниппет из WebView
            webView.post(() -> {
                webView.evaluateJavascript(
                    "(function() {" +
                    "var scrollTop = window.pageYOffset || document.documentElement.scrollTop;" +
                    "var windowHeight = window.innerHeight;" +
                    "var documentHeight = document.body.scrollHeight;" +
                    "var scrollPercent = scrollTop / (documentHeight - windowHeight);" +
                    "var pagesPerScreen = documentHeight / windowHeight;" +
                    "var estimatedPages = Math.max(1, Math.ceil(pagesPerScreen));" +
                    "estimatedPages = Math.min(estimatedPages, 500);" +
                    "var estimatedPage = Math.max(1, Math.round(scrollPercent * estimatedPages) + 1);" +
                    // Сниппет: ищем ближайший видимый параграф и берем первые 80 символов
                    "var snippet = '';" +
                    "var paragraphs = document.querySelectorAll('p');" +
                    "for (var i = 0; i < paragraphs.length; i++) {" +
                    "  var rect = paragraphs[i].getBoundingClientRect();" +
                    "  if (rect.bottom > 0 && rect.top < window.innerHeight) {" +
                    "    snippet = (paragraphs[i].innerText || '').trim();" +
                    "    if (snippet.length > 120) snippet = snippet.substring(0, 120) + '…';" +
                    "    break;" +
                    "  }" +
                    "}" +
                    "return JSON.stringify({page: estimatedPage, snippet: snippet});" +
                    "})()",
                    (result) -> {
                        try {
                            String clean = result == null ? "" : result.trim();
                            if (clean.startsWith("\"")) clean = clean.substring(1, clean.length() - 1);
                            clean = clean.replace("\\\"", "\"");
                            org.json.JSONObject obj = new org.json.JSONObject(clean);
                            int currentPage = obj.optInt("page", 1);
                            String snippet = obj.optString("snippet", "");

                            String displayName = (docTitle != null && !docTitle.isEmpty()) ? docTitle : fileName;
                            String title = displayName + ": документ";
                            BookmarkStore.addBookmark(FullView.this, fileName, currentPage - 1, title, snippet);

                            runOnUiThread(() -> {
                                String toastText = snippet == null || snippet.isEmpty()
                                        ? ("Закладка сохранена на странице " + currentPage)
                                        : ("Закладка сохранена: \"" + (snippet.length() > 40 ? snippet.substring(0, 40) + "…" : snippet) + "\"");
                                Toast.makeText(FullView.this, toastText, Toast.LENGTH_SHORT).show();
                            });
                        } catch (Throwable e) {
                            android.util.Log.e("BookmarkDebug", "Error creating bookmark with snippet: " + result, e);
                            String displayName = (docTitle != null && !docTitle.isEmpty()) ? docTitle : fileName;
                            String title = displayName + ": документ";
                            BookmarkStore.addBookmark(FullView.this, fileName, 0, title);
                            runOnUiThread(() -> Toast.makeText(FullView.this, R.string.fullview_bookmark_saved, Toast.LENGTH_SHORT).show());
                        }
                    }
                );
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        loadCancelled = true;
        if (pendingSearchRunnable != null) {
            searchHandler.removeCallbacks(pendingSearchRunnable);
            pendingSearchRunnable = null;
        }
        closeOptionsMenu();
        if (toolbar != null) {
            toolbar.dismissPopupMenus();
        }
        if (docLoadExecutor != null) {
            docLoadExecutor.shutdownNow();
            docLoadExecutor = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        deleteTempHtmlFile(tempHtmlFilePath);
        tempHtmlFilePath = null;
        cleanupRenderTempFiles();
        super.onDestroy();
    }

    private void scheduleSearch(String query) {
        if (pendingSearchRunnable != null) {
            searchHandler.removeCallbacks(pendingSearchRunnable);
        }
        pendingSearchRunnable = () -> runSearch(query, false);
        searchHandler.postDelayed(pendingSearchRunnable, SEARCH_DEBOUNCE_MS);
    }

    private void openSearchPanel(String prefillQuery) {
        searchPanelVisible = true;
        if (searchPanel != null) {
            searchPanel.setVisibility(View.VISIBLE);
        }
        if (searchInput != null) {
            String value = prefillQuery != null ? prefillQuery : "";
            Editable current = searchInput.getText();
            if (current == null || !value.contentEquals(current)) {
                searchInput.setText(value);
                if (searchInput.getText() != null) {
                    searchInput.setSelection(searchInput.getText().length());
                }
            } else if (!value.isEmpty()) {
                if (pendingSearchRunnable != null) {
                    searchHandler.removeCallbacks(pendingSearchRunnable);
                    pendingSearchRunnable = null;
                }
                runSearch(value, true);
            }
            searchInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
        updateNavigationButtons();
    }

    private void closeSearchPanel(boolean clearHighlights) {
        searchPanelVisible = false;
        if (pendingSearchRunnable != null) {
            searchHandler.removeCallbacks(pendingSearchRunnable);
            pendingSearchRunnable = null;
        }
        if (searchPanel != null) {
            searchPanel.setVisibility(View.GONE);
        }
        setSearchResultsListVisible(false);
        hideKeyboard();
        if (clearHighlights) {
            clearSearchState(true);
        }
        restoreDocumentTitle();
    }

    private void setSearchResultsListVisible(boolean visible) {
        searchResultsListVisible = visible && !searchResults.isEmpty();
        if (searchResultsList != null) {
            searchResultsList.setVisibility(searchResultsListVisible ? View.VISIBLE : View.GONE);
        }
        if (btnSearchResultsToggle != null) {
            btnSearchResultsToggle.setAlpha(!searchResults.isEmpty() ? 1f : 0.35f);
            btnSearchResultsToggle.setEnabled(!searchResults.isEmpty());
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null && searchInput != null) {
            focus = searchInput;
        }
        if (focus == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private void runSearch(String query, boolean announceEmpty) {
        String normalizedQuery = DocumentSearchMatcher.normalizeQuery(query);
        final int generation = searchGeneration.incrementAndGet();

        if (normalizedQuery.isEmpty()) {
            lastSearchQuery = "";
            // Не вызываем clearSearchState() — он снова bump'ает generation.
            searchResults.clear();
            currentResultIndex = -1;
            if (searchSnippetAdapter != null) {
                searchSnippetAdapter.notifyDataSetChanged();
            }
            setSearchResultsListVisible(false);
            updateSearchCountUi();
            updateNavigationButtons();
            clearHighlightsInWebView();
            return;
        }

        lastSearchQuery = normalizedQuery;
        final String contentSnapshot = documentContent != null ? documentContent : "";
        final boolean caseFlag = caseSensitive;
        final boolean wholeFlag = wholeWordsOnly;

        android.util.Log.d("DocSearch", "runSearch q=\"" + normalizedQuery
                + "\" contentLen=" + contentSnapshot.length()
                + " gen=" + generation);

        if (contentSnapshot.isEmpty()) {
            applySearchResults(normalizedQuery, Collections.emptyList(), announceEmpty);
            return;
        }

        if (docLoadExecutor == null || docLoadExecutor.isShutdown()) {
            docLoadExecutor = Executors.newSingleThreadExecutor();
        }

        docLoadExecutor.execute(() -> {
            final List<DocSearchResult> found;
            try {
                found = DocumentSearchMatcher.findMatches(
                        contentSnapshot, normalizedQuery, caseFlag, wholeFlag);
            } catch (Throwable t) {
                android.util.Log.e("DocSearch", "Ошибка поиска", t);
                searchHandler.post(() -> {
                    if (generation != searchGeneration.get() || isFinishing()) {
                        return;
                    }
                    applySearchResults(normalizedQuery, Collections.emptyList(), announceEmpty);
                });
                return;
            }

            android.util.Log.d("DocSearch", "found=" + found.size() + " gen=" + generation);
            searchHandler.post(() -> {
                if (generation != searchGeneration.get() || isFinishing()) {
                    android.util.Log.d("DocSearch", "drop stale results gen=" + generation
                            + " current=" + searchGeneration.get());
                    return;
                }
                applySearchResults(normalizedQuery, found, announceEmpty);
            });
        });
    }

    private void applySearchResults(String normalizedQuery, List<DocSearchResult> found,
                                    boolean announceEmpty) {
        searchResults.clear();
        if (found != null) {
            searchResults.addAll(found);
        }
        currentResultIndex = searchResults.isEmpty() ? -1 : 0;

        if (searchSnippetAdapter != null) {
            searchSnippetAdapter.notifyDataSetChanged();
        }
        if (searchResults.size() >= DocumentSearchMatcher.MAX_RESULTS) {
            Toast.makeText(this,
                    getString(R.string.search_too_many_results, DocumentSearchMatcher.MAX_RESULTS),
                    Toast.LENGTH_SHORT).show();
        } else if (searchResults.isEmpty()) {
            setSearchResultsListVisible(false);
            if (announceEmpty) {
                Toast.makeText(this, R.string.search_nothing_found, Toast.LENGTH_SHORT).show();
            }
            clearHighlightsInWebView();
        }

        updateSearchCountUi();
        updateNavigationButtons();

        if (!searchResults.isEmpty()) {
            applyHighlightsInWebView(normalizedQuery, true);
        }
    }

    private void clearSearchState(boolean clearDomHighlights) {
        searchGeneration.incrementAndGet();
        searchResults.clear();
        currentResultIndex = -1;
        lastSearchQuery = "";
        if (searchSnippetAdapter != null) {
            searchSnippetAdapter.notifyDataSetChanged();
        }
        setSearchResultsListVisible(false);
        updateSearchCountUi();
        updateNavigationButtons();
        if (clearDomHighlights) {
            clearHighlightsInWebView();
        }
    }

    private void ensureSearchApi(Runnable afterReady) {
        if (webView == null || !documentPageReady) {
            android.util.Log.w("DocSearch", "ensureSearchApi skipped ready=" + documentPageReady);
            return;
        }
        if (searchApiInjected) {
            afterReady.run();
            return;
        }
        // Важно: скрипт должен возвращать примитив (true), не объект с функциями
        webView.evaluateJavascript(DocumentSearchMatcher.highlightApiJavaScript(), value -> {
            android.util.Log.d("DocSearch", "search api inject result=" + value);
            searchApiInjected = true;
            if (!isFinishing()) {
                afterReady.run();
            }
        });
    }

    private void applyHighlightsInWebView(String query, boolean activateFirst) {
        if (webView == null || !documentPageReady) {
            return;
        }
        final String normalizedQuery = DocumentSearchMatcher.normalizeQuery(query);
        final boolean caseFlag = caseSensitive;
        final boolean wholeFlag = wholeWordsOnly;
        ensureSearchApi(() -> {
            if (webView == null || isFinishing()) {
                return;
            }
            // Передаём обычную строку, не regex
            String script = "(function(){"
                    + "try{"
                    + "if(!window.__paperkaSearch){return -1;}"
                    + "return window.__paperkaSearch.highlight("
                    + org.json.JSONObject.quote(normalizedQuery) + ","
                    + caseFlag + ","
                    + DocumentSearchMatcher.MAX_DOM_HIGHLIGHTS + ","
                    + wholeFlag + ");"
                    + "}catch(e){return -2;}"
                    + "})()";
            webView.evaluateJavascript(script, value -> {
                android.util.Log.d("DocSearch", "highlight result=" + value);
                if (isFinishing()) {
                    return;
                }
                // Если API пропал после перезагрузки страницы — переинъектим один раз
                if ("-1".equals(value) || "null".equals(value)) {
                    searchApiInjected = false;
                }
                if (activateFirst && !searchResults.isEmpty()) {
                    currentResultIndex = 0;
                    navigateToResult(0);
                } else {
                    updateSearchCountUi();
                    updateNavigationButtons();
                }
            });
        });
    }

    private void clearHighlightsInWebView() {
        if (webView == null || !documentPageReady) {
            return;
        }
        if (!searchApiInjected) {
            return;
        }
        webView.evaluateJavascript(
                "(function(){try{if(window.__paperkaSearch){window.__paperkaSearch.clear();}return true;}catch(e){return false;}})()",
                null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Обработка клавиш для навигации по результатам поиска
        if (searchPanelVisible && !searchResults.isEmpty()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_PAGE_UP:
                    navigateToPreviousResult();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    navigateToNextResult();
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateNavigationButtons() {
        if (btnSearchPrev == null || btnSearchNext == null) {
            return;
        }

        boolean hasResults = !searchResults.isEmpty();
        btnSearchPrev.setEnabled(hasResults);
        btnSearchNext.setEnabled(hasResults);
        btnSearchPrev.setAlpha(hasResults ? 1.0f : 0.35f);
        btnSearchNext.setAlpha(hasResults ? 1.0f : 0.35f);
        if (btnSearchResultsToggle != null) {
            btnSearchResultsToggle.setEnabled(hasResults);
            btnSearchResultsToggle.setAlpha(hasResults ? 1f : 0.35f);
        }
    }

    private void navigateToPreviousResult() {
        if (searchResults.isEmpty()) {
            return;
        }
        if (currentResultIndex <= 0) {
            currentResultIndex = searchResults.size() - 1;
            Toast.makeText(this, R.string.search_wrapped_to_end, Toast.LENGTH_SHORT).show();
        } else {
            currentResultIndex--;
        }
        navigateToResult(currentResultIndex);
    }

    private void navigateToNextResult() {
        if (searchResults.isEmpty()) {
            return;
        }
        if (currentResultIndex >= searchResults.size() - 1) {
            currentResultIndex = 0;
            Toast.makeText(this, R.string.search_wrapped_to_start, Toast.LENGTH_SHORT).show();
        } else {
            currentResultIndex++;
        }
        navigateToResult(currentResultIndex);
    }

    private void navigateToResult(int index) {
        if (index < 0 || index >= searchResults.size() || webView == null) {
            return;
        }
        currentResultIndex = index;
        updateSearchCountUi();
        updateNavigationButtons();
        if (searchSnippetAdapter != null) {
            searchSnippetAdapter.notifyDataSetChanged();
        }

        final int charPos = searchResults.get(index).getPosition();
        final int contentLen = Math.max(1, documentContent != null ? documentContent.length() : 1);
        ensureSearchApi(() -> {
            if (webView == null || isFinishing()) {
                return;
            }
            webView.evaluateJavascript(
                    "(function(){try{"
                            + "if(window.__paperkaSearch&&window.__paperkaSearch.activate(" + index + ")){return true;}"
                            + "var h=document.body?document.body.scrollHeight:0;"
                            + "var wh=window.innerHeight||1;"
                            + "var p=" + charPos + "/" + contentLen + ";"
                            + "window.scrollTo(0,Math.max(0,(h-wh)*p));"
                            + "return false;"
                            + "}catch(e){return false;}})()",
                    null);
        });
    }

    private void updateSearchCountUi() {
        if (searchCountText == null) {
            return;
        }
        if (lastSearchQuery == null || lastSearchQuery.isEmpty()) {
            searchCountText.setVisibility(View.GONE);
            restoreDocumentTitle();
            return;
        }
        searchCountText.setVisibility(View.VISIBLE);
        if (searchResults.isEmpty()) {
            searchCountText.setText(R.string.search_count_zero);
            restoreDocumentTitle();
        } else {
            int shown = Math.max(1, currentResultIndex + 1);
            searchCountText.setText(getString(R.string.search_count_format, shown, searchResults.size()));
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(
                        getString(R.string.search_count_format, shown, searchResults.size()));
            }
        }
    }

    private void restoreDocumentTitle() {
        String displayName = (docTitle != null && !docTitle.isEmpty()) ? docTitle : fileName;
        if (getSupportActionBar() != null && displayName != null) {
            getSupportActionBar().setTitle(displayName);
        }
    }

    private final class SearchSnippetAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return searchResults.size();
        }

        @Override
        public DocSearchResult getItem(int position) {
            return searchResults.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_search_snippet, parent, false);
            }
            DocSearchResult item = getItem(position);
            TextView indexView = view.findViewById(R.id.snippetIndex);
            TextView textView = view.findViewById(R.id.snippetText);
            indexView.setText(getString(R.string.search_snippet_index, position + 1));
            textView.setText(item.getSnippet());
            view.setBackgroundColor(position == currentResultIndex ? 0x22309F6B : 0x00000000);
            return view;
        }
    }
    
    private void jumpToBookmarkPage(int pageIndex) {
        if (webView == null) return;
        
        android.util.Log.d("BookmarkJump", "jumpToBookmarkPage вызван с pageIndex: " + pageIndex + ", totalPages: " + totalPages);
        
        // Проверяем, что totalPages инициализирован
        if (totalPages <= 0) {
            android.util.Log.w("BookmarkJump", "totalPages не инициализирован, ждем инициализации...");
            // Повторяем попытку через 500мс
            webView.postDelayed(() -> jumpToBookmarkPage(pageIndex), 500);
            return;
        }
        
        // Добавляем небольшую задержку, чтобы WebView успел полностью загрузиться
        webView.postDelayed(() -> {
            webView.evaluateJavascript(
                "(function() {" +
                "var windowHeight = window.innerHeight;" +
                "var documentHeight = document.body.scrollHeight;" +
                "var targetPage = " + (pageIndex + 1) + ";" + // pageIndex начинается с 0, но страницы с 1
                "var totalPages = " + totalPages + ";" +
                "var scrollPercent = (targetPage - 1) / (totalPages - 1);" +
                "var targetScrollTop = scrollPercent * (documentHeight - windowHeight);" +
                "console.log('Jumping to page ' + targetPage + ', scrollPercent: ' + scrollPercent + ', targetScrollTop: ' + targetScrollTop);" +
                "window.scrollTo(0, targetScrollTop);" +
                "return targetPage;" +
                "})()",
                (result) -> {
                    try {
                        String cleanResult = result.replaceAll("\"", "");
                        int newPage = Integer.parseInt(cleanResult);
                        currentPage = newPage;
                        updatePageIndicator();
                        showPageIndicator();
                        
                        android.util.Log.d("BookmarkJump", "Переход выполнен к странице: " + newPage);
                        
                        // Показываем уведомление о переходе к закладке
                        runOnUiThread(() -> {
                            Toast.makeText(FullView.this, getString(R.string.fullview_bookmark_jump_page, newPage), Toast.LENGTH_SHORT).show();
                        });
                    } catch (NumberFormatException e) {
                        android.util.Log.e("BookmarkJump", "Ошибка парсинга результата: " + result, e);
                    }
                }
            );
        }, 1000); // Задержка 1 секунда для полной загрузки
    }
    
    // Методы для работы с индикатором страниц
    private void initializePageIndicator() {
        if (pageIndicator == null) return;
        
        // Получаем информацию о страницах из WebView
        webView.post(() -> {
            webView.evaluateJavascript(
                "(function() {" +
                "var windowHeight = window.innerHeight;" +
                "var documentHeight = document.body.scrollHeight;" +
                "var pagesPerScreen = documentHeight / windowHeight;" +
                "var estimatedPages = Math.max(1, Math.ceil(pagesPerScreen));" +
                "estimatedPages = Math.min(estimatedPages, 500);" +
                "return estimatedPages;" +
                "})()",
                (result) -> {
                    try {
                        String cleanResult = result.replaceAll("\"", "");
                        totalPages = Integer.parseInt(cleanResult);
                        currentPage = 1;
                        updatePageIndicator();
                        showPageIndicator();
                    } catch (NumberFormatException e) {
                        totalPages = 1;
                        currentPage = 1;
                        updatePageIndicator();
                    }
                }
            );
        });
        
        // Добавляем слушатель прокрутки
        addScrollListener();
    }
    
    private void addScrollListener() {
        if (webView == null) return;
        
        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScrollTime < SCROLL_THROTTLE_MS) {
                return; // Ограничиваем частоту обновлений
            }
            lastScrollTime = currentTime;
            
            updatePageIndicatorFromScroll(scrollY);
        });
    }
    
    private void updatePageIndicatorFromScroll(int scrollY) {
        if (webView == null) return;
        
        webView.post(() -> {
            webView.evaluateJavascript(
                "(function() {" +
                "var scrollTop = " + scrollY + ";" +
                "var windowHeight = window.innerHeight;" +
                "var documentHeight = document.body.scrollHeight;" +
                "var scrollPercent = scrollTop / (documentHeight - windowHeight);" +
                "var pagesPerScreen = documentHeight / windowHeight;" +
                "var estimatedPages = Math.max(1, Math.ceil(pagesPerScreen));" +
                "estimatedPages = Math.min(estimatedPages, 500);" +
                "var estimatedPage = Math.max(1, Math.round(scrollPercent * estimatedPages) + 1);" +
                "return estimatedPage;" +
                "})()",
                (result) -> {
                    try {
                        String cleanResult = result.replaceAll("\"", "");
                        int newPage = Integer.parseInt(cleanResult);
                        if (newPage != currentPage) {
                            currentPage = newPage;
                            updatePageIndicator();
                            showPageIndicator();
                        }
                    } catch (NumberFormatException e) {
                        // Игнорируем ошибки парсинга
                    }
                }
            );
        });
    }
    
    private void updatePageIndicator() {
        if (pageIndicator == null) return;
        
        if (currentPage >= 1 && currentPage <= totalPages) {
            pageIndicator.setText(String.valueOf(currentPage));
        }
    }
    
    private void showPageIndicator() {
        if (pageIndicator == null) return;
        
        pageIndicator.setVisibility(View.VISIBLE);
        
        // Скрываем индикатор через 3 секунды
        pageIndicator.removeCallbacks(hideIndicatorRunnable);
        pageIndicator.postDelayed(hideIndicatorRunnable, 3000);
    }
    
    private final Runnable hideIndicatorRunnable = () -> {
        if (pageIndicator != null) {
            pageIndicator.setVisibility(View.GONE);
        }
    };
    
    private void setupPageIndicatorTouchListener() {
        if (pageIndicator == null) return;
        
        pageIndicator.setOnClickListener(v -> {
            // При нажатии на индикатор показываем диалог выбора страницы
            showPageSelectionDialog();
        });
    }
    
    private void showPageSelectionDialog() {
        if (totalPages <= 1) {
            Toast.makeText(this, R.string.fullview_single_page_only, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Создаем массив страниц для выбора
        String[] pageNumbers = new String[totalPages];
        for (int i = 0; i < totalPages; i++) {
            pageNumbers[i] = "Страница " + (i + 1);
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Перейти к странице")
            .setItems(pageNumbers, (dialog, which) -> {
                scrollToPage(which + 1);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
    
    private void scrollToPage(int pageNumber) {
        if (webView == null || pageNumber < 1 || pageNumber > totalPages) return;
        
        webView.post(() -> {
            webView.evaluateJavascript(
                "(function() {" +
                "var windowHeight = window.innerHeight;" +
                "var documentHeight = document.body.scrollHeight;" +
                "var targetPage = " + pageNumber + ";" +
                "var totalPages = " + totalPages + ";" +
                "var scrollPercent = (targetPage - 1) / (totalPages - 1);" +
                "var targetScrollTop = scrollPercent * (documentHeight - windowHeight);" +
                "window.scrollTo(0, targetScrollTop);" +
                "return targetPage;" +
                "})()",
                (result) -> {
                    try {
                        String cleanResult = result.replaceAll("\"", "");
                        int newPage = Integer.parseInt(cleanResult);
                        currentPage = newPage;
                        updatePageIndicator();
                        showPageIndicator();
                    } catch (NumberFormatException e) {
                        // Игнорируем ошибки парсинга
                    }
                }
            );
        });
    }
    
    // Методы для управления прогрессом загрузки
    private void showLoadingProgress() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }
        if (webView != null) {
            webView.setVisibility(View.GONE);
        }
    }
    
    private void hideLoadingProgress() {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.GONE);
        }
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
        }
    }
}