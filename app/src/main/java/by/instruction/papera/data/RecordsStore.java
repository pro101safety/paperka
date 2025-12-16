package by.instruction.papera.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores and returns last three survival records (ms) for the mini-game.
 */
public class RecordsStore {
    private static final String PREF_NAME = "iot_game_records";
    private static final String KEY_TIMES_MS = "times_ms";

    private final SharedPreferences prefs;

    public RecordsStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<Long> addRecord(long durationMs) {
        List<Long> records = new ArrayList<>(getRecords());
        records.add(durationMs);
        Collections.sort(records);
        Collections.reverse(records);
        if (records.size() > 3) {
            records = new ArrayList<>(records.subList(0, 3));
        }
        save(records);
        return records;
    }

    public List<Long> getRecords() {
        String raw = prefs.getString(KEY_TIMES_MS, "");
        List<Long> items = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return items;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            try {
                items.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    private void save(List<Long> records) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(records.get(i));
        }
        prefs.edit().putString(KEY_TIMES_MS, sb.toString()).apply();
    }
}

