package by.instruction.papera;

import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SectionDocumentCatalogTest {

    @Test
    public void coversAllTwentyFourSectionsWithUpToTwoDocumentsEach() {
        Set<String> sections = new LinkedHashSet<>();
        Map<String, Integer> counts = new HashMap<>();

        for (SectionDocumentCatalog.Entry entry : SectionDocumentCatalog.allSelectedDocuments()) {
            String num = entry.section.replaceFirst("^(\\d+)\\..*$", "$1");
            sections.add(num);
            counts.put(num, counts.getOrDefault(num, 0) + 1);
        }

        for (int i = 1; i <= 24; i++) {
            String key = String.valueOf(i);
            if (!sections.contains(key)) {
                fail("В каталоге тестов нет раздела " + i);
            }
            int count = counts.getOrDefault(key, 0);
            assertTrue("Раздел " + i + " должен содержать 1–2 документа, сейчас: " + count,
                    count >= 1 && count <= 2);
        }
    }
}
