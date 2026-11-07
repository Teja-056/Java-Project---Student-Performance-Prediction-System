package studentperformance;

import java.util.ArrayList;
import java.util.List;

/** One verbatim syllabus line, optionally with indented subtopic lines. */
public class SyllabusTopic {
    private String text;
    private final List<SyllabusTopic> subtopics = new ArrayList<>();
    public SyllabusTopic(String text) { setText(text); }
    public String getText() { return text; }
    public void setText(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Topic text cannot be blank.");
        this.text = text.trim();
    }
    public List<SyllabusTopic> getSubtopics() { return subtopics; }
}
