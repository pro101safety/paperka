package by.instruction.papera;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChapterCatalogTest {

    @Test
    public void catalogHasNamedDocuments() {
        List<Chapter> chapters = ChapterCatalog.build();
        assertFalse(chapters.isEmpty());
        DocumentSectionRegistry.refreshFromChapters(chapters);
        String title = DocumentSectionRegistry.resolveDisplayName("t116");
        assertTrue(title.toLowerCase().contains("медосмотр"));
    }
}