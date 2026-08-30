package by.instruction.papera;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LocalDocumentRetriever {
    private static final String TAG = "LocalRetriever";
    private static final String QUERY_SYNONYMS_ASSET = "query_synonyms.json";
    private static final int CHUNK_CHAR_LIMIT = 700;
    private static final int MAX_DOC_TEXT = 25000;
    private static final int MAX_SNIPPET_CHARS = 180;
    private static final int MAX_INDEX_CHUNKS = 2500;
    private static final Object SYNONYMS_LOCK = new Object();
    private static volatile boolean synonymsLoaded = false;

    private static final Object INDEX_LOCK = new Object();
    private static final List<DocumentChunk> SHARED_INDEX = new ArrayList<>();
    private static final Map<String, Integer> TERM_DF = new HashMap<>();
    private static final LocalDocumentQueryEngine SHARED_ENGINE = new LocalDocumentQueryEngine();
    private static volatile boolean indexed = false;

    private final Context appContext;
    private final LocalDocumentQueryEngine engine = SHARED_ENGINE;
    private final VoiceSearchIndex voiceSearchIndex;

    public LocalDocumentRetriever(Context context) {
        this.appContext = context.getApplicationContext();
        this.voiceSearchIndex = new VoiceSearchIndex(this.appContext, engine);
        ensureSynonymsLoaded();
    }

    private void ensureSynonymsLoaded() {
        if (synonymsLoaded) {
            return;
        }
        synchronized (SYNONYMS_LOCK) {
            if (synonymsLoaded) {
                return;
            }
            try {
                loadSynonymsFromAsset(appContext.getAssets());
                Log.d(TAG, "Словарь запросов загружен из assets/" + QUERY_SYNONYMS_ASSET);
            } catch (Exception e) {
                Log.w(TAG, "Не удалось загрузить словарь из assets, используем встроенный", e);
            } finally {
                synonymsLoaded = true;
            }
        }
    }

    public void ensureIndexBuilt() {
        if (indexed) {
            return;
        }
        synchronized (INDEX_LOCK) {
            if (indexed) {
                return;
            }
            DocumentSectionRegistry.ensureLoaded();
            buildIndex();
            indexed = true;
        }
    }

    public List<DocumentChunk> retrieveTopChunks(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        DocumentSectionRegistry.ensureLoaded();
        if (voiceSearchIndex.isReady()) {
            List<DocumentChunk> candidates = voiceSearchIndex.loadCandidates(query);
            Log.d(TAG, "Голосовой поиск по FTS, кандидатов: " + candidates.size());
            return engine.mergeCatalogAndBody(
                    query, ChapterCatalog.build(), candidates, engine.buildDf(candidates), limit);
        }
        try {
            ensureIndexBuilt();
        } catch (Throwable t) {
            Log.e(TAG, "Индекс не построен из-за ошибки", t);
            return engine.mergeCatalogAndBody(
                    query, ChapterCatalog.build(), Collections.emptyList(),
                    Collections.emptyMap(), limit);
        }
        return engine.mergeCatalogAndBody(query, ChapterCatalog.build(), SHARED_INDEX, TERM_DF, limit);
    }

    String pickHighlightQuery(String query) {
        return engine.pickHighlightQuery(query);
    }

    public List<SearchHit> toSources(List<DocumentChunk> chunks, int maxCount, String query) {
        List<SearchHit> sources = new ArrayList<>();
        if (chunks == null) {
            return sources;
        }
        for (DocumentChunk chunk : chunks) {
            if (sources.size() >= maxCount) {
                break;
            }
            String snippet = engine.createRelevantSnippet(chunk.text, query, MAX_SNIPPET_CHARS);
            sources.add(new SearchHit(chunk.documentName, snippet.replaceAll("\\s+", " ").trim(), chunk.fileKey));
        }
        return sources;
    }

    private void buildIndex() {
        SHARED_INDEX.clear();
        TERM_DF.clear();
        try {
            AssetManager assets = appContext.getAssets();
            String[] rootFiles = assets.list("");
            if (rootFiles == null) {
                return;
            }
            Arrays.sort(rootFiles);
            for (String fileName : rootFiles) {
                if (fileName == null) {
                    continue;
                }
                String normalized = fileName.toLowerCase(Locale.ROOT);
                if (!normalized.endsWith(".doc") && !normalized.endsWith(".docx")) {
                    continue;
                }
                String text = extractTextFromAsset(assets, fileName);
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }
                addChunks(fileName, text);
                if (SHARED_INDEX.size() >= MAX_INDEX_CHUNKS) {
                    Log.w(TAG, "Достигнут лимит чанков индекса, дальнейшие документы пропущены");
                    break;
                }
            }
            rebuildDfStats();
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "Недостаточно памяти при построении индекса, используем частичный индекс", oom);
            rebuildDfStats();
        } catch (Exception e) {
            Log.e(TAG, "Не удалось построить индекс документов", e);
            rebuildDfStats();
        }
    }

    private String extractTextFromAsset(AssetManager assets, String fileName) {
        try (InputStream is = assets.open(fileName)) {
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
                return parseDocx(is);
            }
            return parseDoc(is);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OOM при парсинге документа: " + fileName, oom);
            return "";
        } catch (Exception e) {
            Log.e(TAG, "Ошибка парсинга документа: " + fileName, e);
            return "";
        }
    }

    private String parseDocx(InputStream is) throws Exception {
        // Легковесный парсер: читаем только word/document.xml из zip,
        // чтобы избежать высокой нагрузки памяти от полной XWPF-модели.
        StringBuilder sb = new StringBuilder(Math.min(MAX_DOC_TEXT, 8192));
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(zis, java.nio.charset.StandardCharsets.UTF_8));
                String line;
                StringBuilder para = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    int idx = 0;
                    while (idx < line.length()) {
                        int tStart = line.indexOf("<w:t", idx);
                        int pEnd = line.indexOf("</w:p>", idx);
                        if (pEnd >= 0 && (tStart < 0 || pEnd < tStart)) {
                            if (para.length() > 0) {
                                appendCapped(sb, para.toString());
                                appendCapped(sb, "\n");
                                para.setLength(0);
                                if (sb.length() >= MAX_DOC_TEXT) {
                                    return sb.toString();
                                }
                            }
                            idx = pEnd + 6;
                            continue;
                        }
                        if (tStart < 0) {
                            break;
                        }
                        int tClose = line.indexOf('>', tStart);
                        if (tClose < 0) {
                            break;
                        }
                        int tEnd = line.indexOf("</w:t>", tClose + 1);
                        if (tEnd < 0) {
                            idx = tClose + 1;
                            continue;
                        }
                        String txt = line.substring(tClose + 1, tEnd);
                        para.append(decodeXmlEntities(txt));
                        idx = tEnd + 6;
                    }
                }
                if (para.length() > 0) {
                    appendCapped(sb, para.toString());
                }
                break;
            }
        }
        return sb.toString();
    }

    private String parseDoc(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (HWPFDocument document = new HWPFDocument(is)) {
            Range range = document.getRange();
            String text = range.text();
            appendCapped(sb, text);
        }
        return sb.toString();
    }

    private void appendCapped(StringBuilder sb, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int remain = MAX_DOC_TEXT - sb.length();
        if (remain <= 0) {
            return;
        }
        if (text.length() <= remain) {
            sb.append(text);
        } else {
            sb.append(text, 0, remain);
        }
    }

    private void addChunks(String fileName, String text) {
        if (SHARED_INDEX.size() >= MAX_INDEX_CHUNKS) {
            return;
        }
        DocumentSectionRegistry.SectionInfo sectionInfo = DocumentSectionRegistry.resolveSectionInfo(fileName);
        String displayName = sectionInfo.displayName;
        String fileKey = sectionInfo.fileKey;
        String normalized = text.replace("\r", "\n");
        String[] paragraphs = normalized.split("\n");
        StringBuilder current = new StringBuilder();
        int chunkOrder = 0;
        for (String paragraph : paragraphs) {
            String line = paragraph == null ? "" : paragraph.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (current.length() + line.length() + 1 > CHUNK_CHAR_LIMIT && current.length() > 0) {
                SHARED_INDEX.add(new DocumentChunk(
                        displayName, fileKey, chunkOrder++, current.toString().trim(), engine));
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (current.length() > 0) {
            SHARED_INDEX.add(new DocumentChunk(
                    displayName, fileKey, chunkOrder, current.toString().trim(), engine));
        }
    }

    private String decodeXmlEntities(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private void rebuildDfStats() {
        TERM_DF.clear();
        TERM_DF.putAll(engine.buildDf(SHARED_INDEX));
    }

    private void loadSynonymsFromAsset(AssetManager assets) throws Exception {
        String jsonText = readAssetText(assets, QUERY_SYNONYMS_ASSET);
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return;
        }
        JSONObject root = new JSONObject(jsonText);
        Map<String, String[]> tokenMap = readSynonymObject(root.optJSONObject("tokenSynonyms"));
        Map<String, String[]> queryMap = readSynonymObject(root.optJSONObject("querySynonyms"));
        Set<String> stopwordSet = new HashSet<>();
        JSONArray stopwords = root.optJSONArray("stopwords");
        if (stopwords != null) {
            for (int i = 0; i < stopwords.length(); i++) {
                String sw = LocalDocumentQueryEngine.normalizeText(stopwords.optString(i, ""));
                if (!sw.isEmpty()) {
                    stopwordSet.add(sw);
                }
            }
        }
        engine.replaceLexicon(tokenMap, queryMap, stopwordSet);
    }

    private Map<String, String[]> readSynonymObject(JSONObject object) {
        Map<String, String[]> result = new HashMap<>();
        if (object == null) {
            return result;
        }
        JSONArray names = object.names();
        if (names == null) {
            return result;
        }
        for (int i = 0; i < names.length(); i++) {
            String key = LocalDocumentQueryEngine.normalizeText(names.optString(i, ""));
            if (key.isEmpty()) {
                continue;
            }
            String[] values = jsonArrayToStringArray(object.optJSONArray(key));
            if (values.length > 0) {
                result.put(key, values);
            }
        }
        return result;
    }

    private String readAssetText(AssetManager assets, String fileName) throws IOException {
        try (InputStream is = assets.open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private String[] jsonArrayToStringArray(JSONArray array) {
        if (array == null || array.length() == 0) {
            return new String[0];
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = LocalDocumentQueryEngine.normalizeText(array.optString(i, "")).trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out.toArray(new String[0]);
    }

    public static class DocumentChunk {
        public final String documentName;
        public final String fileKey;
        public final int order;
        public final String text;
        final String normalizedText;
        final Set<String> tokens;

        DocumentChunk(String documentName, String fileKey, int order, String text) {
            this(documentName, fileKey, order, text, new LocalDocumentQueryEngine());
        }

        DocumentChunk(String documentName, String fileKey, int order, String text,
                      LocalDocumentQueryEngine queryEngine) {
            this.documentName = documentName;
            this.fileKey = fileKey;
            this.order = order;
            this.text = text;
            this.normalizedText = LocalDocumentQueryEngine.normalizeText(text);
            this.tokens = queryEngine == null
                    ? new HashSet<String>()
                    : queryEngine.tokenize(text);
        }
    }
}
