package studentperformance.prediction;

import java.util.Map;

public class StudentPredictionResult {
    private final String rollNumber;
    private final String studentName;
    private final Double sem1Sgpa;
    private final double sem2Sgpa;
    private final double previousCgpa;
    private final Double attendancePercent;
    private final double overallCiePercentage;
    private final Map<String, ScenarioPrediction> scenarios;
    private final double configuredImprovementPercent;
    private final double configuredDeclinePercent;

    public StudentPredictionResult(String rollNumber, String studentName, Double sem1Sgpa,
                                   double sem2Sgpa, double previousCgpa, Double attendancePercent,
                                   double overallCiePercentage, Map<String, ScenarioPrediction> scenarios,
                                   double configuredImprovementPercent, double configuredDeclinePercent) {
        this.rollNumber = rollNumber;
        this.studentName = studentName;
        this.sem1Sgpa = sem1Sgpa;
        this.sem2Sgpa = sem2Sgpa;
        this.previousCgpa = previousCgpa;
        this.attendancePercent = attendancePercent;
        this.overallCiePercentage = overallCiePercentage;
        this.scenarios = scenarios;
        this.configuredImprovementPercent = configuredImprovementPercent;
        this.configuredDeclinePercent = configuredDeclinePercent;
    }

    public String getRollNumber() { return rollNumber; }
    public String getStudentName() { return studentName; }
    public Double getSem1Sgpa() { return sem1Sgpa; }
    public double getSem2Sgpa() { return sem2Sgpa; }
    public double getPreviousCgpa() { return previousCgpa; }
    public Double getAttendancePercent() { return attendancePercent; }
    public double getOverallCiePercentage() { return overallCiePercentage; }
    public Map<String, ScenarioPrediction> getScenarios() { return scenarios; }
    public double getConfiguredImprovementPercent() { return configuredImprovementPercent; }
    public double getConfiguredDeclinePercent() { return configuredDeclinePercent; }
}
