package studentperformance.syllabus;

import studentperformance.Subject;

import java.util.ArrayList;
import java.util.List;

public class SubjectValidationResult {
    private String subjectName = "";
    private String courseCode = "";
    private Subject matchedSubject;
    private int extractedCredits;
    private int expectedCredits;
    private int unitCount;
    private int topicCount;
    private boolean openElective;
    private boolean valid = true;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> info = new ArrayList<>();

    public SubjectValidationResult() {}

    public SubjectValidationResult(String subjectName, String courseCode, Subject matchedSubject) {
        this.subjectName = subjectName;
        this.courseCode = courseCode;
        this.matchedSubject = matchedSubject;
    }

    public void addError(String error) {
        errors.add(error);
        valid = false;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public void addInfo(String infoMsg) {
        info.add(infoMsg);
    }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public Subject getMatchedSubject() { return matchedSubject; }
    public void setMatchedSubject(Subject matchedSubject) { this.matchedSubject = matchedSubject; }

    public int getExtractedCredits() { return extractedCredits; }
    public void setExtractedCredits(int extractedCredits) { this.extractedCredits = extractedCredits; }

    public int getExpectedCredits() { return expectedCredits; }
    public void setExpectedCredits(int expectedCredits) { this.expectedCredits = expectedCredits; }

    public int getUnitCount() { return unitCount; }
    public void setUnitCount(int unitCount) { this.unitCount = unitCount; }

    public int getTopicCount() { return topicCount; }
    public void setTopicCount(int topicCount) { this.topicCount = topicCount; }

    public boolean isOpenElective() { return openElective; }
    public void setOpenElective(boolean openElective) { this.openElective = openElective; }

    public boolean isValid() { return valid && errors.isEmpty(); }
    public void setValid(boolean valid) { this.valid = valid; }

    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getInfo() { return info; }

    @Override
    public String toString() {
        return "SubjectValidationResult{" +
                "subjectName='" + subjectName + '\'' +
                ", courseCode='" + courseCode + '\'' +
                ", matched=" + (matchedSubject != null ? matchedSubject.name() : "NONE") +
                ", credits=" + extractedCredits + "/" + expectedCredits +
                ", units=" + unitCount +
                ", topics=" + topicCount +
                ", valid=" + isValid() +
                ", errors=" + errors.size() +
                ", warnings=" + warnings.size() +
                '}';
    }
}
