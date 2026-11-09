package studentperformance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import studentperformance.dto.SubjectCategoryStatsDto;
import studentperformance.dto.SubjectPerformanceResponseDto;
import studentperformance.dto.SubjectStudentEntryDto;
import studentperformance.service.SubjectPerformanceService;

import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SubjectPerformanceServiceTest {

    private StudentRepository studentRepository;
    private PerformanceAnalyzer performanceAnalyzer;
    private SubjectPerformanceService service;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        performanceAnalyzer = new PerformanceAnalyzer();
        service = new SubjectPerformanceService(studentRepository, performanceAnalyzer);
    }

    private Student createStudent(String roll, String name,
                                  Double ds1Exam, Double ds2Exam,
                                  Double cs1Exam, Double cs2Exam) {
        Map<Subject, SubjectAssessment> m1 = new EnumMap<>(Subject.class);
        Map<Subject, SubjectAssessment> m2 = new EnumMap<>(Subject.class);

        if (ds1Exam != null) {
            // DS CIE max = 40 (exam 30, assgn 5, quiz 5)
            // assign 5, 5, 5 (avg 5), quiz 5, 5, 5 (avg 5) -> total = exam + 10
            m1.put(Subject.DS, new SubjectAssessment(ds1Exam.intValue(), List.of(5, 5, 5), List.of(5, 5, 5)));
        }
        if (ds2Exam != null) {
            m2.put(Subject.DS, new SubjectAssessment(ds2Exam.intValue(), List.of(5, 5, 5), List.of(5, 5, 5)));
        }

        if (cs1Exam != null) {
            // CS CIE max = 30 (exam 20, assgn 5, quiz 5)
            m1.put(Subject.CS, new SubjectAssessment(cs1Exam.intValue(), List.of(5, 5, 5), List.of(5, 5, 5)));
        }
        if (cs2Exam != null) {
            m2.put(Subject.CS, new SubjectAssessment(cs2Exam.intValue(), List.of(5, 5, 5), List.of(5, 5, 5)));
        }

        return new Student(name, roll, 8.5, 8.5, m1, m2);
    }

    @Test
    @DisplayName("Verify exact category boundaries: 90%, 80%, 70%, 60%, 40%, below 40%")
    void testExactCategoryBoundaries() {
        assertEquals("Excellent", SubjectPerformanceService.categorize(100.0));
        assertEquals("Excellent", SubjectPerformanceService.categorize(90.0));
        assertEquals("Very Good", SubjectPerformanceService.categorize(89.99));
        assertEquals("Very Good", SubjectPerformanceService.categorize(80.0));
        assertEquals("Good", SubjectPerformanceService.categorize(79.99));
        assertEquals("Good", SubjectPerformanceService.categorize(70.0));
        assertEquals("Average", SubjectPerformanceService.categorize(69.99));
        assertEquals("Average", SubjectPerformanceService.categorize(60.0));
        assertEquals("Needs Improvement", SubjectPerformanceService.categorize(59.99));
        assertEquals("Needs Improvement", SubjectPerformanceService.categorize(40.0));
        assertEquals("At Risk", SubjectPerformanceService.categorize(39.99));
        assertEquals("At Risk", SubjectPerformanceService.categorize(0.0));
        assertEquals("Unassessed", SubjectPerformanceService.categorize(null));
        assertEquals("Unassessed", SubjectPerformanceService.categorize(Double.NaN));
    }

    @Test
    @DisplayName("Verify 40-mark subject (DS) calculations and category distribution")
    void testDataStructuresPerformance() throws SQLException {
        // DS: max CIE = 40. Exam max = 30.
        // Student 1: exam 28 -> total 38 / 40 = 95.0% -> Excellent
        Student s1 = createStudent("1602-25-733-001", "Student One", 28.0, 26.0, null, null);
        // Student 2: exam 23 -> total 33 / 40 = 82.5% -> Very Good
        Student s2 = createStudent("1602-25-733-002", "Student Two", 23.0, 24.0, null, null);
        // Student 3: exam 19 -> total 29 / 40 = 72.5% -> Good
        Student s3 = createStudent("1602-25-733-003", "Student Three", 19.0, 19.0, null, null);
        // Student 4: exam 15 -> total 25 / 40 = 62.5% -> Average
        Student s4 = createStudent("1602-25-733-004", "Student Four", 15.0, 16.0, null, null);
        // Student 5: exam 8 -> total 18 / 40 = 45.0% -> Needs Improvement
        Student s5 = createStudent("1602-25-733-005", "Student Five", 8.0, 9.0, null, null);
        // Student 6: exam 3 -> total 13 / 40 = 32.5% -> At Risk
        Student s6 = createStudent("1602-25-733-006", "Student Six", 3.0, 4.0, null, null);
        // Student 7: unassessed in DS
        Student s7 = createStudent("1602-25-733-007", "Student Seven", null, null, null, null);

        when(studentRepository.findAll()).thenReturn(List.of(s1, s2, s3, s4, s5, s6, s7));

        SubjectPerformanceResponseDto res = service.getSubjectPerformance("DS", "INTERNAL_1");

        assertEquals("DS", res.getSubjectCode());
        assertEquals(40, res.getCieMaxMarks());
        assertEquals(7, res.getTotalStudents());
        assertEquals(6, res.getAssessedCount());
        assertEquals(1, res.getUnassessedCount());

        // Check top and bottom
        assertEquals(38.0, res.getHighestScore());
        assertEquals("1602-25-733-001", res.getHighestStudentRoll());
        assertEquals(13.0, res.getLowestScore());
        assertEquals("1602-25-733-006", res.getLowestStudentRoll());

        // Check 6 category stats
        List<SubjectCategoryStatsDto> stats = res.getCategoryStats();
        assertEquals(6, stats.size());
        assertEquals("Excellent", stats.get(0).getCategory());
        assertEquals(1, stats.get(0).getCount());
        assertEquals("Very Good", stats.get(1).getCategory());
        assertEquals(1, stats.get(1).getCount());
        assertEquals("Good", stats.get(2).getCategory());
        assertEquals(1, stats.get(2).getCount());
        assertEquals("Average", stats.get(3).getCategory());
        assertEquals(1, stats.get(3).getCount());
        assertEquals("Needs Improvement", stats.get(4).getCategory());
        assertEquals(1, stats.get(4).getCount());
        assertEquals("At Risk", stats.get(5).getCategory());
        assertEquals(1, stats.get(5).getCount());

        // Verify unassessed student is in unassessedStudents list, not counted in average
        assertEquals(1, res.getUnassessedStudents().size());
        assertEquals("1602-25-733-007", res.getUnassessedStudents().get(0).getRollNumber());
    }

    @Test
    @DisplayName("Verify 30-mark subject (CS) maximum marks and percentage scaling")
    void testCommunicationSkillsPerformance() throws SQLException {
        // CS max marks = 30. Exam max = 20.
        // Student: exam 18, assign 5, quiz 5 -> total = 28 / 30 = 93.33% -> Excellent
        Student s1 = createStudent("1602-25-733-001", "Student One", null, null, 18.0, 17.0);
        when(studentRepository.findAll()).thenReturn(List.of(s1));

        SubjectPerformanceResponseDto res = service.getSubjectPerformance("CS", "INTERNAL_1");
        assertEquals("CS", res.getSubjectCode());
        assertEquals(30, res.getCieMaxMarks());
        assertEquals(1, res.getAssessedCount());
        assertEquals(28.0, res.getHighestScore());
        assertEquals(93.33, res.getAveragePercentage());
        assertEquals("Excellent", res.getStudents().get(0).getCategory());
    }
}
