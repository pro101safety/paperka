package by.instruction.papera;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BookmarkStore {

    private static final String PREFS_NAME = "bookmarks_store";

    public static class BookmarkEntry {
        public final int pageIndex;
        public final String title;
        public final long timestampMillis;
        public final String uniqueId; // Уникальный идентификатор закладки
        public final String snippet; // Короткий текст со страницы

        public BookmarkEntry(int pageIndex, String title, long timestampMillis) {
            this(pageIndex, title, timestampMillis, "", null);
        }
        
        public BookmarkEntry(int pageIndex, String title, long timestampMillis, String snippet, String uniqueId) {
            this.pageIndex = pageIndex;
            this.title = title;
            this.timestampMillis = timestampMillis;
            this.snippet = snippet == null ? "" : snippet;
            this.uniqueId = (uniqueId == null || uniqueId.isEmpty()) ? generateUniqueId(pageIndex, timestampMillis) : uniqueId;
        }
        
        private static String generateUniqueId(int pageIndex, long timestamp) {
            return pageIndex + "_" + timestamp;
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<BookmarkEntry> getBookmarks(Context context, String fileKey) {
        List<BookmarkEntry> result = new ArrayList<>();
        String raw = getPrefs(context).getString(fileKey, null);
        if (raw == null || raw.isEmpty()) return result;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long ts = o.has("ts") ? o.optLong("ts", 0L) : 0L;
                String uniqueId = o.has("id") ? o.optString("id", "") : "";
                String snippet = o.has("snippet") ? o.optString("snippet", "") : "";
                BookmarkEntry entry = new BookmarkEntry(o.optInt("page", 0), o.optString("title", ""), ts, snippet, uniqueId);
                result.add(entry);
            }
        } catch (JSONException ignored) {}
        return result;
    }

    public static void addBookmark(Context context, String fileKey, int pageIndex, String title) {
        List<BookmarkEntry> list = getBookmarks(context, fileKey);
        // Разрешаем множественные закладки на одной странице
        list.add(new BookmarkEntry(pageIndex, title, System.currentTimeMillis()));
        save(context, fileKey, list);
    }

    public static void addBookmark(Context context, String fileKey, int pageIndex, String title, String snippet) {
        List<BookmarkEntry> list = getBookmarks(context, fileKey);
        list.add(new BookmarkEntry(pageIndex, title, System.currentTimeMillis(), snippet, null));
        save(context, fileKey, list);
    }

    public static void removeBookmark(Context context, String fileKey, int pageIndex) {
        List<BookmarkEntry> list = getBookmarks(context, fileKey);
        List<BookmarkEntry> filtered = new ArrayList<>();
        for (BookmarkEntry e : list) {
            if (e.pageIndex != pageIndex) filtered.add(e);
        }
        save(context, fileKey, filtered);
    }
    
    public static void removeBookmarkById(Context context, String fileKey, String uniqueId) {
        List<BookmarkEntry> list = getBookmarks(context, fileKey);
        List<BookmarkEntry> filtered = new ArrayList<>();
        for (BookmarkEntry e : list) {
            if (!e.uniqueId.equals(uniqueId)) filtered.add(e);
        }
        save(context, fileKey, filtered);
    }

    private static void save(Context context, String fileKey, List<BookmarkEntry> list) {
        JSONArray arr = new JSONArray();
        for (BookmarkEntry e : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("page", e.pageIndex);
                o.put("title", e.title);
                o.put("ts", e.timestampMillis);
                o.put("id", e.uniqueId);
                if (e.snippet != null && !e.snippet.isEmpty()) {
                    o.put("snippet", e.snippet);
                }
            } catch (JSONException ignored) {}
            arr.put(o);
        }
        getPrefs(context).edit().putString(fileKey, arr.toString()).apply();
    }
}


