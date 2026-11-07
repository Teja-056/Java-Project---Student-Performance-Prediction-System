package studentperformance.service;

import org.springframework.stereotype.Service;
import studentperformance.*;
import studentperformance.dto.*;
import studentperformance.exception.BadRequestException;
import studentperformance.exception.ResourceNotFoundException;

import java.sql.SQLException;
import java.util.*;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final SyllabusRepository syllabusRepository;
    private final PerformanceAnalyzer performanceAnalyzer;

    public StudentService(StudentRepository studentRepository,
                          SyllabusRepository syllabusRepository,
                          PerformanceAnalyzer performanceAnalyzer) {
        this.studentRepository = studentRepository;
        this.syllabusRepository = syllabusRepository;
        this.performanceAnalyzer = performanceAnalyzer;
    }

    public List<StudentResponseDto> getAllStudents() {
        try {
            List<Student> students = studentRepository.findAll();
            return students.stream()
                    .map(s -> new StudentResponseDto(s, performanceAnalyzer))
                    .toList();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch students: " + e.getMessage(), e);
        }
    }

    public StudentResponseDto getStudentByRoll(String rollNumber) {
        Student student = findStudentOrThrow(rollNumber);
        return new StudentResponseDto(student, performanceAnalyzer);
    }

    public StudentResponseDto createStudent(StudentRequestDto req) {
        try {
            if (studentRepository.findByRollNumber(req.getRollNumber()) != null) {
                throw new BadRequestException("Student with roll number " + req.getRollNumber() + " already exists.");
            }

            EnumMap<Subject, SubjectAssessment> m1 = toAssessmentMap(req.getInternal1Marks());
            EnumMap<Subject, SubjectAssessment> m2 = toAssessmentMap(req.getInternal2Marks());

            Student student = new Student(req.getName(), req.getRollNumber(), req.getCgpa(), req.getLastSemesterSgpa(), m1, m2);
            studentRepository.save(student);

            return new StudentResponseDto(student, performanceAnalyzer);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create student: " + e.getMessage(), e);
        }
    }

    public StudentResponseDto updateStudent(String rollNumber, StudentRequestDto req) {
        findStudentOrThrow(rollNumber);
        try {
            // Update profile metadata
            studentRepository.updateDetails(rollNumber, req.getName(), req.getCgpa(), req.getLastSemesterSgpa());

            // If marks provided, update them
            if (req.getInternal1Marks() != null || req.getInternal2Marks() != null) {
                EnumMap<Subject, SubjectAssessment> m1 = toAssessmentMap(req.getInternal1Marks());
                EnumMap<Subject, SubjectAssessment> m2 = toAssessmentMap(req.getInternal2Marks());
                Student updated = new Student(req.getName(), rollNumber, req.getCgpa(), req.getLastSemesterSgpa(), m1, m2);
                studentRepository.updateMarks(updated);
            }

            Student refreshed = studentRepository.findByRollNumber(rollNumber);
            return new StudentResponseDto(refreshed, performanceAnalyzer);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update student: " + e.getMessage(), e);
        }
    }

    public void deleteStudent(String rollNumber) {
        findStudentOrThrow(rollNumber);
        try {
            studentRepository.delete(rollNumber);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete student: " + e.getMessage(), e);
        }
    }

    public StudentReportDto getStudentReport(String rollNumber) {
        Student student = findStudentOrThrow(rollNumber);
        Map<Integer, List<Integer>> unitMapping = Map.of(1, List.of(1, 2), 2, List.of(3, 4, 5));
        try {
            Map<Integer, List<Integer>> dbMapping = syllabusRepository.getInternalUnitMapping();
            if (dbMapping != null && !dbMapping.isEmpty()) {
                unitMapping = dbMapping;
            }
        } catch (Exception ignored) {
        }
        return new StudentReportDto(student, performanceAnalyzer, unitMapping);
    }

    public List<SubjectAnalyticsDto> getClassSubjectAnalytics() {
        try {
            List<Student> students = studentRepository.findAll();
            List<SubjectAnalyticsDto> analyticsList = new ArrayList<>();

            for (Subject subject : Subject.values()) {
                int totalEvaluated = 0;
                int wellCount = 0;
                int improvementCount = 0;
                double totalPercentSum = 0.0;

                for (Student student : students) {
                    double p1 = performanceAnalyzer.subjectPercentage(student, 1, subject);
                    double p2 = performanceAnalyzer.subjectPercentage(student, 2, subject);
                    if (!Double.isNaN(p1) && !Double.isNaN(p2)) {
                        totalEvaluated++;
                        double avgSubjectPercent = (p1 + p2) / 2.0;
                        totalPercentSum += avgSubjectPercent;

                        if (avgSubjectPercent >= 75.0) {
                            wellCount++;
                        } else if (avgSubjectPercent < 50.0) {
                            improvementCount++;
                        }
                    }
                }

                double classAverage = totalEvaluated > 0 ? (totalPercentSum / totalEvaluated) : 0.0;

                String summary;
                if (totalEvaluated == 0) {
                    summary = "No complete assessment records available yet for analysis.";
                } else if (classAverage >= 75.0) {
                    summary = String.format(Locale.ROOT, "Strong cohort mastery: Class average %.1f%% with %d high-achieving students.", classAverage, wellCount);
                } else if (classAverage >= 60.0) {
                    summary = String.format(Locale.ROOT, "Satisfactory progress: Class average %.1f%%. %d students require reinforcement.", classAverage, improvementCount);
                } else {
                    summary = String.format(Locale.ROOT, "Critical attention required: Class average %.1f%% below expectation. %d students below 50%%.", classAverage, improvementCount);
                }

                analyticsList.add(new SubjectAnalyticsDto(
                        subject.name(),
                        subject.getDisplayName(),
                        totalEvaluated,
                        wellCount,
                        improvementCount,
                        Math.round(classAverage * 100.0) / 100.0,
                        summary
                ));
            }

            return analyticsList;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to generate subject analytics: " + e.getMessage(), e);
        }
    }

    public Student findStudentOrThrow(String rollNumber) {
        if (rollNumber == null || !rollNumber.matches("^1602-25-733-\\d{3}$")) {
            throw new BadRequestException("Invalid roll number format: " + rollNumber + ". Must follow 1602-25-733-XXX.");
        }
        try {
            Student student = studentRepository.findByRollNumber(rollNumber);
            if (student == null) {
                throw new ResourceNotFoundException("Student with roll number " + rollNumber + " was not found.");
            }
            return student;
        } catch (SQLException e) {
            throw new RuntimeException("Database error looking up roll number: " + e.getMessage(), e);
        }
    }

    private EnumMap<Subject, SubjectAssessment> toAssessmentMap(Map<String, SubjectAssessmentDto> dtoMap) {
        EnumMap<Subject, SubjectAssessment> map = new EnumMap<>(Subject.class);
        if (dtoMap == null) return map;

        for (Map.Entry<String, SubjectAssessmentDto> entry : dtoMap.entrySet()) {
            try {
                Subject s = Subject.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                if (entry.getValue() != null) {
                    map.put(s, entry.getValue().toSubjectAssessment());
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return map;
    }
}
