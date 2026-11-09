package studentperformance.syllabus;

import java.util.ArrayList;
import java.util.List;

public class ValidationReport {
    public enum OverallStatus {
        PASSED,
        PASSED_WITH_WARNINGS,
        FAILED
    }

    private OverallStatus overallStatus = OverallStatus.PASSED;
    private int subjectCount;
    private int matchedSubjectCount;
    private int unresolvedSubjectCount;
    private int totalUnits;
    private int totalTopics;
    private int totalCredits;

    private final List<ValidationMessage> messages = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> info = new ArrayList<>();
    private final List<SubjectValidationResult> subjectResults = new ArrayList<>();

    public ValidationReport() {}

    public void addMessage(ValidationMessage msg) {
        if (msg == null) return;
        messages.add(msg);
        switch (msg.getSeverity()) {
            case ERROR -> {
                errors.add(msg.toString());
                overallStatus = OverallStatus.FAILED;
            }
            case WARNING -> {
                warnings.add(msg.toString());
                if (overallStatus == OverallStatus.PASSED) {
                    overallStatus = OverallStatus.PASSED_WITH_WARNINGS;
                }
            }
            case INFO -> info.add(msg.toString());
        }
    }

    public void addSubjectResult(SubjectValidationResult result) {
        if (result != null) {
            subjectResults.add(result);
            if (!result.isValid() && overallStatus != OverallStatus.FAILED) {
                overallStatus = OverallStatus.FAILED;
            }
        }
    }

    public boolean isValid() {
        return overallStatus == OverallStatus.PASSED || overallStatus == OverallStatus.PASSED_WITH_WARNINGS;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public OverallStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(OverallStatus overallStatus) { this.overallStatus = overallStatus; }

    public int getSubjectCount() { return subjectCount; }
    public void setSubjectCount(int subjectCount) { this.subjectCount = subjectCount; }

    public int getMatchedSubjectCount() { return matchedSubjectCount; }
    public void setMatchedSubjectCount(int matchedSubjectCount) { this.matchedSubjectCount = matchedSubjectCount; }

    public int getUnresolvedSubjectCount() { return unresolvedSubjectCount; }
    public void setUnresolvedSubjectCount(int unresolvedSubjectCount) { this.unresolvedSubjectCount = unresolvedSubjectCount; }

    public int getTotalUnits() { return totalUnits; }
    public void setTotalUnits(int totalUnits) { this.totalUnits = totalUnits; }

    public int getTotalTopics() { return totalTopics; }
    public void setTotalTopics(int totalTopics) { this.totalTopics = totalTopics; }

    public int getTotalCredits() { return totalCredits; }
    public void setTotalCredits(int totalCredits) { this.totalCredits = totalCredits; }

    public List<ValidationMessage> getMessages() { return messages; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getInfo() { return info; }
    public List<SubjectValidationResult> getSubjectResults() { return subjectResults; }

    public String formatConsoleSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================================\n");
        sb.append("               SYLLABUS JAVA VALIDATION REPORT                   \n");
        sb.append("=================================================================\n");
        sb.append(String.format("Overall Status: %s | Subjects: %d (%d matched, %d unresolved)\n",
                overallStatus, subjectCount, matchedSubjectCount, unresolvedSubjectCount));
        sb.append(String.format("Total Units: %d | Total Topics: %d | Total Credits: %d / 19\n",
                totalUnits, totalTopics, totalCredits));
        sb.append(String.format("Issues: %d Errors, %d Warnings, %d Info\n",
                errors.size(), warnings.size(), info.size()));
        sb.append("-----------------------------------------------------------------\n");

        sb.append("SUBJECT-WISE RESULTS:\n");
        for (int i = 0; i < subjectResults.size(); i++) {
            SubjectValidationResult res = subjectResults.get(i);
            String matchStr = res.getMatchedSubject() != null ? res.getMatchedSubject().name() : "UNRESOLVED";
            sb.append(String.format("[%d] %s (%s) -> Matched: %s\n",
                    i + 1, res.getSubjectName(), res.getCourseCode(), matchStr));
            sb.append(String.format("    Status: %s | Credits: %d (Expected: %d) | Units: %d | Topics: %d\n",
                    res.isValid() ? "VALID" : "INVALID", res.getExtractedCredits(), res.getExpectedCredits(),
                    res.getUnitCount(), res.getTopicCount()));
            if (res.isOpenElective()) {
                sb.append("    Note: Open Elective-I (unresolved, 0 units expected pending student selection)\n");
            }
            for (String err : res.getErrors()) {
                sb.append(String.format("    - [ERROR] %s\n", err));
            }
            for (String warn : res.getWarnings()) {
                sb.append(String.format("    - [WARN]  %s\n", warn));
            }
            for (String inf : res.getInfo()) {
                sb.append(String.format("    - [INFO]  %s\n", inf));
            }
            sb.append("\n");
        }

        if (!errors.isEmpty()) {
            sb.append("-----------------------------------------------------------------\n");
            sb.append("ERROR DETAILS:\n");
            for (String err : errors) {
                sb.append("  * ").append(err).append("\n");
            }
        }

        if (!warnings.isEmpty()) {
            sb.append("-----------------------------------------------------------------\n");
            sb.append("WARNING DETAILS:\n");
            for (String warn : warnings) {
                sb.append("  ! ").append(warn).append("\n");
            }
        }

        if (!info.isEmpty()) {
            sb.append("-----------------------------------------------------------------\n");
            sb.append("INFORMATIONAL NOTES:\n");
            for (String inf : info) {
                sb.append("  i ").append(inf).append("\n");
            }
        }
        sb.append("=================================================================\n");
        return sb.toString();
    }
}
