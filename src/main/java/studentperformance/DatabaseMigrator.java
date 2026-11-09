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
        applyV7SyllabusRedesign(connection);
        applyV8SyllabusAccuracyAndReprocessing(connection);
        applyV9SyllabusRelationalRepair(connection);
        applyV10DatasetManagement(connection);
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
        createSyllabusDocumentSubjectsTable(connection);
    }

    private void createSyllabusDocumentSubjectsTable(Connection connection) throws SQLException {
        boolean tableAlreadyExisted = tableExists(connection, "syllabus_document_subjects");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS syllabus_document_subjects ("
                    + "document_id BIGINT NOT NULL, subject_id BIGINT NOT NULL, "
                    + "PRIMARY KEY (document_id, subject_id), "
                    + "FOREIGN KEY (document_id) REFERENCES syllabus_documents(document_id) ON DELETE CASCADE, "
                    + "FOREIGN KEY (subject_id) REFERENCES syllabus_subjects(subject_id) ON DELETE CASCADE, "
                    + "INDEX idx_doc_sub_doc(document_id), INDEX idx_doc_sub_sub(subject_id)) ENGINE=InnoDB");
            if (!tableAlreadyExisted) {
                statement.executeUpdate("INSERT IGNORE INTO syllabus_document_subjects(document_id, subject_id) "
                        + "SELECT document_id, subject_id FROM syllabus_subjects WHERE document_id IS NOT NULL");
            }
        }
    }

    private void ensureSyllabusSubjectColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "syllabus_subjects", "department_code", "VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'");
        addColumnIfMissing(connection, "syllabus_subjects", "semester", "VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN'");
        addColumnIfMissing(connection, "syllabus_subjects", "course_code", "VARCHAR(40) NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "syllabus_subjects", "credits", "VARCHAR(30) NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "syllabus_subjects", "total_marks", "VARCHAR(30) NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "syllabus_subjects", "is_open_elective", "BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfMissing(connection, "syllabus_subjects", "course_category", "VARCHAR(30) NOT NULL DEFAULT 'THEORY'");
    }

    private void applyV7SyllabusRedesign(Connection connection) throws SQLException {
        // Document metadata and validation report storage
        addColumnIfMissing(connection, "syllabus_documents", "institution", "VARCHAR(255) NULL");
        addColumnIfMissing(connection, "syllabus_documents", "academic_scheme", "VARCHAR(50) NULL");
        addColumnIfMissing(connection, "syllabus_documents", "academic_year", "VARCHAR(50) NULL");
        addColumnIfMissing(connection, "syllabus_documents", "branch", "VARCHAR(100) NULL");
        addColumnIfMissing(connection, "syllabus_documents", "semester", "VARCHAR(20) NULL");
        addColumnIfMissing(connection, "syllabus_documents", "validation_report", "LONGTEXT NULL");
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("ALTER TABLE syllabus_documents MODIFY COLUMN validation_report LONGTEXT NULL");
        } catch (SQLException ignored) {}
        addColumnIfMissing(connection, "syllabus_documents", "error_message", "TEXT NULL");

        // Page number references
        addColumnIfMissing(connection, "syllabus_units", "page_number", "INT NULL");
        addColumnIfMissing(connection, "syllabus_topics", "page_number", "INT NULL");

        // Lifecycle status constraint update (PROCESSING, REVIEW_REQUIRED, REVIEW, APPROVED, FAILED)
        updateSyllabusDocumentStatusConstraint(connection);

        // Record migration version
        try (PreparedStatement p = connection.prepareStatement(
                "INSERT IGNORE INTO schema_migrations(version, detail) VALUES (?, ?)")) {
            p.setString(1, "V7_SYLLABUS_GEMINI_REDESIGN");
            p.setString(2, "Added document metadata, source page numbers, and extended lifecycle statuses (PROCESSING, REVIEW_REQUIRED, APPROVED, FAILED).");
            p.executeUpdate();
        }
    }

    private boolean isMigrationApplied(Connection connection, String version) {
        try (PreparedStatement p = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
            p.setString(1, version);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void applyV8SyllabusAccuracyAndReprocessing(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "syllabus_documents", "extraction_engine", "VARCHAR(50) NULL DEFAULT 'LEGACY'");
        addColumnIfMissing(connection, "syllabus_documents", "pdf_path", "VARCHAR(500) NULL");
        addColumnIfMissing(connection, "syllabus_documents", "parent_document_id", "BIGINT NULL");
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_syllabus_parent_document ON syllabus_documents(parent_document_id)");
        } catch (SQLException ignored) {}

        if (!isMigrationApplied(connection, "V8_SYLLABUS_ACCURACY_REPROCESSING")) {
            // Correct legacy contaminated topics and deduplicate subjects (one-time Phase 5.1 cleanup)
            repairLegacySyllabusContamination(connection);

            try (PreparedStatement p = connection.prepareStatement(
                    "INSERT IGNORE INTO schema_migrations(version, detail) VALUES (?, ?)")) {
                p.setString(1, "V8_SYLLABUS_ACCURACY_REPROCESSING");
                p.setString(2, "Added extraction_engine, pdf_path, and parent_document_id for safe legacy reprocessing and revision tracking.");
                p.executeUpdate();
            }
        }
    }

    private void applyV9SyllabusRelationalRepair(Connection connection) throws SQLException {
        // Drop hazardous foreign key constraint syllabus_subjects_ibfk_1 (document_id CASCADE delete on syllabus_subjects)
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("ALTER TABLE syllabus_subjects DROP FOREIGN KEY syllabus_subjects_ibfk_1");
        } catch (SQLException ignored) {
            // Constraint may not exist or already dropped
        }

        // Make syllabus_subjects.document_id nullable so canonical subjects can exist independently of documents
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("ALTER TABLE syllabus_subjects MODIFY COLUMN document_id BIGINT NULL");
        } catch (SQLException ignored) {}

        // Record migration version
        try (PreparedStatement p = connection.prepareStatement(
                "INSERT IGNORE INTO schema_migrations(version, detail) VALUES (?, ?)")) {
            p.setString(1, "V9_SYLLABUS_RELATIONAL_REPAIR");
            p.setString(2, "Dropped hazardous CASCADE foreign key on syllabus_subjects(document_id) to prevent canonical subject deletion.");
            p.executeUpdate();
        }
    }

    private void applyV10DatasetManagement(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS datasets ("
                    + "dataset_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "dataset_code VARCHAR(64) NOT NULL UNIQUE, "
                    + "name VARCHAR(255) NOT NULL, "
                    + "original_filename VARCHAR(255) NOT NULL, "
                    + "storage_path VARCHAR(500) NULL, "
                    + "semester VARCHAR(20) NOT NULL DEFAULT 'Semester III', "
                    + "academic_year VARCHAR(50) NULL, "
                    + "total_rows INT NOT NULL DEFAULT 0, "
                    + "valid_count INT NOT NULL DEFAULT 0, "
                    + "invalid_count INT NOT NULL DEFAULT 0, "
                    + "is_active BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "status VARCHAR(30) NOT NULL DEFAULT 'READY', "
                    + "validation_report LONGTEXT NULL, "
                    + "error_message TEXT NULL, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "INDEX idx_datasets_active (is_active), "
                    + "INDEX idx_datasets_code (dataset_code)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            s.executeUpdate("INSERT INTO datasets (dataset_id, dataset_code, name, original_filename, semester, academic_year, total_rows, valid_count, invalid_count, is_active, status) "
                    + "VALUES (1, 'DS-2026-BASELINE', 'Baseline Cohort (Semester III)', 'student_records_100.xlsx', 'Semester III', '2025-2026', 101, 101, 0, TRUE, 'READY') "
                    + "ON DUPLICATE KEY UPDATE is_active = TRUE");
        }

        if (!columnExists(connection, "students", "dataset_id")) {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE students ADD COLUMN dataset_id BIGINT NOT NULL DEFAULT 1 FIRST");
            }
        }
        if (!columnExists(connection, "internal_assessments", "dataset_id")) {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE internal_assessments ADD COLUMN dataset_id BIGINT NOT NULL DEFAULT 1 FIRST");
            }
        }

        ensureDatasetCompositeKeys(connection);

        try (PreparedStatement p = connection.prepareStatement(
                "INSERT IGNORE INTO schema_migrations(version, detail) VALUES (?, ?)")) {
            p.setString(1, "V10_DATASET_ISOLATION");
            p.setString(2, "Added datasets table, dataset_id to students and internal_assessments, and composite primary/foreign keys.");
            p.executeUpdate();
        }
    }

    private void ensureDatasetCompositeKeys(Connection connection) {
        try (Statement s = connection.createStatement()) {
            try { s.executeUpdate("ALTER TABLE internal_assessments DROP FOREIGN KEY internal_assessments_ibfk_1"); } catch (SQLException ignored) {}

            try (ResultSet rs = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, "students")) {
                int pkCols = 0;
                while (rs.next()) pkCols++;
                if (pkCols == 1) {
                    try { s.executeUpdate("ALTER TABLE internal_assessments DROP PRIMARY KEY"); } catch (SQLException ignored) {}
                    try { s.executeUpdate("ALTER TABLE students DROP PRIMARY KEY"); } catch (SQLException ignored) {}
                    s.executeUpdate("ALTER TABLE students ADD PRIMARY KEY (dataset_id, roll_number)");
                    s.executeUpdate("ALTER TABLE internal_assessments ADD PRIMARY KEY (dataset_id, roll_number, internal_number, subject_name)");
                    try { s.executeUpdate("ALTER TABLE students ADD INDEX idx_students_roll (roll_number)"); } catch (SQLException ignored) {}
                    try { s.executeUpdate("ALTER TABLE students ADD CONSTRAINT fk_students_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(dataset_id) ON DELETE CASCADE"); } catch (SQLException ignored) {}
                    try { s.executeUpdate("ALTER TABLE internal_assessments ADD CONSTRAINT fk_assessments_student FOREIGN KEY (dataset_id, roll_number) REFERENCES students(dataset_id, roll_number) ON DELETE CASCADE"); } catch (SQLException ignored) {}
                }
            }
        } catch (SQLException e) {
            System.err.println("Notice: ensureDatasetCompositeKeys: " + e.getMessage());
        }
    }

    private void repairLegacySyllabusContamination(Connection connection) {
        try (Statement s = connection.createStatement()) {
            // 1. Remove contaminated Critical Thinking topics mistakenly inserted into Data Structures (course UI25PC310CS)
            s.executeUpdate("DELETE t FROM syllabus_topics t "
                    + "JOIN syllabus_units u ON u.unit_id = t.unit_id "
                    + "JOIN syllabus_subjects s ON s.subject_id = u.subject_id "
                    + "WHERE s.course_code = 'UI25PC310CS' AND ("
                    + "t.topic_text LIKE '%Logic and reasoning%' OR t.topic_text LIKE '%identifying assumptions%' "
                    + "OR t.topic_text LIKE '%bias and fallacies%' OR t.topic_text LIKE '%Evaluating evidence%' "
                    + "OR t.topic_text LIKE '%drawing logical conclusions%' OR t.topic_text LIKE '%differentiating facts from opinions%' "
                    + "OR t.topic_text LIKE '%Root Cause Analysis%' OR t.topic_text LIKE '%5 Whys%' "
                    + "OR t.topic_text LIKE '%Decision Trees%' OR t.topic_text LIKE '%SWOT Analysis%' "
                    + "OR t.topic_text LIKE '%ethical dilemmas%' OR t.topic_text LIKE '%Critical thinking in action%' "
                    + "OR t.topic_text LIKE '%debating complex engineering issues%')");

            // 2. Remove contaminated Communication Skills topics mistakenly inserted into TTPS (course U25BS340MA)
            s.executeUpdate("DELETE t FROM syllabus_topics t "
                    + "JOIN syllabus_units u ON u.unit_id = t.unit_id "
                    + "JOIN syllabus_subjects s ON s.subject_id = u.subject_id "
                    + "WHERE s.course_code = 'U25BS340MA' AND ("
                    + "t.topic_text LIKE '%communication: intrapersonal%' OR t.topic_text LIKE '%types of listening%' "
                    + "OR t.topic_text LIKE '%Team building: stages%' OR t.topic_text LIKE '%Paragraph writing%' "
                    + "OR t.topic_text LIKE '%Grammar focus%' OR t.topic_text LIKE '%Styles, channels and flow of communication%' "
                    + "OR t.topic_text LIKE '%Letter writing and email etiquette%' OR t.topic_text LIKE '%Reading comprehension%' "
                    + "OR t.topic_text LIKE '%Barriers to communication%' OR t.topic_text LIKE '%MODEL INGESTION%' "
                    + "OR t.topic_text LIKE '%Yesterday Was Beautiful%')");

            // 3. Remove font corruption duplicates (? prefix) in DS and TTPS
            s.executeUpdate("DELETE t FROM syllabus_topics t "
                    + "JOIN syllabus_units u ON u.unit_id = t.unit_id "
                    + "JOIN syllabus_subjects s ON s.subject_id = u.subject_id "
                    + "WHERE (s.course_code = 'UI25PC310CS' OR s.course_code = 'U25BS340MA') "
                    + "AND t.topic_text LIKE '? %'");

            // 4. Remove test topics from production DS subject
            s.executeUpdate("DELETE t FROM syllabus_topics t "
                    + "JOIN syllabus_units u ON u.unit_id = t.unit_id "
                    + "JOIN syllabus_subjects s ON s.subject_id = u.subject_id "
                    + "WHERE s.course_code = 'UI25PC310CS' AND ("
                    + "t.topic_text LIKE 'DS Unit %' OR t.topic_text = 'Original Topic')");

            // 5. Deduplicate subjects dynamically and safely: remap any duplicate subjects to MIN(subject_id)
            try (ResultSet rs = s.executeQuery(
                    "SELECT course_code, MIN(subject_id) as canonical_id, COUNT(*) as cnt "
                    + "FROM syllabus_subjects "
                    + "WHERE course_code IS NOT NULL AND course_code != '' AND course_code != 'UNKNOWN' AND course_code NOT LIKE '%XXXX%' "
                    + "GROUP BY course_code HAVING COUNT(*) > 1")) {
                java.util.List<long[]> duplicates = new java.util.ArrayList<>();
                while (rs.next()) {
                    long canonicalId = rs.getLong("canonical_id");
                    String code = rs.getString("course_code");
                    try (PreparedStatement p = connection.prepareStatement(
                            "SELECT subject_id FROM syllabus_subjects WHERE course_code = ? AND subject_id != ?")) {
                        p.setString(1, code);
                        p.setLong(2, canonicalId);
                        try (ResultSet r2 = p.executeQuery()) {
                            while (r2.next()) {
                                duplicates.add(new long[]{canonicalId, r2.getLong("subject_id")});
                            }
                        }
                    }
                }
                for (long[] pair : duplicates) {
                    long canonicalId = pair[0];
                    long duplicateId = pair[1];
                    try (PreparedStatement up = connection.prepareStatement(
                            "UPDATE IGNORE syllabus_document_subjects SET subject_id = ? WHERE subject_id = ?")) {
                        up.setLong(1, canonicalId);
                        up.setLong(2, duplicateId);
                        up.executeUpdate();
                    }
                    try (PreparedStatement del = connection.prepareStatement(
                            "DELETE FROM syllabus_subjects WHERE subject_id = ?")) {
                        del.setLong(1, duplicateId);
                        del.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Notice: repairLegacySyllabusContamination: " + e.getMessage());
        }
    }

    private void updateSyllabusDocumentStatusConstraint(Connection connection) {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("ALTER TABLE syllabus_documents DROP CHECK chk_syllabus_status");
        } catch (SQLException ignored) {
            // Constraint may not exist or cannot be dropped
        }
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("ALTER TABLE syllabus_documents ADD CONSTRAINT chk_syllabus_status "
                    + "CHECK (status IN ('PROCESSING', 'REVIEW_REQUIRED', 'REVIEW', 'APPROVED', 'FAILED'))");
        } catch (SQLException e) {
            System.err.println("Notice: chk_syllabus_status constraint update: " + e.getMessage());
        }
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        if (!columnExists(connection, table, column)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
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
