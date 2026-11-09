package studentperformance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerformanceAnalyzerTest {
    private final PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

    @Test
    void averagesActivitiesAndComputesSubjectAndInternalPercentages() {
        Student student = fullStudent("1602-25-733-101", 15, 24);
        SubjectAssessment javaInternal1 = student.getMarks(1).get(Subject.JAVA);
        assertEquals(3.0, javaInternal1.assignmentAverage(), 0.001);
        assertEquals(4.0, javaInternal1.quizAverage(), 0.001);
        assertEquals(22.0, javaInternal1.totalMarks(), 0.001);
        assertEquals(55.0, analyzer.subjectPercentage(student, 1, Subject.JAVA), 0.001);
        assertEquals(290, Subject.maximumTotal());
        assertEquals(211.0, analyzer.totalMarks(student, 1), 0.001);
        assertEquals(220.0, analyzer.totalMarks(student, 2), 0.001);
        assertEquals(74.31, analyzer.currentInternalPercentage(student), 0.01);
        assertTrue(analyzer.hasBothInternals(student));
    }

    @Test
    void identifiesSubjectImprovementAndDecline() {
        Student student = fullStudent("1602-25-733-102", 15, 24);
        assertEquals(22.5, analyzer.subjectChange(student, Subject.JAVA), 0.001);
        assertEquals(0.0, analyzer.subjectChange(student, Subject.DS), 0.001);
        assertEquals(Subject.CS, analyzer.strongestSubject(student));
        assertNotNull(analyzer.weakestSubject(student));
    }

    @Test
    void incompleteDataIsRetainedButExcludedFromSupportAndRanking() {
        EnumMap<Subject, SubjectAssessment> first = new EnumMap<>(Subject.class);
        EnumMap<Subject, SubjectAssessment> second = new EnumMap<>(Subject.class);
        first.put(Subject.JAVA, new SubjectAssessment(15, Arrays.asList(null, null, null), Arrays.asList(null, null, null), true));
        Student student = new Student("Partial Record", "1602-25-733-103", 6.5, 6.0, first, second,
                List.of(new LegacyMark(1, "JAVA", 15)));
        assertFalse(analyzer.hasBothInternals(student));
        assertFalse(analyzer.requiresSupport(student));
        assertEquals("Incomplete assessment data", analyzer.prediction(student));
        assertEquals(1, student.getLegacyMarks().size());
        assertTrue(analyzer.unitAdvice(student).contains("incomplete"));
    }

    @Test
    void rejectsInvalidRollNumbersMarksAndActivityScores() {
        assertThrows(IllegalArgumentException.class, () -> new Student("Bad", "roll", 8, 8,
                new EnumMap<>(Subject.class), new EnumMap<>(Subject.class)));
        EnumMap<Subject, SubjectAssessment> invalid = allMarks(10, 1, 1);
        invalid.put(Subject.JAVA, new SubjectAssessment(31, List.of(1, 1, 1), List.of(1, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new Student("Bad Marks", "1602-25-733-104", 8, 8, invalid, allMarks(1, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new SubjectAssessment(10, List.of(6, 1, 1), List.of(1, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new Student("Bad GPA", "1602-25-733-105", 11, 8,
                allMarks(1, 1, 1), allMarks(1, 1, 1)));
    }

    @Test
    void unitAdviceUsesCallerProvidedInternalUnitConfiguration() {
        Student student = new Student("Low Score", "1602-25-733-106", 4, 4,
                allMarks(0, 0, 0), allMarks(0, 0, 0));
        String advice = analyzer.unitAdvice(student, java.util.Map.of(1, List.of(7, 9), 2, List.of(11)));
        assertTrue(advice.contains("Internal 1"));
        assertTrue(advice.contains("Units 7 and 9"));
        assertTrue(advice.contains("Units 11"));
    }

    static Student fullStudent(String roll, int javaFirst, int javaSecond) {
        EnumMap<Subject, SubjectAssessment> first = allMarks(20, 3, 4);
        EnumMap<Subject, SubjectAssessment> second = allMarks(20, 3, 4);
        first.put(Subject.JAVA, new SubjectAssessment(javaFirst, List.of(3, 3, 3), List.of(4, 4, 4)));
        second.put(Subject.JAVA, new SubjectAssessment(javaSecond, List.of(3, 3, 3), List.of(4, 4, 4)));
        return new Student("Test Student", roll, 8.0, 7.5, first, second);
    }

    static EnumMap<Subject, SubjectAssessment> allMarks(int examMark, int assignmentMark, int quizMark) {
        EnumMap<Subject, SubjectAssessment> result = new EnumMap<>(Subject.class);
        for (Subject subject : Subject.values()) {
            int validExam = Math.min(examMark, subject.getMaximumExamMarks());
            result.put(subject, new SubjectAssessment(validExam, List.of(assignmentMark, assignmentMark, assignmentMark),
                    List.of(quizMark, quizMark, quizMark)));
        }
        return result;
    }
}
