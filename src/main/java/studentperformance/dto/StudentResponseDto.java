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
    private double cgpa;
    private double lastSemesterSgpa;
    private Double internal1Percentage;
    private Double internal2Percentage;
    private Double combinedInternalPercentage;
    private boolean hasBothInternals;
    private String strongestSubject;
    private String weakestSubject;
    private String prediction;
    private Map<String, SubjectAssessmentDto> internal1Marks;
    private Map<String, SubjectAssessmentDto> internal2Marks;

    public StudentResponseDto() {}

    public StudentResponseDto(Student student, PerformanceAnalyzer analyzer) {
        this.rollNumber = student.getRollNumber();
        this.name = student.getName();
        this.cgpa = student.getCgpa();
        this.lastSemesterSgpa = student.getLastSemesterSgpa();
        this.hasBothInternals = analyzer.hasBothInternals(student);

        if (analyzer.isInternalComplete(student, 1)) {
            this.internal1Percentage = analyzer.percentage(student, 1);
        }
        if (analyzer.isInternalComplete(student, 2)) {
            this.internal2Percentage = analyzer.percentage(student, 2);
        }
        if (hasBothInternals) {
            this.combinedInternalPercentage = analyzer.currentInternalPercentage(student);
            Subject strong = analyzer.strongestSubject(student);
            this.strongestSubject = strong != null ? strong.getDisplayName() : null;
            Subject weak = analyzer.weakestSubject(student);
            this.weakestSubject = weak != null ? weak.getDisplayName() : null;
            this.prediction = analyzer.prediction(student);
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
    public double getCgpa() { return cgpa; }
    public double getLastSemesterSgpa() { return lastSemesterSgpa; }
    public Double getInternal1Percentage() { return internal1Percentage; }
    public Double getInternal2Percentage() { return internal2Percentage; }
    public Double getCombinedInternalPercentage() { return combinedInternalPercentage; }
    public boolean isHasBothInternals() { return hasBothInternals; }
    public String getStrongestSubject() { return strongestSubject; }
    public String getWeakestSubject() { return weakestSubject; }
    public String getPrediction() { return prediction; }
    public Map<String, SubjectAssessmentDto> getInternal1Marks() { return internal1Marks; }
    public Map<String, SubjectAssessmentDto> getInternal2Marks() { return internal2Marks; }
}
