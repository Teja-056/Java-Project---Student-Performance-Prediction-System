package studentperformance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studentperformance.syllabus.ExtractedCurriculum;
import studentperformance.syllabus.ExtractedDocumentMetadata;
import studentperformance.syllabus.ExtractedSubject;
import studentperformance.syllabus.ExtractedTopic;
import studentperformance.syllabus.ExtractedUnit;
import studentperformance.syllabus.SyllabusValidator;
import studentperformance.syllabus.ValidationReport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SyllabusPersistenceWorkflowIntegrationTest {

    private final String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC");
    private final String user = System.getenv().getOrDefault("DB_USER", "root");
    private final String password = System.getenv().getOrDefault("DB_PASSWORD", "Teja.J@2007");

    private SyllabusRepository repository;
    private final List<Long> createdDocumentIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws SQLException {
        repository = new SyllabusRepository(url, user, password);
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            new DatabaseMigrator().migrate(c);
        }
    }

    @AfterEach
    void tearDown() {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            for (Long docId : createdDocumentIds) {
                if (docId != null && docId > 0) {
                    try (PreparedStatement p = c.prepareStatement("DELETE FROM syllabus_documents WHERE document_id = ?")) {
                        p.setLong(1, docId);
                        p.executeUpdate();
                    } catch (SQLException ignored) {}
                }
            }
        } catch (SQLException ignored) {}
    }

    private ExtractedCurriculum buildTestCurriculum() {
        ExtractedCurriculum curr = new ExtractedCurriculum();
        curr.setDocument(new ExtractedDocumentMetadata(
                "Vasavi College of Engineering (Autonomous)",
                "R-25",
                "2026-27",
                "Computer Science & Engineering",
                "III"
        ));

        // Subject 1: Data Structures
        ExtractedSubject ds = new ExtractedSubject("Data Structures");
        ds.setCourseCode("UI25PC310CS");
        ds.setCredits("4");
        ds.setCategory("THEORY");
        ds.setStartPage(2);
        ds.setEndPage(2);

        for (int u = 1; u <= 5; u++) {
            ExtractedUnit unit = new ExtractedUnit(u, "DS Unit " + u, 2);
            unit.getTopics().add(new ExtractedTopic("DS Unit " + u + " Topic A", 2));
            unit.getTopics().add(new ExtractedTopic("DS Unit " + u + " Topic B", 2));
            ds.getUnits().add(unit);
        }
        curr.getSubjects().add(ds);

        // Subject 2: OOP through Java
        ExtractedSubject javaSub = new ExtractedSubject("Object Oriented Programming through Java");
        javaSub.setCourseCode("UI25PC320CS");
        javaSub.setCredits("3");
        javaSub.setCategory("THEORY");
        javaSub.setStartPage(3);
        javaSub.setEndPage(3);

        for (int u = 1; u <= 5; u++) {
            ExtractedUnit unit = new ExtractedUnit(u, "Java Unit " + u, 3);
            unit.getTopics().add(new ExtractedTopic("Java Unit " + u + " Topic 1", 3));
            javaSub.getUnits().add(unit);
        }
        curr.getSubjects().add(javaSub);

        return curr;
    }

    @Test
    void testSaveAndRetrieveReviewRequiredWithMetadataAndPageNumbers() throws Exception {
        String filename = "test_sem3_" + UUID.randomUUID() + ".pdf";
        ExtractedCurriculum curr = buildTestCurriculum();
        ValidationReport report = new SyllabusValidator().validate(curr, false);

        SyllabusDocument doc = repository.fromExtractedCurriculum(filename, curr, report);
        assertEquals("REVIEW_REQUIRED", doc.getStatus());

        long docId = repository.saveReviewRequired(doc);
        createdDocumentIds.add(docId);
        assertTrue(docId > 0, "Document ID must be positive");

        // Retrieve saved document
        SyllabusDocument loaded = repository.findById(docId);
        assertNotNull(loaded, "Loaded document must not be null");
        assertEquals("REVIEW_REQUIRED", loaded.getStatus());
        assertEquals("Vasavi College of Engineering (Autonomous)", loaded.getInstitution());
        assertEquals("R-25", loaded.getAcademicScheme());
        assertEquals("2026-27", loaded.getAcademicYear());
        assertEquals("Computer Science & Engineering", loaded.getBranch());
        assertEquals("III", loaded.getSemester());
        assertNotNull(loaded.getValidationReportJson());
        assertFalse(loaded.getValidationReportJson().isBlank());

        // Verify subjects
        assertEquals(2, loaded.getSubjects().size());
        SyllabusSubject dsSub = loaded.getSubjects().stream()
                .filter(s -> s.getName().toUpperCase().contains("DATA STRUCTURES")).findFirst().orElseThrow();
        assertEquals("UI25PC310CS", dsSub.getCourseCode());
        assertEquals("4", dsSub.getCredits());
        assertEquals(5, dsSub.getUnits().size());

        // Verify unit and topic page numbers
        SyllabusUnit u1 = dsSub.getUnits().get(0);
        assertEquals(1, u1.getNumber());
        assertEquals(2, u1.getPageNumber(), "Unit 1 must have page number 2");
        assertFalse(u1.getTopics().isEmpty());
        SyllabusTopic topicA = u1.getTopics().stream()
                .filter(t -> t.getText().contains("DS Unit 1 Topic A")).findFirst().orElseThrow();
        assertEquals(2, topicA.getPageNumber(), "Topic A must have page number 2");
    }

    @Test
    void testApprovalWorkflowTransitionsFromReviewRequiredToApproved() throws Exception {
        String filename = "test_approval_" + UUID.randomUUID() + ".pdf";
        ExtractedCurriculum curr = buildTestCurriculum();
        SyllabusDocument doc = repository.fromExtractedCurriculum(filename, curr, null);

        long docId = repository.saveReviewRequired(doc);
        createdDocumentIds.add(docId);

        SyllabusDocument initial = repository.findById(docId);
        assertEquals("REVIEW_REQUIRED", initial.getStatus());

        // Explicit approval by faculty
        boolean approved = repository.approveDocument(docId);
        assertTrue(approved, "approveDocument should return true");

        SyllabusDocument afterApproval = repository.findById(docId);
        assertEquals("APPROVED", afterApproval.getStatus(), "Status must transition to APPROVED");
    }

    @Test
    void testCannotApproveAlreadyApprovedDocument() throws Exception {
        String filename = "test_double_approve_" + UUID.randomUUID() + ".pdf";
        ExtractedCurriculum curr = buildTestCurriculum();
        SyllabusDocument doc = repository.fromExtractedCurriculum(filename, curr, null);

        long docId = repository.saveReviewRequired(doc);
        createdDocumentIds.add(docId);

        assertTrue(repository.approveDocument(docId));

        // Approving again should fail with IllegalStateException
        assertThrows(IllegalStateException.class, () -> repository.approveDocument(docId),
                "Approving an already approved document must throw an exception");
    }

    @Test
    void testPreserveHistoricalApprovedDocumentOnReupload() throws Exception {
        String filename = "official_approved_course_syllabus.pdf";
        ExtractedCurriculum curr1 = buildTestCurriculum();
        SyllabusDocument doc1 = repository.fromExtractedCurriculum(filename, curr1, null);

        long doc1Id = repository.saveApproved(doc1);
        createdDocumentIds.add(doc1Id);

        SyllabusDocument originalApproved = repository.findById(doc1Id);
        assertEquals("APPROVED", originalApproved.getStatus());

        // Now an upload of a draft with the SAME filename is submitted in REVIEW_REQUIRED state
        ExtractedCurriculum curr2 = buildTestCurriculum();
        SyllabusDocument doc2 = repository.fromExtractedCurriculum(filename, curr2, null);
        long doc2Id = repository.saveReviewRequired(doc2);
        createdDocumentIds.add(doc2Id);

        // Crucial requirement: Historical approved document must remain intact and NOT overwritten!
        assertNotEquals(doc1Id, doc2Id, "New upload must NOT overwrite historical approved document ID");

        SyllabusDocument checkedDoc1 = repository.findById(doc1Id);
        assertEquals("APPROVED", checkedDoc1.getStatus(), "Approved document status must remain APPROVED");

        SyllabusDocument checkedDoc2 = repository.findById(doc2Id);
        assertEquals("REVIEW_REQUIRED", checkedDoc2.getStatus(), "New upload must be REVIEW_REQUIRED");
    }

    @Test
    void testRepeatedUploadOfDraftDocumentUpdatesInPlace() throws Exception {
        String filename = "draft_in_progress_" + UUID.randomUUID() + ".pdf";
        ExtractedCurriculum curr1 = buildTestCurriculum();
        SyllabusDocument doc1 = repository.fromExtractedCurriculum(filename, curr1, null);

        long doc1Id = repository.saveReviewRequired(doc1);
        createdDocumentIds.add(doc1Id);

        // Second upload of same draft filename while still in REVIEW_REQUIRED
        ExtractedCurriculum curr2 = buildTestCurriculum();
        curr2.getDocument().setInstitution("Vasavi College of Engineering (Updated)");
        SyllabusDocument doc2 = repository.fromExtractedCurriculum(filename, curr2, null);

        long doc2Id = repository.saveReviewRequired(doc2);
        assertEquals(doc1Id, doc2Id, "Re-upload of draft document should update same document ID in place");

        SyllabusDocument updated = repository.findById(doc1Id);
        assertEquals("REVIEW_REQUIRED", updated.getStatus());
        assertEquals("Vasavi College of Engineering (Updated)", updated.getInstitution());
    }

    @Test
    void testSaveFailedDocumentStoresErrorMessageWithoutPartialUnits() throws Exception {
        String filename = "corrupt_syllabus_" + UUID.randomUUID() + ".pdf";
        String errorMsg = "Gemini API returned error: Could not decipher scan";
        String reportJson = "{\"status\":\"FAILED\",\"errors\":[\"Document scan unreadable\"]}";

        long docId = repository.saveFailedDocument(filename, errorMsg, reportJson);
        createdDocumentIds.add(docId);
        assertTrue(docId > 0);

        SyllabusDocument failedDoc = repository.findById(docId);
        assertNotNull(failedDoc);
        assertEquals("FAILED", failedDoc.getStatus());
        assertEquals(errorMsg, failedDoc.getErrorMessage());
        assertTrue(failedDoc.getSubjects().isEmpty(), "Failed document must NOT persist partial subjects");
    }

    @Test
    void testRejectDocumentTransitionsStatusToFailed() throws Exception {
        String filename = "reject_test_" + UUID.randomUUID() + ".pdf";
        ExtractedCurriculum curr = buildTestCurriculum();
        SyllabusDocument doc = repository.fromExtractedCurriculum(filename, curr, null);

        long docId = repository.saveReviewRequired(doc);
        createdDocumentIds.add(docId);

        boolean rejected = repository.rejectDocument(docId, "Topic count too low for Unit 2");
        assertTrue(rejected);

        SyllabusDocument rejectedDoc = repository.findById(docId);
        assertEquals("FAILED", rejectedDoc.getStatus());
        assertEquals("Topic count too low for Unit 2", rejectedDoc.getErrorMessage());
    }

    @Test
    void testFindApprovedAndFindReviewRequiredFilters() throws Exception {
        String approvedFilename = "approved_doc_" + UUID.randomUUID() + ".pdf";
        ExtractedCurriculum currA = buildTestCurriculum();
        SyllabusDocument docA = repository.fromExtractedCurriculum(approvedFilename, currA, null);
        long docAId = repository.saveApproved(docA);
        createdDocumentIds.add(docAId);

        String reviewFilename = "review_doc_" + UUID.randomUUID() + ".pdf";
        ExtractedCurriculum currR = buildTestCurriculum();
        SyllabusDocument docR = repository.fromExtractedCurriculum(reviewFilename, currR, null);
        long docRId = repository.saveReviewRequired(docR);
        createdDocumentIds.add(docRId);

        List<SyllabusDocument> approvedList = repository.findApproved();
        assertTrue(approvedList.stream().anyMatch(d -> d.getDocumentId() == docAId),
                "Approved list must contain docA");
        assertFalse(approvedList.stream().anyMatch(d -> d.getDocumentId() == docRId),
                "Approved list must NOT contain docR");

        List<SyllabusDocument> reviewList = repository.findReviewRequired();
        assertTrue(reviewList.stream().anyMatch(d -> d.getDocumentId() == docRId),
                "Review list must contain docR");
    }
}
