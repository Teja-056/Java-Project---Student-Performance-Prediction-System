package studentperformance.syllabus;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationMessage {
    private final ValidationSeverity severity;
    private final String category; // "DOCUMENT", "SUBJECT", "CREDITS", "UNIT", "TOPIC"
    private final String message;
    private final String subjectName;
    private final String courseCode;
    private final Integer unitNumber;
    private final Integer pageNumber;

    public ValidationMessage(ValidationSeverity severity, String category, String message) {
        this(severity, category, message, null, null, null, null);
    }

    public ValidationMessage(ValidationSeverity severity, String category, String message,
                             String subjectName, String courseCode, Integer unitNumber, Integer pageNumber) {
        this.severity = severity;
        this.category = category;
        this.message = message;
        this.subjectName = subjectName;
        this.courseCode = courseCode;
        this.unitNumber = unitNumber;
        this.pageNumber = pageNumber;
    }

    public static ValidationMessage error(String category, String message) {
        return new ValidationMessage(ValidationSeverity.ERROR, category, message);
    }

    public static ValidationMessage error(String category, String message, String subjectName, String courseCode, Integer unitNumber, Integer pageNumber) {
        return new ValidationMessage(ValidationSeverity.ERROR, category, message, subjectName, courseCode, unitNumber, pageNumber);
    }

    public static ValidationMessage warning(String category, String message) {
        return new ValidationMessage(ValidationSeverity.WARNING, category, message);
    }

    public static ValidationMessage warning(String category, String message, String subjectName, String courseCode, Integer unitNumber, Integer pageNumber) {
        return new ValidationMessage(ValidationSeverity.WARNING, category, message, subjectName, courseCode, unitNumber, pageNumber);
    }

    public static ValidationMessage info(String category, String message) {
        return new ValidationMessage(ValidationSeverity.INFO, category, message);
    }

    public static ValidationMessage info(String category, String message, String subjectName, String courseCode, Integer unitNumber, Integer pageNumber) {
        return new ValidationMessage(ValidationSeverity.INFO, category, message, subjectName, courseCode, unitNumber, pageNumber);
    }

    public ValidationSeverity getSeverity() { return severity; }
    public String getCategory() { return category; }
    public String getMessage() { return message; }
    public String getSubjectName() { return subjectName; }
    public String getCourseCode() { return courseCode; }
    public Integer getUnitNumber() { return unitNumber; }
    public Integer getPageNumber() { return pageNumber; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] [").append(category).append("] ");
        if (subjectName != null && !subjectName.isBlank()) {
            sb.append(subjectName);
            if (courseCode != null && !courseCode.isBlank()) sb.append(" (").append(courseCode).append(")");
            sb.append(": ");
        }
        if (unitNumber != null) {
            sb.append("Unit ").append(unitNumber).append(": ");
        }
        sb.append(message);
        if (pageNumber != null) {
            sb.append(" (Page ").append(pageNumber).append(")");
        }
        return sb.toString();
    }
}
