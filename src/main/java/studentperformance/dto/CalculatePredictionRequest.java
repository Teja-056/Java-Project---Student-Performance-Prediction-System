package studentperformance.dto;

public class CalculatePredictionRequest {
    private Double improvementPercent;
    private Double declinePercent;
    private Double attendanceOverride;

    public CalculatePredictionRequest() {}

    public CalculatePredictionRequest(Double improvementPercent, Double declinePercent, Double attendanceOverride) {
        this.improvementPercent = improvementPercent;
        this.declinePercent = declinePercent;
        this.attendanceOverride = attendanceOverride;
    }

    public Double getImprovementPercent() { return improvementPercent; }
    public void setImprovementPercent(Double improvementPercent) { this.improvementPercent = improvementPercent; }

    public Double getDeclinePercent() { return declinePercent; }
    public void setDeclinePercent(Double declinePercent) { this.declinePercent = declinePercent; }

    public Double getAttendanceOverride() { return attendanceOverride; }
    public void setAttendanceOverride(Double attendanceOverride) { this.attendanceOverride = attendanceOverride; }
}
