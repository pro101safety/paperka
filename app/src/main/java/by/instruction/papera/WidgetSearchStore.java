package by.instruction.papera;

import android.content.Context;
import android.content.SharedPreferences;

final class WidgetSearchStore {
    private static final String PREFS = "voice_search_widget";
    private static final String KEY_QUERY = "query";
    private static final String KEY_TITLE = "title";
    private static final String KEY_SNIPPET = "snippet";
    private static final String KEY_FILE = "fileKey";
    private static final String KEY_HIGHLIGHT = "highlight";
    private static final String KEY_FOUND = "found";

    private WidgetSearchStore() {
    }

    static final class Result {
        final String query;
        final String documentName;
        final String snippet;
        final String fileKey;
        final String highlightQuery;
        final boolean found;

        Result(String query, String documentName, String snippet, String fileKey,
               String highlightQuery, boolean found) {
            this.query = query;
            this.documentName = documentName;
            this.snippet = snippet;
            this.fileKey = fileKey;
            this.highlightQuery = highlightQuery;
            this.found = found;
        }
    }

    static void save(Context context, Result result) {
        if (context == null || result == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_QUERY, safe(result.query))
                .putString(KEY_TITLE, safe(result.documentName))
                .putString(KEY_SNIPPET, safe(result.snippet))
                .putString(KEY_FILE, safe(result.fileKey))
                .putString(KEY_HIGHLIGHT, safe(result.highlightQuery))
                .putBoolean(KEY_FOUND, result.found)
                .apply();
    }

    static Result load(Context context) {
        if (context == null) {
            return empty("");
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String query = prefs.getString(KEY_QUERY, "");
        if (query == null || query.isEmpty()) {
            return empty("");
        }
        return new Result(
                query,
                prefs.getString(KEY_TITLE, ""),
                prefs.getString(KEY_SNIPPET, ""),
                prefs.getString(KEY_FILE, ""),
                prefs.getString(KEY_HIGHLIGHT, ""),
                prefs.getBoolean(KEY_FOUND, false)
        );
    }

    static Result empty(String query) {
        return new Result(query, "", "", "", "", false);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
