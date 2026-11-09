package studentperformance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studentperformance.syllabus.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SyllabusValidatorTest {

    private SyllabusValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SyllabusValidator();
    }

    private ExtractedCurriculum createValidCurriculum() {
        ExtractedCurriculum c = new ExtractedCurriculum();
        c.setDocument(new ExtractedDocumentMetadata(
                "Vasavi College of Engineering (Autonomous)",
                "R-25",
                "2026-27",
                "Computer Science & Engineering",
                "III"
        ));

        // 1. Data Structures (4 credits, 5 units)
        c.getSubjects().add(createSubject("Data Structures", "UI25PC310CS", "4", "THEORY", 5, 2));

        // 2. OOP through Java (3 credits, 5 units)
        c.getSubjects().add(createSubject("Object Oriented Programming through Java", "UI25PC320CS", "3", "THEORY", 5, 3));

        // 3. Computer Architecture (3 credits, 5 units)
        c.getSubjects().add(createSubject("Computer Architecture", "UI25PC330CS", "3", "THEORY", 5, 4));

        // 4. TTPS (4 credits, 5 units)
        c.getSubjects().add(createSubject("Transform Techniques, Probability & Statistics", "U25BS340MA", "4", "THEORY", 5, 4));

        // 5. Open Elective-I (2 credits, 0 units - unresolved)
        ExtractedSubject oe = new ExtractedSubject("Open Elective-I");
        oe.setCourseCode("U25OE3XXXX");
        oe.setCredits("2");
        oe.setCategory("OPEN_ELECTIVE");
        oe.setOpenElective(true);
        oe.setStartPage(5);
        oe.setEndPage(5);
        c.getSubjects().add(oe);

        // 6. Critical Thinking (1 credit, 4 units)
        c.getSubjects().add(createSubject("Critical Thinking", "U25HS310EH", "1", "THEORY", 4, 5));

        // 7. HVPE-II (1 credit, 3 units)
        c.getSubjects().add(createSubject("Human Values and Professional Ethics-II", "U25HS030EH", "1", "THEORY", 3, 5));

        // 8. Communication Skills-I (1 credit, 4 units)
        c.getSubjects().add(createSubject("Skill Development Course-III: Communication Skills-I", "U25HS320EH", "1", "SKILL_DEVELOPMENT", 4, 6));

        return c;
    }

    private ExtractedSubject createSubject(String name, String code, String credits, String category, int unitCount, int page) {
        ExtractedSubject s = new ExtractedSubject(name);
        s.setCourseCode(code);
        s.setCredits(credits);
        s.setCategory(category);
        s.setStartPage(page);
        s.setEndPage(page);

        for (int u = 1; u <= unitCount; u++) {
            ExtractedUnit unit = new ExtractedUnit(u, name + " Unit " + u, page);
            unit.getTopics().add(new ExtractedTopic("Topic 1 for unit " + u, page));
            unit.getTopics().add(new ExtractedTopic("Topic 2 for unit " + u, page));
            s.getUnits().add(unit);
        }
        return s;
    }

    @Test
    void testAllEightSubjectsPresentAndValid19Credits() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        ValidationReport report = validator.validate(curriculum, true);

        System.out.println(report.formatConsoleSummary());

        assertTrue(report.isValid(), "Clean full curriculum must be valid");
        assertEquals(ValidationReport.OverallStatus.PASSED, report.getOverallStatus());
        assertEquals(8, report.getSubjectCount());
        assertEquals(8, report.getMatchedSubjectCount());
        assertEquals(0, report.getUnresolvedSubjectCount());
        assertEquals(19, report.getTotalCredits(), "Total credits must equal 19");
        assertEquals(31, report.getTotalUnits(), "Total units must equal 31 (5+5+5+5+0+4+3+4)");
        assertTrue(report.getErrors().isEmpty(), "Report must have 0 errors");
    }

    @Test
    void testMissingSubjectDetected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        // Remove Computer Architecture
        curriculum.getSubjects().removeIf(s -> s.getSubjectName().contains("Architecture"));

        ValidationReport report = validator.validate(curriculum, true);
        assertFalse(report.isValid());
        assertEquals(ValidationReport.OverallStatus.FAILED, report.getOverallStatus());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("Computer Architecture")),
                "Must report missing Computer Architecture");
    }

    @Test
    void testDuplicateCourseCodeDetected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        // Set Java course code equal to DS course code
        curriculum.getSubjects().get(1).setCourseCode("UI25PC310CS");

        ValidationReport report = validator.validate(curriculum, true);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("Duplicate course code 'UI25PC310CS'")),
                "Must flag duplicate course code");
    }

    @Test
    void testIncorrectCreditsDetected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        // Change Data Structures credits from 4 to 3
        curriculum.getSubjects().get(0).setCredits("3");

        ValidationReport report = validator.validate(curriculum, true);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("Credit mismatch for Data Structures")),
                "Must report credit mismatch for Data Structures");
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("Total semester credits mismatch")),
                "Must report total credit mismatch");
    }

    @Test
    void testMissingUnitDetected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        // Remove unit 3 from Data Structures (leaving units 1, 2, 4, 5)
        curriculum.getSubjects().get(0).getUnits().removeIf(u -> u.getUnitNumber() == 3);

        ValidationReport report = validator.validate(curriculum, true);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("Missing Unit 3 in Data Structures")),
                "Must flag missing unit 3");
    }

    @Test
    void testDuplicateUnitDetected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        // Add a duplicate unit 2 to Data Structures
        ExtractedSubject ds = curriculum.getSubjects().get(0);
        ExtractedUnit dupUnit = new ExtractedUnit(2, "Duplicate Unit 2", 2);
        dupUnit.getTopics().add(new ExtractedTopic("Extra topic", 2));
        ds.getUnits().add(dupUnit);

        ValidationReport report = validator.validate(curriculum, true);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("Duplicate unit number 2 in Data Structures")),
                "Must flag duplicate unit number 2");
    }

    @Test
    void testDuplicateTopicsWithinUnitDetected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        // Add duplicate topic to Java Unit 1
        ExtractedUnit unit1 = curriculum.getSubjects().get(1).getUnits().get(0);
        unit1.getTopics().add(new ExtractedTopic("Topic 1 for unit 1", 3)); // duplicate

        ValidationReport report = validator.validate(curriculum, true);
        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("contains duplicate topic")),
                "Must flag duplicate topic within unit as a warning");
    }

    @Test
    void testEmptyTopicsDetected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        // Add an empty topic to CA Unit 1
        ExtractedUnit unit1 = curriculum.getSubjects().get(2).getUnits().get(0);
        unit1.getTopics().add(new ExtractedTopic("   ", 4)); // blank

        ValidationReport report = validator.validate(curriculum, true);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("empty topic text")),
                "Must flag empty topic text as error");
    }

    @Test
    void testEmptyUnitTopicListDetected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        // Clear topics from TTPS Unit 1
        curriculum.getSubjects().get(3).getUnits().get(0).getTopics().clear();

        ValidationReport report = validator.validate(curriculum, true);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("has no topics")),
                "Must flag unit with no topics as error");
    }

    @Test
    void testInvalidPageReferencesDetected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        // Set unit page number to null and its topic page to null
        ExtractedUnit unit = curriculum.getSubjects().get(0).getUnits().get(0);
        unit.setPageNumber(null);
        for (ExtractedTopic t : unit.getTopics()) {
            t.setPageNumber(null);
        }

        ValidationReport report = validator.validate(curriculum, true);
        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("missing a source page reference")),
                "Must flag missing unit page reference as a warning");
    }

    @Test
    void testUnresolvedOpenElectiveExpected() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        ExtractedSubject oe = curriculum.getSubjects().stream()
                .filter(ExtractedSubject::isOpenElective).findFirst().orElseThrow();

        assertEquals(0, oe.getUnits().size(), "OE must have 0 units");
        ValidationReport report = validator.validate(curriculum, true);

        // OE unresolved is expected and NOT an error
        assertTrue(report.isValid());
        SubjectValidationResult oeResult = report.getSubjectResults().stream()
                .filter(SubjectValidationResult::isOpenElective).findFirst().orElseThrow();
        assertTrue(oeResult.isValid(), "OE result must be valid despite 0 units");
        assertTrue(oeResult.getErrors().isEmpty());
    }

    @Test
    void testValidThreeUnitHvpeExtraction() {
        ExtractedCurriculum curriculum = createValidCurriculum();
        ExtractedSubject hvpe = curriculum.getSubjects().stream()
                .filter(s -> s.getSubjectName().contains("Human Values")).findFirst().orElseThrow();

        assertEquals(3, hvpe.getUnits().size(), "HVPE must have 3 units");
        assertEquals("1", hvpe.getCredits());

        ValidationReport report = validator.validate(curriculum, true);
        assertTrue(report.isValid());
        SubjectValidationResult hvpeResult = report.getSubjectResults().stream()
                .filter(r -> r.getMatchedSubject() == Subject.HVPE).findFirst().orElseThrow();
        assertTrue(hvpeResult.isValid(), "3-unit HVPE must be completely valid");
        assertEquals(3, hvpeResult.getUnitCount());
        assertEquals(1, hvpeResult.getExpectedCredits());
        assertEquals(1, hvpeResult.getExtractedCredits());
    }

    @Test
    void testLiveOfficialPdfExtractionAndValidation() throws Exception {
        Path officialPdf = Paths.get("C:/Users/tejaj/Downloads/Vasavi_CSE_Semester_III_Subject_Units_and_Topics.pdf");
        if (!Files.exists(officialPdf)) {
            System.out.println("Official PDF not found, skipping live PDF validation test.");
            return;
        }

        GeminiSyllabusExtractor extractor = new GeminiSyllabusExtractor();
        ExtractedCurriculum curriculum = extractor.extract(officialPdf);
        assertNotNull(curriculum);

        ValidationReport report = validator.validate(curriculum, true);
        System.out.println(report.formatConsoleSummary());

        // Assertions on the real PDF validation report
        assertTrue(report.isValid(), "Official syllabus extraction must pass Java validation");
        assertEquals(8, report.getSubjectCount(), "Must have 8 subjects");
        assertEquals(8, report.getMatchedSubjectCount(), "All 8 subjects must match official catalog");
        assertEquals(19, report.getTotalCredits(), "Total credits must equal 19");
        assertEquals(31, report.getTotalUnits(), "Total units must equal 31");
        assertTrue(report.getTotalTopics() >= 90, "Total topics should be >= 90");
        assertTrue(report.getErrors().isEmpty(), "Official syllabus must have 0 errors");
    }
}
