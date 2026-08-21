package by.instruction.papera.data;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import by.instruction.papera.Chapter;
import by.instruction.papera.Topics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CatalogStoreTest {

    @Test
    public void fromJson_parsesChaptersAndTopics() throws JSONException {
        String json = "{"
                + "\"chapters\":["
                + "{\"name\":\"Глава 1\",\"topics\":["
                + "{\"title\":\"Документ А\",\"file\":\"t01\"},"
                + "{\"title\":\"\",\"file\":\"skip\"},"
                + "{\"title\":\"Документ Б\",\"file\":\"t02\"}"
                + "]},"
                + "{\"name\":\"\",\"topics\":[{\"title\":\"X\",\"file\":\"t99\"}]}"
                + "]}";

        List<Chapter> chapters = CatalogParser.fromJson(json);

        assertEquals(1, chapters.size());
        assertEquals("Глава 1", chapters.get(0).getChapterName());
        assertEquals(2, chapters.get(0).getTopicsList().size());
        assertEquals("Документ А", chapters.get(0).getTopicsList().get(0).getTopicName());
        assertEquals("t01", chapters.get(0).getTopicsList().get(0).getFileName());
        assertEquals("t02", chapters.get(0).getTopicsList().get(1).getFileName());
    }

    @Test
    public void fromJson_loadsFullCatalogAsset() throws Exception {
        String json = readCatalogAsset();
        List<Chapter> chapters = CatalogParser.fromJson(json);

        assertEquals(23, chapters.size());
        int topicCount = 0;
        for (Chapter chapter : chapters) {
            assertFalse(chapter.getChapterName().trim().isEmpty());
            for (Topics topic : chapter.getTopicsList()) {
                assertFalse(topic.getTopicName().trim().isEmpty());
                assertFalse(topic.getFileName().trim().isEmpty());
                topicCount++;
            }
        }
        assertEquals(236, topicCount);
        assertEquals("1. Кодексы, Законы, Директивы...", chapters.get(0).getChapterName());
        assertEquals("t001", chapters.get(0).getTopicsList().get(0).getFileName());
        assertTrue(chapters.get(22).getChapterName().contains("Типичные нарушения"));
    }

    private static String readCatalogAsset() throws IOException {
        Path[] candidates = new Path[] {
                Paths.get("src/main/assets/catalog.json"),
                Paths.get("app/src/main/assets/catalog.json")
        };
        for (Path path : candidates) {
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("catalog.json not found");
    }
}
