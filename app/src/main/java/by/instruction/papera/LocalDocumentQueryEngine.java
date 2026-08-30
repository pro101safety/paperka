package by.instruction.papera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ранжирование фрагментов локальных документов.
 * Точность важнее полноты: слабые и слишком общие совпадения отбрасываются.
 */
final class LocalDocumentQueryEngine {
    private static final int MAX_SNIPPET_CHARS = 180;
    private static final Set<String> INFLECTION_SUFFIXES = new HashSet<>(Arrays.asList(
            "иями", "ями", "ами", "ого", "ему", "ому", "ыми", "ими",
            "ия", "ие", "ий", "ая", "ое", "ые", "ый", "ой",
            "ам", "ям", "ах", "ях", "ов", "ев", "ом", "ем",
            "ей", "ью", "ья", "ье",
            "а", "я", "ы", "и", "е", "у", "ю", "о",
            "ть", "ти", "ся", "сь", "ческий", "еский", "ность", "ение", "ание"
    ));
    private static final Set<String> GENERIC_TERMS = new HashSet<>(Arrays.asList(
            "охрана", "охран", "труда", "труд", "работник", "работника", "работники",
            "работников", "работа", "работы", "работе", "работ", "требование",
            "требования", "требований", "порядок", "порядке", "согласно",
            "соответствии", "настоящий", "настоящего", "настоящей", "пункт",
            "пункта", "статья", "статьи", "глава", "раздел", "организация",
            "организации", "обеспечении", "обеспечение", "проведение",
            "проведении", "проводится", "проводят"
    ));

    private final Set<String> stopwords = new HashSet<>();
    private final Map<String, String[]> tokenSynonyms = new HashMap<>();
    private final Map<String, String[]> querySynonyms = new HashMap<>();

    LocalDocumentQueryEngine() {
        loadDefaultLexicon();
    }

    void loadDefaultLexicon() {
        stopwords.clear();
        tokenSynonyms.clear();
        querySynonyms.clear();
        stopwords.addAll(defaultStopwords());

        putTokenSynonym("сиз", "средства индивидуальной защиты");
        putTokenSynonym("пз", "проверка знаний");
        putTokenSynonym("лпа", "локальный правовой акт");
        putTokenSynonym("суот", "система управления охраной труда");
        putTokenSynonym("нс", "несчастный случай");
        putTokenSynonym("медосмотр", "медицинский осмотр");
        putTokenSynonym("пожарка", "пожарная безопасность");
        putTokenSynonym("радиация", "радиационная безопасность");
        putTokenSynonym("работодатель", "наниматель");
        putTokenSynonym("наниматель", "работодатель");
        putTokenSynonym("высоте", "работы на высоте");
        putTokenSynonym("высота", "работы на высоте");
        putTokenSynonym("высоту", "работы на высоте");
        putTokenSynonym("высоты", "работы на высоте");
        putTokenSynonym("наряд", "наряд-допуск");

        querySynonyms.put("несчастный случай", new String[]{"нс", "расследование нс"});
        querySynonyms.put("проверка знаний", new String[]{"пз"});
        querySynonyms.put("средства индивидуальной защиты", new String[]{"сиз"});
        querySynonyms.put("система управления охраной труда", new String[]{"суот"});
        querySynonyms.put("медосмотр", new String[]{"медицинский осмотр", "обязательный медосмотр"});
        querySynonyms.put("медицинский осмотр", new String[]{"медосмотр"});
        querySynonyms.put("медицинские осмотры", new String[]{"медосмотр"});
        querySynonyms.put("вводный инструктаж", new String[]{"инструктаж", "инструктажа"});
        querySynonyms.put("работы на высоте", new String[]{"работы на высоте", "работ на высоте"});
    }

    void replaceLexicon(Map<String, String[]> newTokenSynonyms,
                        Map<String, String[]> newQuerySynonyms,
                        Set<String> newStopwords) {
        if (newTokenSynonyms != null && !newTokenSynonyms.isEmpty()) {
            tokenSynonyms.clear();
            tokenSynonyms.putAll(newTokenSynonyms);
        }
        if (newQuerySynonyms != null && !newQuerySynonyms.isEmpty()) {
            querySynonyms.clear();
            querySynonyms.putAll(newQuerySynonyms);
        }
        if (newStopwords != null && !newStopwords.isEmpty()) {
            stopwords.addAll(newStopwords);
        }
    }

