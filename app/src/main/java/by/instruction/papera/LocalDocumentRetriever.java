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
import java.util.Comparator;
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
    private static final String QUERY_SYNONYMS_ASSET = "ai_query_synonyms.json";
    private static final int CHUNK_CHAR_LIMIT = 700;
    private static final int MAX_DOC_TEXT = 25000;
    private static final int MAX_SNIPPET_CHARS = 180;
    private static final int MAX_INDEX_CHUNKS = 2500;
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "и", "в", "во", "на", "по", "для", "с", "со", "к", "ко", "о", "об", "от", "до",
            "или", "а", "но", "как", "что", "это", "при", "из", "за", "под", "над", "у", "не"
    ));
    private static final Map<String, String[]> TOKEN_SYNONYMS = new HashMap<>();
    private static final Map<String, String[]> QUERY_SYNONYMS = new HashMap<>();
    private static final Object SYNONYMS_LOCK = new Object();
    private static volatile boolean synonymsLoaded = false;

    static {
        loadDefaultSynonyms();
    }

    private static void loadDefaultSynonyms() {
        TOKEN_SYNONYMS.clear();
        QUERY_SYNONYMS.clear();
        STOPWORDS.clear();
        STOPWORDS.addAll(Arrays.asList(
                "и", "в", "во", "на", "по", "для", "с", "со", "к", "ко", "о", "об", "от", "до",
                "или", "а", "но", "как", "что", "это", "при", "из", "за", "под", "над", "у", "не"
        ));

        TOKEN_SYNONYMS.put("сиз", new String[]{"средства", "индивидуальной", "защиты"});
        TOKEN_SYNONYMS.put("средства", new String[]{"сиз"});
        TOKEN_SYNONYMS.put("пз", new String[]{"проверка", "знаний"});
        TOKEN_SYNONYMS.put("лпа", new String[]{"локальный", "правовой", "акт"});
        TOKEN_SYNONYMS.put("суот", new String[]{"система", "управления", "охраной", "труда"});
        TOKEN_SYNONYMS.put("нс", new String[]{"несчастный", "случай"});
        TOKEN_SYNONYMS.put("медосмотр", new String[]{"медицинский", "осмотр"});
        TOKEN_SYNONYMS.put("от", new String[]{"охрана", "труда"});
        TOKEN_SYNONYMS.put("инструктаж", new String[]{"обучение"});
        TOKEN_SYNONYMS.put("стажировка", new String[]{"обучение"});
        TOKEN_SYNONYMS.put("пожарка", new String[]{"пожарная", "безопасность"});
        TOKEN_SYNONYMS.put("радиация", new String[]{"радиационная", "безопасность"});
        TOKEN_SYNONYMS.put("работодатель", new String[]{"наниматель"});
        TOKEN_SYNONYMS.put("наниматель", new String[]{"работодатель"});

        QUERY_SYNONYMS.put("несчастный случай", new String[]{"нс", "расследование", "травма"});
        QUERY_SYNONYMS.put("проверка знаний", new String[]{"пз", "обучение", "инструктаж"});
        QUERY_SYNONYMS.put("средства индивидуальной защиты", new String[]{"сиз"});
        QUERY_SYNONYMS.put("система управления охраной труда", new String[]{"суот"});
        QUERY_SYNONYMS.put("медосмотр", new String[]{"медицинский осмотр", "обязательный медосмотр"});
    }

    private static final Object INDEX_LOCK = new Object();
    private static final List<DocumentChunk> SHARED_INDEX = new ArrayList<>();
    private static final Map<String, Integer> TERM_DF = new HashMap<>();
    private static volatile boolean indexed = false;

    private final Context appContext;

    public LocalDocumentRetriever(Context context) {
        this.appContext = context.getApplicationContext();
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
            buildIndex();
            indexed = true;
        }
    }

    public List<DocumentChunk> retrieveTopChunks(String query, int limit) {
        try {
            ensureIndexBuilt();
        } catch (Throwable t) {
            Log.e(TAG, "Индекс не построен из-за ошибки", t);
            return Collections.emptyList();
        }
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String queryPhrase = normalizeText(query).replaceAll("\\s+", " ").trim();
        Set<String> tokens = expandQueryTokens(query, tokenize(query));
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (DocumentChunk chunk : SHARED_INDEX) {
            int score = scoreChunk(tokens, queryPhrase, chunk);
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }
        scored.sort(Comparator.comparingInt((ScoredChunk c) -> c.score).reversed());

        List<DocumentChunk> result = new ArrayList<>();
        for (ScoredChunk item : scored) {
            if (result.size() >= limit) {
                break;
            }
            result.add(item.chunk);
        }
        return result;
    }

    public List<AiSource> toSources(List<DocumentChunk> chunks, int maxCount, String query) {
        List<AiSource> sources = new ArrayList<>();
        if (chunks == null) {
            return sources;
        }
        for (DocumentChunk chunk : chunks) {
            if (sources.size() >= maxCount) {
                break;
            }
            String snippet = createRelevantSnippet(chunk.text, query, MAX_SNIPPET_CHARS);
            sources.add(new AiSource(chunk.documentName, snippet.replaceAll("\\s+", " ").trim(), chunk.fileKey));
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
                SHARED_INDEX.add(new DocumentChunk(displayName, fileKey, chunkOrder++, current.toString().trim()));
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (current.length() > 0) {
            SHARED_INDEX.add(new DocumentChunk(displayName, fileKey, chunkOrder, current.toString().trim()));
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
        for (DocumentChunk chunk : SHARED_INDEX) {
            Set<String> unique = tokenize(chunk.text);
            for (String token : unique) {
                TERM_DF.put(token, TERM_DF.getOrDefault(token, 0) + 1);
            }
        }
    }

    private Set<String> tokenize(String value) {
        String[] parts = normalizeText(value).split("[^\\p{L}\\p{Nd}]+");
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            if (part.length() < 2 || STOPWORDS.contains(part)) {
                continue;
            }
            tokens.add(part);
            String stem = stemToken(part);
            if (!stem.equals(part) && stem.length() >= 3) {
                tokens.add(stem);
            }
        }
        return tokens;
    }

    private int scoreChunk(Set<String> queryTokens, String queryPhrase, DocumentChunk chunk) {
        String lowerChunk = normalizeText(chunk.text);
        String lowerTitle = normalizeText(chunk.documentName);
        int score = 0;
        int matched = 0;

        if (queryPhrase.length() >= 4 && lowerChunk.contains(queryPhrase)) {
            score += 80;
        }

        for (String token : queryTokens) {
            int occ = countOccurrences(lowerChunk, token);
            if (occ > 0) {
                matched++;
                int df = TERM_DF.getOrDefault(token, SHARED_INDEX.size());
                int rarityBoost = Math.max(1, (SHARED_INDEX.size() + 5) / (df + 5));
                score += Math.min(occ, 5) * (4 + Math.min(token.length(), 10)) * rarityBoost;
                int firstIdx = lowerChunk.indexOf(token);
                if (firstIdx >= 0) {
                    score += Math.max(0, 20 - firstIdx / 80);
                }
            }
            if (lowerTitle.contains(token)) {
                score += 30;
            }
        }
        if (!queryTokens.isEmpty() && matched == queryTokens.size()) {
            score += 40;
        } else {
            score += matched * 6;
        }
        return score;
    }

    private int countOccurrences(String text, String token) {
        int from = 0;
        int count = 0;
        while (true) {
            int idx = text.indexOf(token, from);
            if (idx < 0) {
                break;
            }
            count++;
            from = idx + token.length();
        }
        return count;
    }

    private String createRelevantSnippet(String text, String query, int maxChars) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String normalizedText = normalizeText(text);
        Set<String> tokens = expandQueryTokens(query == null ? "" : query, tokenize(query == null ? "" : query));
        int bestIdx = -1;
        for (String token : tokens) {
            int idx = normalizedText.indexOf(token);
            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                bestIdx = idx;
            }
        }
        if (bestIdx < 0) {
            String fallback = text.replaceAll("\\s+", " ").trim();
            return fallback.length() > maxChars ? fallback.substring(0, maxChars) + "..." : fallback;
        }
        int start = Math.max(0, bestIdx - maxChars / 3);
        int end = Math.min(text.length(), start + maxChars);
        if (end - start < maxChars && start > 0) {
            start = Math.max(0, end - maxChars);
        }
        String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
        if (start > 0) {
            snippet = "... " + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + " ...";
        }
        return snippet;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private Set<String> expandQueryTokens(String query, Set<String> baseTokens) {
        Set<String> expanded = new HashSet<>(baseTokens);
        String normalizedQuery = normalizeText(query).replaceAll("\\s+", " ").trim();

        for (String token : new HashSet<>(baseTokens)) {
            String[] synonyms = TOKEN_SYNONYMS.get(token);
            if (synonyms == null) {
                continue;
            }
            for (String synonym : synonyms) {
                expanded.addAll(tokenize(synonym));
            }
        }

        for (Map.Entry<String, String[]> entry : QUERY_SYNONYMS.entrySet()) {
            if (!normalizedQuery.contains(entry.getKey())) {
                continue;
            }
            for (String synonymPhrase : entry.getValue()) {
                expanded.addAll(tokenize(synonymPhrase));
            }
        }

        if (normalizedQuery.contains("нс") || normalizedQuery.contains("несчаст")) {
            expanded.addAll(tokenize("расследование несчастных случаев травма производственная"));
        }
        if (normalizedQuery.contains("сиз")) {
            expanded.addAll(tokenize("индивидуальная защита средства защиты"));
        }
        if (normalizedQuery.contains("суот")) {
            expanded.addAll(tokenize("система управления охраной труда мероприятия"));
        }
        return expanded;
    }

    private String stemToken(String token) {
        String t = token;
        String[] suffixes = {
                "иями", "ями", "ами", "иями", "ого", "ему", "ому", "ыми", "ими", "ия", "ие", "ий",
                "ая", "ое", "ые", "ий", "ый", "ой", "ам", "ям", "ах", "ях", "ов", "ев", "ом", "ем",
                "а", "я", "ы", "и", "е", "у", "ю", "о"
        };
        for (String s : suffixes) {
            if (t.endsWith(s) && t.length() - s.length() >= 3) {
                return t.substring(0, t.length() - s.length());
            }
        }
        return t;
    }

    private void loadSynonymsFromAsset(AssetManager assets) throws Exception {
        String jsonText = readAssetText(assets, QUERY_SYNONYMS_ASSET);
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return;
        }
        JSONObject root = new JSONObject(jsonText);

        JSONObject tokenSynonymsObj = root.optJSONObject("tokenSynonyms");
        if (tokenSynonymsObj != null) {
            TOKEN_SYNONYMS.clear();
            JSONArray names = tokenSynonymsObj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = normalizeText(names.optString(i, ""));
                    if (key.isEmpty()) {
                        continue;
                    }
                    JSONArray arr = tokenSynonymsObj.optJSONArray(key);
                    String[] values = jsonArrayToStringArray(arr);
                    if (values.length > 0) {
                        TOKEN_SYNONYMS.put(key, values);
                    }
                }
            }
        }

        JSONObject querySynonymsObj = root.optJSONObject("querySynonyms");
        if (querySynonymsObj != null) {
            QUERY_SYNONYMS.clear();
            JSONArray names = querySynonymsObj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = normalizeText(names.optString(i, ""));
                    if (key.isEmpty()) {
                        continue;
                    }
                    JSONArray arr = querySynonymsObj.optJSONArray(key);
                    String[] values = jsonArrayToStringArray(arr);
                    if (values.length > 0) {
                        QUERY_SYNONYMS.put(key, values);
                    }
                }
            }
        }

        JSONArray stopwords = root.optJSONArray("stopwords");
        if (stopwords != null && stopwords.length() > 0) {
            STOPWORDS.clear();
            for (int i = 0; i < stopwords.length(); i++) {
                String sw = normalizeText(stopwords.optString(i, ""));
                if (!sw.isEmpty()) {
                    STOPWORDS.add(sw);
                }
            }
        }
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
            String value = normalizeText(array.optString(i, "")).trim();
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

        DocumentChunk(String documentName, String fileKey, int order, String text) {
            this.documentName = documentName;
            this.fileKey = fileKey;
            this.order = order;
            this.text = text;
        }
    }

    private static class ScoredChunk {
        private final DocumentChunk chunk;
        private final int score;

        private ScoredChunk(DocumentChunk chunk, int score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
