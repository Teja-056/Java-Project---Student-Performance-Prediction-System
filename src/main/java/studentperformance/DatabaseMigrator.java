package studentperformance;

import java.sql.*;
import java.util.Locale;

/** Repeatable, additive MySQL migrations. Existing legacy tables are never dropped or edited. */
public class DatabaseMigrator {
    public record MigrationReport(int archivedLegacyRows) { }

    public MigrationReport migrate(Connection connection) throws SQLException {
        createCurrentTables(connection);
        makeAssessmentComponentsNullable(connection);
        ensureImportedLegacyColumn(connection);
        createMigrationTables(connection);
        MigrationReport report = migrateOldMarks(connection);
        createSyllabusTables(connection);
        return report;
    }

    /** Additive Phase 3 schema; it does not alter student or assessment rows. */
    private void createSyllabusTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS syllabus_documents ("
                    + "document_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, original_filename VARCHAR(255) NOT NULL,"
                    + "status VARCHAR(20) NOT NULL DEFAULT 'APPROVED', uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "CONSTRAINT chk_syllabus_status CHECK (status IN ('REVIEW','APPROVED'))) ENGINE=InnoDB");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS syllabus_subjects ("
                    + "subject_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL, subject_name VARCHAR(255) NOT NULL,"
                    + "department_code VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN', semester VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN',"
                    + "course_code VARCHAR(40) NOT NULL DEFAULT '', credits VARCHAR(30) NOT NULL DEFAULT '', total_marks VARCHAR(30) NOT NULL DEFAULT '',"
                    + "is_open_elective BOOLEAN NOT NULL DEFAULT FALSE, course_category VARCHAR(30) NOT NULL DEFAULT 'THEORY',"
                    + "sort_order INT NOT NULL DEFAULT 0, FOREIGN KEY (document_id) REFERENCES syllabus_documents(document_id) ON DELETE CASCADE,"
                    + "INDEX idx_syllabus_subject_document(document_id)) ENGINE=InnoDB");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS syllabus_units ("
                    + "unit_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, subject_id BIGINT NOT NULL, unit_number INT NOT NULL,"
                    + "unit_title TEXT NOT NULL, sort_order INT NOT NULL DEFAULT 0,"
                    + "FOREIGN KEY (subject_id) REFERENCES syllabus_subjects(subject_id) ON DELETE CASCADE,"
                    + "UNIQUE KEY uq_syllabus_subject_unit(subject_id,unit_number), INDEX idx_syllabus_units_subject(subject_id)) ENGINE=InnoDB");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS syllabus_topics ("
                    + "topic_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, unit_id BIGINT NOT NULL, parent_topic_id BIGINT NULL,"
                    + "topic_text TEXT NOT NULL, sort_order INT NOT NULL DEFAULT 0,"
                    + "FOREIGN KEY (unit_id) REFERENCES syllabus_units(unit_id) ON DELETE CASCADE,"
                    + "FOREIGN KEY (parent_topic_id) REFERENCES syllabus_topics(topic_id) ON DELETE CASCADE,"
                    + "INDEX idx_syllabus_topics_unit(unit_id), INDEX idx_syllabus_topics_parent(parent_topic_id)) ENGINE=InnoDB");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS internal_unit_mapping ("
                    + "internal_number TINYINT NOT NULL, unit_number INT NOT NULL, PRIMARY KEY(internal_number,unit_number),"
                    + "CONSTRAINT chk_mapping_internal CHECK (internal_number IN (1,2)), INDEX idx_mapping_unit(unit_number)) ENGINE=InnoDB");
            statement.executeUpdate("INSERT IGNORE INTO internal_unit_mapping(internal_number,unit_number) VALUES (1,1),(1,2),(2,3),(2,4),(2,5)");
            statement.executeUpdate("INSERT IGNORE INTO schema_migrations(version,detail) VALUES "
                    + "('V3_SYLLABUS','Added normalized syllabus document, subject, unit, topic and configurable internal-unit mapping tables.')");
        }
        ensureSyllabusSubjectColumns(connection);
    }

    private void ensureSyllabusSubjectColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "department_code", "VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'");
        addColumnIfMissing(connection, "semester", "VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN'");
        addColumnIfMissing(connection, "course_code", "VARCHAR(40) NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "credits", "VARCHAR(30) NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "total_marks", "VARCHAR(30) NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "is_open_elective", "BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfMissing(connection, "course_category", "VARCHAR(30) NOT NULL DEFAULT 'THEORY'");
    }

    private void addColumnIfMissing(Connection connection, String column, String definition) throws SQLException {
        if (!columnExists(connection, "syllabus_subjects", column)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE syllabus_subjects ADD COLUMN " + column + " " + definition);
            }
        }
    }

    private void createCurrentTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS students ("
                    + "roll_number VARCHAR(20) PRIMARY KEY, name VARCHAR(120) NOT NULL,"
                    + "cgpa DECIMAL(4,2) NOT NULL, last_sgpa DECIMAL(4,2) NOT NULL,"
                    + "CONSTRAINT chk_student_cgpa CHECK (cgpa BETWEEN 0 AND 10),"
                    + "CONSTRAINT chk_student_sgpa CHECK (last_sgpa BETWEEN 0 AND 10))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS internal_assessments ("
                    + "roll_number VARCHAR(20) NOT NULL, internal_number TINYINT NOT NULL,"
                    + "subject_name VARCHAR(20) NOT NULL, exam_marks INT NOT NULL,"
                    + "assignment1 INT NULL, assignment2 INT NULL, assignment3 INT NULL,"
                    + "quiz1 INT NULL, quiz2 INT NULL, quiz3 INT NULL, imported_legacy BOOLEAN NOT NULL DEFAULT FALSE,"
                    + "PRIMARY KEY (roll_number, internal_number, subject_name),"
                    + "FOREIGN KEY (roll_number) REFERENCES students(roll_number) ON DELETE CASCADE,"
                    + "CONSTRAINT chk_internal_number CHECK (internal_number IN (1,2)),"
                    + "CONSTRAINT chk_exam_marks CHECK ((subject_name IN ('JAVA','DS','CA','TTPS') AND exam_marks BETWEEN 0 AND 30) "
                    + "OR (subject_name IN ('CS','CT','HVPE') AND exam_marks BETWEEN 0 AND 20)),"
                    + "CONSTRAINT chk_components CHECK ((assignment1 IS NULL OR assignment1 BETWEEN 0 AND 5) "
                    + "AND (assignment2 IS NULL OR assignment2 BETWEEN 0 AND 5) AND (assignment3 IS NULL OR assignment3 BETWEEN 0 AND 5) "
                    + "AND (quiz1 IS NULL OR quiz1 BETWEEN 0 AND 5) AND (quiz2 IS NULL OR quiz2 BETWEEN 0 AND 5) "
                    + "AND (quiz3 IS NULL OR quiz3 BETWEEN 0 AND 5)))");
        }
    }

    private void makeAssessmentComponentsNullable(Connection connection) throws SQLException {
        for (String column : new String[]{"assignment1", "assignment2", "assignment3", "quiz1", "quiz2", "quiz3"}) {
            if (columnExists(connection, "internal_assessments", column) && !columnNullable(connection, "internal_assessments", column)) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE internal_assessments MODIFY COLUMN " + column + " INT NULL");
                }
            }
        }
    }

    private void ensureImportedLegacyColumn(Connection connection) throws SQLException {
        if (!columnExists(connection, "internal_assessments", "imported_legacy")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE internal_assessments ADD COLUMN imported_legacy BOOLEAN NOT NULL DEFAULT FALSE");
            }
        }
    }

    private void createMigrationTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_migrations ("
                    + "version VARCHAR(100) PRIMARY KEY, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, detail VARCHAR(500) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_internal_marks_archive ("
                    + "roll_number VARCHAR(20) NOT NULL, internal_number TINYINT NOT NULL,"
                    + "subject_name VARCHAR(64) NOT NULL, raw_marks INT NOT NULL,"
                    + "archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (roll_number, internal_number, subject_name),"
                    + "INDEX idx_legacy_roll_number (roll_number))");
        }
    }

    private MigrationReport migrateOldMarks(Connection connection) throws SQLException {
        if (!tableExists(connection, "internal_marks")
                || !columnExists(connection, "internal_marks", "roll_number")
                || !columnExists(connection, "internal_marks", "internal_number")
                || !columnExists(connection, "internal_marks", "subject_name")
                || !columnExists(connection, "internal_marks", "marks")) return new MigrationReport(0);

        int archived = 0;
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement read = connection.createStatement();
             ResultSet rows = read.executeQuery("SELECT m.roll_number,m.internal_number,m.subject_name,m.marks "
                     + "FROM internal_marks m JOIN students s ON s.roll_number=m.roll_number")) {
            try (PreparedStatement archive = connection.prepareStatement("INSERT IGNORE INTO legacy_internal_marks_archive "
                    + "(roll_number,internal_number,subject_name,raw_marks) VALUES (?,?,?,?)")) {
                while (rows.next()) {
                    String roll = rows.getString(1), subjectName = rows.getString(3);
                    int raw = rows.getInt(4);
                    archive.setString(1, roll); archive.setInt(2, rows.getInt(2)); archive.setString(3, subjectName); archive.setInt(4, raw);
                    archived += archive.executeUpdate();
                }
            }
            try (PreparedStatement version = connection.prepareStatement("INSERT IGNORE INTO schema_migrations (version,detail) VALUES (?,?)")) {
                version.setString(1, "V2_LEGACY_INTERNAL_MARKS");
                version.setString(2, "Archived legacy raw marks; did not reinterpret values because their original maximum/component is unknown.");
                version.executeUpdate();
            }
            connection.commit();
        } catch (SQLException | RuntimeException ex) {
            connection.rollback(); throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
        return new MigrationReport(archived);
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            if (tables.next()) return true;
        }
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, table.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            if (columns.next()) return true;
        }
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
            return columns.next();
        }
    }

    private boolean columnNullable(Connection connection, String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            if (columns.next()) return "YES".equalsIgnoreCase(columns.getString("IS_NULLABLE"));
        }
        return false;
    }
}
