package by.instruction.papera.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import by.instruction.papera.Chapter;
import by.instruction.papera.Topics;

/**
 * Parses the document catalog JSON into chapters and topics.
 */
public final class CatalogParser {
    private CatalogParser() {}

    public static List<Chapter> fromJson(String json) throws JSONException {
        List<Chapter> chapters = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return chapters;
        }

        JSONObject root = new JSONObject(json);
        JSONArray chapterArray = root.optJSONArray("chapters");
        if (chapterArray == null) {
            return chapters;
        }

        for (int i = 0; i < chapterArray.length(); i++) {
            JSONObject chapterObject = chapterArray.optJSONObject(i);
            if (chapterObject == null) {
                continue;
            }
            String name = chapterObject.optString("name", "").trim();
            if (name.isEmpty()) {
                continue;
            }

            List<Topics> topics = new ArrayList<>();
            JSONArray topicArray = chapterObject.optJSONArray("topics");
            if (topicArray != null) {
                for (int j = 0; j < topicArray.length(); j++) {
                    JSONObject topicObject = topicArray.optJSONObject(j);
                    if (topicObject == null) {
                        continue;
                    }
                    String title = topicObject.optString("title", "").trim();
                    String file = topicObject.optString("file", "").trim();
                    if (title.isEmpty() || file.isEmpty()) {
                        continue;
                    }
                    topics.add(new Topics(title, file));
                }
            }
            chapters.add(new Chapter(name, topics));
        }
        return chapters;
    }
}
