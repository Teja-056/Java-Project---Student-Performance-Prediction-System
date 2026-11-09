package studentperformance;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DuplicateSafeSyllabusUploadTest {

    private final String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC");
    private final String user = System.getenv().getOrDefault("DB_USER", "root");
    private final String password = System.getenv().getOrDefault("DB_PASSWORD", "Teja.J@2007");

    private SyllabusRepository repository;
    private SyllabusParser parser;

    private Long testDoc1Id;
    private Long testDoc2Id;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new SyllabusRepository(url, user, password);
        parser = new SyllabusParser();
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            new DatabaseMigrator().migrate(c);
        }
    }

    @AfterEach
    void cleanUp() {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            if (testDoc1Id != null) {
                try (PreparedStatement p = c.prepareStatement("DELETE FROM syllabus_subjects WHERE document_id = ?")) {
                    p.setLong(1, testDoc1Id);
                    p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM syllabus_documents WHERE document_id = ?")) {
                    p.setLong(1, testDoc1Id);
                    p.executeUpdate();
                }
            }
            if (testDoc2Id != null) {
                try (PreparedStatement p = c.prepareStatement("DELETE FROM syllabus_subjects WHERE document_id = ?")) {
                    p.setLong(1, testDoc2Id);
                    p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM syllabus_documents WHERE document_id = ?")) {
                    p.setLong(1, testDoc2Id);
                    p.executeUpdate();
                }
            }
        } catch (SQLException ignored) {
        }
    }

    @Test
    void testParserHandlesNumberedHeadingsAndExtractsMetadata() throws Exception {
        String text = "Vasavi College of Engineering\n"
                + "1. DATA STRUCTURES\n"
                + "Course Code: UI25PC310CS | 4 credits | CIE 40\n"
                + "UNIT-I: Basic Concepts and Arrays\n"
                + "Array operations\n"
                + "UNIT-II: Stacks and Queues\n"
                + "Stack implementation\n"
                + "2. OBJECT ORIENTED PROGRAMMING THROUGH JAVA\n"
                + "Course Code: UI25PC320CS | 3 credits | CIE 40\n"
                + "UNIT-I: OOP Principles\n"
                + "Classes and Objects\n";

        SyllabusDocument doc = parser.parseText("combined.pdf", text);
        assertEquals(2, doc.getSubjects().size());

        SyllabusSubject ds = doc.getSubjects().get(0);
        assertEquals("DATA STRUCTURES", ds.getName());
        assertEquals("UI25PC310CS", ds.getCourseCode());
        assertEquals("4", ds.getCredits());
        assertEquals(2, ds.getUnits().size());
        assertEquals(1, ds.getUnits().get(0).getNumber());
        assertEquals("Basic Concepts and Arrays", ds.getUnits().get(0).getTitle());

        SyllabusSubject java = doc.getSubjects().get(1);
        assertEquals("OBJECT ORIENTED PROGRAMMING THROUGH JAVA", java.getName());
        assertEquals("UI25PC320CS", java.getCourseCode());
        assertEquals("3", java.getCredits());
        assertEquals(1, java.getUnits().size());
    }

    @Test
    void testSubjectMergesDuplicateUnitsInParser() {
        SyllabusSubject sub = new SyllabusSubject("Test Course");
        SyllabusUnit u1a = new SyllabusUnit(1, "Unit 1");
        u1a.getTopics().add(new SyllabusTopic("Topic 1"));
        sub.addUnit(u1a);

        SyllabusUnit u1b = new SyllabusUnit(1, "Introduction to Algorithms");
        u1b.getTopics().add(new SyllabusTopic("Topic 1"));
        u1b.getTopics().add(new SyllabusTopic("Topic 2"));
        sub.addUnit(u1b);

        assertEquals(1, sub.getUnits().size(), "Duplicate unit number must be merged, not appended.");
        assertEquals("Introduction to Algorithms", sub.getUnits().get(0).getTitle());
        assertEquals(2, sub.getUnits().get(0).getTopics().size(), "Duplicate topic text must not be duplicated.");
    }

    @Test
    void testDuplicateSafeSyllabusUploadAndReUpload() throws Exception {
        String doc1Name = "test_doc1_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc1 = new SyllabusDocument(doc1Name);

        String dsCode = ("UI25PC310CS_T" + UUID.randomUUID().toString().substring(0, 5)).toUpperCase(java.util.Locale.ROOT);
        String javaCode = ("UI25PC301CS_T" + UUID.randomUUID().toString().substring(0, 5)).toUpperCase(java.util.Locale.ROOT);

        SyllabusSubject ds1 = new SyllabusSubject("DATA STRUCTURES");
        ds1.setCourseCode(dsCode);
        ds1.setDepartmentCode("CSE");
        ds1.setSemester("III");
        ds1.setCredits("4");
        for (int u = 1; u <= 5; u++) {
            SyllabusUnit unit = new SyllabusUnit(u, "DS Unit " + u);
            unit.getTopics().add(new SyllabusTopic("DS Unit " + u + " Topic A"));
            ds1.getUnits().add(unit);
        }
        doc1.getSubjects().add(ds1);

        testDoc1Id = repository.saveApproved(doc1);
        assertNotNull(testDoc1Id);
        assertTrue(testDoc1Id > 0);

        // Upload a second document containing Data Structures AND Java
        String doc2Name = "test_doc2_" + UUID.randomUUID() + ".pdf";
        SyllabusDocument doc2 = new SyllabusDocument(doc2Name);

        SyllabusSubject ds2 = new SyllabusSubject("DATA STRUCTURES");
        ds2.setCourseCode(dsCode);
        ds2.setDepartmentCode("CSE");
        ds2.setSemester("III");
        ds2.setCredits("4");
        for (int u = 1; u <= 5; u++) {
            SyllabusUnit unit = new SyllabusUnit(u, "DS Unit " + u + " Revised");
            unit.getTopics().add(new SyllabusTopic("DS Unit " + u + " Topic A")); // existing topic
            unit.getTopics().add(new SyllabusTopic("DS Unit " + u + " Topic B")); // new topic
            ds2.getUnits().add(unit);
        }

        SyllabusSubject javaSub = new SyllabusSubject("OBJECT ORIENTED PROGRAMMING THROUGH JAVA");
        javaSub.setCourseCode(javaCode);
        javaSub.setDepartmentCode("CSE");
        javaSub.setSemester("III");
        javaSub.setCredits("3");
        for (int u = 1; u <= 5; u++) {
            SyllabusUnit unit = new SyllabusUnit(u, "Java Unit " + u);
            unit.getTopics().add(new SyllabusTopic("Java Unit " + u + " Topic 1"));
            javaSub.getUnits().add(unit);
        }

        doc2.getSubjects().add(ds2);
        doc2.getSubjects().add(javaSub);

        // This MUST NOT throw Duplicate entry 'x-1' for key 'syllabus_units.uq_syllabus_subject_unit'
        assertDoesNotThrow(() -> {
            testDoc2Id = repository.saveApproved(doc2);
        });
        assertNotNull(testDoc2Id);
        assertNotEquals(testDoc1Id, testDoc2Id);

        // Verify that Doc 1 and Doc 2 are both accessible and have their subjects
        SyllabusDocument loadedDoc1 = repository.findById(testDoc1Id);
        assertNotNull(loadedDoc1);
        assertEquals(doc1Name, loadedDoc1.getFilename());
        assertTrue(loadedDoc1.getSubjects().stream().anyMatch(s -> s.getCourseCode().equals(dsCode)));

        SyllabusDocument loadedDoc2 = repository.findById(testDoc2Id);
        assertNotNull(loadedDoc2);
        assertEquals(doc2Name, loadedDoc2.getFilename());
        assertEquals(2, loadedDoc2.getSubjects().size(), "Doc 2 must contain both DS and Java");
        assertTrue(loadedDoc2.getSubjects().stream().anyMatch(s -> s.getCourseCode().equals(dsCode)));
        assertTrue(loadedDoc2.getSubjects().stream().anyMatch(s -> s.getCourseCode().equals(javaCode)));

        // Test repeated upload of Doc 2 (same filename)
        assertDoesNotThrow(() -> {
            long repeatedId = repository.saveApproved(doc2);
            assertEquals(testDoc2Id, repeatedId, "Repeated upload of same filename should reuse document ID");
        });

        // Verify DB integrity: Count DS subject rows in syllabus_subjects
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM syllabus_subjects WHERE course_code = ?")) {
            p.setString(1, dsCode);
            try (ResultSet rs = p.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "DS must exist exactly ONCE in syllabus_subjects");
            }
        }

        // Verify DB integrity: Count DS units in syllabus_units
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM syllabus_units u "
                     + "JOIN syllabus_subjects s ON s.subject_id = u.subject_id WHERE s.course_code = ?")) {
            p.setString(1, dsCode);
            try (ResultSet rs = p.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1), "DS must have exactly 5 units in syllabus_units (NO DUPLICATE UNITS)");
            }
        }

        // Verify junction mapping in syllabus_document_subjects
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM syllabus_document_subjects WHERE document_id = ?")) {
            p.setLong(1, testDoc2Id);
            try (ResultSet rs = p.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "Doc 2 must be linked to 2 subjects in junction table");
            }
        }
    }
}