    Set<String> tokenize(String value) {
        String[] parts = normalizeText(value).split("[^\\p{L}\\p{Nd}]+");
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            if (part.length() < 2 || stopwords.contains(part)) {
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

    List<String> extractOriginalTerms(String query) {
        String[] parts = normalizeText(query).split("[^\\p{L}\\p{Nd}]+");
        List<String> terms = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : parts) {
            if (part.length() < 2 || stopwords.contains(part) || seen.contains(part)) {
                continue;
            }
            seen.add(part);
            terms.add(part);
        }
        return terms;
    }

    AnalyzedQuery analyze(String query) {
        String phrase = normalizeText(query).replaceAll("\\s+", " ").trim();
        List<String> originalTerms = extractOriginalTerms(query);
        Set<String> originalKeys = new HashSet<>();
        for (String term : originalTerms) {
            originalKeys.addAll(tokenize(term));
        }

        Set<String> expandedKeys = new HashSet<>(originalKeys);
        for (String term : originalTerms) {
            addSynonymsForToken(term, expandedKeys);
            addSynonymsForToken(stemToken(term), expandedKeys);
        }
        for (Map.Entry<String, String[]> entry : querySynonyms.entrySet()) {
            if (!containsWholePhrase(phrase, entry.getKey())) {
                continue;
            }
            for (String synonymPhrase : entry.getValue()) {
                expandedKeys.addAll(tokenize(synonymPhrase));
            }
        }

        Set<String> synonymOnly = new HashSet<>(expandedKeys);
        synonymOnly.removeAll(originalKeys);
        return new AnalyzedQuery(phrase, originalTerms, originalKeys, synonymOnly);
    }

    String buildFtsQuery(String query) {
        return buildFtsQuery(query, false);
    }

    String buildFtsQueryOr(String query) {
        return buildFtsQuery(query, true);
    }

    private String buildFtsQuery(String query, boolean orGroups) {
        AnalyzedQuery analyzed = analyze(query);
        if (analyzed.originalTerms.isEmpty()) {
            return toFtsPhrase(analyzed.phrase);
        }
        List<String> groups = new ArrayList<>();
        for (String term : analyzed.originalTerms) {
            if (isGenericTerm(term)) {
                continue;
            }
            List<String> alternatives = new ArrayList<>();
            String stem = stemToken(term);
            String token = toFtsToken(stem.length() >= 4 ? stem : term);
            if (!token.isEmpty()) {
                alternatives.add(token);
            }
            if (!term.equals(stem)) {
                String originalToken = toFtsToken(term);
                if (!originalToken.isEmpty() && !alternatives.contains(originalToken)) {
                    alternatives.add(originalToken);
                }
            }
            addFtsSynonyms(term, alternatives);
            addFtsSynonyms(stemToken(term), alternatives);
            if (!alternatives.isEmpty()) {
                groups.add("(" + joinFts(alternatives, " OR ") + ")");
            }
        }
        for (Map.Entry<String, String[]> entry : querySynonyms.entrySet()) {
            if (!containsWholePhrase(analyzed.phrase, entry.getKey())) {
                continue;
            }
            List<String> alternatives = new ArrayList<>();
            alternatives.add(toFtsPhrase(entry.getKey()));
            if (entry.getValue() != null) {
                for (String value : entry.getValue()) {
                    alternatives.add(toFtsPhrase(value));
                }
            }
            groups.add("(" + joinFts(alternatives, " OR ") + ")");
        }
        if (groups.isEmpty()) {
            return toFtsPhrase(analyzed.phrase);
        }
        return joinFts(groups, orGroups ? " OR " : " AND ");
    }

    private void addFtsSynonyms(String term, List<String> alternatives) {
        String[] synonyms = tokenSynonyms.get(term);
        if (synonyms == null) {
            return;
        }
        for (String synonym : synonyms) {
            String fts = toFtsPhrase(synonym);
            if (!fts.isEmpty() && !alternatives.contains(fts)) {
                alternatives.add(fts);
            }
        }
    }

    private static String toFtsToken(String term) {
        if (term == null) {
            return "";
        }
        String cleaned = term.replaceAll("[^\\p{L}\\p{Nd}]+", "");
        if (cleaned.length() < 2) {
            return "";
        }
        if (cleaned.length() >= 4) {
            return cleaned + "*";
        }
        return cleaned;
    }

    private static String toFtsPhrase(String value) {
        String normalized = normalizeText(value).replace("\"", " ").replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (!normalized.contains(" ")) {
            return toFtsToken(normalized);
        }
        return "\"" + normalized + "\"";
    }

    private static String joinFts(List<String> parts, String separator) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(part);
        }
        return sb.toString();
    }

    List<LocalDocumentRetriever.DocumentChunk> retrieve(
            String query,
            List<LocalDocumentRetriever.DocumentChunk> index,
            Map<String, Integer> termDf,
            int limit) {
        if (query == null || query.trim().isEmpty() || index == null || index.isEmpty()) {
            return Collections.emptyList();
        }
        AnalyzedQuery analyzed = analyze(query);
        if (analyzed.originalTerms.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        int indexSize = Math.max(1, index.size());
        for (LocalDocumentRetriever.DocumentChunk chunk : index) {
            int score = scoreChunk(analyzed, chunk, termDf, indexSize);
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }
        if (scored.isEmpty()) {
            return Collections.emptyList();
        }
        scored.sort(Comparator.comparingInt((ScoredChunk c) -> c.score).reversed());

        int topScore = scored.get(0).score;
        int distinctive = countDistinctiveTerms(analyzed.originalTerms);
        int absoluteMin = 40 + distinctive * 12;
        int relativeMin = Math.max(absoluteMin, topScore * 55 / 100);

        LinkedHashMap<String, LocalDocumentRetriever.DocumentChunk> unique = new LinkedHashMap<>();
        for (ScoredChunk item : scored) {
            if (item.score < relativeMin) {
                continue;
            }
            String key = item.chunk.fileKey == null ? item.chunk.documentName : item.chunk.fileKey;
            if (unique.containsKey(key)) {
                continue;
            }
            unique.put(key, item.chunk);
            if (unique.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    List<TitleHit> rankTitles(String query, List<Chapter> chapters, int limit) {
        List<TitleHit> empty = Collections.emptyList();
        if (query == null || query.trim().isEmpty() || chapters == null || chapters.isEmpty()) {
            return empty;
        }
        AnalyzedQuery analyzed = analyze(query);
        if (analyzed.originalTerms.isEmpty()) {
            return empty;
        }
        List<TitleHit> scored = new ArrayList<>();
        for (Chapter chapter : chapters) {
            if (chapter == null || chapter.getTopicsList() == null) {
                continue;
            }
            String chapterName = chapter.getChapterName() == null ? "" : chapter.getChapterName();
            for (Topics topic : chapter.getTopicsList()) {
                if (topic == null || topic.getFileName() == null) {
                    continue;
                }
                String title = topic.getTopicName() == null ? "" : topic.getTopicName();
                int score = scoreTitle(analyzed, title, chapterName);
                if (score > 0) {
                    scored.add(new TitleHit(topic.getFileName(), title, chapterName, score));
                }
            }
        }
        if (scored.isEmpty()) {
            return empty;
        }
        scored.sort(Comparator.comparingInt((TitleHit h) -> h.score).reversed());
        int top = scored.get(0).score;
        int minKeep = Math.max(70, top * 55 / 100);
        List<TitleHit> result = new ArrayList<>();
        for (TitleHit hit : scored) {
            if (hit.score < minKeep) {
                continue;
            }
            result.add(hit);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    List<LocalDocumentRetriever.DocumentChunk> mergeCatalogAndBody(
            String query,
            List<Chapter> chapters,
            List<LocalDocumentRetriever.DocumentChunk> index,
            Map<String, Integer> termDf,
            int limit) {
        List<TitleHit> titles = rankTitles(query, chapters, Math.max(limit, 5));
        List<LocalDocumentRetriever.DocumentChunk> body = retrieve(query, index, termDf, Math.max(limit * 3, 6));
        LinkedHashMap<String, LocalDocumentRetriever.DocumentChunk> merged = new LinkedHashMap<>();
        boolean strongTitle = !titles.isEmpty() && titles.get(0).score >= 110;
        for (TitleHit title : titles) {
            String key = title.fileKey == null ? "" : title.fileKey.trim().toLowerCase(Locale.ROOT);
            LocalDocumentRetriever.DocumentChunk bodyChunk = findChunkByFile(body, key);
            String text = bodyChunk != null
                    ? bodyChunk.text
                    : (title.chapter + ". " + title.title).trim();
            int order = bodyChunk != null ? bodyChunk.order : 0;
            merged.put(key, new LocalDocumentRetriever.DocumentChunk(title.title, key, order, text, this));
            if (merged.size() >= limit) {
                return new ArrayList<>(merged.values());
            }
        }
        if (!strongTitle) {
            for (LocalDocumentRetriever.DocumentChunk chunk : body) {
                String key = chunk.fileKey == null ? chunk.documentName : chunk.fileKey;
                if (key == null || merged.containsKey(key)) {
                    continue;
                }
                merged.put(key, chunk);
                if (merged.size() >= limit) {
                    break;
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private int scoreTitle(AnalyzedQuery query, String title, String chapter) {
        LocalDocumentRetriever.DocumentChunk titleChunk =
                new LocalDocumentRetriever.DocumentChunk(title, "", 0, title, this);
        String titleNorm = titleChunk.normalizedText;
        int distinctive = countDistinctiveTerms(query.originalTerms);
        int matchedDistinctive = 0;
        int matchedAll = 0;
        for (String term : query.originalTerms) {
            if (!chunkHasTerm(titleChunk, term)) {
                continue;
            }
            matchedAll++;
            if (!isGenericTerm(term)) {
                matchedDistinctive++;
            }
        }
        boolean synonymTopic = titleHasSynonymTopic(query, titleChunk);
        int topicCoverage = matchedDistinctive;
        if (topicCoverage == 0 && synonymTopic) {
            topicCoverage = 1;
        } else if (topicCoverage > 0 && synonymAddsUnmatchedConcept(query, titleChunk)) {
            topicCoverage++;
        }
        if (distinctive > 0 && topicCoverage == 0) {
            return 0;
        }
        if (distinctive == 0 && !containsWholePhrase(titleNorm, query.phrase)) {
            return 0;
        }
        int score = topicCoverage * 120 + matchedAll * 10;
        if (containsWholePhrase(titleNorm, query.phrase)) {
            score += 240;
        }
        if (distinctive >= 2 && topicCoverage >= distinctive) {
            score += 90;
        } else if (topicCoverage == 1 && distinctive <= 1) {
            score += 50;
        }
        if (synonymTopic) {
            score += 80;
        }
        for (String term : query.originalTerms) {
            if (!isGenericTerm(term) && titleHasTerm(titleNorm, term)) {
                score += 40;
            }
        }
        if (chapter != null && !chapter.isEmpty() && topicCoverage > 0) {
            LocalDocumentRetriever.DocumentChunk chapterChunk =
                    new LocalDocumentRetriever.DocumentChunk(chapter, "", 0, chapter, this);
            for (String term : query.originalTerms) {
                if (!isGenericTerm(term) && chunkHasTerm(chapterChunk, term)) {
                    score += 8;
                }
            }
        }
        return score;
    }

    private boolean titleHasSynonymTopic(AnalyzedQuery query,
                                         LocalDocumentRetriever.DocumentChunk titleChunk) {
        for (String term : query.originalTerms) {
            if (chunkHasSynonymPhrase(titleChunk, term)
                    || chunkHasSynonymPhrase(titleChunk, stemToken(term))) {
                return true;
            }
        }
        for (Map.Entry<String, String[]> entry : querySynonyms.entrySet()) {
            if (!queryMentionsSynonym(query.phrase, entry.getKey(), entry.getValue())) {
                continue;
            }
            if (titleMatchesSynonymEntry(titleChunk, entry.getKey(), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean synonymAddsUnmatchedConcept(AnalyzedQuery query,
                                                LocalDocumentRetriever.DocumentChunk titleChunk) {
        for (Map.Entry<String, String[]> entry : querySynonyms.entrySet()) {
            if (!queryMentionsSynonym(query.phrase, entry.getKey(), entry.getValue())) {
                continue;
            }
            if (!titleMatchesSynonymEntry(titleChunk, entry.getKey(), entry.getValue())) {
                continue;
            }
            if (matchedOriginalCoversSynonym(query, titleChunk, entry.getKey(), entry.getValue())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean queryMentionsSynonym(String phrase, String key, String[] values) {
        if (containsWholePhrase(phrase, normalizeText(key))) {
            return true;
        }
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (containsWholePhrase(phrase, normalizeText(value))) {
                return true;
            }
        }
        return false;
    }

    private boolean titleMatchesSynonymEntry(LocalDocumentRetriever.DocumentChunk titleChunk,
                                             String key,
                                             String[] values) {
        String keyNorm = normalizeText(key);
        if (containsWholePhrase(titleChunk.normalizedText, keyNorm) || chunkHasTerm(titleChunk, keyNorm)) {
            return true;
        }
        List<String> keyWords = extractOriginalTerms(key);
        if (!keyWords.isEmpty()) {
            boolean allPresent = true;
            for (String word : keyWords) {
                if (!chunkHasTerm(titleChunk, word)) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent && keyWords.size() >= 2) {
                return true;
            }
        }
        if (values == null) {
            return false;
        }
        for (String value : values) {
            String valueNorm = normalizeText(value);
            if (containsWholePhrase(titleChunk.normalizedText, valueNorm)
                    || chunkHasTerm(titleChunk, valueNorm)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchedOriginalCoversSynonym(AnalyzedQuery query,
                                                LocalDocumentRetriever.DocumentChunk titleChunk,
                                                String key,
                                                String[] values) {
        List<String> matched = new ArrayList<>();
        for (String term : query.originalTerms) {
            if (chunkHasTerm(titleChunk, term)) {
                matched.add(term);
            }
        }
        return originalContainsSynonym(matched, key, values);
    }

    private boolean originalContainsSynonym(List<String> originalTerms, String key, String[] values) {
        Set<String> originals = new HashSet<>();
        for (String term : originalTerms) {
            originals.add(term);
            originals.add(stemToken(term));
        }
        for (String word : extractOriginalTerms(key)) {
            if (originals.contains(word) || originals.contains(stemToken(word))) {
                return true;
            }
        }
        if (values == null) {
            return false;
        }
        for (String value : values) {
            for (String word : extractOriginalTerms(value)) {
                if (originals.contains(word) || originals.contains(stemToken(word))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LocalDocumentRetriever.DocumentChunk findChunkByFile(
            List<LocalDocumentRetriever.DocumentChunk> chunks, String fileKey) {
        if (chunks == null || fileKey == null || fileKey.isEmpty()) {
            return null;
        }
        for (LocalDocumentRetriever.DocumentChunk chunk : chunks) {
            if (fileKey.equalsIgnoreCase(chunk.fileKey)) {
                return chunk;
            }
        }
        return null;
    }

    Map<String, Integer> buildDf(List<LocalDocumentRetriever.DocumentChunk> index) {
        Map<String, Integer> df = new HashMap<>();
        if (index == null) {
            return df;
        }
        for (LocalDocumentRetriever.DocumentChunk chunk : index) {
            Set<String> unique = chunk.tokens != null ? chunk.tokens : tokenize(chunk.text);
            for (String token : unique) {
                df.put(token, df.getOrDefault(token, 0) + 1);
            }
        }
        return df;
    }

    String createRelevantSnippet(String text, String query, int maxChars) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        int limit = maxChars > 0 ? maxChars : MAX_SNIPPET_CHARS;
        AnalyzedQuery analyzed = analyze(query == null ? "" : query);
        String normalizedText = normalizeText(text);
        int bestIdx = findBestMatchIndex(normalizedText, analyzed);
        if (bestIdx < 0) {
            String fallback = text.replaceAll("\\s+", " ").trim();
            return fallback.length() > limit ? fallback.substring(0, limit) + "..." : fallback;
        }
        int start = Math.max(0, bestIdx - limit / 3);
        int end = Math.min(text.length(), start + limit);
        if (end - start < limit && start > 0) {
            start = Math.max(0, end - limit);
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

    String pickHighlightQuery(String query) {
        List<String> terms = extractOriginalTerms(query);
        if (terms.isEmpty()) {
            return query == null ? "" : query.trim();
        }
        List<String> ranked = new ArrayList<>();
        for (String term : terms) {
            if (!isGenericTerm(term)) {
                ranked.add(term);
            }
        }
        if (ranked.isEmpty()) {
            ranked.addAll(terms);
        }
        Collections.sort(ranked, (a, b) -> Integer.compare(b.length(), a.length()));
        if (ranked.size() == 1) {
            return ranked.get(0);
        }
        return ranked.get(0) + " " + ranked.get(1);
    }

    static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private int scoreChunk(AnalyzedQuery query,
                           LocalDocumentRetriever.DocumentChunk chunk,
                           Map<String, Integer> termDf,
                           int indexSize) {
        int originalMatchedTerms = 0;
        int distinctiveMatched = 0;
        for (String term : query.originalTerms) {
            if (chunkHasTerm(chunk, term)) {
                originalMatchedTerms++;
                if (!isGenericTerm(term)) {
                    distinctiveMatched++;
                }
            }
        }

        int distinctiveCount = countDistinctiveTerms(query.originalTerms);
        if (!hasPrimaryTopicMatch(chunk, query)
                || distinctiveMatched < requiredOriginalMatches(distinctiveCount)) {
            return 0;
        }

        int score = 0;
        String lowerTitle = normalizeText(chunk.documentName);

        if (query.phrase.length() >= 6 && containsWholePhrase(chunk.normalizedText, query.phrase)) {
            score += 140;
        } else if (query.originalTerms.size() >= 2
                && containsWholePhrase(chunk.normalizedText, joinTerms(query.originalTerms))) {
            score += 90;
        }

        for (String term : query.originalTerms) {
            int occ = countTermMatches(chunk, term);
            if (occ > 0) {
                int df = lookupDf(termDf, term, indexSize);
                int rarity = Math.min(8, Math.max(1, (indexSize + 5) / (df + 5)));
                int weight = isGenericTerm(term) ? 4 : (12 + Math.min(term.length(), 10));
                score += Math.min(occ, 4) * weight * rarity;
                int firstIdx = firstTermIndex(chunk, term);
                if (firstIdx >= 0 && !isGenericTerm(term)) {
                    score += Math.max(0, 18 - firstIdx / 90);
                }
            }
            if (titleHasTerm(lowerTitle, term)) {
                if (isGenericTerm(term)) {
                    score += 12;
                } else {
                    score += term.length() <= 3 ? 55 : 85;
                }
            }
        }

        for (String token : query.synonymOnly) {
            int occ = countTokenMatches(chunk, token);
            if (occ > 0) {
                int df = termDf == null ? indexSize : termDf.getOrDefault(token, indexSize);
                int rarity = Math.min(5, Math.max(1, (indexSize + 5) / (df + 5)));
                score += Math.min(occ, 3) * (5 + Math.min(token.length(), 8)) * rarity;
            }
            if (titleHasTerm(lowerTitle, token)) {
                score += 18;
            }
        }

        if (originalMatchedTerms >= 2 && hasNearbyOriginalTerms(chunk, query.originalTerms)) {
            score += 45;
        }
        if (originalMatchedTerms == query.originalTerms.size() && query.originalTerms.size() >= 2) {
            score += 55;
        } else {
            score += originalMatchedTerms * 10;
        }
        return score;
    }

    private boolean chunkHasTerm(LocalDocumentRetriever.DocumentChunk chunk, String term) {
        if (chunkHasToken(chunk, term) || chunkHasToken(chunk, stemToken(term))) {
            return true;
        }
        return chunkHasSynonymPhrase(chunk, term) || chunkHasSynonymPhrase(chunk, stemToken(term));
    }

    private boolean chunkHasSynonymPhrase(LocalDocumentRetriever.DocumentChunk chunk, String term) {
        String[] phrases = tokenSynonyms.get(term);
        if (phrases == null || phrases.length == 0) {
            return false;
        }
        for (String phrase : phrases) {
            String normalizedPhrase = normalizeText(phrase).replaceAll("\\s+", " ").trim();
            if (normalizedPhrase.isEmpty()) {
                continue;
            }
            if (containsWholePhrase(chunk.normalizedText, normalizedPhrase)
                    || containsWholePhrase(normalizeText(chunk.documentName), normalizedPhrase)) {
                return true;
            }
            List<String> words = extractOriginalTerms(phrase);
            if (words.size() < 2) {
                if (!words.isEmpty() && chunkHasToken(chunk, words.get(0))) {
                    return true;
                }
                continue;
            }
            boolean allPresent = true;
            for (String word : words) {
                if (!chunkHasToken(chunk, word) && !chunkHasToken(chunk, stemToken(word))) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPrimaryTopicMatch(LocalDocumentRetriever.DocumentChunk chunk, AnalyzedQuery query) {
        if (query.originalTerms.isEmpty()) {
            return false;
        }
        String primary = pickPrimaryTerm(query.originalTerms);
        if (primary != null) {
            if (chunkHasTerm(chunk, primary)) {
                return true;
            }
            for (String term : query.originalTerms) {
                String stem = stemToken(term);
                if ((tokenSynonyms.containsKey(term) || tokenSynonyms.containsKey(stem))
                        && chunkHasTerm(chunk, term)) {
                    return true;
                }
            }
            return false;
        }
        return containsWholePhrase(chunk.normalizedText, joinTerms(query.originalTerms))
                || containsWholePhrase(normalizeText(chunk.documentName), joinTerms(query.originalTerms));
    }

    private String pickPrimaryTerm(List<String> terms) {
        String best = null;
        for (String term : terms) {
            if (isGenericTerm(term)) {
                continue;
            }
            if (best == null
                    || term.length() > best.length()
                    || (term.length() == best.length() && tokenSynonyms.containsKey(stemToken(term)))) {
                best = term;
            }
        }
        return best;
    }

    private boolean isGenericTerm(String term) {
        if (term == null || term.isEmpty()) {
            return true;
        }
        return GENERIC_TERMS.contains(term) || GENERIC_TERMS.contains(stemToken(term));
    }

    private int countDistinctiveTerms(List<String> terms) {
        int count = 0;
        for (String term : terms) {
            if (!isGenericTerm(term)) {
                count++;
            }
        }
        return count;
    }

    private boolean chunkHasToken(LocalDocumentRetriever.DocumentChunk chunk, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (chunk.tokens != null && !chunk.tokens.isEmpty()) {
            if (chunk.tokens.contains(token)) {
                return true;
            }
            if (token.length() >= 4) {
                for (String existing : chunk.tokens) {
                    if (isInflectionPrefix(existing, token) || isInflectionPrefix(token, existing)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return firstTokenIndex(chunk.normalizedText, token) >= 0;
    }

    private int countTermMatches(LocalDocumentRetriever.DocumentChunk chunk, String term) {
        int direct = countTokenMatches(chunk, term);
        if (direct > 0) {
            return direct;
        }
        return countTokenMatches(chunk, stemToken(term));
    }

    private int countTokenMatches(LocalDocumentRetriever.DocumentChunk chunk, String token) {
        if (token == null || token.isEmpty() || chunk.normalizedText == null) {
            return 0;
        }
        int from = 0;
        int count = 0;
        while (from < chunk.normalizedText.length()) {
            int idx = firstTokenIndex(chunk.normalizedText, token, from);
            if (idx < 0) {
                break;
            }
            count++;
            from = idx + token.length();
        }
        return count;
    }

    private int firstTermIndex(LocalDocumentRetriever.DocumentChunk chunk, String term) {
        int direct = firstTokenIndex(chunk.normalizedText, term);
        if (direct >= 0) {
            return direct;
        }
        return firstTokenIndex(chunk.normalizedText, stemToken(term));
    }

    private int firstTokenIndex(String text, String token) {
        return firstTokenIndex(text, token, 0);
    }

    private int firstTokenIndex(String text, String token, int from) {
        if (text == null || token == null || token.isEmpty()) {
            return -1;
        }
        int start = Math.max(0, from);
        while (start <= text.length() - token.length()) {
            int idx = text.indexOf(token, start);
            if (idx < 0) {
                return -1;
            }
            if (isTokenBoundaryMatch(text, idx, token)) {
                return idx;
            }
            start = idx + 1;
        }
        return -1;
    }

    private boolean isTokenBoundaryMatch(String text, int idx, String token) {
        boolean startOk = idx == 0 || !isWordChar(text.charAt(idx - 1));
        if (!startOk) {
            return false;
        }
        int end = idx + token.length();
        if (end >= text.length() || !isWordChar(text.charAt(end))) {
            return true;
        }
        if (token.length() < 4) {
            return false;
        }
        int wordEnd = end;
        while (wordEnd < text.length() && isWordChar(text.charAt(wordEnd))) {
            wordEnd++;
        }
        String word = text.substring(idx, wordEnd);
        return isInflectionPrefix(word, token);
    }

    private boolean titleHasTerm(String normalizedTitle, String term) {
        return firstTokenIndex(normalizedTitle, term) >= 0
                || firstTokenIndex(normalizedTitle, stemToken(term)) >= 0;
    }

    private boolean hasNearbyOriginalTerms(LocalDocumentRetriever.DocumentChunk chunk, List<String> terms) {
        List<Integer> positions = new ArrayList<>();
        for (String term : terms) {
            int idx = firstTermIndex(chunk, term);
            if (idx >= 0) {
                positions.add(idx);
            }
        }
        if (positions.size() < 2) {
            return false;
        }
        Collections.sort(positions);
        for (int i = 1; i < positions.size(); i++) {
            if (positions.get(i) - positions.get(i - 1) <= 140) {
                return true;
            }
        }
        return false;
    }

    private int findBestMatchIndex(String normalizedText, AnalyzedQuery analyzed) {
        List<Integer> positions = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (String term : analyzed.originalTerms) {
            if (!isGenericTerm(term)) {
                keys.add(term);
            }
        }
        if (keys.isEmpty()) {
            keys.addAll(analyzed.originalTerms);
        }
        for (String token : keys) {
            int idx = firstTokenIndex(normalizedText, token);
            if (idx >= 0) {
                positions.add(idx);
            }
        }
        if (positions.isEmpty()) {
            for (String token : analyzed.synonymOnly) {
                int idx = firstTokenIndex(normalizedText, token);
                if (idx >= 0) {
                    return idx;
                }
            }
            return -1;
        }
        Collections.sort(positions);
        int bestIdx = positions.get(0);
        int bestCount = 0;
        int window = MAX_SNIPPET_CHARS;
        for (int start : positions) {
            int count = 0;
            for (int pos : positions) {
                if (pos >= start && pos <= start + window) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                bestIdx = start;
            }
        }
        return bestIdx;
    }

    private int lookupDf(Map<String, Integer> termDf, String term, int indexSize) {
        if (termDf == null) {
            return indexSize;
        }
        Integer direct = termDf.get(term);
        if (direct != null) {
            return direct;
        }
        Integer stemmed = termDf.get(stemToken(term));
        return stemmed == null ? indexSize : stemmed;
    }

    private int requiredOriginalMatches(int originalTermCount) {
        if (originalTermCount <= 2) {
            return 1;
        }
        return Math.max(2, (originalTermCount + 1) / 2);
    }

    private void addSynonymsForToken(String token, Set<String> target) {
        String[] synonyms = tokenSynonyms.get(token);
        if (synonyms == null) {
            return;
        }
        for (String synonym : synonyms) {
            target.addAll(tokenize(synonym));
        }
    }

    private void putTokenSynonym(String key, String value) {
        tokenSynonyms.put(key, new String[]{value});
    }

    private static String joinTerms(List<String> terms) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(terms.get(i));
        }
        return sb.toString();
    }

    private static boolean containsWholePhrase(String text, String phrase) {
        if (text == null || phrase == null || phrase.isEmpty()) {
            return false;
        }
        if (text.contains(phrase)) {
            int idx = text.indexOf(phrase);
            boolean startOk = idx == 0 || !isWordChar(text.charAt(idx - 1));
            int end = idx + phrase.length();
            boolean endOk = end >= text.length() || !isWordChar(text.charAt(end));
            if (startOk && endOk) {
                return true;
            }
        }
        String[] tokens = phrase.split("\\s+");
        if (tokens.length < 2) {
            return false;
        }
        int from = 0;
        while (from < text.length()) {
            int start = indexOfWord(text, tokens[0], from);
            if (start < 0) {
                return false;
            }
            int cursor = start + tokens[0].length();
            boolean ok = true;
            for (int t = 1; t < tokens.length; t++) {
                while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
                    cursor++;
                }
                if (cursor >= text.length() || !text.startsWith(tokens[t], cursor)) {
                    ok = false;
                    break;
                }
                if (cursor > 0 && isWordChar(text.charAt(cursor - 1))) {
                    ok = false;
                    break;
                }
                cursor += tokens[t].length();
            }
            if (ok) {
                return true;
            }
            from = start + 1;
        }
        return false;
    }

    private static int indexOfWord(String text, String word, int from) {
        int start = from;
        while (start <= text.length() - word.length()) {
            int idx = text.indexOf(word, start);
            if (idx < 0) {
                return -1;
            }
            boolean startOk = idx == 0 || !isWordChar(text.charAt(idx - 1));
            int end = idx + word.length();
            boolean endOk = end >= text.length() || !isWordChar(text.charAt(end));
            if (startOk && endOk) {
                return idx;
            }
            start = idx + 1;
        }
        return -1;
    }

    private static boolean isInflectionPrefix(String word, String stem) {
        if (word == null || stem == null || stem.length() < 3 || !word.startsWith(stem)) {
            return false;
        }
        String suffix = word.substring(stem.length());
        return suffix.isEmpty() || INFLECTION_SUFFIXES.contains(suffix);
    }

    static String stemToken(String token) {
        if (token == null) {
            return "";
        }
        String[] suffixes = {
                "ческий", "еский", "ность", "ение", "ание",
                "иями", "ями", "ами", "ого", "ему", "ому", "ыми", "ими", "ия", "ие", "ий",
                "ая", "ое", "ые", "ый", "ой", "ам", "ям", "ах", "ях", "ов", "ев", "ом", "ем",
                "ей", "ью", "ья", "ье",
                "а", "я", "ы", "и", "е", "у", "ю", "о"
        };
        for (String suffix : suffixes) {
            if (token.endsWith(suffix) && token.length() - suffix.length() >= 3) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static Set<String> defaultStopwords() {
        return new HashSet<>(Arrays.asList(
                "и", "в", "во", "на", "по", "для", "с", "со", "к", "ко", "о", "об", "от", "до",
                "или", "а", "но", "как", "что", "это", "при", "из", "за", "под", "над", "у", "не",
                "когда", "какой", "какая", "какие", "какое", "какого", "какую", "каким", "какими",
                "сколько", "где", "куда", "откуда", "почему", "зачем", "чем", "кто", "кого",
                "кому", "чей", "чья", "нужно", "надо", "можно", "должен", "должна", "должно",
                "должны", "обязан", "обязана", "обязаны", "требуется", "скажите", "подскажите",
                "расскажи", "расскажите", "поясни", "поясните", "есть", "быть", "будет", "были",
                "был", "была", "если", "ли", "же", "бы", "вот", "уже", "еще", "меня", "мне",
                "мой", "моя", "мы", "нас", "вам", "вас", "вы", "ты", "про", "прошу", "хочу",
                "хотел", "хотела", "интересует", "вопрос", "пожалуйста", "также", "либо",
                "между", "после", "перед", "только", "этот", "эта", "эти", "том", "того",
                "этого", "этом", "найди", "найти", "покажи", "показать", "открой",
                "открыть", "ищи", "поискать", "информация", "сведения",
                "документе", "документах"
        ));
    }

    static final class TitleHit {
        final String fileKey;
        final String title;
        final String chapter;
        final int score;

        TitleHit(String fileKey, String title, String chapter, int score) {
            this.fileKey = fileKey;
            this.title = title;
            this.chapter = chapter;
            this.score = score;
        }
    }

    static final class AnalyzedQuery {
        final String phrase;
        final List<String> originalTerms;
        final Set<String> originalKeys;
        final Set<String> synonymOnly;

        AnalyzedQuery(String phrase,
                      List<String> originalTerms,
                      Set<String> originalKeys,
                      Set<String> synonymOnly) {
            this.phrase = phrase;
            this.originalTerms = originalTerms;
            this.originalKeys = originalKeys;
            this.synonymOnly = synonymOnly;
        }
    }

    private static final class ScoredChunk {
        private final LocalDocumentRetriever.DocumentChunk chunk;
        private final int score;

        private ScoredChunk(LocalDocumentRetriever.DocumentChunk chunk, int score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
