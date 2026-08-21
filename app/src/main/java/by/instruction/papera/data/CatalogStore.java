package by.instruction.papera.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import by.instruction.papera.Chapter;

/**
 * Loads the document catalog from {@code assets/catalog.json}.
 */
public final class CatalogStore {
    public static final String CATALOG_FILE = "catalog.json";
    private static final String TAG = "CatalogStore";

    private CatalogStore() {}

    public static List<Chapter> load(Context context) {
        try {
            return CatalogParser.fromJson(readAsset(context, CATALOG_FILE));
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to load catalog", e);
            return new ArrayList<>();
        }
    }

    private static String readAsset(Context context, String fileName) throws IOException {
        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }
}
