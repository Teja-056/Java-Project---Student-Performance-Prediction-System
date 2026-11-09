package studentperformance.dto;

import studentperformance.PerformanceAnalyzer;
import studentperformance.Student;
import studentperformance.Subject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StudentReportDto {
    private String rollNumber;
    private String name;
    private Double sem1Sgpa;
    private double cgpa;
    private double lastSemesterSgpa;
    private Double attendancePercent;
    private Double internal1Percentage;
    private Double internal2Percentage;
    private Boolean internal1IsPartial;
    private Boolean internal2IsPartial;
    private Double combinedInternalPercentage;
    private Double overallCiePercentage;
    private Double finalAcademicScore;
    private Double cieComponent;
    private Double sgpaComponent;
    private Double attendanceComponent;
    private Boolean criticalAcademicWarning;
    private Boolean attendanceWarning;
    private Boolean incompleteAssessmentWarning;
    private String strongestSubject;
    private String weakestSubject;
    private String riskClassification;
    private boolean completeRecord;
    private List<SubjectPerformanceDetailDto> subjectBreakdown;
    private List<String> unitRecommendations;
    private List<String> generalRecommendations;

    public StudentReportDto() {}

    public StudentReportDto(Student student, PerformanceAnalyzer analyzer, Map<Integer, List<Integer>> unitMapping) {
        this.rollNumber = student.getRollNumber();
        this.name = student.getName();
        this.sem1Sgpa = student.getSem1Sgpa();
        this.cgpa = student.getCgpa();
        this.lastSemesterSgpa = student.getLastSemesterSgpa();
        this.attendancePercent = student.getAttendancePercent();
        this.completeRecord = analyzer.hasBothInternals(student);

        if (analyzer.possibleMarks(student, 1) > 0) {
            this.internal1Percentage = analyzer.percentage(student, 1);
            this.internal1IsPartial = !analyzer.isInternalComplete(student, 1);
        }
        if (analyzer.possibleMarks(student, 2) > 0) {
            this.internal2Percentage = analyzer.percentage(student, 2);
            this.internal2IsPartial = !analyzer.isInternalComplete(student, 2);
        }
        if (completeRecord) {
            this.combinedInternalPercentage = analyzer.currentInternalPercentage(student);
            Subject strong = analyzer.strongestSubject(student);
            this.strongestSubject = strong != null ? strong.getDisplayName() : null;
            Subject weak = analyzer.weakestSubject(student);
            this.weakestSubject = weak != null ? weak.getDisplayName() : null;
        }

        PerformanceAnalyzer.AcademicEvaluation eval = analyzer.evaluateStudent(student);
        this.overallCiePercentage = eval.overallCiePercentage();
        this.finalAcademicScore = eval.finalAcademicScore();
        if (eval.isComplete()) {
            this.cieComponent = PerformanceAnalyzer.round2(0.70 * eval.overallCiePercentage());
            this.sgpaComponent = PerformanceAnalyzer.round2(0.20 * (eval.previousSemesterSgpa() * 10.0));
            this.attendanceComponent = PerformanceAnalyzer.round2(0.10 * eval.attendancePercentage());
            this.riskClassification = eval.primaryClassification();
        } else {
            this.cieComponent = null;
            this.sgpaComponent = null;
            this.attendanceComponent = null;
            this.riskClassification = "Incomplete assessment data";
        }
        this.criticalAcademicWarning = eval.criticalAcademicWarning();
        this.attendanceWarning = eval.attendanceWarning();
        this.incompleteAssessmentWarning = eval.incompleteAssessmentWarning();

        // Subject breakdown
        this.subjectBreakdown = new ArrayList<>();
        for (Subject s : Subject.values()) {
            double p1 = analyzer.subjectPercentage(student, 1, s);
            double p2 = analyzer.subjectPercentage(student, 2, s);
            double change = analyzer.subjectChange(student, s);
            Double total1 = student.getMarks(1).containsKey(s) ? student.getMarks(1).get(s).totalMarks() : null;
            Double total2 = student.getMarks(2).containsKey(s) ? student.getMarks(2).get(s).totalMarks() : null;
            Double pct1 = Double.isNaN(p1) ? null : p1;
            Double pct2 = Double.isNaN(p2) ? null : p2;
            Double trend = Double.isNaN(change) ? null : change;

            subjectBreakdown.add(new SubjectPerformanceDetailDto(
                    s.name(),
                    s.getDisplayName(),
                    s.getMaximumTotalMarks(),
                    total1,
                    pct1,
                    total2,
                    pct2,
                    trend
            ));
        }

        // Assessment mapping unit recommendations:
        // Internal 1 covers Units 1 & 2; Internal 2 covers Units 3, 4 & 5
        this.unitRecommendations = new ArrayList<>();
        if (unitMapping == null || unitMapping.isEmpty()) {
            unitMapping = Map.of(1, List.of(1, 2), 2, List.of(3, 4, 5));
        }
        String advice = analyzer.unitAdvice(student, unitMapping);
        if (advice != null && !advice.isBlank() && !advice.equalsIgnoreCase("None")) {
            this.unitRecommendations.add(advice);
        } else {
            this.unitRecommendations.add("Scores meet baseline thresholds across both internals. Maintain current unit study schedule.");
        }

        // General recommendations
        this.generalRecommendations = new ArrayList<>();
        if (completeRecord) {
            if (combinedInternalPercentage < 50.0) {
                generalRecommendations.add("Priority remedial intervention: Academic performance is below the 50% threshold.");
                generalRecommendations.add("Focus on fundamental concepts and review exam problem-solving methodologies.");
            } else if (combinedInternalPercentage < 75.0) {
                generalRecommendations.add("Consistent effort required to elevate performance to first-class distinction.");
                generalRecommendations.add("Target weakest subject (" + (weakestSubject != null ? weakestSubject : "identified areas") + ") with daily practice.");
            } else {
                generalRecommendations.add("Excellent performance. Continue structured revision and assist peer study groups.");
            }
        }
    }

    public static class SubjectPerformanceDetailDto {
        private String subjectCode;
        private String subjectName;
        private int maxMarks;
        private Double internal1Marks;
        private Double internal1Percentage;
        private Double internal2Marks;
        private Double internal2Percentage;
        private Double trendPercentage;

        public SubjectPerformanceDetailDto(String subjectCode, String subjectName, int maxMarks,
                                           Double internal1Marks, Double internal1Percentage,
                                           Double internal2Marks, Double internal2Percentage,
                                           Double trendPercentage) {
            this.subjectCode = subjectCode;
            this.subjectName = subjectName;
            this.maxMarks = maxMarks;
            this.internal1Marks = internal1Marks;
            this.internal1Percentage = internal1Percentage;
            this.internal2Marks = internal2Marks;
            this.internal2Percentage = internal2Percentage;
            this.trendPercentage = trendPercentage;
        }

        public String getSubjectCode() { return subjectCode; }
        public String getSubjectName() { return subjectName; }
        public int getMaxMarks() { return maxMarks; }
        public Double getInternal1Marks() { return internal1Marks; }
        public Double getInternal1Percentage() { return internal1Percentage; }
        public Double getInternal2Marks() { return internal2Marks; }
        public Double getInternal2Percentage() { return internal2Percentage; }
        public Double getTrendPercentage() { return trendPercentage; }
    }

    public String getRollNumber() { return rollNumber; }
    public String getName() { return name; }
    public Double getSem1Sgpa() { return sem1Sgpa; }
    public double getCgpa() { return cgpa; }
    public double getLastSemesterSgpa() { return lastSemesterSgpa; }
    public Double getAttendancePercent() { return attendancePercent; }
    public Double getInternal1Percentage() { return internal1Percentage; }
    public Double getInternal2Percentage() { return internal2Percentage; }
    public Boolean getInternal1IsPartial() { return internal1IsPartial; }
    public Boolean getInternal2IsPartial() { return internal2IsPartial; }
    public Double getCombinedInternalPercentage() { return combinedInternalPercentage; }
    public Double getOverallCiePercentage() { return overallCiePercentage; }
    public Double getFinalAcademicScore() { return finalAcademicScore; }
    public Double getCieComponent() { return cieComponent; }
    public Double getSgpaComponent() { return sgpaComponent; }
    public Double getAttendanceComponent() { return attendanceComponent; }
    public Boolean getCriticalAcademicWarning() { return criticalAcademicWarning; }
    public Boolean getAttendanceWarning() { return attendanceWarning; }
    public Boolean getIncompleteAssessmentWarning() { return incompleteAssessmentWarning; }
    public String getStrongestSubject() { return strongestSubject; }
    public String getWeakestSubject() { return weakestSubject; }
    public String getRiskClassification() { return riskClassification; }
    public boolean isCompleteRecord() { return completeRecord; }
    public List<SubjectPerformanceDetailDto> getSubjectBreakdown() { return subjectBreakdown; }
    public List<String> getUnitRecommendations() { return unitRecommendations; }
    public List<String> getGeneralRecommendations() { return generalRecommendations; }
}
