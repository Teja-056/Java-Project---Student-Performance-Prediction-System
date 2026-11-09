package studentperformance;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studentperformance.syllabus.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GeminiSyllabusExtractorTest {

    private GeminiSyllabusExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new GeminiSyllabusExtractor();
    }

    @Test
    void testExtractFromSmallOfficialSyllabusSection() throws Exception {
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 13);
                cs.newLineAtOffset(50, 720);
                cs.showText("VASAVI COLLEGE OF ENGINEERING (AUTONOMOUS)");
                cs.newLineAtOffset(0, -20);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.showText("B.E. Computer Science & Engineering - III Semester | R-25 Scheme | Academic Year 2026-27");
                cs.newLineAtOffset(0, -30);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.showText("1. DATA STRUCTURES");
                cs.newLineAtOffset(0, -18);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("UI25PC310CS | 4 credits | CIE 40 | SEE 60");
                cs.newLineAtOffset(0, -20);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                cs.showText("UNIT I - Basic Concepts and Arrays");
                cs.newLineAtOffset(0, -16);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("Algorithm specification: introduction and recursive algorithms.");
                cs.newLineAtOffset(0, -14);
                cs.showText("Performance analysis: time complexity and space complexity.");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                cs.showText("UNIT II - Stacks, Queues and Linked Lists");
                cs.newLineAtOffset(0, -16);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("Stacks; stacks using dynamic arrays.");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        ExtractedCurriculum result = extractor.extractFromBytes(pdfBytes, "small_sample.pdf");
        if (result == null || result.getSubjects().isEmpty()) {
            // Live LLM retry against Google Gemini API in case of network throttle
            Thread.sleep(2500);
            result = extractor.extractFromBytes(pdfBytes, "small_sample.pdf");
        }
        assertNotNull(result);
        assertNotNull(result.getDocument());
        System.out.println("Extracted Document Metadata: " + result.getDocument());
        System.out.println("Extracted Subjects count: " + result.getSubjects().size());

        assertFalse(result.getSubjects().isEmpty(), "Must extract at least 1 subject");
        ExtractedSubject ds = result.getSubjects().get(0);
        assertTrue(ds.getSubjectName().toUpperCase().contains("DATA STRUCTURES"));
        assertEquals("UI25PC310CS", ds.getCourseCode());
        assertEquals(2, ds.getUnits().size());
        assertEquals("Basic Concepts and Arrays", ds.getUnits().get(0).getUnitTitle());
        assertEquals(1, ds.getUnits().get(0).getUnitNumber());
        assertEquals(1, ds.getUnits().get(0).getPageNumber());
        assertTrue(ds.getUnits().get(0).getTopics().stream().anyMatch(t -> t.getTopicText().contains("Algorithm specification")));
    }

    @Test
    void testExtractCompleteSemesterIIICseSyllabusPdf() throws Exception {
        Path officialPdf = Paths.get("C:/Users/tejaj/Downloads/Vasavi_CSE_Semester_III_Subject_Units_and_Topics.pdf");
        if (!Files.exists(officialPdf)) {
            System.out.println("Official PDF not found at " + officialPdf + ", skipping live full document test.");
            return;
        }

        System.out.println("Extracting full official syllabus: " + officialPdf);
        long start = System.currentTimeMillis();
        ExtractedCurriculum curriculum = extractor.extract(officialPdf);
        long duration = System.currentTimeMillis() - start;

        assertNotNull(curriculum);
        System.out.println("Extraction finished in " + duration + " ms.");

        // Generate and print verification report
        ExtractionVerificationReport report = extractor.generateVerificationReport(officialPdf.getFileName().toString(), curriculum);
        System.out.println(report.formatConsoleSummary());

        // Document level assertions
        assertNotNull(curriculum.getDocument().getInstitution());
        assertTrue(curriculum.getDocument().getInstitution().toUpperCase().contains("VASAVI"), "Institution should contain Vasavi");
        assertEquals("III", curriculum.getDocument().getSemester().trim());

        // Verify subjects count (Expected: 8 subjects)
        assertEquals(8, curriculum.getSubjects().size(), "Must extract all 8 Semester III CSE subjects");

        // Verify specific subjects
        // 1. Data Structures
        ExtractedSubject ds = curriculum.getSubjects().stream()
                .filter(s -> s.getSubjectName().toUpperCase().contains("DATA STRUCTURES"))
                .findFirst().orElseThrow(() -> new AssertionError("Data Structures missing"));
        assertEquals("UI25PC310CS", ds.getCourseCode());
        assertEquals("4", ds.getCredits().trim());
        assertEquals(5, ds.getUnits().size(), "DS must have exactly 5 units");

        // 2. OOP through Java
        ExtractedSubject java = curriculum.getSubjects().stream()
                .filter(s -> s.getSubjectName().toUpperCase().contains("JAVA"))
                .findFirst().orElseThrow(() -> new AssertionError("OOP through Java missing"));
        assertEquals("UI25PC320CS", java.getCourseCode());
        assertEquals("3", java.getCredits().trim());
        assertEquals(5, java.getUnits().size(), "Java must have exactly 5 units");

        // 3. Computer Architecture
        ExtractedSubject ca = curriculum.getSubjects().stream()
                .filter(s -> s.getSubjectName().toUpperCase().contains("ARCHITECTURE"))
                .findFirst().orElseThrow(() -> new AssertionError("Computer Architecture missing"));
        assertEquals("UI25PC330CS", ca.getCourseCode());
        assertEquals("3", ca.getCredits().trim());
        assertEquals(5, ca.getUnits().size(), "CA must have exactly 5 units");

        // 4. TTPS
        ExtractedSubject ttps = curriculum.getSubjects().stream()
                .filter(s -> s.getSubjectName().toUpperCase().contains("TRANSFORM") || s.getSubjectName().toUpperCase().contains("PROBABILITY"))
                .findFirst().orElseThrow(() -> new AssertionError("TTPS missing"));
        assertEquals("U25BS340MA", ttps.getCourseCode());
        assertEquals("4", ttps.getCredits().trim());
        assertEquals(5, ttps.getUnits().size(), "TTPS must have exactly 5 units");

        // 5. Open Elective-I
        ExtractedSubject oe = curriculum.getSubjects().stream()
                .filter(ExtractedSubject::isOpenElective)
                .findFirst().orElseThrow(() -> new AssertionError("Open Elective-I missing"));
        assertEquals("2", oe.getCredits().trim());
        assertTrue(oe.getUnits().isEmpty(), "Open Elective-I must have EMPTY units list (unresolved)");

        // 6. Critical Thinking
        ExtractedSubject ct = curriculum.getSubjects().stream()
                .filter(s -> s.getSubjectName().toUpperCase().contains("CRITICAL THINKING"))
                .findFirst().orElseThrow(() -> new AssertionError("Critical Thinking missing"));
        assertEquals("U25HS310EH", ct.getCourseCode());
        assertEquals("1", ct.getCredits().trim());
        assertEquals(4, ct.getUnits().size(), "Critical Thinking must have 4 units");

        // 7. Human Values
        ExtractedSubject hvpe = curriculum.getSubjects().stream()
                .filter(s -> s.getSubjectName().toUpperCase().contains("HUMAN VALUES") || s.getSubjectName().toUpperCase().contains("ETHICS"))
                .findFirst().orElseThrow(() -> new AssertionError("Human Values missing"));
        assertEquals("U25HS030EH", hvpe.getCourseCode());
        assertEquals("1", hvpe.getCredits().trim());
        assertEquals(3, hvpe.getUnits().size(), "Human Values must have 3 units");

        // 8. Communication Skills-I
        ExtractedSubject cs = curriculum.getSubjects().stream()
                .filter(s -> s.getSubjectName().toUpperCase().contains("COMMUNICATION SKILLS"))
                .findFirst().orElseThrow(() -> new AssertionError("Communication Skills-I missing"));
        assertEquals("U25HS320EH", cs.getCourseCode());
        assertEquals("1", cs.getCredits().trim());
        assertEquals(4, cs.getUnits().size(), "Communication Skills-I must have 4 units");

        // Total units across all subjects: 5 + 5 + 5 + 5 + 0 + 4 + 3 + 4 = 31 units!
        assertEquals(31, curriculum.getTotalUnitsCount(), "Total units count must equal exactly 31");
        assertTrue(curriculum.getTotalTopicsCount() > 80, "Total topics should be > 80");

        // Page references check
        for (ExtractedSubject s : curriculum.getSubjects()) {
            assertNotNull(s.getStartPage(), "Start page must not be null for " + s.getSubjectName());
            for (ExtractedUnit u : s.getUnits()) {
                assertNotNull(u.getPageNumber(), "Unit page number should not be null for " + s.getSubjectName() + " Unit " + u.getUnitNumber());
                assertTrue(u.getPageNumber() >= 1 && u.getPageNumber() <= 7, "Page number must be within 1..7");
            }
        }
    }

    @Test
    void testErrorHandlingForInvalidPdfs() {
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(null));
        assertThrows(IOException.class, () -> extractor.extract(Paths.get("non_existent_file.pdf")));

        Path txtFile = Paths.get("temp_test.txt");
        try {
            Files.writeString(txtFile, "Not a PDF");
            assertThrows(IllegalArgumentException.class, () -> extractor.extract(txtFile));
        } catch (IOException ignored) {
        } finally {
            try { Files.deleteIfExists(txtFile); } catch (Exception ignored) {}
        }
    }

    @Test
    void testVerificationReportDetectsMissingUnitsAndDuplicates() {
        ExtractedCurriculum curr = new ExtractedCurriculum();
        ExtractedSubject ds = new ExtractedSubject("DATA STRUCTURES");
        ds.setCourseCode("WRONG_CODE_123");
        ds.setCredits("4");
        // Only 3 units (missing 4 and 5)
        ds.getUnits().add(new ExtractedUnit(1, "Unit 1", 2));
        ds.getUnits().add(new ExtractedUnit(2, "Unit 2", 2));
        ds.getUnits().add(new ExtractedUnit(3, "Unit 3", 2));

        // Add duplicate topic
        ExtractedUnit u1 = ds.getUnits().get(0);
        u1.getTopics().add(new ExtractedTopic("Duplicate Topic"));
        u1.getTopics().add(new ExtractedTopic("Duplicate Topic"));

        curr.getSubjects().add(ds);

        ExtractionVerificationReport report = extractor.generateVerificationReport("test.pdf", curr);
        assertNotNull(report);
        assertEquals(1, report.getSubjectEntries().size());
        var entry = report.getSubjectEntries().get(0);

        // Missing units check
        assertTrue(entry.missingUnits().contains("Unit 4"));
        assertTrue(entry.missingUnits().contains("Unit 5"));

        // Duplicate topics check
        assertEquals(1, entry.duplicateTopics().size());
        assertTrue(entry.duplicateTopics().get(0).contains("Duplicate Topic"));

        // Discrepancies check
        assertFalse(report.getDiscrepancies().isEmpty());
        assertTrue(report.getDiscrepancies().get(0).contains("WRONG_CODE_123"));
    }
}
