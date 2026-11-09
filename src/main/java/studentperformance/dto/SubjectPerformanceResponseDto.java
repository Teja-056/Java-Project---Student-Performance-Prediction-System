package studentperformance.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete subject-wise cohort analytics payload.
 */
public class SubjectPerformanceResponseDto {
    private String subjectCode;
    private String subjectName;
    private String fullName;
    private String courseCode;
    private int credits;
    private int cieMaxMarks;
    private String assessmentType; // "INTERNAL_1", "INTERNAL_2", or "COMBINED"

    // Cohort Summary Metrics
    private int totalStudents;
    private int assessedCount;
    private int unassessedCount;
    private Double averagePercentage;
    private Double averageMarks;

    // Highest and Lowest Performers
    private Double highestScore;
    private String highestStudentRoll;
    private String highestStudentName;
    private List<SubjectStudentEntryDto> highestStudents = new ArrayList<>();

    private Double lowestScore;
    private String lowestStudentRoll;
    private String lowestStudentName;
    private List<SubjectStudentEntryDto> lowestStudents = new ArrayList<>();

    // Internal 1 vs Internal 2 Cohort Comparison
    private Double internal1AveragePercentage;
    private Double internal2AveragePercentage;
    private Double averageChangePercentage;
    private int improvedCount;
    private int declinedCount;
    private int stableCount;

    // Performance Category Distributions
    private List<SubjectCategoryStatsDto> categoryStats = new ArrayList<>();
    private List<SubjectCategoryStatsDto> internal1CategoryStats = new ArrayList<>();
    private List<SubjectCategoryStatsDto> internal2CategoryStats = new ArrayList<>();

    // Assessed and Unassessed Student Drilldown Lists
    private List<SubjectStudentEntryDto> students = new ArrayList<>();
    private List<SubjectStudentEntryDto> unassessedStudents = new ArrayList<>();

    public SubjectPerformanceResponseDto() {}

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }

    public int getCieMaxMarks() { return cieMaxMarks; }
    public void setCieMaxMarks(int cieMaxMarks) { this.cieMaxMarks = cieMaxMarks; }

    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }

    public int getTotalStudents() { return totalStudents; }
    public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }

    public int getAssessedCount() { return assessedCount; }
    public void setAssessedCount(int assessedCount) { this.assessedCount = assessedCount; }

    public int getUnassessedCount() { return unassessedCount; }
    public void setUnassessedCount(int unassessedCount) { this.unassessedCount = unassessedCount; }

    public Double getAveragePercentage() { return averagePercentage; }
    public void setAveragePercentage(Double averagePercentage) { this.averagePercentage = averagePercentage; }

    public Double getAverageMarks() { return averageMarks; }
    public void setAverageMarks(Double averageMarks) { this.averageMarks = averageMarks; }

    public Double getHighestScore() { return highestScore; }
    public void setHighestScore(Double highestScore) { this.highestScore = highestScore; }

    public String getHighestStudentRoll() { return highestStudentRoll; }
    public void setHighestStudentRoll(String highestStudentRoll) { this.highestStudentRoll = highestStudentRoll; }

    public String getHighestStudentName() { return highestStudentName; }
    public void setHighestStudentName(String highestStudentName) { this.highestStudentName = highestStudentName; }

    public List<SubjectStudentEntryDto> getHighestStudents() { return highestStudents; }
    public void setHighestStudents(List<SubjectStudentEntryDto> highestStudents) { this.highestStudents = highestStudents; }

    public Double getLowestScore() { return lowestScore; }
    public void setLowestScore(Double lowestScore) { this.lowestScore = lowestScore; }

    public String getLowestStudentRoll() { return lowestStudentRoll; }
    public void setLowestStudentRoll(String lowestStudentRoll) { this.lowestStudentRoll = lowestStudentRoll; }

    public String getLowestStudentName() { return lowestStudentName; }
    public void setLowestStudentName(String lowestStudentName) { this.lowestStudentName = lowestStudentName; }

    public List<SubjectStudentEntryDto> getLowestStudents() { return lowestStudents; }
    public void setLowestStudents(List<SubjectStudentEntryDto> lowestStudents) { this.lowestStudents = lowestStudents; }

    public Double getInternal1AveragePercentage() { return internal1AveragePercentage; }
    public void setInternal1AveragePercentage(Double internal1AveragePercentage) { this.internal1AveragePercentage = internal1AveragePercentage; }

    public Double getInternal2AveragePercentage() { return internal2AveragePercentage; }
    public void setInternal2AveragePercentage(Double internal2AveragePercentage) { this.internal2AveragePercentage = internal2AveragePercentage; }

    public Double getAverageChangePercentage() { return averageChangePercentage; }
    public void setAverageChangePercentage(Double averageChangePercentage) { this.averageChangePercentage = averageChangePercentage; }

    public int getImprovedCount() { return improvedCount; }
    public void setImprovedCount(int improvedCount) { this.improvedCount = improvedCount; }

    public int getDeclinedCount() { return declinedCount; }
    public void setDeclinedCount(int declinedCount) { this.declinedCount = declinedCount; }

    public int getStableCount() { return stableCount; }
    public void setStableCount(int stableCount) { this.stableCount = stableCount; }

    public List<SubjectCategoryStatsDto> getCategoryStats() { return categoryStats; }
    public void setCategoryStats(List<SubjectCategoryStatsDto> categoryStats) { this.categoryStats = categoryStats; }

    public List<SubjectCategoryStatsDto> getInternal1CategoryStats() { return internal1CategoryStats; }
    public void setInternal1CategoryStats(List<SubjectCategoryStatsDto> internal1CategoryStats) { this.internal1CategoryStats = internal1CategoryStats; }

    public List<SubjectCategoryStatsDto> getInternal2CategoryStats() { return internal2CategoryStats; }
    public void setInternal2CategoryStats(List<SubjectCategoryStatsDto> internal2CategoryStats) { this.internal2CategoryStats = internal2CategoryStats; }

    public List<SubjectStudentEntryDto> getStudents() { return students; }
    public void setStudents(List<SubjectStudentEntryDto> students) { this.students = students; }

    public List<SubjectStudentEntryDto> getUnassessedStudents() { return unassessedStudents; }
    public void setUnassessedStudents(List<SubjectStudentEntryDto> unassessedStudents) { this.unassessedStudents = unassessedStudents; }
}
