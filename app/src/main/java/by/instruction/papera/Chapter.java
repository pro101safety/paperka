package by.instruction.papera;

import java.util.ArrayList;
import java.util.List;

public class Chapter
{
    String chapterName;
    List<Topics> topicsList=new ArrayList<>();

    public Chapter(String chapterName, List<Topics> topicsList) {
        this.chapterName = chapterName;
        this.topicsList = topicsList;
    }

    public String getChapterName() {
        return chapterName;
    }

    public List<Topics> getTopicsList() {
        return topicsList;
    }
}