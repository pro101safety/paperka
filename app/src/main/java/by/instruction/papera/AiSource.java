package by.instruction.papera;

public class AiSource {
    private final String documentName;
    private final String snippet;
    private final String fileKey;

    public AiSource(String documentName, String snippet, String fileKey) {
        this.documentName = documentName;
        this.snippet = snippet;
        this.fileKey = fileKey;
    }

    public String getDocumentName() {
        return documentName;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getFileKey() {
        return fileKey;
    }
}
