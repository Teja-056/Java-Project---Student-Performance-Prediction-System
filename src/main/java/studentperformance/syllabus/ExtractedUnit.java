package studentperformance.syllabus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedUnit {
    private int unitNumber;
    private String unitTitle = "";
    private Integer pageNumber;
    private List<ExtractedTopic> topics = new ArrayList<>();

    public ExtractedUnit() {}

    public ExtractedUnit(int unitNumber, String unitTitle, Integer pageNumber) {
        this.unitNumber = unitNumber;
        this.unitTitle = unitTitle == null ? "" : unitTitle.trim();
        this.pageNumber = pageNumber;
    }

    public int getUnitNumber() { return unitNumber; }
    public void setUnitNumber(int unitNumber) { this.unitNumber = unitNumber; }

    public String getUnitTitle() { return unitTitle; }
    public void setUnitTitle(String unitTitle) { this.unitTitle = unitTitle == null ? "" : unitTitle.trim(); }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public List<ExtractedTopic> getTopics() { return topics; }
    public void setTopics(List<ExtractedTopic> topics) { this.topics = topics == null ? new ArrayList<>() : topics; }

    @Override
    public String toString() {
        return "ExtractedUnit{" +
                "unitNumber=" + unitNumber +
                ", unitTitle='" + unitTitle + '\'' +
                ", pageNumber=" + pageNumber +
                ", topics=" + topics.size() +
                '}';
    }
}
