package studentperformance.prediction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studentperformance.Student;
import studentperformance.Subject;
import studentperformance.SubjectAssessment;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PredictionEngineTest {

    private PredictionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PredictionEngine();
    }

    private Student createTestStudent(Double sem1, double sem2, double cgpa, Double attendance) {
        EnumMap<Subject, SubjectAssessment> int1 = new EnumMap<>(Subject.class);
        EnumMap<Subject, SubjectAssessment> int2 = new EnumMap<>(Subject.class);

        for (Subject s : Subject.values()) {
            int exam1 = (int) Math.round(s.getMaximumExamMarks() * 0.75);
            int exam2 = (int) Math.round(s.getMaximumExamMarks() * 0.85); // Showing improvement
            int1.put(s, new SubjectAssessment(exam1, List.of(4, 4, 4), List.of(4, 4, 4)));
            int2.put(s, new SubjectAssessment(exam2, List.of(5, 4, 5), List.of(5, 5, 4)));
        }

        return new Student("Aarav Sharma", "1602-25-733-001", sem1, cgpa, sem2, attendance, int1, int2);
    }

    @Test
    void predictsAllEightSubjectsWithExactCredits() {
        Student student = createTestStudent(8.50, 8.70, 8.59, 90.0);
        StudentPredictionResult result = engine.predict(student);

        assertNotNull(result);
        assertEquals("1602-25-733-001", result.getRollNumber());
        assertEquals("Aarav Sharma", result.getStudentName());
        assertEquals(8.50, result.getSem1Sgpa());
        assertEquals(8.70, result.getSem2Sgpa());
        assertEquals(90.0, result.getAttendancePercent());

        assertTrue(result.getScenarios().containsKey("CONSISTENT"));
        assertTrue(result.getScenarios().containsKey("IMPROVED"));
        assertTrue(result.getScenarios().containsKey("DECLINING"));

        ScenarioPrediction consistent = result.getScenarios().get("CONSISTENT");
        assertEquals(8, consistent.getSubjects().size());

        int totalCredits = consistent.getSubjects().stream().mapToInt(SubjectPrediction::getCredits).sum();
        assertEquals(19, totalCredits);

        for (SubjectPrediction sp : consistent.getSubjects()) {
            assertTrue(sp.getPredictedSee() >= 0);
            assertTrue(sp.getPredictedSee() <= sp.getSeeMax());
            assertEquals(sp.getActualCie() + sp.getPredictedSee(), sp.getFinalMarks(), 0.01);
            assertEquals((sp.getFinalMarks() / sp.getTotalMax()) * 100.0, sp.getPercentage(), 0.05);
            assertNotNull(sp.getGrade());
            assertTrue(sp.getGradePoints() >= 0 && sp.getGradePoints() <= 10);
        }
    }

    @Test
    void verifiesPredictedSgpaAndCgpaFormulas() {
        Student student = createTestStudent(8.00, 8.50, 8.22, 85.0);
        StudentPredictionResult result = engine.predict(student);

        ScenarioPrediction consistent = result.getScenarios().get("CONSISTENT");
        assertEquals("VERIFIED", consistent.getCgpaStatus());
        assertNotNull(consistent.getPredictedCgpa());

        // Verify SGPA formula: sum(credits * GP) / 19
        double sumWeighted = consistent.getSubjects().stream()
                .mapToDouble(s -> s.getCredits() * s.getGradePoints())
                .sum();
        double expectedSgpa = Math.round((sumWeighted / 19.0) * 100.0) / 100.0;
        assertEquals(expectedSgpa, consistent.getPredictedSgpa(), 0.01);

        // Verify 48-credit CGPA formula: (sem1 * 16 + sem2 * 13 + predSgpa * 19) / 48
        double expectedCgpa = Math.round(((8.00 * 16.0 + 8.50 * 13.0 + consistent.getPredictedSgpa() * 19.0) / 48.0) * 100.0) / 100.0;
        assertEquals(expectedCgpa, consistent.getPredictedCgpa(), 0.01);
    }

    @Test
    void leavesPredictedCgpaUnavailableWhenSem1SgpaIsMissing() {
        Student studentWithoutSem1 = createTestStudent(null, 8.50, 8.50, 85.0);
        StudentPredictionResult result = engine.predict(studentWithoutSem1);

        ScenarioPrediction consistent = result.getScenarios().get("CONSISTENT");
        assertNull(consistent.getPredictedCgpa());
        assertEquals("SEMESTER_1_SGPA_REQUIRED", consistent.getCgpaStatus());
        assertTrue(consistent.getPredictedSgpa() > 0);
    }

    @Test
    void verifiesScenarioDivergenceOrder() {
        Student student = createTestStudent(7.50, 7.80, 7.63, 80.0);
        StudentPredictionResult result = engine.predict(student, 15.0, 15.0, null);

        ScenarioPrediction consistent = result.getScenarios().get("CONSISTENT");
        ScenarioPrediction improved = result.getScenarios().get("IMPROVED");
        ScenarioPrediction declining = result.getScenarios().get("DECLINING");

        assertTrue(declining.getPredictedSgpa() <= consistent.getPredictedSgpa(),
                "Declining SGPA (" + declining.getPredictedSgpa() + ") should be <= Consistent (" + consistent.getPredictedSgpa() + ")");
        assertTrue(consistent.getPredictedSgpa() <= improved.getPredictedSgpa(),
                "Consistent SGPA (" + consistent.getPredictedSgpa() + ") should be <= Improved (" + improved.getPredictedSgpa() + ")");

        // Check subject level divergence
        for (int i = 0; i < 8; i++) {
            SubjectPrediction decSubj = declining.getSubjects().get(i);
            SubjectPrediction conSubj = consistent.getSubjects().get(i);
            SubjectPrediction impSubj = improved.getSubjects().get(i);

            assertTrue(decSubj.getPredictedSee() <= conSubj.getPredictedSee());
            assertTrue(conSubj.getPredictedSee() <= impSubj.getPredictedSee());
        }
    }

    @Test
    void handlesMissingOpenElectiveWithoutTreatingNullAsZero() {
        EnumMap<Subject, SubjectAssessment> int1 = new EnumMap<>(Subject.class);
        EnumMap<Subject, SubjectAssessment> int2 = new EnumMap<>(Subject.class);

        // Populate only the 7 core subjects, leaving OE missing/unassessed
        for (Subject s : Subject.values()) {
            if (s == Subject.OE) continue; // Unassessed
            int exam1 = (int) Math.round(s.getMaximumExamMarks() * 0.80);
            int exam2 = (int) Math.round(s.getMaximumExamMarks() * 0.85);
            int1.put(s, new SubjectAssessment(exam1, List.of(5, 4, 4), List.of(4, 5, 4)));
            int2.put(s, new SubjectAssessment(exam2, List.of(5, 5, 4), List.of(5, 5, 5)));
        }

        Student student = new Student("Priya Patel", "1602-25-733-002", 8.80, 8.85, 8.90, 92.0, int1, int2);
        StudentPredictionResult result = engine.predict(student);

        assertNotNull(result);
        ScenarioPrediction consistent = result.getScenarios().get("CONSISTENT");
        assertEquals(8, consistent.getSubjects().size());

        SubjectPrediction oePred = consistent.getSubjects().stream()
                .filter(sp -> "OE".equals(sp.getSubjectKey()))
                .findFirst()
                .orElse(null);

        assertNotNull(oePred);
        assertFalse(oePred.isAssessed());
        assertNull(oePred.getActualCie());
        assertNull(oePred.getPredictedSee());
        assertNull(oePred.getFinalMarks());
        assertNull(oePred.getPercentage());
        assertEquals("UNASSESSED", oePred.getGrade());
        assertNull(oePred.getGradePoints());

        // Predicted SGPA must evaluate across the 17 assessed credits without being dragged to 0 by OE
        assertTrue(consistent.getPredictedSgpa() >= 8.0, "Predicted SGPA (" + consistent.getPredictedSgpa() + ") should reflect assessed performance, not be dragged down by 0");
        assertEquals("PARTIAL_ASSESSMENT_VERIFIED", consistent.getCgpaStatus());
    }

    @Test
    void allThreeScenariosGenerateDetailedPredictionsForAllEightSubjects() {
        Student student = createTestStudent(8.20, 8.40, 8.29, 88.0);
        StudentPredictionResult result = engine.predict(student, 14.0, 10.0, null);

        List<String> scenarioKeys = List.of("CONSISTENT", "IMPROVED", "DECLINING");
        for (String key : scenarioKeys) {
            assertTrue(result.getScenarios().containsKey(key), "Scenario " + key + " must exist");
            ScenarioPrediction sp = result.getScenarios().get(key);
            assertEquals(8, sp.getSubjects().size(), "Each scenario must have 8 subjects");

            int creditSum = 0;
            for (SubjectPrediction sub : sp.getSubjects()) {
                assertNotNull(sub.getSubjectKey());
                assertNotNull(sub.getDisplayName());
                assertTrue(sub.getCredits() > 0);
                creditSum += sub.getCredits();

                assertNotNull(sub.getActualCie());
                assertTrue(sub.getActualCie() >= 0 && sub.getActualCie() <= sub.getCieMax());
                assertNotNull(sub.getPredictedSee());
                assertTrue(sub.getPredictedSee() >= 0 && sub.getPredictedSee() <= sub.getSeeMax());
                assertNotNull(sub.getFinalMarks());
                assertTrue(sub.getFinalMarks() >= 0 && sub.getFinalMarks() <= sub.getTotalMax());
                assertNotNull(sub.getPercentage());
                assertTrue(sub.getPercentage() >= 0 && sub.getPercentage() <= 100);
                assertNotNull(sub.getGrade());
                assertNotNull(sub.getGradePoints());
                assertTrue(sub.getGradePoints() >= 0 && sub.getGradePoints() <= 10);
            }
            assertEquals(19, creditSum, "Total semester credits across 8 subjects must be 19");
            assertTrue(sp.getPredictedSgpa() > 0 && sp.getPredictedSgpa() <= 10.0);
            assertNotNull(sp.getPredictedCgpa());
        }
    }

    @Test
    void verifiesHighPerformerGradeBracketStabilityAndMarksIncrease() {
        // High performer with ~85% marks across subjects (sitting in Grade A: 80-89%)
        EnumMap<Subject, SubjectAssessment> int1 = new EnumMap<>(Subject.class);
        EnumMap<Subject, SubjectAssessment> int2 = new EnumMap<>(Subject.class);

        for (Subject s : Subject.values()) {
            int exam1 = (int) Math.round(s.getMaximumExamMarks() * 0.85);
            int exam2 = (int) Math.round(s.getMaximumExamMarks() * 0.86);
            int1.put(s, new SubjectAssessment(exam1, List.of(5, 5, 5), List.of(5, 5, 5)));
            int2.put(s, new SubjectAssessment(exam2, List.of(5, 5, 5), List.of(5, 5, 5)));
        }

        Student student = new Student("High Performer", "1602-25-733-001", 9.20, 9.25, 9.30, 95.0, int1, int2);
        StudentPredictionResult result = engine.predict(student, 12.0, 12.0, null);

        ScenarioPrediction con = result.getScenarios().get("CONSISTENT");
        ScenarioPrediction imp = result.getScenarios().get("IMPROVED");
        ScenarioPrediction dec = result.getScenarios().get("DECLINING");

        // Mathematical condition: Improved SGPA >= Consistent SGPA >= Declining SGPA
        assertTrue(imp.getPredictedSgpa() >= con.getPredictedSgpa());
        assertTrue(con.getPredictedSgpa() >= dec.getPredictedSgpa());

        // Total predicted SEE marks must strictly increase for Improved and decrease for Declining
        assertTrue(imp.getTotalPredictedSeeMarks() > con.getTotalPredictedSeeMarks(),
                "Improved total SEE marks (" + imp.getTotalPredictedSeeMarks() + ") should be > Consistent (" + con.getTotalPredictedSeeMarks() + ")");
        assertTrue(dec.getTotalPredictedSeeMarks() < con.getTotalPredictedSeeMarks(),
                "Declining total SEE marks (" + dec.getTotalPredictedSeeMarks() + ") should be < Consistent (" + con.getTotalPredictedSeeMarks() + ")");

        // Marks increase count must be > 0
        assertTrue(imp.getSubjectsWithMarksIncrease() > 0, "Improved scenario should increase marks in several subjects");
        assertTrue(dec.getSubjectsWithMarksDecrease() > 0, "Declining scenario should decrease marks in several subjects");
    }

    @Test
    void verifiesAveragePerformerGradeElevationInImprovedScenario() {
        // Average performer with ~68-72% marks sitting right below B+ / A thresholds
        EnumMap<Subject, SubjectAssessment> int1 = new EnumMap<>(Subject.class);
        EnumMap<Subject, SubjectAssessment> int2 = new EnumMap<>(Subject.class);

        for (Subject s : Subject.values()) {
            int exam1 = (int) Math.round(s.getMaximumExamMarks() * 0.68);
            int exam2 = (int) Math.round(s.getMaximumExamMarks() * 0.72);
            int1.put(s, new SubjectAssessment(exam1, List.of(4, 4, 3), List.of(4, 3, 4)));
            int2.put(s, new SubjectAssessment(exam2, List.of(4, 4, 4), List.of(4, 4, 4)));
        }

        Student student = new Student("Average Student", "1602-25-733-050", 7.00, 7.10, 7.20, 82.0, int1, int2);
        StudentPredictionResult result = engine.predict(student, 15.0, 15.0, null);

        ScenarioPrediction con = result.getScenarios().get("CONSISTENT");
        ScenarioPrediction imp = result.getScenarios().get("IMPROVED");
        ScenarioPrediction dec = result.getScenarios().get("DECLINING");

        // For an average student with 15% headroom boost, marks cross the 70% cutoff (B -> B+)
        assertTrue(imp.getPredictedSgpa() > con.getPredictedSgpa(),
                "For average performer, Improved SGPA (" + imp.getPredictedSgpa() + ") should exceed Consistent (" + con.getPredictedSgpa() + ")");
        assertTrue(imp.getSubjectsWithGradeIncrease() > 0, "Grade increase count should be > 0 for average performer");
        assertTrue(dec.getPredictedSgpa() < con.getPredictedSgpa(),
                "Declining SGPA (" + dec.getPredictedSgpa() + ") should be lower than Consistent (" + con.getPredictedSgpa() + ")");
    }

    @Test
    void verifiesLowPerformerVulnerabilityAndProtection() {
        // Low performer with ~45% marks (near passing threshold 40% / 50%)
        EnumMap<Subject, SubjectAssessment> int1 = new EnumMap<>(Subject.class);
        EnumMap<Subject, SubjectAssessment> int2 = new EnumMap<>(Subject.class);

        for (Subject s : Subject.values()) {
            int exam1 = (int) Math.round(s.getMaximumExamMarks() * 0.45);
            int exam2 = (int) Math.round(s.getMaximumExamMarks() * 0.42);
            int1.put(s, new SubjectAssessment(exam1, List.of(2, 2, 2), List.of(2, 2, 1)));
            int2.put(s, new SubjectAssessment(exam2, List.of(2, 1, 2), List.of(2, 1, 2)));
        }

        Student student = new Student("Struggling Student", "1602-25-733-090", 5.00, 5.10, 5.20, 68.0, int1, int2);
        StudentPredictionResult result = engine.predict(student, 15.0, 15.0, null);

        ScenarioPrediction con = result.getScenarios().get("CONSISTENT");
        ScenarioPrediction imp = result.getScenarios().get("IMPROVED");
        ScenarioPrediction dec = result.getScenarios().get("DECLINING");

        assertTrue(imp.getPredictedSgpa() > con.getPredictedSgpa());
        assertTrue(con.getPredictedSgpa() > dec.getPredictedSgpa());
        assertTrue(imp.getTotalPredictedSeeMarks() > con.getTotalPredictedSeeMarks());
        assertTrue(con.getTotalPredictedSeeMarks() > dec.getTotalPredictedSeeMarks());
    }
}
