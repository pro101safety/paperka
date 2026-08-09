package by.instruction.papera;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Поиск внутри документов: по 2 документа из каждого раздела каталога.
 */
@RunWith(Parameterized.class)
public class SectionDocumentSearchTest {

    @Parameterized.Parameter
    public SectionDocumentCatalog.Entry entry;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> rows = new ArrayList<>();
        for (SectionDocumentCatalog.Entry entry : SectionDocumentCatalog.allSelectedDocuments()) {
            rows.add(new Object[]{entry});
        }
        return rows;
    }

    @Test
    public void documentExistsAndHasSearchableText() throws Exception {
        String text = DocumentTextTestUtils.extractText(entry.fileKey);
        assertNotNull(text);
        assertTrue("Пустой текст: " + entry,
                text.replaceAll("\\s+", "").length() >= 40);
    }

    @Test
    public void searchFindsWordFromDocument() throws Exception {
        String text = DocumentTextTestUtils.extractText(entry.fileKey);
        String query = DocumentTextTestUtils.pickSearchQuery(text);

        List<DocSearchResult> results = DocumentSearchMatcher.findMatches(
                text, query, false, false);

        assertFalse("Не найдено \"" + query + "\" в " + entry, results.isEmpty());
        assertTrue("Индекс результата должен быть >= 0", results.get(0).getIndex() >= 0);
        assertTrue("Позиция должна быть в пределах текста",
                results.get(0).getPosition() >= 0
                        && results.get(0).getPosition() < text.length());
        assertNotNull(results.get(0).getSnippet());
        assertFalse(results.get(0).getSnippet().trim().isEmpty());
    }

    @Test
    public void searchIsCaseInsensitive() throws Exception {
        String text = DocumentTextTestUtils.extractText(entry.fileKey);
        String query = DocumentTextTestUtils.pickSearchQuery(text);

        List<DocSearchResult> lower = DocumentSearchMatcher.findMatches(
                text, query.toLowerCase(Locale.ROOT), false, false);
        List<DocSearchResult> upper = DocumentSearchMatcher.findMatches(
                text, query.toUpperCase(Locale.ROOT), false, false);

        assertFalse("Нижний регистр не нашёл в " + entry, lower.isEmpty());
        assertFalse("Верхний регистр не нашёл в " + entry, upper.isEmpty());
        assertTrue("Число совпадений upper/lower сильно разъехалось в " + entry,
                Math.abs(lower.size() - upper.size()) <= 2);
    }

    @Test
    public void secondQueryAlsoFindsMatches() throws Exception {
        String text = DocumentTextTestUtils.extractText(entry.fileKey);
        String first = DocumentTextTestUtils.pickSearchQuery(text);
        String second = DocumentTextTestUtils.pickSecondSearchQuery(text, first);

        List<DocSearchResult> results = DocumentSearchMatcher.findMatches(
                text, second, false, false);
        assertFalse("Второй запрос \"" + second + "\" не найден в " + entry, results.isEmpty());
    }

    @Test
    public void nonsenseQueryNotFound() throws Exception {
        String text = DocumentTextTestUtils.extractText(entry.fileKey);
        List<DocSearchResult> results = DocumentSearchMatcher.findMatches(
                text, "qwertyнесуществующийтокен_zzz_42", false, false);
        assertTrue("Случайный запрос не должен находиться в " + entry, results.isEmpty());
    }
}
