package studentperformance;

import java.util.ArrayList;
import java.util.List;

public class SyllabusUnit {
    private final int number;
    private String title;
    private final List<SyllabusTopic> topics = new ArrayList<>();
    public SyllabusUnit(int number, String title) { this.number = number; setTitle(title); }
    public int getNumber() { return number; }
    public String getTitle() { return title; }
    public void setTitle(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Unit title cannot be blank.");
        this.title = title.trim();
    }
    public List<SyllabusTopic> getTopics() { return topics; }
}
