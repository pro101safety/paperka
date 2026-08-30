package by.instruction.papera;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Локальный SQLite FTS-индекс голосового поиска (file_key + title + fragment).
 * Word-файлы в assets не заменяет — только подсказывает фрагмент и ключ файла.
 */
final class VoiceSearchIndex {
    private static final String TAG = "VoiceSearchIndex";
    private static final String DB_ASSET = "voice_search.db";
    private static final String STAMP_ASSET = "voice_search.stamp";
    private static final String DB_NAME = "voice_search.db";
    private static final String PREFS = "voice_search_index";
    private static final String PREF_STAMP = "stamp";
    private static final int CANDIDATE_LIMIT = 80;

    private final Context appContext;
    private final LocalDocumentQueryEngine engine;
    private SQLiteDatabase database;
    private boolean unavailable;

    VoiceSearchIndex(Context context, LocalDocumentQueryEngine engine) {
        this.appContext = context.getApplicationContext();
        this.engine = engine;
    }

    boolean isReady() {
        return ensureOpen();
    }

    List<LocalDocumentRetriever.DocumentChunk> loadCandidates(String query) {
        if (!ensureOpen() || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String andQuery = engine.buildFtsQuery(query);
        List<LocalDocumentRetriever.DocumentChunk> hits = queryFts(andQuery);
        if (hits.isEmpty()) {
            String orQuery = engine.buildFtsQueryOr(query);
            if (orQuery != null && !orQuery.equals(andQuery)) {
                hits = queryFts(orQuery);
            }
        }
        return hits;
    }

    private boolean ensureOpen() {
        if (database != null && database.isOpen()) {
            return true;
        }
        if (unavailable) {
            return false;
        }
        synchronized (this) {
            if (database != null && database.isOpen()) {
                return true;
            }
            if (unavailable) {
                return false;
            }
            try {
                File dbFile = copyIfNeeded();
                if (dbFile == null || !dbFile.exists() || dbFile.length() == 0) {
                    unavailable = true;
                    return false;
                }
                database = SQLiteDatabase.openDatabase(
                        dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Индекс голосового поиска недоступен", e);
                unavailable = true;
                return false;
            }
        }
    }

    private File copyIfNeeded() throws Exception {
        String stamp = readAssetText(STAMP_ASSET);
        if (stamp.isEmpty()) {
            stamp = "missing-stamp";
        }
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        File dbFile = appContext.getDatabasePath(DB_NAME);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create database directory");
        }
        if (dbFile.exists() && stamp.equals(prefs.getString(PREF_STAMP, ""))) {
            return dbFile;
        }
        try (InputStream in = appContext.getAssets().open(DB_ASSET);
             OutputStream out = new FileOutputStream(dbFile, false)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        prefs.edit().putString(PREF_STAMP, stamp).apply();
        Log.d(TAG, "Скопирован индекс голосового поиска, stamp=" + stamp);
        return dbFile;
    }

    private List<LocalDocumentRetriever.DocumentChunk> queryFts(String ftsQuery) {
        if (ftsQuery == null || ftsQuery.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<LocalDocumentRetriever.DocumentChunk> hits = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT file_key, title, fragment FROM voice_fts WHERE voice_fts MATCH ? LIMIT "
                        + CANDIDATE_LIMIT,
                new String[]{ftsQuery})) {
            int order = 0;
            while (cursor.moveToNext()) {
                String fileKey = cursor.getString(0);
                String title = cursor.getString(1);
                String fragment = cursor.getString(2);
                hits.add(new LocalDocumentRetriever.DocumentChunk(
                        title, fileKey, order++, fragment == null ? "" : fragment, engine));
            }
        } catch (Exception e) {
            Log.w(TAG, "FTS-запрос отклонён: " + ftsQuery, e);
        }
        return hits;
    }

    private String readAssetText(String name) {
        try (InputStream in = appContext.getAssets().open(name)) {
            byte[] buffer = new byte[4096];
            int read = in.read(buffer);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
