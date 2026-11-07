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
    private double cgpa;
    private double lastSemesterSgpa;
    private Double internal1Percentage;
    private Double internal2Percentage;
    private Double combinedInternalPercentage;
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
        this.cgpa = student.getCgpa();
        this.lastSemesterSgpa = student.getLastSemesterSgpa();
        this.completeRecord = analyzer.hasBothInternals(student);

        if (analyzer.isInternalComplete(student, 1)) {
            this.internal1Percentage = analyzer.percentage(student, 1);
        }
        if (analyzer.isInternalComplete(student, 2)) {
            this.internal2Percentage = analyzer.percentage(student, 2);
        }
        if (completeRecord) {
            this.combinedInternalPercentage = analyzer.currentInternalPercentage(student);
            Subject strong = analyzer.strongestSubject(student);
            this.strongestSubject = strong != null ? strong.getDisplayName() : null;
            Subject weak = analyzer.weakestSubject(student);
            this.weakestSubject = weak != null ? weak.getDisplayName() : null;
            this.riskClassification = analyzer.prediction(student);
        } else {
            this.riskClassification = "Incomplete Assessment Records";
        }

        // Subject breakdown
        this.subjectBreakdown = new ArrayList<>();
        for (Subject s : Subject.values()) {
            double p1 = analyzer.subjectPercentage(student, 1, s);
            double p2 = analyzer.subjectPercentage(student, 2, s);
            double change = analyzer.subjectChange(student, s);
            double total1 = student.getMarks(1).containsKey(s) ? student.getMarks(1).get(s).totalMarks() : 0.0;
            double total2 = student.getMarks(2).containsKey(s) ? student.getMarks(2).get(s).totalMarks() : 0.0;

            subjectBreakdown.add(new SubjectPerformanceDetailDto(
                    s.name(),
                    s.getDisplayName(),
                    s.getMaximumTotalMarks(),
                    total1,
                    p1,
                    total2,
                    p2,
                    change
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
        private double internal1Marks;
        private double internal1Percentage;
        private double internal2Marks;
        private double internal2Percentage;
        private double trendPercentage;

        public SubjectPerformanceDetailDto(String subjectCode, String subjectName, int maxMarks,
                                           double internal1Marks, double internal1Percentage,
                                           double internal2Marks, double internal2Percentage,
                                           double trendPercentage) {
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
        public double getInternal1Marks() { return internal1Marks; }
        public double getInternal1Percentage() { return internal1Percentage; }
        public double getInternal2Marks() { return internal2Marks; }
        public double getInternal2Percentage() { return internal2Percentage; }
        public double getTrendPercentage() { return trendPercentage; }
    }

    public String getRollNumber() { return rollNumber; }
    public String getName() { return name; }
    public double getCgpa() { return cgpa; }
    public double getLastSemesterSgpa() { return lastSemesterSgpa; }
    public Double getInternal1Percentage() { return internal1Percentage; }
    public Double getInternal2Percentage() { return internal2Percentage; }
    public Double getCombinedInternalPercentage() { return combinedInternalPercentage; }
    public String getStrongestSubject() { return strongestSubject; }
    public String getWeakestSubject() { return weakestSubject; }
    public String getRiskClassification() { return riskClassification; }
    public boolean isCompleteRecord() { return completeRecord; }
    public List<SubjectPerformanceDetailDto> getSubjectBreakdown() { return subjectBreakdown; }
    public List<String> getUnitRecommendations() { return unitRecommendations; }
    public List<String> getGeneralRecommendations() { return generalRecommendations; }
}
