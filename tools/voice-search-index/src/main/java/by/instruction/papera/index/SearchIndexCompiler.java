package by.instruction.papera.index;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Собирает SQLite FTS-базу голосового поиска: file_key + title + fragment.
 * Word-файлы не изменяет — только читает их и каталог разделов.
 */
public final class SearchIndexCompiler {
    private static final int CHUNK_CHAR_LIMIT = 700;
    private static final int MAX_DOC_CHARS = 200000;
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("new Topics\\(\"([^\"]*)\", \"(t[^\"]+)\"\\)");

    private SearchIndexCompiler() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "Usage: SearchIndexCompiler <ChapterCatalog.java> <assetsDir> <out.db> <out.stamp>");
        }
        File catalogFile = new File(args[0]);
        File assetsDir = new File(args[1]);
        File outDb = new File(args[2]);
        File outStamp = new File(args[3]);

        Map<String, String> titles = parseCatalog(catalogFile);
        List<File> docs = listDocuments(assetsDir);
        File parent = outDb.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create " + parent);
        }
        if (outDb.exists() && !outDb.delete()) {
            throw new IllegalStateException("Cannot overwrite " + outDb);
        }

        Class.forName("org.sqlite.JDBC");
        String jdbcUrl = "jdbc:sqlite:" + outDb.getAbsolutePath().replace('\\', '/');
        int rows = 0;
        int docsWithText = 0;
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA journal_mode=OFF");
                pragma.execute("PRAGMA synchronous=OFF");
            }
            connection.setAutoCommit(false);
            try (Statement schema = connection.createStatement()) {
                schema.execute("CREATE VIRTUAL TABLE voice_fts USING fts4(file_key, title, fragment)");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO voice_fts(file_key, title, fragment) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, String> entry : titles.entrySet()) {
                    String fileKey = entry.getKey();
                    String title = entry.getValue();
                    rows += insertChunks(insert, fileKey, title, title);
                    File doc = findDocument(docs, fileKey);
                    if (doc == null) {
                        continue;
                    }
                    String text = extractText(doc);
                    if (text == null || text.trim().isEmpty()) {
                        continue;
                    }
                    docsWithText++;
                    rows += insertChunks(insert, fileKey, title, text);
                }
            }
            connection.commit();
        }

        String stamp = buildStamp(catalogFile, docs, rows, docsWithText);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(outStamp.toPath()), StandardCharsets.UTF_8)) {
            writer.write(stamp);
        }
        System.out.println("Voice search index: " + rows + " fragments, "
                + docsWithText + " documents with text, "
                + titles.size() + " catalog titles -> " + outDb.getAbsolutePath());
    }

    static Map<String, String> parseCatalog(File catalogFile) throws Exception {
        String source = new String(Files.readAllBytes(catalogFile.toPath()), StandardCharsets.UTF_8);
        Matcher matcher = TOPIC_PATTERN.matcher(source);
        Map<String, String> titles = new LinkedHashMap<String, String>();
        while (matcher.find()) {
            String title = matcher.group(1);
            String fileKey = matcher.group(2).trim().toLowerCase(Locale.ROOT);
            if (!fileKey.isEmpty() && !titles.containsKey(fileKey)) {
                titles.put(fileKey, title);
            }
        }
        if (titles.isEmpty()) {
            throw new IllegalStateException("No topics found in " + catalogFile);
        }
        return titles;
    }

    private static List<File> listDocuments(File assetsDir) {
        File[] files = assetsDir.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<File> docs = new ArrayList<File>();
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".doc") || name.endsWith(".docx")) {
                docs.add(file);
            }
        }
        return docs;
    }

    private static File findDocument(List<File> docs, String fileKey) {
        for (File file : docs) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.equals(fileKey + ".doc") || name.equals(fileKey + ".docx")) {
                return file;
            }
        }
        return null;
    }

    private static int insertChunks(PreparedStatement insert, String fileKey, String title, String text)
            throws Exception {
        int count = 0;
        for (String chunk : splitChunks(text)) {
            insert.setString(1, fileKey);
            insert.setString(2, title);
            insert.setString(3, chunk);
            insert.addBatch();
            count++;
        }
        if (count > 0) {
            insert.executeBatch();
        }
        return count;
    }

    static List<String> splitChunks(String text) {
        List<String> chunks = new ArrayList<String>();
        if (text == null) {
            return chunks;
        }
        String normalized = text.replace('\r', '\n');
        String[] paragraphs = normalized.split("\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String line = paragraph == null ? "" : paragraph.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (current.length() + line.length() + 1 > CHUNK_CHAR_LIMIT && current.length() > 0) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private static String extractText(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            if (name.endsWith(".docx")) {
                return parseDocx(is);
            }
            return parseDoc(is);
        } catch (Exception e) {
            System.err.println("Skip " + file.getName() + ": " + e.getMessage());
            return "";
        }
    }

    private static String parseDocx(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder(8192);
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
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
                                if (sb.length() >= MAX_DOC_CHARS) {
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
                        para.append(decodeXmlEntities(line.substring(tClose + 1, tEnd)));
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

    private static String parseDoc(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (HWPFDocument document = new HWPFDocument(is)) {
            Range range = document.getRange();
            appendCapped(sb, range.text());
        }
        return sb.toString();
    }

    private static void appendCapped(StringBuilder sb, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int remain = MAX_DOC_CHARS - sb.length();
        if (remain <= 0) {
            return;
        }
        if (text.length() <= remain) {
            sb.append(text);
        } else {
            sb.append(text, 0, remain);
        }
    }

    private static String decodeXmlEntities(String value) {
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

    private static String buildStamp(File catalogFile, List<File> docs, int rows, int docsWithText)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(catalogFile.toPath()));
        List<File> sorted = new ArrayList<File>(docs);
        Collections.sort(sorted);
        for (File file : sorted) {
            digest.update(file.getName().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(file.length()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(file.lastModified()).getBytes(StandardCharsets.UTF_8));
        }
        byte[] hash = digest.digest();
        StringBuilder hex = new StringBuilder(rows + ":" + docsWithText + ":");
        for (byte b : hash) {
            hex.append(String.format(Locale.US, "%02x", b));
        }
        return hex.toString();
    }
}
