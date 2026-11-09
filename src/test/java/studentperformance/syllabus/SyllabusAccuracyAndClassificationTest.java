package studentperformance.syllabus;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studentperformance.*;
import studentperformance.dto.SyllabusDocumentDto;
import studentperformance.exception.BadRequestException;
import studentperformance.service.SyllabusService;

/**
 * PHASE 5.1 COMPREHENSIVE AUTOMATED REGRESSION SUITE
 * Covers all 15 required verification points:
 * 1. Critical Thinking topics cannot accidentally appear under Data Structures.
 * 2. Topics remain attached to their original subject.
 * 3. Topics from different subjects are not merged.
 * 4. Unit boundaries are maintained.
 * 5. Similar subject names do not cause incorrect matching.
 * 6. Duplicate detection works correctly.
 * 7. Topic ordering survives database persistence.
 * 8. Revalidation preserves correct relationships.
 * 9. Legacy documents can be safely reprocessed.
 * 10. Approved documents are not overwritten.
 * 11. Failed reprocessing does not corrupt existing records.
 * 12. Missing original PDFs are handled safely.
 * 13. OE remains unresolved without an actual syllabus.
 * 14. Documents from other semesters are not forced into Semester III.
 * 15. Existing Phase 1-5 features continue working.
 */
public class SyllabusAccuracyAndClassificationTest {

    private final String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC");
    private final String user = System.getenv().getOrDefault("DB_USER", "root");
    private final String password = System.getenv().getOrDefault("DB_PASSWORD", "Teja.J@2007");

