package studentperformance.dto;

/**
 * Individual student assessment entry for a subject performance view.
 */
public class SubjectStudentEntryDto {
    private String rollNumber;
    private String studentName;
    private Double obtainedMarks;
    private int maxMarks;
    private Double percentage;
    private String category;
    private Double internal1Marks;
    private Double internal2Marks;
    private Double trendPercentage;

    public SubjectStudentEntryDto() {}

    public SubjectStudentEntryDto(String rollNumber, String studentName, Double obtainedMarks, int maxMarks,
                                  Double percentage, String category, Double internal1Marks,
                                  Double internal2Marks, Double trendPercentage) {
        this.rollNumber = rollNumber;
        this.studentName = studentName;
        this.obtainedMarks = obtainedMarks;
        this.maxMarks = maxMarks;
        this.percentage = percentage;
        this.category = category;
        this.internal1Marks = internal1Marks;
        this.internal2Marks = internal2Marks;
        this.trendPercentage = trendPercentage;
    }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public Double getObtainedMarks() { return obtainedMarks; }
    public void setObtainedMarks(Double obtainedMarks) { this.obtainedMarks = obtainedMarks; }

    public int getMaxMarks() { return maxMarks; }
    public void setMaxMarks(int maxMarks) { this.maxMarks = maxMarks; }

    public Double getPercentage() { return percentage; }
    public void setPercentage(Double percentage) { this.percentage = percentage; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getInternal1Marks() { return internal1Marks; }
    public void setInternal1Marks(Double internal1Marks) { this.internal1Marks = internal1Marks; }

    public Double getInternal2Marks() { return internal2Marks; }
    public void setInternal2Marks(Double internal2Marks) { this.internal2Marks = internal2Marks; }

    public Double getTrendPercentage() { return trendPercentage; }
    public void setTrendPercentage(Double trendPercentage) { this.trendPercentage = trendPercentage; }
}
