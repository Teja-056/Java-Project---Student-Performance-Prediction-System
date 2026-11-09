package studentperformance.syllabus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedTopic {
    private String topicText = "";
    private Integer pageNumber;
    private List<ExtractedTopic> subtopics = new ArrayList<>();

    public ExtractedTopic() {}

    public ExtractedTopic(String topicText) {
        this.topicText = topicText == null ? "" : topicText.trim();
    }

    public ExtractedTopic(String topicText, Integer pageNumber) {
        this.topicText = topicText == null ? "" : topicText.trim();
        this.pageNumber = pageNumber;
    }

    public String getTopicText() { return topicText; }
    public void setTopicText(String topicText) { this.topicText = topicText == null ? "" : topicText.trim(); }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public List<ExtractedTopic> getSubtopics() { return subtopics; }
    public void setSubtopics(List<ExtractedTopic> subtopics) { this.subtopics = subtopics == null ? new ArrayList<>() : subtopics; }

    @Override
    public String toString() {
        return "ExtractedTopic{" +
                "topicText='" + topicText + '\'' +
                ", pageNumber=" + pageNumber +
                ", subtopics=" + subtopics.size() +
                '}';
    }
}
