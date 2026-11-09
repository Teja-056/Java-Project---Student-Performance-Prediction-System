package studentperformance;

import java.util.ArrayList;
import java.util.List;

/** One verbatim syllabus line, optionally with indented subtopic lines and source page reference. */
public class SyllabusTopic {
    private String text;
    private Integer pageNumber;
    private final List<SyllabusTopic> subtopics = new ArrayList<>();

    public SyllabusTopic(String text) {
        this(text, null);
    }

    public SyllabusTopic(String text, Integer pageNumber) {
        setText(text);
        this.pageNumber = pageNumber;
    }

    public String getText() { return text; }
    public void setText(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Topic text cannot be blank.");
        this.text = text.trim();
    }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public List<SyllabusTopic> getSubtopics() { return subtopics; }
}
