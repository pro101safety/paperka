package by.instruction.papera;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalDocumentQueryEngineTest {

    private final LocalDocumentQueryEngine engine = new LocalDocumentQueryEngine();

    @Test
    public void instructionQueryDoesNotReturnAccidentChunks() {
        List<LocalDocumentRetriever.DocumentChunk> results = retrieve(
                "когда проводят вводный инструктаж",
                chunk("Инструктаж по ОТ", "t90",
                        "Вводный инструктаж по охране труда проводится со всеми принятыми на работу лицами."),
                chunk("Расследование НС", "t125",
                        "Расследование несчастного случая на производстве проводится комиссией нанимателя.")
        );

        assertFalse(results.isEmpty());
        assertEquals("t90", results.get(0).fileKey);
        assertFalse(containsFile(results, "t125"));
    }

    @Test
    public void measurementQueryDoesNotPreferSiz() {
        List<LocalDocumentRetriever.DocumentChunk> results = retrieve(
                "какие средства измерения нужны",
                chunk("Средства измерений", "t200",
                        "Поверка средств измерений проводится в сроки, установленные изготовителем."),
                chunk("Обеспечение СИЗ", "t130",
                        "Наниматель обеспечивает работников средствами индивидуальной защиты.")
        );

        assertFalse(results.isEmpty());
        assertEquals("t200", results.get(0).fileKey);
        assertFalse(containsFile(results, "t130"));
    }

    @Test
    public void sizAbbreviationFindsProtectiveEquipment() {
        List<LocalDocumentRetriever.DocumentChunk> results = retrieve(
                "сиз",
                chunk("Обеспечение СИЗ", "t130",
                        "Работники обеспечиваются средствами индивидуальной защиты согласно нормам."),
                chunk("Медосмотры", "t116",
                        "Обязательные медицинские осмотры проводятся в организациях здравоохранения.")
        );

        assertFalse(results.isEmpty());
        assertEquals("t130", results.get(0).fileKey);
        assertFalse(containsFile(results, "t116"));
    }

    @Test
    public void medicalExamPrefersMatchingTitle() {
        List<LocalDocumentRetriever.DocumentChunk> results = retrieve(
                "периодичность медосмотра водителей",
                chunk("Обязательные медосмотры", "t116",
                        "Периодический медицинский осмотр водителей проводится в установленные сроки."),
                chunk("Правила по охране труда", "t11",
                        "Работники обязаны соблюдать требования по охране труда.")
        );

        assertFalse("Ожидался документ по медосмотрам, получено: " + fileKeys(results), results.isEmpty());
        assertEquals("t116", results.get(0).fileKey);
        assertFalse("Общий документ по ОТ не должен попадать в выдачу: " + fileKeys(results),
                containsFile(results, "t11"));
    }

    @Test
    public void unrelatedQueryReturnsEmpty() {
        List<LocalDocumentRetriever.DocumentChunk> results = retrieve(
                "ксилофон балалайка",
                chunk("Охрана труда", "t01",
                        "Настоящий Закон регулирует отношения в области охраны труда.")
        );

        assertTrue(results.isEmpty());
    }

    @Test
    public void heightWorkQueryIgnoresGenericLaborProtection() {
        List<LocalDocumentRetriever.DocumentChunk> results = retrieve(
                "найди требования охраны труда при работе на высоте",
                chunk("ПОТ при выполнении работ на высоте", "t27",
                        "Работы на высоте выполняются по наряду-допуску с применением страховочных систем."),
                chunk("Закон об охране труда", "t01",
                        "Настоящий Закон регулирует требования охраны труда работников.")
        );

        assertFalse("Ожидался документ по работам на высоте, получено: " + fileKeys(results),
                results.isEmpty());
        assertEquals("t27", results.get(0).fileKey);
        assertFalse(containsFile(results, "t01"));
    }

    @Test
    public void catalogPrefersDriverMedicalExam() {
        List<LocalDocumentRetriever.DocumentChunk> results = fromCatalog("медосмотр водителей");
        assertFalse("Ожидался документ по медосмотру водителей, получено: " + fileKeys(results),
                results.isEmpty());
        assertEquals("t118", results.get(0).fileKey);
    }

    @Test
    public void spokenMedicalExamFindsDriverTitle() {
        List<LocalDocumentRetriever.DocumentChunk> results = fromCatalog("медицинский осмотр водителей");
        assertFalse("Ожидался документ по медосмотру водителей, получено: " + fileKeys(results),
                results.isEmpty());
        assertEquals("t118", results.get(0).fileKey);
    }

    @Test
    public void catalogPrefersHeightWorkRules() {
        List<LocalDocumentRetriever.DocumentChunk> results = fromCatalog("работы на высоте");
        assertFalse("Ожидался документ по работам на высоте, получено: " + fileKeys(results),
                results.isEmpty());
        assertEquals("t27", results.get(0).fileKey);
    }

    @Test
    public void catalogPrefersSizInstruction() {
        List<LocalDocumentRetriever.DocumentChunk> results = fromCatalog("сиз");
        assertFalse("Ожидалась инструкция по СИЗ, получено: " + fileKeys(results), results.isEmpty());
        assertEquals("t130", results.get(0).fileKey);
    }

    @Test
    public void catalogPrefersInstructionTraining() {
        List<LocalDocumentRetriever.DocumentChunk> results = fromCatalog("вводный инструктаж");
        assertFalse("Ожидалась инструкция по инструктажу, получено: " + fileKeys(results),
                results.isEmpty());
        assertEquals("t90", results.get(0).fileKey);
    }

    @Test
    public void ftsQueryKeepsSizAndSynonym() {
        String fts = engine.buildFtsQuery("сиз");
        assertTrue(fts.contains("сиз"));
        assertTrue(fts.contains("средства индивидуальной защиты"));
    }

    @Test
    public void ftsQueryRequiresBothDriverMedicalTerms() {
        String fts = engine.buildFtsQuery("медосмотр водителей");
        assertTrue(fts, fts.contains("медосмотр"));
        assertTrue(fts, fts.contains("водител"));
        assertTrue(fts, fts.contains(" AND "));
    }

    @Test
    public void highlightQueryKeepsDistinctiveTerms() {
        String highlight = engine.pickHighlightQuery("подскажите когда проводят вводный инструктаж");
        assertTrue(highlight.contains("инструктаж"));
        assertFalse(highlight.contains("подскажите"));
        assertFalse(highlight.contains("когда"));
    }

    private List<LocalDocumentRetriever.DocumentChunk> retrieve(
            String query,
            LocalDocumentRetriever.DocumentChunk... chunks) {
        List<LocalDocumentRetriever.DocumentChunk> index = Arrays.asList(chunks);
        return engine.retrieve(query, index, engine.buildDf(index), 5);
    }

    private List<LocalDocumentRetriever.DocumentChunk> fromCatalog(String query) {
        return engine.mergeCatalogAndBody(
                query, ChapterCatalog.build(), Collections.emptyList(), Collections.emptyMap(), 3);
    }

    private static LocalDocumentRetriever.DocumentChunk chunk(String title, String fileKey, String text) {
        return new LocalDocumentRetriever.DocumentChunk(title, fileKey, 0, text);
    }

    private static boolean containsFile(List<LocalDocumentRetriever.DocumentChunk> chunks, String fileKey) {
        for (LocalDocumentRetriever.DocumentChunk chunk : chunks) {
            if (fileKey.equals(chunk.fileKey)) {
                return true;
            }
        }
        return false;
    }

    private static String fileKeys(List<LocalDocumentRetriever.DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(chunks.get(i).fileKey);
        }
        return sb.toString();
    }
}
