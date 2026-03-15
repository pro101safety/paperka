package by.instruction.papera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiChatMessage {
    public enum Role {
        USER,
        ASSISTANT
    }

    private final Role role;
    private final String text;
    private final List<AiSource> sources;
    private boolean sourcesExpanded;

    public AiChatMessage(Role role, String text, List<AiSource> sources) {
        this.role = role;
        this.text = text;
        if (sources == null) {
            this.sources = new ArrayList<>();
        } else {
            this.sources = new ArrayList<>(sources);
        }
        this.sourcesExpanded = false;
    }

    public Role getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public List<AiSource> getSources() {
        return Collections.unmodifiableList(sources);
    }

    public boolean isSourcesExpanded() {
        return sourcesExpanded;
    }

    public void setSourcesExpanded(boolean sourcesExpanded) {
        this.sourcesExpanded = sourcesExpanded;
    }
}
