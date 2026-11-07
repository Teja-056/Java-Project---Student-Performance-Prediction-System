package studentperformance;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Runs only against a database explicitly named with a _test suffix. */
@EnabledIfEnvironmentVariable(named = "PHASE2_TEST_DB_URL", matches = ".*[/=][a-z0-9_]*_test([?;].*)?")
class StudentRepositoryIntegrationTest {
    private static StudentRepository repository;
    private static String url, user, password;

    @BeforeAll
    static void connectToDedicatedTestDatabase() throws SQLException {
        url = System.getenv("PHASE2_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && url.toLowerCase().matches(".*[/=][a-z0-9_]*_test([?;].*)?$"),
                "Set PHASE2_TEST_DB_URL to a dedicated MySQL database whose name ends in _test.");
        user = System.getenv().getOrDefault("PHASE2_TEST_DB_USER", "root");
        password = System.getenv().getOrDefault("PHASE2_TEST_DB_PASSWORD", "");
        repository = new StudentRepository(url, user, password);
        repository.initializeSchema();
    }

    @Test
    void addsSearchesUpdatesAndDeletesAStudentAndRejectsDuplicateRollNumbers() throws SQLException {
        String roll = testRoll();
        try {
            Student original = PerformanceAnalyzerTest.fullStudent(roll, 15, 24);
            repository.save(original);
            assertNotNull(repository.findByRollNumber(roll));
            assertEquals(roll, repository.findAll().stream().filter(s -> s.getRollNumber().equals(roll)).findFirst().orElseThrow().getRollNumber());
            assertThrows(SQLException.class, () -> repository.save(original), "roll_number is a unique primary key");
            assertTrue(repository.updateDetails(roll, "Updated Test Student", 8.2, 7.8));
            assertEquals("Updated Test Student", repository.findByRollNumber(roll).getName());
            Student changedMarks = PerformanceAnalyzerTest.fullStudent(roll, 20, 25);
            repository.updateMarks(changedMarks);
            assertEquals(20, repository.findByRollNumber(roll).getMarks(1).get(Subject.JAVA).getExamMarks());
            assertTrue(repository.delete(roll));
            assertNull(repository.findByRollNumber(roll));
        } finally { repository.delete(roll); }
    }

    @Test
    void preservesAndDisplaysIncompleteRecordsWithoutTreatingThemAsZeroScores() throws SQLException {
        String roll = testRoll();
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement insert = connection.prepareStatement("INSERT INTO students(roll_number,name,cgpa,last_sgpa) VALUES(?,?,?,?)")) {
            insert.setString(1, roll); insert.setString(2, "Incomplete Test Record"); insert.setDouble(3, 6); insert.setDouble(4, 6);
            insert.executeUpdate();
            Student student = repository.findByRollNumber(roll);
            assertNotNull(student);
            assertFalse(new PerformanceAnalyzer().hasBothInternals(student));
            assertEquals("Incomplete assessment data", new PerformanceAnalyzer().prediction(student));
        } finally { repository.delete(roll); }
    }

    @Test
    void archivesLegacyMarksRepeatablyWithoutGuessingTheirOriginalScale() throws SQLException {
        String roll = testRoll();
        try {
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS internal_marks (roll_number VARCHAR(20) NOT NULL,"
                        + "internal_number TINYINT NOT NULL,subject_name VARCHAR(64) NOT NULL,marks INT NOT NULL,"
                        + "PRIMARY KEY(roll_number,internal_number,subject_name))");
                try (PreparedStatement student = connection.prepareStatement("INSERT INTO students(roll_number,name,cgpa,last_sgpa) VALUES(?,?,?,?)")) {
                    student.setString(1, roll); student.setString(2, "Legacy Test Record"); student.setDouble(3, 5); student.setDouble(4, 5); student.executeUpdate();
                }
                try (PreparedStatement mark = connection.prepareStatement("INSERT INTO internal_marks(roll_number,internal_number,subject_name,marks) VALUES(?,?,?,?)")) {
                    mark.setString(1, roll); mark.setInt(2, 1); mark.setString(3, "JAVA"); mark.setInt(4, 25); mark.executeUpdate();
                    mark.setString(3, "DS"); mark.setInt(4, 55); mark.executeUpdate();
                }
            }
            try (Connection connection = DriverManager.getConnection(url, user, password)) { new DatabaseMigrator().migrate(connection); }
            try (Connection connection = DriverManager.getConnection(url, user, password)) { new DatabaseMigrator().migrate(connection); }
            Student student = repository.findByRollNumber(roll);
            assertNull(student.getMarks(1).get(Subject.JAVA));
            assertFalse(new PerformanceAnalyzer().hasBothInternals(student));
            assertEquals(2, student.getLegacyMarks().size());
            assertTrue(student.getLegacyMarks().stream().anyMatch(mark -> mark.marks() == 55));
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM internal_marks WHERE roll_number=?")) {
                count.setString(1, roll);
                try (ResultSet rows = count.executeQuery()) { rows.next(); assertEquals(2, rows.getInt(1)); }
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 PreparedStatement archive = connection.prepareStatement("DELETE FROM legacy_internal_marks_archive WHERE roll_number=?")) {
                archive.setString(1, roll); archive.executeUpdate();
            } catch (SQLException ignored) { }
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 PreparedStatement legacy = connection.prepareStatement("DELETE FROM internal_marks WHERE roll_number=?")) {
                legacy.setString(1, roll); legacy.executeUpdate();
            } catch (SQLException ignored) { }
            repository.delete(roll);
        }
    }

    @Test
    void reportsDatabaseConnectionFailuresAsSqlExceptions() {
        StudentRepository broken = new StudentRepository("jdbc:unsupported:phase2-test", "nobody", "");
        assertThrows(SQLException.class, broken::initializeSchema);
    }

    private static String testRoll() { return "1602-25-733-" + String.format("%03d", ThreadLocalRandom.current().nextInt(1000)); }
}
