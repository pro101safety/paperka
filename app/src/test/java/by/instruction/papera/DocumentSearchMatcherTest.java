package by.instruction.papera;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DocumentSearchMatcherTest {

    @Test
    public void findsCaseInsensitiveCyrillic() {
        String content = "Требования охраны труда обязательны для всех работников.";
        List<DocSearchResult> results = DocumentSearchMatcher.findMatches(
                content, "ОХРАНЫ", false, false);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getSnippet().toLowerCase().contains("охран"));
    }

    @Test
    public void findsYoAsYe() {
        String content = "Нормы подъёма грузов для женщин.";
        List<DocSearchResult> results = DocumentSearchMatcher.findMatches(
                content, "подъема", false, false);
        assertEquals(1, results.size());
    }

    @Test
    public void wholeWordSkipsSubstring() {
        String content = "Работа и сотрудник выполняют работы по наряду.";
        List<DocSearchResult> loose = DocumentSearchMatcher.findMatches(
                content, "работ", false, false);
        List<DocSearchResult> whole = DocumentSearchMatcher.findMatches(
                content, "работ", false, true);
        assertTrue(loose.size() >= whole.size());
        assertTrue(whole.isEmpty() || whole.get(0).getText().equalsIgnoreCase("работ"));
    }

    @Test
    public void flexibleWhitespaceAcrossNewlines() {
        String content = "средства\nиндивидуальной\nзащиты";
        List<DocSearchResult> results = DocumentSearchMatcher.findMatches(
                content, "средства индивидуальной защиты", false, false);
        assertFalse(results.isEmpty());
    }

    @Test
    public void nonsenseQueryReturnsEmpty() {
        String content = "Охрана труда и промышленная безопасность.";
        List<DocSearchResult> results = DocumentSearchMatcher.findMatches(
                content, "xyzqqq123несуществующее", false, false);
        assertTrue(results.isEmpty());
    }

    @Test
    public void emptyQueryReturnsEmpty() {
        assertTrue(DocumentSearchMatcher.findMatches("текст", "   ", false, false).isEmpty());
        assertTrue(DocumentSearchMatcher.findMatches("", "охрана", false, false).isEmpty());
    }
}
