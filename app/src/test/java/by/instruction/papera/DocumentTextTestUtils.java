package by.instruction.papera;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Утилиты для unit-тестов: чтение текста документов из assets.
 */
final class DocumentTextTestUtils {
    private static final int TEXT_CAP = 400_000;
    private static final long LIGHTWEIGHT_THRESHOLD = 256L * 1024L;
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "и", "в", "во", "на", "по", "для", "с", "со", "к", "ко", "о", "об", "от", "до",
            "или", "а", "но", "как", "что", "это", "при", "из", "за", "под", "над", "у", "не",
            "статья", "пункт", "глава", "раздел", "часть", "настоящей", "настоящего",
            "республики", "беларусь", "постановления", "утверждено", "министерства"
    ));

    private DocumentTextTestUtils() {
    }

    static File resolveAssetsDir() {
        String[] candidates = {
                "src/main/assets",
                "app/src/main/assets",
                "../app/src/main/assets"
        };
        for (String path : candidates) {
            File dir = new File(path);
            if (dir.isDirectory()) {
                return dir.getAbsoluteFile();
            }
        }
        throw new IllegalStateException("Не найдена папка assets с документами");
    }

    static File resolveDocumentFile(String fileKey) {
        File assets = resolveAssetsDir();
        File docx = new File(assets, fileKey + ".docx");
        if (docx.isFile()) {
            return docx;
        }
        File doc = new File(assets, fileKey + ".doc");
        if (doc.isFile()) {
            return doc;
        }
        throw new IllegalArgumentException("Файл не найден для ключа: " + fileKey
                + " в " + assets.getAbsolutePath());
    }

    static String extractText(String fileKey) throws IOException {
        File file = resolveDocumentFile(fileKey);
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".docx")) {
            if (file.length() >= LIGHTWEIGHT_THRESHOLD) {
                return extractDocxLightweight(file);
            }
            return extractDocxPoi(file);
        }
        if (name.endsWith(".doc")) {
            return extractDocPoi(file);
        }
        throw new IOException("Неизвестный формат: " + file.getName());
    }

    private static String extractDocPoi(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file));
             HWPFDocument document = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(document)) {
            String text = extractor.getText();
            return truncate(text != null ? text : "");
        }
    }

    private static String extractDocxPoi(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file));
             XWPFDocument document = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                appendCapped(sb, paragraph.getText(), '\n');
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        appendCapped(sb, cell.getText(), '\t');
                    }
                    appendCapped(sb, "", '\n');
                }
            }
            return sb.toString();
        }
    }

    private static String extractDocxLightweight(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                byte[] raw = readAll(zis, 8 * 1024 * 1024);
                String xml = new String(raw, StandardCharsets.UTF_8);
                boolean inText = false;
                StringBuilder token = new StringBuilder();
                for (int i = 0; i < xml.length(); i++) {
                    char c = xml.charAt(i);
                    if (c == '<') {
                        if (inText && token.length() > 0) {
                            appendCapped(sb, token.toString(), '\0');
                            token.setLength(0);
                        }
                        inText = false;
                        int close = xml.indexOf('>', i);
                        if (close < 0) {
                            break;
                        }
                        String tag = xml.substring(i + 1, close);
                        if (tag.startsWith("w:t") || tag.startsWith("w:t ")) {
                            inText = true;
                        } else if (tag.startsWith("w:p") || tag.equals("/w:p")
                                || tag.startsWith("w:p ") || tag.startsWith("/w:p ")) {
                            appendCapped(sb, "", '\n');
                        } else if (tag.startsWith("w:tab") || tag.equals("w:tab/")) {
                            appendCapped(sb, "\t", '\0');
                        }
                        i = close;
                        continue;
                    }
                    if (inText) {
                        token.append(c);
                    }
                }
                if (token.length() > 0) {
                    appendCapped(sb, token.toString(), '\0');
                }
                break;
            }
        }
        return sb.toString();
    }

    private static void appendCapped(StringBuilder sb, String text, char separator) {
        if (sb.length() >= TEXT_CAP) {
            return;
        }
        if (text != null && !text.isEmpty()) {
            int remain = TEXT_CAP - sb.length();
            if (text.length() > remain) {
                sb.append(text, 0, remain);
                return;
            }
            sb.append(text);
        }
        if (separator != '\0' && sb.length() < TEXT_CAP) {
            sb.append(separator);
        }
    }

    private static String truncate(String text) {
        if (text.length() <= TEXT_CAP) {
            return text;
        }
        return text.substring(0, TEXT_CAP);
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        byte[] buffer = new byte[Math.min(maxBytes, 64 * 1024)];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int total = 0;
        int n;
        while ((n = in.read(buffer)) >= 0) {
            int toWrite = Math.min(n, maxBytes - total);
            if (toWrite <= 0) {
                break;
            }
            out.write(buffer, 0, toWrite);
            total += toWrite;
            if (total >= maxBytes) {
                break;
            }
        }
        return out.toByteArray();
    }

    /**
     * Берёт характерное слово из середины документа для проверки поиска.
     */
    static String pickSearchQuery(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Пустой текст документа");
        }
        String[] parts = content.split("[^\\p{L}\\p{Nd}]+");
        int start = Math.max(0, parts.length / 3);
        for (int i = start; i < parts.length; i++) {
            String word = parts[i];
            if (word == null) {
                continue;
            }
            word = word.trim();
            if (word.length() < 5) {
                continue;
            }
            String lower = word.toLowerCase(Locale.ROOT);
            if (STOPWORDS.contains(lower)) {
                continue;
            }
            if (word.chars().allMatch(Character::isDigit)) {
                continue;
            }
            return word;
        }
        for (String word : parts) {
            if (word != null && word.trim().length() >= 4) {
                return word.trim();
            }
        }
        throw new IllegalStateException("Не удалось выбрать поисковое слово из документа");
    }

    static String pickSecondSearchQuery(String content, String firstQuery) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Пустой текст документа");
        }
        String firstNorm = DocumentSearchMatcher.normalizeForSearch(firstQuery);
        String[] parts = content.split("[^\\p{L}\\p{Nd}]+");
        int start = Math.max(0, parts.length / 2);
        for (int i = start; i < parts.length; i++) {
            String word = parts[i];
            if (word == null) {
                continue;
            }
            word = word.trim();
            if (word.length() < 5) {
                continue;
            }
            String lower = word.toLowerCase(Locale.ROOT);
            if (STOPWORDS.contains(lower)) {
                continue;
            }
            if (DocumentSearchMatcher.normalizeForSearch(word).equals(firstNorm)) {
                continue;
            }
            return word;
        }
        return firstQuery;
    }
}