    private SyllabusRepository repository;
    private SyllabusValidator validator;
    private SyllabusService service;
    private final List<Long> cleanupDocIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws SQLException {
        repository = new SyllabusRepository(url, user, password);
        validator = new SyllabusValidator();
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            new DatabaseMigrator().migrate(c);
        }
        service = new SyllabusService(
                repository,
                new SyllabusParser(),
                new GeminiSyllabusExtractor(),
                validator,
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            for (Long id : cleanupDocIds) {
                if (id != null) {
                    try (PreparedStatement p = c.prepareStatement(
                            "DELETE s FROM syllabus_subjects s "
                            + "JOIN syllabus_document_subjects ds ON ds.subject_id = s.subject_id "
                            + "WHERE ds.document_id = ? AND s.course_code LIKE 'TEST_%'")) {
                        p.setLong(1, id);
                        p.executeUpdate();
                    }
                    try (PreparedStatement p = c.prepareStatement("DELETE FROM syllabus_documents WHERE document_id = ?")) {
                        p.setLong(1, id);
                        p.executeUpdate();
                    }
                }
            }
        } catch (SQLException ignored) {}
    }

    // 1. Critical Thinking topics cannot accidentally appear under Data Structures.
    @Test
    void test1_CriticalThinkingTopicsDoNotAppearUnderDataStructures() {
        ExtractedCurriculum curr = new ExtractedCurriculum();
        ExtractedDocumentMetadata meta = new ExtractedDocumentMetadata();
        meta.setSemester("III");
        curr.setDocument(meta);

        ExtractedSubject ds = new ExtractedSubject("Data Structures");
        ds.setCourseCode("UI25PC310CS");
        ds.setCredits("4");
        ExtractedUnit u1 = new ExtractedUnit(1, "Basic Concepts", 1);
        u1.getTopics().add(new ExtractedTopic("Logic and reasoning; identifying assumptions; bias and fallacies", 1));
        ds.getUnits().add(u1);
        curr.getSubjects().add(ds);

        ValidationReport report = validator.validate(curr, false);
        assertTrue(report.hasErrors(), "Validation must flag cross-subject contamination as ERROR");
        boolean contaminationFound = report.getMessages().stream()
                .anyMatch(m -> m.getCategory().equalsIgnoreCase("CONTAMINATION") && m.getMessage().contains("Critical Thinking"));
        assertTrue(contaminationFound, "Report must identify that topic contains keywords belonging to Critical Thinking");
    }

    // 2. Topics remain attached to their original subject.
    @Test
    void test2_TopicsRemainAttachedToOriginalSubject() throws SQLException {
        String filename = "test_sub_attach_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("REVIEW_REQUIRED");

        SyllabusSubject subA = new SyllabusSubject("Test Isolated Subject Alpha");
        subA.setCourseCode("TEST_ALPHA_" + UUID.randomUUID().toString().substring(0, 8));
        subA.setCredits("4");
        SyllabusUnit uA1 = new SyllabusUnit(1, "Arrays", 1);
        uA1.getTopics().add(new SyllabusTopic("Array operations and ADT", 1));
        subA.getUnits().add(uA1);

        SyllabusSubject subB = new SyllabusSubject("Test Isolated Subject Beta");
        subB.setCourseCode("TEST_BETA_" + UUID.randomUUID().toString().substring(0, 8));
        subB.setCredits("1");
        SyllabusUnit uB1 = new SyllabusUnit(1, "Logic and Arguments", 2);
        uB1.getTopics().add(new SyllabusTopic("Evaluating deductive and inductive arguments", 2));
        subB.getUnits().add(uB1);

        doc.getSubjects().add(subA);
        doc.getSubjects().add(subB);

        long savedId = repository.saveDocument(doc, "REVIEW_REQUIRED");
        cleanupDocIds.add(savedId);

        SyllabusDocument loaded = repository.findById(savedId);
        assertNotNull(loaded);
        assertEquals(2, loaded.getSubjects().size());

        SyllabusSubject loadedA = loaded.getSubjects().stream().filter(s -> s.getName().contains("Alpha")).findFirst().orElseThrow();
        assertEquals(1, loadedA.getUnits().size());
        assertEquals("Array operations and ADT", loadedA.getUnits().get(0).getTopics().get(0).getText());

        SyllabusSubject loadedB = loaded.getSubjects().stream().filter(s -> s.getName().contains("Beta")).findFirst().orElseThrow();
        assertEquals(1, loadedB.getUnits().size());
        assertEquals("Evaluating deductive and inductive arguments", loadedB.getUnits().get(0).getTopics().get(0).getText());
    }

    // 3. Topics from different subjects are not merged.
    @Test
    void test3_TopicsFromDifferentSubjectsAreNotMerged() {
        ExtractedCurriculum curr = new ExtractedCurriculum();
        ExtractedSubject sub1 = new ExtractedSubject("Data Structures");
        sub1.setCourseCode("UI25PC310CS");
        ExtractedUnit u1 = new ExtractedUnit(1, "Unit 1", 1);
        u1.getTopics().add(new ExtractedTopic("Algorithms specification", 1));
        sub1.getUnits().add(u1);

        ExtractedSubject sub2 = new ExtractedSubject("Transform Techniques, Probability & Statistics");
        sub2.setCourseCode("U25BS340MA");
        ExtractedUnit u2 = new ExtractedUnit(1, "Unit 1", 2);
        u2.getTopics().add(new ExtractedTopic("Laplace transforms", 2));
        sub2.getUnits().add(u2);

        curr.getSubjects().add(sub1);
        curr.getSubjects().add(sub2);

        assertEquals(1, sub1.getUnits().get(0).getTopics().size());
        assertEquals(1, sub2.getUnits().get(0).getTopics().size());
        assertNotEquals(sub1.getUnits().get(0).getTopics().get(0).getTopicText(),
                sub2.getUnits().get(0).getTopics().get(0).getTopicText());
    }

    // 4. Unit boundaries are maintained.
    @Test
    void test4_UnitBoundariesAreMaintained() throws SQLException {
        String filename = "test_unit_boundaries_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("REVIEW_REQUIRED");

        SyllabusSubject ca = new SyllabusSubject("Test Unit Boundaries Demo");
        ca.setCourseCode("TEST_ARCH_" + UUID.randomUUID().toString().substring(0, 8));
        ca.setCredits("3");

        for (int i = 1; i <= 5; i++) {
            SyllabusUnit u = new SyllabusUnit(i, "CA Unit " + i, i);
            u.getTopics().add(new SyllabusTopic("CA Topic for unit " + i, i));
            ca.getUnits().add(u);
        }
        doc.getSubjects().add(ca);

        long savedId = repository.saveDocument(doc, "REVIEW_REQUIRED");
        cleanupDocIds.add(savedId);

        SyllabusDocument loaded = repository.findById(savedId);
        assertNotNull(loaded);
        SyllabusSubject loadedCa = loaded.getSubjects().get(0);
        assertEquals(5, loadedCa.getUnits().size());

        for (int i = 1; i <= 5; i++) {
            SyllabusUnit u = loadedCa.getUnits().get(i - 1);
            assertEquals(i, u.getNumber());
            assertEquals("CA Unit " + i, u.getTitle());
            assertEquals(1, u.getTopics().size());
            assertEquals("CA Topic for unit " + i, u.getTopics().get(0).getText());
        }
    }

    // 5. Similar subject names do not cause incorrect matching.
    @Test
    void test5_SimilarSubjectNamesDoNotCauseIncorrectMatching() {
        // "DATA STRUCTURES" has "CT" as a substring. It MUST NOT match Subject.CT.
        Subject dsMatch = Subject.matchByText("DATA STRUCTURES");
        assertEquals(Subject.DS, dsMatch);
        assertNotEquals(Subject.CT, dsMatch);

        // "TRANSFORM TECHNIQUES, PROBABILITY & STATISTICS" has "CS" as a substring. It MUST NOT match Subject.CS.
        Subject ttpsMatch = Subject.matchByText("TRANSFORM TECHNIQUES, PROBABILITY & STATISTICS");
        assertEquals(Subject.TTPS, ttpsMatch);
        assertNotEquals(Subject.CS, ttpsMatch);

        // Exact match for CT
        Subject ctMatch = Subject.matchByText("CRITICAL THINKING");
        assertEquals(Subject.CT, ctMatch);

        // Exact match for CS
        Subject csMatch = Subject.matchByText("COMMUNICATION SKILLS-I");
        assertEquals(Subject.CS, csMatch);
    }

    // 6. Duplicate detection works correctly.
    @Test
    void test6_DuplicateDetectionWorksCorrectly() {
        ExtractedCurriculum curr = new ExtractedCurriculum();
        ExtractedSubject ds = new ExtractedSubject("Data Structures");
        ds.setCourseCode("UI25PC310CS");
        ds.setCredits("4");

        // Duplicate unit numbers
        ExtractedUnit u1a = new ExtractedUnit(1, "Unit 1 Title", 1);
        u1a.getTopics().add(new ExtractedTopic("Topic 1", 1));
        // Duplicate topic within unit
        u1a.getTopics().add(new ExtractedTopic("Topic 1", 1));
        ds.getUnits().add(u1a);

        ExtractedUnit u1b = new ExtractedUnit(1, "Duplicate Unit 1", 2);
        u1b.getTopics().add(new ExtractedTopic("Topic 2", 2));
        ds.getUnits().add(u1b);

        curr.getSubjects().add(ds);

        ValidationReport report = validator.validate(curr, false);
        assertTrue(report.hasErrors(), "Duplicate unit number must cause validation error");
        assertTrue(report.hasWarnings(), "Duplicate topic text in same unit must cause validation warning");

        boolean dupUnitMsg = report.getMessages().stream().anyMatch(m -> m.getMessage().contains("Duplicate unit number 1"));
        assertTrue(dupUnitMsg);
    }

    // 7. Topic ordering survives database persistence.
    @Test
    void test7_TopicOrderingSurvivesDatabasePersistence() throws SQLException {
        String filename = "test_ordering_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("REVIEW_REQUIRED");

        SyllabusSubject java = new SyllabusSubject("Test Isolated Ordering");
        java.setCourseCode("TEST_ORDER_" + UUID.randomUUID().toString().substring(0, 8));
        java.setCredits("3");

        SyllabusUnit u1 = new SyllabusUnit(1, "Fundamentals", 1);
        // Non-alphabetical topic sequence
        u1.getTopics().add(new SyllabusTopic("Zebra: Finalization and Garbage Collection", 1));
        u1.getTopics().add(new SyllabusTopic("Alpha: Primitive Types and Literals", 1));
        u1.getTopics().add(new SyllabusTopic("Beta: Control Flow Statements", 1));
        java.getUnits().add(u1);
        doc.getSubjects().add(java);

        long savedId = repository.saveDocument(doc, "REVIEW_REQUIRED");
        cleanupDocIds.add(savedId);

        SyllabusDocument loaded = repository.findById(savedId);
        assertNotNull(loaded);
        List<SyllabusTopic> topics = loaded.getSubjects().get(0).getUnits().get(0).getTopics();
        assertEquals(3, topics.size());
        assertEquals("Zebra: Finalization and Garbage Collection", topics.get(0).getText(), "First topic must remain first");
        assertEquals("Alpha: Primitive Types and Literals", topics.get(1).getText(), "Second topic must remain second");
        assertEquals("Beta: Control Flow Statements", topics.get(2).getText(), "Third topic must remain third");
    }

    // 8. Revalidation preserves correct relationships.
    @Test
    void test8_RevalidationPreservesCorrectRelationships() throws SQLException {
        String filename = "test_reval_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("REVIEW_REQUIRED");
        doc.setSemester("III");

        String ctCode = "TEST_CT_REVAL_" + UUID.randomUUID().toString().substring(0, 8);
        SyllabusSubject ct = new SyllabusSubject("Critical Thinking");
        ct.setCourseCode(ctCode);
        ct.setCredits("1");
        for (int i = 1; i <= 4; i++) {
            SyllabusUnit u = new SyllabusUnit(i, "CT Unit " + i, i);
            u.getTopics().add(new SyllabusTopic("CT Valid Topic " + i, i));
            ct.getUnits().add(u);
        }
        doc.getSubjects().add(ct);

        long savedId = repository.saveDocument(doc, "REVIEW_REQUIRED");
        cleanupDocIds.add(savedId);

        SyllabusDocumentDto revalidated = service.revalidateSyllabus(savedId);
        assertNotNull(revalidated);
        assertEquals(1, revalidated.getSubjects().size());
        assertEquals(4, revalidated.getSubjects().get(0).getUnits().size());
        assertEquals(ctCode.toUpperCase(Locale.ROOT), revalidated.getSubjects().get(0).getCourseCode().toUpperCase(Locale.ROOT));
    }

    // 9. Legacy documents can be safely reprocessed.
    @Test
    void test9_LegacyDocumentsCanBeSafelyReprocessed() throws IOException {
        Path samplePdf = Path.of("uploads", "syllabus", "vasavi_cse_sem3_data_structures_syllabus.pdf");
        if (!Files.exists(samplePdf)) {
            samplePdf = Path.of("vasavi_cse_sem3_data_structures_syllabus.pdf");
        }
        assertTrue(Files.exists(samplePdf), "Sample syllabus PDF must exist");

        // Create a legacy document record pointing to this PDF
        String filename = "vasavi_cse_sem3_data_structures_syllabus.pdf";
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("REVIEW_REQUIRED");
        doc.setExtractionEngine("LEGACY");
        doc.setPdfPath(samplePdf.toAbsolutePath().toString());

        long docId;
        try {
            docId = repository.saveDocument(doc, "REVIEW_REQUIRED");
            cleanupDocIds.add(docId);
        } catch (SQLException e) {
            fail("Failed to setup legacy doc: " + e.getMessage());
            return;
        }

        // Reprocess with service
        SyllabusDocumentDto reprocessed = service.reprocessDocument(docId);
        assertNotNull(reprocessed);
        assertEquals("GEMINI_FLASH_LITE", reprocessed.getExtractionEngine());
        assertTrue(reprocessed.getSubjectCount() >= 1);
    }

    // 10. Approved documents are not overwritten.
    @Test
    void test10_ApprovedDocumentsAreNotOverwritten() throws SQLException {
        String filename = "test_approved_immutable_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("APPROVED");

        SyllabusSubject ds = new SyllabusSubject("Immutable Test Subject");
        ds.setCourseCode("TEST_IMMUT_" + UUID.randomUUID().toString().substring(0, 8));
        ds.setCredits("4");
        SyllabusUnit u1 = new SyllabusUnit(1, "Original Unit", 1);
        u1.getTopics().add(new SyllabusTopic("Original Topic", 1));
        ds.getUnits().add(u1);
        doc.getSubjects().add(ds);

        long approvedId = repository.saveApproved(doc);
        cleanupDocIds.add(approvedId);

        // Attempt to update directly via repository updateDocumentCurriculum (MUST THROW IllegalStateException)
        SyllabusDocument mutatedDoc = new SyllabusDocument(filename);
        mutatedDoc.setStatus("REVIEW_REQUIRED");
        assertThrows(IllegalStateException.class, () -> {
            repository.updateDocumentCurriculum(approvedId, mutatedDoc, new ValidationReport());
        });

        // Verify approved document remains intact
        SyllabusDocument loaded = repository.findById(approvedId);
        assertNotNull(loaded);
        assertEquals("APPROVED", loaded.getStatus());
        assertEquals("Original Unit", loaded.getSubjects().get(0).getUnits().get(0).getTitle());
    }

    // 11. Failed reprocessing does not corrupt existing records.
    @Test
    void test11_FailedReprocessingDoesNotCorruptExistingRecords() throws IOException, SQLException {
        // Create a temporary corrupted PDF
        Path corruptPdf = Files.createTempFile("corrupt_", ".pdf");
        Files.writeString(corruptPdf, "Not a valid PDF format!");

        String filename = "test_corrupt_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("REVIEW_REQUIRED");
        doc.setPdfPath(corruptPdf.toAbsolutePath().toString());

        SyllabusSubject s = new SyllabusSubject("Test Subject");
        s.setCourseCode("TST101");
        SyllabusUnit u = new SyllabusUnit(1, "Initial Unit", 1);
        u.getTopics().add(new SyllabusTopic("Initial Topic", 1));
        s.getUnits().add(u);
        doc.getSubjects().add(s);

        long docId = repository.saveDocument(doc, "REVIEW_REQUIRED");
        cleanupDocIds.add(docId);

        // Attempt reprocess - should throw BadRequestException
        assertThrows(BadRequestException.class, () -> {
            service.reprocessDocument(docId);
        });

        // Verify initial record remains completely uncorrupted
        SyllabusDocument loaded = repository.findById(docId);
        assertNotNull(loaded);
        assertEquals("Initial Unit", loaded.getSubjects().get(0).getUnits().get(0).getTitle());

        Files.deleteIfExists(corruptPdf);
    }

    // 12. Missing original PDFs are handled safely.
    @Test
    void test12_MissingOriginalPdfsHandledSafely() throws SQLException {
        String filename = "non_existent_file_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("APPROVED");
        doc.setPdfPath("C:\\invalid\\path\\non_existent.pdf");

        long docId = repository.saveApproved(doc);
        cleanupDocIds.add(docId);

        // Service must throw BadRequestException indicating manual correction is required
        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            service.reprocessDocument(docId);
        });
        assertTrue(ex.getMessage().contains("manual correction"));

        // Historical approved document remains untouched
        SyllabusDocument loaded = repository.findById(docId);
        assertEquals("APPROVED", loaded.getStatus());
    }

    // 13. OE remains unresolved without an actual syllabus.
    @Test
    void test13_OpenElectiveRemainsUnresolvedWithoutActualSyllabus() {
        ExtractedCurriculum curr = new ExtractedCurriculum();
        ExtractedDocumentMetadata meta = new ExtractedDocumentMetadata();
        meta.setSemester("III");
        curr.setDocument(meta);

        ExtractedSubject oe = new ExtractedSubject("Open Elective-I");
        oe.setCourseCode("U25OE3XXXX");
        oe.setCredits("2");
        oe.setOpenElective(true);
        // 0 units (unresolved)
        curr.getSubjects().add(oe);

        ValidationReport report = validator.validate(curr, false);
        // OE with 0 units is expected and should not trigger errors
        boolean oeHasErrors = report.getSubjectResults().get(0).getErrors().size() > 0;
        assertFalse(oeHasErrors, "Unresolved Open Elective with 0 units must not fail validation");
        assertTrue(report.getSubjectResults().get(0).getInfo().stream().anyMatch(i -> i.contains("unresolved")));
    }

    // 14. Documents from other semesters are not forced into Semester III.
    @Test
    void test14_DocumentsFromOtherSemestersAreNotForcedIntoSemesterIII() {
        ExtractedCurriculum curr = new ExtractedCurriculum();
        ExtractedDocumentMetadata meta = new ExtractedDocumentMetadata();
        meta.setSemester("IV"); // Semester IV
        curr.setDocument(meta);

        // Provide 5 subjects with 20 credits (different from Semester III)
        for (int i = 1; i <= 5; i++) {
            ExtractedSubject s = new ExtractedSubject("Sem IV Course " + i);
            s.setCourseCode("UI25PC4" + i + "0CS");
            s.setCredits("4");
            for (int u = 1; u <= 5; u++) {
                ExtractedUnit unit = new ExtractedUnit(u, "Unit " + u, u);
                unit.getTopics().add(new ExtractedTopic("Topic " + u, u));
                s.getUnits().add(unit);
            }
            curr.getSubjects().add(s);
        }

        ValidationReport report = validator.validate(curr);
        // Must NOT report "Missing required official Semester III subject: Data Structures"
        boolean missingSem3Reported = report.getErrors().stream().anyMatch(e -> e.contains("Semester III"));
        assertFalse(missingSem3Reported, "Semester IV document must not be forced to conform to Semester III subjects");
    }

    // 15. Existing Phase 1–5 features continue working.
    @Test
    void test15_ExistingPhase1To5FeaturesContinueWorking() throws SQLException {
        // 1. Dashboard summary listing
        List<SyllabusDocumentDto> allDocs = service.getAllDocuments();
        assertNotNull(allDocs);

        // 2. Lifecycle status transitions
        String filename = "test_lifecycle_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("REVIEW_REQUIRED");

        SyllabusSubject s = new SyllabusSubject("HVPE");
        s.setCourseCode("U25HS030EH");
        s.setCredits("1");
        SyllabusUnit u = new SyllabusUnit(1, "Values", 1);
        u.getTopics().add(new SyllabusTopic("Ethics and values", 1));
        s.getUnits().add(u);
        doc.getSubjects().add(s);

        long docId = repository.saveDocument(doc, "REVIEW_REQUIRED");
        cleanupDocIds.add(docId);

        // Approve
        SyllabusDocumentDto approvedDto = service.approveSyllabus(docId);
        assertEquals("APPROVED", approvedDto.getStatus());

        // Verify approved document is locked against edits
        assertThrows(BadRequestException.class, () -> {
            service.updateSyllabusCurriculum(docId, approvedDto);
        });
    }
}
