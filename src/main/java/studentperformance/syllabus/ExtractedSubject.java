package studentperformance.syllabus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedSubject {
    private String subjectName = "";
    private String courseCode = "";
    private String credits = "";
    private String category = "THEORY";
    private String semester = "III";
    private Integer startPage;
    private Integer endPage;
    private List<ExtractedUnit> units = new ArrayList<>();
    private List<String> uncertainFields = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean openElective = false;

    public ExtractedSubject() {}

    public ExtractedSubject(String subjectName) {
        this.subjectName = subjectName == null ? "" : subjectName.trim();
    }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName == null ? "" : subjectName.trim(); }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode == null ? "" : courseCode.trim(); }

    public String getCredits() { return credits; }
    public void setCredits(String credits) {
        if (credits == null) {
            this.credits = "";
            return;
        }
        String cleaned = credits.replaceAll("(?i)\\s*credits?\\s*", "").trim();
        this.credits = cleaned.isEmpty() ? credits.trim() : cleaned;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category == null ? "THEORY" : category.trim(); }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester == null ? "III" : semester.trim(); }

    public Integer getStartPage() { return startPage; }
    public void setStartPage(Integer startPage) { this.startPage = startPage; }

    public Integer getEndPage() { return endPage; }
    public void setEndPage(Integer endPage) { this.endPage = endPage; }

    public List<ExtractedUnit> getUnits() { return units; }
    public void setUnits(List<ExtractedUnit> units) { this.units = units == null ? new ArrayList<>() : units; }

    public List<String> getUncertainFields() { return uncertainFields; }
    public void setUncertainFields(List<String> uncertainFields) { this.uncertainFields = uncertainFields == null ? new ArrayList<>() : uncertainFields; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings == null ? new ArrayList<>() : warnings; }

    public boolean isOpenElective() { return openElective || "OPEN_ELECTIVE".equalsIgnoreCase(category); }
    public void setOpenElective(boolean openElective) { this.openElective = openElective; }

    @Override
    public String toString() {
        return "ExtractedSubject{" +
                "name='" + subjectName + '\'' +
                ", code='" + courseCode + '\'' +
                ", credits='" + credits + '\'' +
                ", category='" + category + '\'' +
                ", pages=" + startPage + "-" + endPage +
                ", units=" + units.size() +
                '}';
    }
}
