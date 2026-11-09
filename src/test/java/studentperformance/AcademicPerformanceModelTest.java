package studentperformance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AcademicPerformanceModelTest {

    private final PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

    @Test
    @DisplayName("Case A: High-performing student formula and classification")
    void testCaseA_HighPerformingStudent() {
        // (0.70 * 85.0) + (0.20 * 9.0 * 10) + (0.10 * 95.0) = 59.5 + 18.0 + 9.5 = 87.0
        double score95Att = PerformanceAnalyzer.calculateAcademicScore(85.0, 9.0, 95.0);
        assertEquals(87.0, score95Att, 0.001);
        assertEquals("Good Progress", PerformanceAnalyzer.classifyScore(score95Att));

        // If attendance is 100%: 59.5 + 18.0 + 10.0 = 87.5
        double score100Att = PerformanceAnalyzer.calculateAcademicScore(85.0, 9.0, 100.0);
        assertEquals(87.5, score100Att, 0.001);
        assertEquals("Good Progress", PerformanceAnalyzer.classifyScore(score100Att));
    }

    @Test
    @DisplayName("Case B: Average-performing student formula and classification")
    void testCaseB_AveragePerformingStudent() {
        // (0.70 * 65.0) + (0.20 * 7.0 * 10) + (0.10 * 80.0) = 45.5 + 14.0 + 8.0 = 67.5
        double score = PerformanceAnalyzer.calculateAcademicScore(65.0, 7.0, 80.0);
        assertEquals(67.5, score, 0.001);
        assertEquals("Monitor Progress", PerformanceAnalyzer.classifyScore(score));
    }

    @Test
    @DisplayName("Case C: Low-performing student formula and classification")
    void testCaseC_LowPerformingStudent() {
        // (0.70 * 45.0) + (0.20 * 5.0 * 10) + (0.10 * 70.0) = 31.5 + 10.0 + 7.0 = 48.5
        double score = PerformanceAnalyzer.calculateAcademicScore(45.0, 5.0, 70.0);
        assertEquals(48.5, score, 0.001);
        assertEquals("May Need Additional Support", PerformanceAnalyzer.classifyScore(score));

        // With SGPA 3.0: 31.5 + 6.0 + 7.0 = 44.5
        double score445 = PerformanceAnalyzer.calculateAcademicScore(45.0, 3.0, 70.0);
        assertEquals(44.5, score445, 0.001);
        assertEquals("May Need Additional Support", PerformanceAnalyzer.classifyScore(score445));
    }

    @Test
    @DisplayName("Boundary testing for exact 75.0, 60.0, 74.99, 59.99")
    void testBoundaryConditions() {
        // Exactly 75.0 -> Good Progress
        assertEquals("Good Progress", PerformanceAnalyzer.classifyScore(75.0));

        // Exactly 60.0 -> Monitor Progress
        assertEquals("Monitor Progress", PerformanceAnalyzer.classifyScore(60.0));

        // Just below 75.0 (74.99) -> Monitor Progress
        assertEquals("Monitor Progress", PerformanceAnalyzer.classifyScore(74.99));

        // Just below 60.0 (59.99) -> May Need Additional Support
        assertEquals("May Need Additional Support", PerformanceAnalyzer.classifyScore(59.99));

        // Bounded limits: clamping between 0 and 100
        assertEquals(100.0, PerformanceAnalyzer.calculateAcademicScore(110.0, 11.0, 110.0), 0.001);
        assertEquals(0.0, PerformanceAnalyzer.calculateAcademicScore(-10.0, -1.0, -10.0), 0.001);
    }

    @Test
    @DisplayName("Warning flags: Critical Academic Warning & Attendance Warning")
    void testWarningFlags() {
        Student goodStudentWithAttWarning = createStudentWithMarks(80.0, 8.5, 71.0);
        double finalScore = analyzer.finalAcademicScore(goodStudentWithAttWarning);
        assertEquals("Good Progress", analyzer.prediction(goodStudentWithAttWarning));
        assertTrue(analyzer.hasAttendanceWarning(goodStudentWithAttWarning), "Attendance 71% must trigger attendance warning");
        assertFalse(analyzer.hasCriticalAcademicWarning(goodStudentWithAttWarning), "CIE 80% should not trigger critical academic warning");

        Student criticalCieStudent = createStudentWithMarks(38.0, 7.0, 85.0);
        assertTrue(analyzer.hasCriticalAcademicWarning(criticalCieStudent), "CIE 38% must trigger critical academic warning");
        assertFalse(analyzer.hasAttendanceWarning(criticalCieStudent));
        assertEquals("May Need Additional Support", analyzer.prediction(criticalCieStudent));
    }

    @Test
    @DisplayName("Subject-wise and Credit-weighted CIE verification")
    void testSubjectWiseAndCreditWeightedCie() {
        // Theory subjects: 40 max marks (30 exam + 5 a + 5 q)
        // Skill subjects: 30 max marks (20 exam + 5 a + 5 q)
        // Test student with distinct marks per subject:
        // DS (4 credits): 90%
        // CS (1 credit): 50%
        EnumMap<Subject, SubjectAssessment> m1 = new EnumMap<>(Subject.class);
        EnumMap<Subject, SubjectAssessment> m2 = new EnumMap<>(Subject.class);

        for (Subject s : Subject.values()) {
            // Default 70%
            int exam = (int) Math.round(s.getMaximumExamMarks() * 0.70);
            m1.put(s, new SubjectAssessment(exam, List.of(4, 4, 4), List.of(3, 3, 3)));
            m2.put(s, new SubjectAssessment(exam, List.of(4, 4, 4), List.of(3, 3, 3)));
        }

        // Give DS (4 credits) high marks: 30 exam + 5 a + 5 q = 40/40 (100%)
        m1.put(Subject.DS, new SubjectAssessment(30, List.of(5, 5, 5), List.of(5, 5, 5)));
        m2.put(Subject.DS, new SubjectAssessment(30, List.of(5, 5, 5), List.of(5, 5, 5)));

        // Give CS (1 credit) low marks: 10 exam + 2 a + 2 q = 14/30 (46.67%)
        m1.put(Subject.CS, new SubjectAssessment(10, List.of(2, 2, 2), List.of(2, 2, 2)));
        m2.put(Subject.CS, new SubjectAssessment(10, List.of(2, 2, 2), List.of(2, 2, 2)));

        Student s = new Student("Weighted Test", "1602-25-733-200", 8.0, 8.0, 7.5, 85.0, m1, m2);

        assertEquals(100.0, analyzer.subjectCie(s, Subject.DS), 0.01);
        assertEquals(46.67, analyzer.subjectCie(s, Subject.CS), 0.01);

        double creditWeightedCie = analyzer.creditWeightedCiePercentage(s);
        assertTrue(creditWeightedCie > 0, "Credit weighted CIE must be positive");

        // Verify that custom credits alter weighting properly
        Map<Subject, Integer> equalCredits = new EnumMap<>(Subject.class);
        for (Subject sub : Subject.values()) equalCredits.put(sub, 1);
        double unweightedCie = analyzer.creditWeightedCiePercentage(s, equalCredits);

        // Because 4-credit DS scored 100%, credit-weighted CIE must be higher than equal-credit CIE
        assertTrue(creditWeightedCie > unweightedCie, "Credit-weighted CIE should favor 4-credit DS over 1-credit CS");
    }

    @Test
    @DisplayName("Incomplete assessment data handling")
    void testIncompleteAssessmentData() {
        EnumMap<Subject, SubjectAssessment> partialM1 = new EnumMap<>(Subject.class);
        partialM1.put(Subject.JAVA, new SubjectAssessment(20, List.of(3, 3, 3), List.of(3, 3, 3)));
        EnumMap<Subject, SubjectAssessment> emptyM2 = new EnumMap<>(Subject.class);

        Student incompleteStudent = new Student("Incomplete", "1602-25-733-201", 7.0, 7.0, 7.0, 80.0, partialM1, emptyM2);

        assertFalse(analyzer.hasBothInternals(incompleteStudent));
        assertTrue(Double.isNaN(analyzer.overallCiePercentage(incompleteStudent)));
        assertTrue(Double.isNaN(analyzer.finalAcademicScore(incompleteStudent)));
        assertEquals("Incomplete assessment data", analyzer.prediction(incompleteStudent));
        assertTrue(analyzer.hasIncompleteAssessmentWarning(incompleteStudent));

        // Student with missing attendance
        Student noAttStudent = createStudentWithMarks(70.0, 7.5, null);
        assertTrue(Double.isNaN(analyzer.finalAcademicScore(noAttStudent)));
        assertEquals("Incomplete assessment data", analyzer.prediction(noAttStudent));
        assertTrue(analyzer.hasIncompleteAssessmentWarning(noAttStudent));
    }

    @Test
    @DisplayName("EvaluateStudent comprehensive record output")
    void testEvaluateStudentRecord() {
        Student st = createStudentWithMarks(80.0, 8.0, 90.0);
        PerformanceAnalyzer.AcademicEvaluation eval = analyzer.evaluateStudent(st);

        assertTrue(eval.isComplete());
        assertNotNull(eval.overallCiePercentage());
        assertNotNull(eval.finalAcademicScore());
        assertEquals(8.0, eval.previousSemesterSgpa());
        assertEquals(90.0, eval.attendancePercentage());
        assertEquals("Good Progress", eval.primaryClassification());
        assertFalse(eval.criticalAcademicWarning());
        assertFalse(eval.attendanceWarning());
        assertFalse(eval.incompleteAssessmentWarning());
        assertEquals(8, eval.subjectCiePercentages().size());
        assertEquals(8, eval.subjectCredits().size());
    }

    private Student createStudentWithMarks(double targetPercentage, double lastSgpa, Double attendance) {
        EnumMap<Subject, SubjectAssessment> m1 = new EnumMap<>(Subject.class);
        EnumMap<Subject, SubjectAssessment> m2 = new EnumMap<>(Subject.class);

        for (Subject s : Subject.values()) {
            double totalTarget = s.getCieMaxMarks() * (targetPercentage / 100.0);
            int exam = (int) Math.max(0, Math.min(s.getMaximumExamMarks(), Math.round(totalTarget - 8.0)));
            int a = 4;
            int q = (int) Math.max(0, Math.min(5, Math.round(totalTarget - exam - a)));
            m1.put(s, new SubjectAssessment(exam, List.of(a, a, a), List.of(q, q, q)));
            m2.put(s, new SubjectAssessment(exam, List.of(a, a, a), List.of(q, q, q)));
        }

        return new Student("Test Student", "1602-25-733-999", lastSgpa, lastSgpa, lastSgpa, attendance, m1, m2);
    }
}
