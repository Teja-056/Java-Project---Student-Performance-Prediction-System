package studentperformance.dto;

import studentperformance.PerformanceAnalyzer;
import studentperformance.Student;
import studentperformance.Subject;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class StudentResponseDto {
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
    private boolean hasBothInternals;
    private String strongestSubject;
    private String weakestSubject;
    private String prediction;
    private Double overallCiePercentage;
    private Double finalAcademicScore;
    private String academicClassification;
    private Boolean criticalAcademicWarning;
    private Boolean attendanceWarning;
    private Boolean incompleteAssessmentWarning;
    private Map<String, Double> subjectCiePercentages;
    private Map<String, Integer> subjectCredits;
    private Map<String, SubjectAssessmentDto> internal1Marks;
    private Map<String, SubjectAssessmentDto> internal2Marks;

    public StudentResponseDto() {}

    public StudentResponseDto(Student student, PerformanceAnalyzer analyzer) {
        this.rollNumber = student.getRollNumber();
        this.name = student.getName();
        this.sem1Sgpa = student.getSem1Sgpa();
        this.cgpa = student.getCgpa();
        this.lastSemesterSgpa = student.getLastSemesterSgpa();
        this.attendancePercent = student.getAttendancePercent();
        this.hasBothInternals = analyzer.hasBothInternals(student);

        if (analyzer.possibleMarks(student, 1) > 0) {
            this.internal1Percentage = analyzer.percentage(student, 1);
            this.internal1IsPartial = !analyzer.isInternalComplete(student, 1);
        }
        if (analyzer.possibleMarks(student, 2) > 0) {
            this.internal2Percentage = analyzer.percentage(student, 2);
            this.internal2IsPartial = !analyzer.isInternalComplete(student, 2);
        }
        if (hasBothInternals) {
            this.combinedInternalPercentage = analyzer.currentInternalPercentage(student);
            Subject strong = analyzer.strongestSubject(student);
            this.strongestSubject = strong != null ? strong.getDisplayName() : null;
            Subject weak = analyzer.weakestSubject(student);
            this.weakestSubject = weak != null ? weak.getDisplayName() : null;
        }

        PerformanceAnalyzer.AcademicEvaluation eval = analyzer.evaluateStudent(student);
        this.overallCiePercentage = eval.overallCiePercentage();
        this.finalAcademicScore = eval.finalAcademicScore();
        this.academicClassification = eval.primaryClassification();
        this.criticalAcademicWarning = eval.criticalAcademicWarning();
        this.attendanceWarning = eval.attendanceWarning();
        this.incompleteAssessmentWarning = eval.incompleteAssessmentWarning();
        this.prediction = eval.primaryClassification();

        this.subjectCiePercentages = new LinkedHashMap<>();
        this.subjectCredits = new LinkedHashMap<>();
        for (Subject s : Subject.values()) {
            Double sCie = eval.subjectCiePercentages().get(s);
            this.subjectCiePercentages.put(s.name(), sCie != null && !Double.isNaN(sCie) ? PerformanceAnalyzer.round2(sCie) : null);
            this.subjectCredits.put(s.name(), eval.subjectCredits().get(s));
        }

        this.internal1Marks = new LinkedHashMap<>();
        Map<Subject, ?> m1 = student.getMarks(1);
        for (Subject s : Subject.values()) {
            if (m1.containsKey(s)) {
                this.internal1Marks.put(s.name(), new SubjectAssessmentDto((studentperformance.SubjectAssessment) m1.get(s)));
            }
        }

        this.internal2Marks = new LinkedHashMap<>();
        Map<Subject, ?> m2 = student.getMarks(2);
        for (Subject s : Subject.values()) {
            if (m2.containsKey(s)) {
                this.internal2Marks.put(s.name(), new SubjectAssessmentDto((studentperformance.SubjectAssessment) m2.get(s)));
            }
        }
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
    public boolean isHasBothInternals() { return hasBothInternals; }
    public String getStrongestSubject() { return strongestSubject; }
    public String getWeakestSubject() { return weakestSubject; }
    public String getPrediction() { return prediction; }
    public Double getOverallCiePercentage() { return overallCiePercentage; }
    public Double getFinalAcademicScore() { return finalAcademicScore; }
    public String getAcademicClassification() { return academicClassification; }
    public Boolean getCriticalAcademicWarning() { return criticalAcademicWarning; }
    public Boolean getAttendanceWarning() { return attendanceWarning; }
    public Boolean getIncompleteAssessmentWarning() { return incompleteAssessmentWarning; }
    public Map<String, Double> getSubjectCiePercentages() { return subjectCiePercentages; }
    public Map<String, Integer> getSubjectCredits() { return subjectCredits; }
    public Map<String, SubjectAssessmentDto> getInternal1Marks() { return internal1Marks; }
    public Map<String, SubjectAssessmentDto> getInternal2Marks() { return internal2Marks; }
}
