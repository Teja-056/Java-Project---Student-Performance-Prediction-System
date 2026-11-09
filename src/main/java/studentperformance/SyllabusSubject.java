package studentperformance;

import java.util.ArrayList;
import java.util.List;

public class SyllabusSubject {
    private String name;
    private String departmentCode = "UNKNOWN";
    private String semester = "UNKNOWN";
    private String courseCode = "";
    private String credits = "";
    private String totalMarks = "";
    private boolean openElective;
    private SyllabusCategory category = SyllabusCategory.THEORY;
    private final List<SyllabusUnit> units = new ArrayList<>();
    public SyllabusSubject(String name) { setName(name); }
    public String getName() { return name; }
    public void setName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Subject name cannot be blank.");
        this.name = name.trim();
    }
    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String value) { departmentCode = value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase(); }
    public String getSemester() { return semester; }
    public void setSemester(String value) { semester = value == null || value.isBlank() ? "UNKNOWN" : value.trim().toUpperCase(); }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String value) { courseCode = value == null ? "" : value.trim().toUpperCase(); }
    public String getCredits() { return credits; }
    public void setCredits(String value) { credits = value == null ? "" : value.trim(); }
    public String getTotalMarks() { return totalMarks; }
    public void setTotalMarks(String value) { totalMarks = value == null ? "" : value.trim(); }
    public boolean isOpenElective() { return openElective || category == SyllabusCategory.OPEN_ELECTIVE; }
    public void setOpenElective(boolean value) { openElective = value; if(value) category=SyllabusCategory.OPEN_ELECTIVE; }
    public SyllabusCategory getCategory() { return category; }
    public String getClassificationReason() {
        return switch(category) {
            case THEORY -> "Classified as theory from the subject heading; no lab or open-elective wording was found.";
            case PROFESSIONAL_ELECTIVE -> "Classified as a professional elective (PE) from syllabus heading or course metadata.";
            case LABORATORY -> "Classified as a laboratory because the subject heading includes LAB.";
            case OPEN_ELECTIVE -> "Excluded from automatic import because the subject metadata explicitly labels it Open Elective.";
            case SKILL_DEVELOPMENT -> "Classified as skill development from the subject heading; review whether it belongs in this import.";
            case BRIDGE_COURSE -> "Classified as a bridge course from the syllabus heading.";
            case OTHER -> "Course type was not clear from the extracted heading; review the source text.";
        };
    }
    public void classifyFromHeading() {
        String upper=name.toUpperCase(java.util.Locale.ROOT);
        if(openElective || upper.matches(".*\\bOPEN\\s+ELECTIVE(?:[- ]?[IVX0-9]+)?\\b.*")) {
            category=SyllabusCategory.OPEN_ELECTIVE;
            openElective=true;
        } else if(upper.matches(".*\\bPROFESSIONAL\\s+ELECTIVE(?:[- ]?[IVX0-9]+)?\\b.*") || upper.matches(".*\\bPE\\s*[-–—]?\\s*[IVX0-9]+\\b.*")) {
            category=SyllabusCategory.PROFESSIONAL_ELECTIVE;
            openElective=false;
        } else if(upper.matches(".*\\bLAB(?:ORATORY)?\\b.*")) {
            category=SyllabusCategory.LABORATORY;
            openElective=false;
        } else if(upper.contains("SKILL DEVELOPMENT")) {
            category=SyllabusCategory.SKILL_DEVELOPMENT;
            openElective=false;
        } else if(upper.contains("BRIDGE COURSE")) {
            category=SyllabusCategory.BRIDGE_COURSE;
            openElective=false;
        } else {
            if (category == null || category == SyllabusCategory.OTHER) {
                category=SyllabusCategory.THEORY;
            }
            openElective=false;
        }
    }
    public void setCategory(SyllabusCategory value) { category=value==null?SyllabusCategory.OTHER:value; openElective=category==SyllabusCategory.OPEN_ELECTIVE; }
    public List<SyllabusUnit> getUnits() { return units; }

    public void addUnit(SyllabusUnit unit) {
        if (unit == null) return;
        for (SyllabusUnit existing : units) {
            if (existing.getNumber() == unit.getNumber()) {
                if ((existing.getTitle().isBlank() || existing.getTitle().matches("(?i)^unit\\s*[-–—:：.?]?[ivx0-9]+$"))
                        && !unit.getTitle().isBlank()) {
                    existing.setTitle(unit.getTitle());
                }
                for (SyllabusTopic t : unit.getTopics()) {
                    boolean topicExists = existing.getTopics().stream()
                            .anyMatch(et -> et.getText().trim().equalsIgnoreCase(t.getText().trim()));
                    if (!topicExists) {
                        existing.getTopics().add(t);
                    }
                }
                return;
            }
        }
        units.add(unit);
    }

    public SyllabusUnit getOrCreateUnit(int number, String title) {
        for (SyllabusUnit existing : units) {
            if (existing.getNumber() == number) {
                if ((existing.getTitle().isBlank() || existing.getTitle().matches("(?i)^unit\\s*[-–—:：.?]?[ivx0-9]+$"))
                        && title != null && !title.isBlank() && !title.matches("(?i)^unit\\s*[-–—:：.?]?[ivx0-9]+$")) {
                    existing.setTitle(title.trim());
                }
                return existing;
            }
        }
        String t = (title == null || title.isBlank()) ? "Unit " + number : title.trim();
        SyllabusUnit u = new SyllabusUnit(number, t);
        units.add(u);
        return u;
    }

    public Subject getMatchedSemesterSubject() {
        if (openElective || category == SyllabusCategory.OPEN_ELECTIVE) {
            return Subject.OE;
        }
        Subject matched = Subject.matchByText(courseCode);
        if (matched != null) return matched;
        return Subject.matchByText(name);
    }
}
