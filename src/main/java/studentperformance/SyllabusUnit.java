package studentperformance;

import java.util.ArrayList;
import java.util.List;

public class SyllabusUnit {
    private final int number;
    private String title;
    private Integer pageNumber;
    private final List<SyllabusTopic> topics = new ArrayList<>();

    public SyllabusUnit(int number, String title) {
        this(number, title, null);
    }

    public SyllabusUnit(int number, String title, Integer pageNumber) {
        this.number = number;
        setTitle(title);
        this.pageNumber = pageNumber;
    }

    public int getNumber() { return number; }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Unit title cannot be blank.");
        this.title = title.trim();
    }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public List<SyllabusTopic> getTopics() { return topics; }
}
