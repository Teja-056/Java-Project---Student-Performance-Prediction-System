package studentperformance.dto;

public class SubjectAnalyticsDto {
    private String subjectCode;
    private String subjectName;
    private int totalStudentsEvaluated;
    private int performingWellCount; // percentage >= 75%
    private int requiringImprovementCount; // percentage < 50%
    private double averagePercentage;
    private String overallSummary;

    public SubjectAnalyticsDto() {}

    public SubjectAnalyticsDto(String subjectCode, String subjectName, int totalStudentsEvaluated,
                               int performingWellCount, int requiringImprovementCount,
                               double averagePercentage, String overallSummary) {
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.totalStudentsEvaluated = totalStudentsEvaluated;
        this.performingWellCount = performingWellCount;
        this.requiringImprovementCount = requiringImprovementCount;
        this.averagePercentage = averagePercentage;
        this.overallSummary = overallSummary;
    }

    public String getSubjectCode() { return subjectCode; }
    public String getSubjectName() { return subjectName; }
    public int getTotalStudentsEvaluated() { return totalStudentsEvaluated; }
    public int getPerformingWellCount() { return performingWellCount; }
    public int getRequiringImprovementCount() { return requiringImprovementCount; }
    public double getAveragePercentage() { return averagePercentage; }
    public String getOverallSummary() { return overallSummary; }
}
