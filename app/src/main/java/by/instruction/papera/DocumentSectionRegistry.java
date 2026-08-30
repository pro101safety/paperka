package by.instruction.papera;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DocumentSectionRegistry {
    private static final Map<String, String> FILE_TO_SECTION = new ConcurrentHashMap<>();

    private DocumentSectionRegistry() {
    }

    public static void ensureLoaded() {
        if (!FILE_TO_SECTION.isEmpty()) {
            return;
        }
        refreshFromChapters(ChapterCatalog.build());
    }

    public static void refreshFromChapters(List<Chapter> chapters) {
        FILE_TO_SECTION.clear();
        if (chapters == null) {
            return;
        }
        for (Chapter chapter : chapters) {
            if (chapter == null || chapter.getTopicsList() == null) {
                continue;
            }
            for (Topics topic : chapter.getTopicsList()) {
                if (topic == null) {
                    continue;
                }
                String fileName = normalizeFileKey(topic.getFileName());
                String sectionTitle = topic.getTopicName();
                if (fileName.isEmpty() || sectionTitle == null || sectionTitle.trim().isEmpty()) {
                    continue;
                }
                FILE_TO_SECTION.put(fileName, sectionTitle.trim());
            }
        }
    }

    public static String resolveDisplayName(String assetFileName) {
        return resolveSectionInfo(assetFileName).displayName;
    }

    public static SectionInfo resolveSectionInfo(String assetFileName) {
        String key = normalizeFileKey(assetFileName);
        String section = FILE_TO_SECTION.get(key);
        String displayName = (section == null || section.trim().isEmpty()) ? assetFileName : section;
        return new SectionInfo(key, displayName);
    }

    private static String normalizeFileKey(String value) {
        if (value == null) {
            return "";
        }
        String key = value.trim().toLowerCase();
        if (key.endsWith(".docx")) {
            return key.substring(0, key.length() - 5);
        }
        if (key.endsWith(".doc")) {
            return key.substring(0, key.length() - 4);
        }
        return key;
    }

    public static final class SectionInfo {
        public final String fileKey;
        public final String displayName;

        public SectionInfo(String fileKey, String displayName) {
            this.fileKey = fileKey;
            this.displayName = displayName;
        }
    }
}
