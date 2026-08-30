package by.instruction.papera;

public class SearchHit {
    private final String documentName;
    private final String snippet;
    private final String fileKey;

    public SearchHit(String documentName, String snippet, String fileKey) {
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
