package studentperformance;

import studentperformance.syllabus.ExtractedCurriculum;
import studentperformance.syllabus.ExtractedDocumentMetadata;
import studentperformance.syllabus.ExtractedSubject;
import studentperformance.syllabus.ExtractedTopic;
import studentperformance.syllabus.ExtractedUnit;
import studentperformance.syllabus.ValidationReport;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC persistence for syllabus structures, source traceability, and approval lifecycle. */
public class SyllabusRepository {
    private final String url, user, password;

    public record DocumentSummary(
            long documentId,
            String filename,
            String status,
            String uploadedAt,
            String institution,
            String semester,
            int subjectCount,
            String extractionEngine,
            String pdfPath,
            Long parentDocumentId
    ) {
        public DocumentSummary(long documentId, String filename, String status, String uploadedAt) {
            this(documentId, filename, status, uploadedAt, "", "", 0, "LEGACY", null, null);
        }
        public DocumentSummary(long documentId, String filename, String status, String uploadedAt,
                               String institution, String semester, int subjectCount) {
            this(documentId, filename, status, uploadedAt, institution, semester, subjectCount, "LEGACY", null, null);
        }
    }

    public SyllabusRepository() {
        this(setting("DB_URL", "jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC"),
                setting("DB_USER", "root"), setting("DB_PASSWORD", "Teja.J@2007"));
    }

    public SyllabusRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public long saveApproved(SyllabusDocument document) throws SQLException {
        return saveDocument(document, "APPROVED");
    }

    public long saveReviewRequired(SyllabusDocument document) throws SQLException {
        return saveDocument(document, "REVIEW_REQUIRED");
    }

    public long saveDocument(SyllabusDocument document, String status) throws SQLException {
        if (status == null || (!status.equals("REVIEW_REQUIRED") && !status.equals("REVIEW")
                && !status.equals("APPROVED") && !status.equals("PROCESSING") && !status.equals("FAILED"))) {
            status = "REVIEW_REQUIRED";
        }

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try {
                long docId;
                // 1. Repeated upload handling: Check if document with same filename exists
                Long existingDocId = null;
                String existingStatus = null;
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT document_id, status FROM syllabus_documents WHERE original_filename = ? ORDER BY document_id DESC LIMIT 1")) {
                    p.setString(1, document.getFilename());
                    try (ResultSet rs = p.executeQuery()) {
                        if (rs.next()) {
                            existingDocId = rs.getLong(1);
                            existingStatus = rs.getString(2);
                        }
                    }
                }

                // Preserve historical APPROVED documents: do not overwrite an approved document merely because a draft was uploaded
                boolean isDraftOverwritingApproved = "APPROVED".equalsIgnoreCase(existingStatus)
                        && !"APPROVED".equalsIgnoreCase(status);
                boolean isReprocessRevision = document.getParentDocumentId() != null;

                if (existingDocId != null && !isDraftOverwritingApproved && !isReprocessRevision) {
                    docId = existingDocId;
                    try (PreparedStatement p = c.prepareStatement(
                            "UPDATE syllabus_documents SET status = ?, uploaded_at = CURRENT_TIMESTAMP, "
                            + "institution = COALESCE(NULLIF(?, ''), institution), "
                            + "academic_scheme = COALESCE(NULLIF(?, ''), academic_scheme), "
                            + "academic_year = COALESCE(NULLIF(?, ''), academic_year), "
                            + "branch = COALESCE(NULLIF(?, ''), branch), "
                            + "semester = COALESCE(NULLIF(?, ''), semester), "
                            + "validation_report = COALESCE(NULLIF(?, ''), validation_report), "
                            + "error_message = ?, "
                            + "extraction_engine = COALESCE(NULLIF(?, ''), extraction_engine), "
                            + "pdf_path = COALESCE(NULLIF(?, ''), pdf_path), "
                            + "parent_document_id = COALESCE(?, parent_document_id) "
                            + "WHERE document_id = ?")) {
                        p.setString(1, status);
                        p.setString(2, document.getInstitution());
                        p.setString(3, document.getAcademicScheme());
                        p.setString(4, document.getAcademicYear());
                        p.setString(5, document.getBranch());
                        p.setString(6, document.getSemester());
                        p.setString(7, document.getValidationReportJson());
                        p.setString(8, document.getErrorMessage());
                        p.setString(9, document.getExtractionEngine());
                        p.setString(10, document.getPdfPath());
                        if (document.getParentDocumentId() != null) p.setLong(11, document.getParentDocumentId());
                        else p.setNull(11, Types.BIGINT);
                        p.setLong(12, docId);
                        p.executeUpdate();
                    }
                } else {
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT INTO syllabus_documents(original_filename, status, institution, academic_scheme, academic_year, branch, semester, validation_report, error_message, extraction_engine, pdf_path, parent_document_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        p.setString(1, document.getFilename());
                        p.setString(2, status);
                        p.setString(3, document.getInstitution());
                        p.setString(4, document.getAcademicScheme());
                        p.setString(5, document.getAcademicYear());
                        p.setString(6, document.getBranch());
                        p.setString(7, document.getSemester());
                        p.setString(8, document.getValidationReportJson());
                        p.setString(9, document.getErrorMessage());
                        p.setString(10, document.getExtractionEngine() != null ? document.getExtractionEngine() : "LEGACY");
                        p.setString(11, document.getPdfPath());
                        if (document.getParentDocumentId() != null) p.setLong(12, document.getParentDocumentId());
                        else p.setNull(12, Types.BIGINT);
                        p.executeUpdate();
                        try (ResultSet keys = p.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Database did not return the syllabus ID.");
                            docId = keys.getLong(1);
                        }
                    }
                }

                // 2. Process subjects duplicate-safely:
                // Canonical subjects are reused by default, while revision drafts isolate subject records to protect parent documents
                boolean isolateSubject = document.getParentDocumentId() != null;
                for (int si = 0; si < document.getSubjects().size(); si++) {
                    SyllabusSubject subject = document.getSubjects().get(si);
                    long subjectId = getOrCreateSubject(c, docId, subject, si, isolateSubject);

                    // Link document and subject in junction table
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT IGNORE INTO syllabus_document_subjects(document_id, subject_id) VALUES (?, ?)")) {
                        p.setLong(1, docId);
                        p.setLong(2, subjectId);
                        p.executeUpdate();
                    }

                    // 3. Process units duplicate-safely
                    for (int ui = 0; ui < subject.getUnits().size(); ui++) {
                        SyllabusUnit unit = subject.getUnits().get(ui);
                        long unitId = getOrCreateUnit(c, subjectId, unit, ui);

                        // 4. Process topics duplicate-safely
                        for (int ti = 0; ti < unit.getTopics().size(); ti++) {
                            saveTopicSafe(c, unitId, null, unit.getTopics().get(ti), ti);
                        }
                    }
                }

                c.commit();
                return docId;
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    public long saveFailedDocument(String filename, String errorMessage, String reportJson) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO syllabus_documents(original_filename, status, error_message, validation_report) VALUES (?, 'FAILED', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, filename);
                p.setString(2, errorMessage);
                p.setString(3, reportJson);
                p.executeUpdate();
                try (ResultSet keys = p.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
            }
        }
        return -1;
    }

    private long getOrCreateSubject(Connection c, long docId, SyllabusSubject subject, int sortOrder) throws SQLException {
        return getOrCreateSubject(c, docId, subject, sortOrder, false);
    }

    private long getOrCreateSubject(Connection c, long docId, SyllabusSubject subject, int sortOrder, boolean isolate) throws SQLException {
        Long existingId = null;
        String courseCode = subject.getCourseCode() == null ? "" : subject.getCourseCode().trim().toUpperCase(java.util.Locale.ROOT);

        // 1. If docId > 0, first check if this specific document already owns a subject for this course
        if (docId > 0) {
            if (!courseCode.isBlank() && !courseCode.equals("UNKNOWN") && !courseCode.contains("XXXX")) {
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT subject_id FROM syllabus_subjects WHERE document_id = ? AND course_code = ?")) {
                    p.setLong(1, docId);
                    p.setString(2, courseCode);
                    try (ResultSet rs = p.executeQuery()) {
                        if (rs.next()) existingId = rs.getLong(1);
                    }
                }
            } else if (subject.isOpenElective() || (subject.getName() != null && subject.getName().toUpperCase(java.util.Locale.ROOT).contains("OPEN ELECTIVE"))) {
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT subject_id FROM syllabus_subjects WHERE document_id = ? AND (is_open_elective = TRUE OR UPPER(subject_name) LIKE '%OPEN ELECTIVE%') ORDER BY subject_id LIMIT 1")) {
                    p.setLong(1, docId);
                    try (ResultSet rs = p.executeQuery()) {
                        if (rs.next()) existingId = rs.getLong(1);
                    }
                }
            }
        }

        // 2. If not found for this document and not isolated, match globally
        if (existingId == null && !isolate) {
            // 2a. Match by course code
            if (!courseCode.isBlank() && !courseCode.equals("UNKNOWN") && !courseCode.contains("XXXX")) {
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT subject_id FROM syllabus_subjects WHERE course_code = ? ORDER BY subject_id ASC")) {
                    p.setString(1, courseCode);
                    try (ResultSet rs = p.executeQuery()) {
                        if (rs.next()) existingId = rs.getLong(1);
                    }
                }
            }

            // 2b. If not found by course code, match by semester subject enum only if course code is unknown
            if (existingId == null && (courseCode.isBlank() || courseCode.equals("UNKNOWN"))) {
                Subject matched = subject.getMatchedSemesterSubject();
                if (matched != null && matched != Subject.OE) {
                    try (PreparedStatement p = c.prepareStatement(
                            "SELECT subject_id FROM syllabus_subjects WHERE course_code = ? OR UPPER(TRIM(subject_name)) = ? OR UPPER(TRIM(subject_name)) = ? ORDER BY subject_id ASC")) {
                        p.setString(1, matched.getCourseCode());
                        p.setString(2, matched.getFullName().toUpperCase(java.util.Locale.ROOT));
                        p.setString(3, matched.getDisplayName().toUpperCase(java.util.Locale.ROOT));
                        try (ResultSet rs = p.executeQuery()) {
                            if (rs.next()) existingId = rs.getLong(1);
                        }
                    }
                }
            }

            // 2c. If still not found, match by exact subject name (excluding generic open electives) only if course code is unknown
            if (existingId == null && (courseCode.isBlank() || courseCode.equals("UNKNOWN")) && !subject.isOpenElective()) {
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT subject_id FROM syllabus_subjects WHERE UPPER(TRIM(subject_name)) = ? ORDER BY subject_id ASC")) {
                    p.setString(1, subject.getName().trim().toUpperCase(java.util.Locale.ROOT));
                    try (ResultSet rs = p.executeQuery()) {
                        if (rs.next()) existingId = rs.getLong(1);
                    }
                }
            }
        }

        if (existingId != null) {
            try (PreparedStatement p = c.prepareStatement(
                    "UPDATE syllabus_subjects SET "
                    + "course_code = CASE WHEN (course_code IS NULL OR course_code = '' OR course_code = 'UNKNOWN') THEN ? ELSE course_code END, "
                    + "credits = CASE WHEN (credits IS NULL OR credits = '') THEN ? ELSE credits END, "
                    + "total_marks = CASE WHEN (total_marks IS NULL OR total_marks = '') THEN ? ELSE total_marks END, "
                    + "department_code = CASE WHEN (department_code IS NULL OR department_code = '' OR department_code = 'UNKNOWN') THEN ? ELSE department_code END, "
                    + "semester = CASE WHEN (semester IS NULL OR semester = '' OR semester = 'UNKNOWN') THEN ? ELSE semester END "
                    + "WHERE subject_id = ?")) {
                p.setString(1, subject.getCourseCode());
                p.setString(2, subject.getCredits());
                p.setString(3, subject.getTotalMarks());
                p.setString(4, subject.getDepartmentCode());
                p.setString(5, subject.getSemester());
                p.setLong(6, existingId);
                p.executeUpdate();
            }
            return existingId;
        }

        // Insert new subject
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO syllabus_subjects(document_id, subject_name, department_code, semester, course_code, credits, total_marks, is_open_elective, course_category, sort_order) VALUES (?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, docId);
            p.setString(2, subject.getName());
            p.setString(3, subject.getDepartmentCode());
            p.setString(4, subject.getSemester());
            p.setString(5, subject.getCourseCode());
            p.setString(6, subject.getCredits());
            p.setString(7, subject.getTotalMarks());
            p.setBoolean(8, subject.isOpenElective());
            p.setString(9, subject.getCategory().name());
            p.setInt(10, sortOrder);
            p.executeUpdate();
            try (ResultSet k = p.getGeneratedKeys()) {
                if (!k.next()) throw new SQLException("Database did not return subject ID.");
                return k.getLong(1);
            }
        }
    }

    private long getOrCreateUnit(Connection c, long subjectId, SyllabusUnit unit, int sortOrder) throws SQLException {
        Long existingUnitId = null;
        String existingTitle = null;
        try (PreparedStatement p = c.prepareStatement(
                "SELECT unit_id, unit_title FROM syllabus_units WHERE subject_id = ? AND unit_number = ?")) {
            p.setLong(1, subjectId);
            p.setInt(2, unit.getNumber());
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    existingUnitId = rs.getLong(1);
                    existingTitle = rs.getString(2);
                }
            }
        }

        if (existingUnitId != null) {
            String newTitle = unit.getTitle();
            boolean isExistingGeneric = existingTitle == null || existingTitle.isBlank() || existingTitle.matches("(?i)^unit\\s*[-–—:：.?]?[ivx0-9]+$");
            boolean isNewDescriptive = newTitle != null && !newTitle.isBlank() && !newTitle.matches("(?i)^unit\\s*[-–—:：.?]?[ivx0-9]+$");
            if (isExistingGeneric && isNewDescriptive) {
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE syllabus_units SET unit_title = ?, page_number = COALESCE(?, page_number) WHERE unit_id = ?")) {
                    p.setString(1, newTitle.trim());
                    if (unit.getPageNumber() != null) p.setInt(2, unit.getPageNumber());
                    else p.setNull(2, Types.INTEGER);
                    p.setLong(3, existingUnitId);
                    p.executeUpdate();
                }
            } else if (unit.getPageNumber() != null) {
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE syllabus_units SET page_number = COALESCE(?, page_number) WHERE unit_id = ?")) {
                    p.setInt(1, unit.getPageNumber());
                    p.setLong(2, existingUnitId);
                    p.executeUpdate();
                }
            }
            return existingUnitId;
        }

        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO syllabus_units(subject_id, unit_number, unit_title, sort_order, page_number) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, subjectId);
            p.setInt(2, unit.getNumber());
            p.setString(3, unit.getTitle() == null || unit.getTitle().isBlank() ? "Unit " + unit.getNumber() : unit.getTitle().trim());
            p.setInt(4, sortOrder);
            if (unit.getPageNumber() != null) p.setInt(5, unit.getPageNumber());
            else p.setNull(5, Types.INTEGER);
            p.executeUpdate();
            try (ResultSet k = p.getGeneratedKeys()) {
                if (!k.next()) throw new SQLException("Database did not return unit ID.");
                return k.getLong(1);
            }
        }
    }

    private void saveTopicSafe(Connection c, long unitId, Long parentId, SyllabusTopic topic, int order) throws SQLException {
        if (topic == null || topic.getText() == null || topic.getText().isBlank()) return;
        String text = topic.getText().trim();
        Long topicId = findExistingTopic(c, unitId, parentId, text);
        if (topicId == null) {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO syllabus_topics(unit_id, parent_topic_id, topic_text, sort_order, page_number) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setLong(1, unitId);
                if (parentId == null) p.setNull(2, Types.BIGINT);
                else p.setLong(2, parentId);
                p.setString(3, text);
                p.setInt(4, order);
                if (topic.getPageNumber() != null) p.setInt(5, topic.getPageNumber());
                else p.setNull(5, Types.INTEGER);
                p.executeUpdate();
                try (ResultSet k = p.getGeneratedKeys()) {
                    if (k.next()) {
                        topicId = k.getLong(1);
                    }
                }
            }
        } else {
            try (PreparedStatement p = c.prepareStatement(
                    "UPDATE syllabus_topics SET sort_order = ?, page_number = COALESCE(?, page_number) WHERE topic_id = ?")) {
                p.setInt(1, order);
                if (topic.getPageNumber() != null) p.setInt(2, topic.getPageNumber());
                else p.setNull(2, Types.INTEGER);
                p.setLong(3, topicId);
                p.executeUpdate();
            }
        }
        if (topicId != null) {
            for (int i = 0; i < topic.getSubtopics().size(); i++) {
                saveTopicSafe(c, unitId, topicId, topic.getSubtopics().get(i), i);
            }
        }
    }

    private Long findExistingTopic(Connection c, long unitId, Long parentId, String text) throws SQLException {
        String sql = parentId == null
                ? "SELECT topic_id FROM syllabus_topics WHERE unit_id = ? AND parent_topic_id IS NULL AND LOWER(TRIM(topic_text)) = LOWER(TRIM(?))"
                : "SELECT topic_id FROM syllabus_topics WHERE unit_id = ? AND parent_topic_id = ? AND LOWER(TRIM(topic_text)) = LOWER(TRIM(?))";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, unitId);
            if (parentId == null) {
                p.setString(2, text);
            } else {
                p.setLong(2, parentId);
                p.setString(3, text);
            }
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    public List<DocumentSummary> findAllDocumentSummaries() throws SQLException {
        List<DocumentSummary> list = new ArrayList<>();
        String sql = "SELECT d.document_id, d.original_filename, d.status, d.uploaded_at, "
                + "d.institution, d.semester, d.extraction_engine, d.pdf_path, d.parent_document_id, "
                + "COUNT(DISTINCT ds.subject_id) AS sub_count "
                + "FROM syllabus_documents d "
                + "LEFT JOIN syllabus_document_subjects ds ON ds.document_id = d.document_id "
                + "GROUP BY d.document_id, d.original_filename, d.status, d.uploaded_at, d.institution, d.semester, d.extraction_engine, d.pdf_path, d.parent_document_id "
                + "ORDER BY d.document_id DESC";
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                Long parentId = r.getObject("parent_document_id") != null ? r.getLong("parent_document_id") : null;
                list.add(new DocumentSummary(
                        r.getLong("document_id"),
                        rString(r, "original_filename"),
                        rString(r, "status"),
                        rString(r, "uploaded_at"),
                        rString(r, "institution"),
                        rString(r, "semester"),
                        r.getInt("sub_count"),
                        rString(r, "extraction_engine"),
                        rString(r, "pdf_path"),
                        parentId
                ));
            }
        }
        return list;
    }

    private static final String BASE_LOAD_QUERY =
            "SELECT d.document_id, d.original_filename, d.status, d.uploaded_at, "
            + "d.institution, d.academic_scheme, d.academic_year, d.branch, d.semester, d.validation_report, d.error_message, "
            + "d.extraction_engine, d.pdf_path, d.parent_document_id, "
            + "s.subject_id, s.subject_name, s.department_code, s.semester AS sub_sem, s.course_code, s.credits, s.total_marks, s.is_open_elective, s.course_category, "
            + "u.unit_id, u.unit_number, u.unit_title, u.page_number AS unit_page, "
            + "t.topic_id, t.parent_topic_id, t.unit_id AS topic_unit, t.topic_text, t.page_number AS topic_page "
            + "FROM syllabus_documents d "
            + "LEFT JOIN syllabus_document_subjects ds ON ds.document_id = d.document_id "
            + "LEFT JOIN syllabus_subjects s ON s.subject_id = ds.subject_id "
            + "LEFT JOIN syllabus_units u ON u.subject_id = s.subject_id "
            + "LEFT JOIN syllabus_topics t ON t.unit_id = u.unit_id ";

    public SyllabusDocument findById(long targetId) throws SQLException {
        String sql = BASE_LOAD_QUERY + "WHERE d.document_id = ? "
                + "ORDER BY d.document_id, s.sort_order, s.subject_id, u.sort_order, u.unit_number, t.sort_order, t.topic_id";
        List<SyllabusDocument> docs = loadDocumentsFromQuery(sql, targetId);
        return docs.isEmpty() ? null : docs.get(0);
    }

    public List<SyllabusDocument> findApproved() throws SQLException {
        String sql = BASE_LOAD_QUERY + "WHERE d.status = 'APPROVED' "
                + "ORDER BY d.document_id, s.sort_order, s.subject_id, u.sort_order, u.unit_number, t.sort_order, t.topic_id";
        return loadDocumentsFromQuery(sql, null);
    }

    public List<SyllabusDocument> findReviewRequired() throws SQLException {
        String sql = BASE_LOAD_QUERY + "WHERE d.status IN ('REVIEW_REQUIRED', 'REVIEW') "
                + "ORDER BY d.document_id, s.sort_order, s.subject_id, u.sort_order, u.unit_number, t.sort_order, t.topic_id";
        return loadDocumentsFromQuery(sql, null);
    }

    public boolean approveDocument(long documentId) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try {
                String currentStatus = null;
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT status FROM syllabus_documents WHERE document_id = ? FOR UPDATE")) {
                    p.setLong(1, documentId);
                    try (ResultSet rs = p.executeQuery()) {
                        if (rs.next()) {
                            currentStatus = rs.getString(1);
                        }
                    }
                }
                if (currentStatus == null) {
                    throw new SQLException("Syllabus document with ID " + documentId + " does not exist.");
                }
                if (!"REVIEW_REQUIRED".equalsIgnoreCase(currentStatus) && !"REVIEW".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Only documents in REVIEW_REQUIRED or REVIEW state can be approved. Current status: " + currentStatus);
                }

                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE syllabus_documents SET status = 'APPROVED' WHERE document_id = ?")) {
                    p.setLong(1, documentId);
                    int updated = p.executeUpdate();
                    c.commit();
                    return updated > 0;
                }
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public boolean rejectDocument(long documentId, String reason) throws SQLException {
        return updateDocumentStatus(documentId, "FAILED", reason);
    }

    public boolean updateDocumentStatus(long documentId, String status) throws SQLException {
        return updateDocumentStatus(documentId, status, null);
    }

    public boolean updateDocumentStatus(long documentId, String status, String errorMessage) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement(
                     "UPDATE syllabus_documents SET status = ?, error_message = COALESCE(?, error_message) WHERE document_id = ?")) {
            p.setString(1, status);
            p.setString(2, errorMessage);
            p.setLong(3, documentId);
            return p.executeUpdate() > 0;
        }
    }

    private List<SyllabusDocument> loadDocumentsFromQuery(String sql, Long targetId) throws SQLException {
        Map<Long, SyllabusDocument> docs = new LinkedHashMap<>();
        Map<String, SyllabusSubject> docSubjects = new LinkedHashMap<>();
        Map<String, SyllabusUnit> docUnits = new LinkedHashMap<>();
        Map<String, SyllabusTopic> docTopics = new LinkedHashMap<>();
        Map<String, String> unitByTopic = new LinkedHashMap<>();
        Map<String, String> parentByTopic = new LinkedHashMap<>();

        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement(sql)) {
            if (targetId != null) {
                p.setLong(1, targetId);
            }
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    long did = r.getLong("document_id");
                    SyllabusDocument doc = docs.computeIfAbsent(did, k -> {
                        SyllabusDocument newDoc = new SyllabusDocument(rString(r, "original_filename"));
                        newDoc.setDocumentId(did);
                        newDoc.setStatus(rString(r, "status"));
                        newDoc.setUploadedAt(rString(r, "uploaded_at"));
                        newDoc.setInstitution(rString(r, "institution"));
                        newDoc.setAcademicScheme(rString(r, "academic_scheme"));
                        newDoc.setAcademicYear(rString(r, "academic_year"));
                        newDoc.setBranch(rString(r, "branch"));
                        newDoc.setSemester(rString(r, "semester"));
                        newDoc.setValidationReportJson(rString(r, "validation_report"));
                        newDoc.setErrorMessage(rString(r, "error_message"));
                        newDoc.setExtractionEngine(rString(r, "extraction_engine"));
                        newDoc.setPdfPath(rString(r, "pdf_path"));
                        try {
                            long pid = r.getLong("parent_document_id");
                            if (!r.wasNull()) newDoc.setParentDocumentId(pid);
                        } catch (SQLException ignored) {}
                        return newDoc;
                    });

                    long sid = r.getLong("subject_id");
                    if (!r.wasNull()) {
                        String docSubKey = did + "_" + sid;
                        SyllabusSubject s = docSubjects.computeIfAbsent(docSubKey, k -> {
                            SyllabusSubject newSub = new SyllabusSubject(rString(r, "subject_name"));
                            newSub.setDepartmentCode(rString(r, "department_code"));
                            newSub.setSemester(rString(r, "semester"));
                            newSub.setCourseCode(rString(r, "course_code"));
                            newSub.setCredits(rString(r, "credits"));
                            newSub.setTotalMarks(rString(r, "total_marks"));
                            try {
                                newSub.setCategory(SyllabusCategory.valueOf(r.getString("course_category")));
                            } catch (Exception e) {
                                newSub.setCategory(SyllabusCategory.OTHER);
                            }
                            try {
                                newSub.setOpenElective(r.getBoolean("is_open_elective"));
                            } catch (SQLException e) {
                                throw new IllegalStateException(e);
                            }
                            doc.getSubjects().add(newSub);
                            return newSub;
                        });

                        long uid = r.getLong("unit_id");
                        if (!r.wasNull()) {
                            String docUnitKey = did + "_" + sid + "_" + uid;
                            docUnits.computeIfAbsent(docUnitKey, k -> {
                                int uPage = 0;
                                Integer uPageObj = null;
                                try {
                                    uPage = r.getInt("unit_page");
                                    if (!r.wasNull()) uPageObj = uPage;
                                } catch (SQLException ignored) {}
                                SyllabusUnit newUnit = new SyllabusUnit(rInt(r, "unit_number"), rString(r, "unit_title"), uPageObj);
                                s.getUnits().add(newUnit);
                                return newUnit;
                            });

                            long tid = r.getLong("topic_id");
                            if (!r.wasNull() && !docTopics.containsKey(did + "_" + tid)) {
                                String topicKey = did + "_" + tid;
                                int tPage = 0;
                                Integer tPageObj = null;
                                try {
                                    tPage = r.getInt("topic_page");
                                    if (!r.wasNull()) tPageObj = tPage;
                                } catch (SQLException ignored) {}
                                SyllabusTopic t = new SyllabusTopic(rString(r, "topic_text"), tPageObj);
                                docTopics.put(topicKey, t);
                                unitByTopic.put(topicKey, docUnitKey);
                                long par = r.getLong("parent_topic_id");
                                parentByTopic.put(topicKey, r.wasNull() ? null : (did + "_" + par));
                            }
                        }
                    }
                }
            }
        }

        for (var entry : docTopics.entrySet()) {
            String parKey = parentByTopic.get(entry.getKey());
            if (parKey == null) {
                String uKey = unitByTopic.get(entry.getKey());
                if (docUnits.containsKey(uKey)) {
                    docUnits.get(uKey).getTopics().add(entry.getValue());
                }
            } else if (docTopics.containsKey(parKey)) {
                docTopics.get(parKey).getSubtopics().add(entry.getValue());
            }
        }
        return new ArrayList<>(docs.values());
    }

    public SyllabusDocument fromExtractedCurriculum(String filename, ExtractedCurriculum curriculum, ValidationReport report) {
        SyllabusDocument doc = new SyllabusDocument(filename);
        doc.setStatus("REVIEW_REQUIRED");
        doc.setExtractionEngine("GEMINI_FLASH_LITE");
        if (curriculum.getDocument() != null) {
            doc.setInstitution(curriculum.getDocument().getInstitution());
            doc.setAcademicScheme(curriculum.getDocument().getAcademicScheme());
            doc.setAcademicYear(curriculum.getDocument().getAcademicYear());
            doc.setBranch(curriculum.getDocument().getBranch());
            doc.setSemester(curriculum.getDocument().getSemester());
        }
        if (report != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                doc.setValidationReportJson(mapper.writeValueAsString(report));
            } catch (Exception e) {
                doc.setValidationReportJson(report.formatConsoleSummary());
            }
        }
        for (ExtractedSubject es : curriculum.getSubjects()) {
            // Ignore empty subjects with 0 units and no course code
            if (es.getUnits().isEmpty() && (es.getCourseCode() == null || es.getCourseCode().isBlank())) {
                continue;
            }
            // Ignore incomplete trailer headings with 0 units unless it is an Open Elective
            if (!es.isOpenElective() && es.getUnits().isEmpty()) {
                if (report != null) {
                    boolean isInvalid = report.getSubjectResults().stream()
                            .anyMatch(sr -> sr.getSubjectName().equalsIgnoreCase(es.getSubjectName()) && !sr.isValid());
                    if (isInvalid) {
                        continue;
                    }
                }
            }
            SyllabusSubject s = new SyllabusSubject(es.getSubjectName());
            s.setCourseCode(es.getCourseCode());
            s.setCredits(es.getCredits());
            s.setSemester(es.getSemester() != null && !es.getSemester().isBlank() ? es.getSemester() : doc.getSemester());
            s.setOpenElective(es.isOpenElective());
            try {
                s.setCategory(SyllabusCategory.valueOf(es.getCategory()));
            } catch (Exception e) {
                s.setCategory(es.isOpenElective() ? SyllabusCategory.OPEN_ELECTIVE : SyllabusCategory.THEORY);
            }
            for (ExtractedUnit eu : es.getUnits()) {
                SyllabusUnit u = new SyllabusUnit(eu.getUnitNumber(), eu.getUnitTitle(), eu.getPageNumber());
                for (ExtractedTopic et : eu.getTopics()) {
                    u.getTopics().add(new SyllabusTopic(et.getTopicText(), et.getPageNumber()));
                }
                s.getUnits().add(u);
            }
            doc.getSubjects().add(s);
        }
        return doc;
    }

    public ExtractedCurriculum toExtractedCurriculum(SyllabusDocument doc) {
        ExtractedCurriculum curriculum = new ExtractedCurriculum();
        ExtractedDocumentMetadata meta = new ExtractedDocumentMetadata();
        meta.setInstitution(doc.getInstitution() != null ? doc.getInstitution() : "");
        meta.setAcademicScheme(doc.getAcademicScheme() != null ? doc.getAcademicScheme() : "");
        meta.setAcademicYear(doc.getAcademicYear() != null ? doc.getAcademicYear() : "");
        meta.setBranch(doc.getBranch() != null ? doc.getBranch() : "");
        meta.setSemester(doc.getSemester() != null ? doc.getSemester() : "");
        curriculum.setDocument(meta);

        for (SyllabusSubject s : doc.getSubjects()) {
            ExtractedSubject es = new ExtractedSubject(s.getName());
            es.setCourseCode(s.getCourseCode() != null ? s.getCourseCode() : "");
            es.setCredits(s.getCredits() != null ? s.getCredits() : "");
            es.setCategory(s.getCategory() != null ? s.getCategory().name() : "THEORY");
            es.setSemester(s.getSemester() != null ? s.getSemester() : doc.getSemester());
            es.setOpenElective(s.isOpenElective());

            for (SyllabusUnit u : s.getUnits()) {
                ExtractedUnit eu = new ExtractedUnit(u.getNumber(), u.getTitle(), u.getPageNumber());
                for (SyllabusTopic t : u.getTopics()) {
                    eu.getTopics().add(new ExtractedTopic(t.getText(), t.getPageNumber()));
                }
                es.getUnits().add(eu);
            }
            curriculum.getSubjects().add(es);
        }
        return curriculum;
    }

    public boolean updateValidationReport(long documentId, String reportJson) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement(
                     "UPDATE syllabus_documents SET validation_report = ? WHERE document_id = ?")) {
            p.setString(1, reportJson);
            p.setLong(2, documentId);
            return p.executeUpdate() > 0;
        }
    }

    public void updateDocumentCurriculum(long docId, SyllabusDocument updatedDoc, ValidationReport report) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try {
                // 1. Verify document exists and is NOT APPROVED
                String currentStatus = null;
                try (PreparedStatement p = c.prepareStatement("SELECT status FROM syllabus_documents WHERE document_id = ? FOR UPDATE")) {
                    p.setLong(1, docId);
                    try (ResultSet rs = p.executeQuery()) {
                        if (rs.next()) currentStatus = rs.getString(1);
                    }
                }
                if (currentStatus == null) {
                    throw new SQLException("Syllabus document with ID " + docId + " does not exist.");
                }
                if ("APPROVED".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Cannot edit an APPROVED syllabus document.");
                }

                // 2. Update document metadata, report, and extraction engine
                String reportJson = updatedDoc.getValidationReportJson();
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE syllabus_documents SET institution = ?, academic_scheme = ?, academic_year = ?, branch = ?, semester = ?, validation_report = ?, "
                        + "extraction_engine = COALESCE(NULLIF(?, ''), extraction_engine), pdf_path = COALESCE(NULLIF(?, ''), pdf_path) "
                        + "WHERE document_id = ?")) {
                    p.setString(1, updatedDoc.getInstitution());
                    p.setString(2, updatedDoc.getAcademicScheme());
                    p.setString(3, updatedDoc.getAcademicYear());
                    p.setString(4, updatedDoc.getBranch());
                    p.setString(5, updatedDoc.getSemester());
                    p.setString(6, reportJson);
                    p.setString(7, updatedDoc.getExtractionEngine());
                    p.setString(8, updatedDoc.getPdfPath());
                    p.setLong(9, docId);
                    p.executeUpdate();
                }

                // 3. Clear existing junction links for this document (preserve canonical subjects)
                try (PreparedStatement p = c.prepareStatement("DELETE FROM syllabus_document_subjects WHERE document_id = ?")) {
                    p.setLong(1, docId);
                    p.executeUpdate();
                }

                // 4. Process updated subjects, units, and topics with exact sequential ordering
                for (int si = 0; si < updatedDoc.getSubjects().size(); si++) {
                    SyllabusSubject subject = updatedDoc.getSubjects().get(si);
                    long subjectId = getOrCreateSubject(c, docId, subject, si, true);

                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT IGNORE INTO syllabus_document_subjects(document_id, subject_id) VALUES (?, ?)")) {
                        p.setLong(1, docId);
                        p.setLong(2, subjectId);
                        p.executeUpdate();
                    }

                    // For document-specific subjects owned by docId, clear existing units so refreshed curriculum matches validation report exactly
                    try (PreparedStatement delU = c.prepareStatement(
                            "DELETE FROM syllabus_units WHERE subject_id = ? AND EXISTS (SELECT 1 FROM syllabus_subjects WHERE subject_id = ? AND document_id = ?)")) {
                        delU.setLong(1, subjectId);
                        delU.setLong(2, subjectId);
                        delU.setLong(3, docId);
                        delU.executeUpdate();
                    }

                    // Process units
                    for (int ui = 0; ui < subject.getUnits().size(); ui++) {
                        SyllabusUnit unit = subject.getUnits().get(ui);
                        long unitId = getOrCreateUnit(c, subjectId, unit, ui);

                        // Process topics with exact sort order
                        for (int ti = 0; ti < unit.getTopics().size(); ti++) {
                            saveTopicSafe(c, unitId, null, unit.getTopics().get(ti), ti);
                        }
                    }
                }

                c.commit();
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    private static String rString(ResultSet r, String col) {
        try {
            String val = r.getString(col);
            return val == null ? "" : val;
        } catch (SQLException e) {
            return "";
        }
    }

    private static int rInt(ResultSet r, String col) {
        try {
            return r.getInt(col);
        } catch (SQLException e) {
            return 0;
        }
    }

    public Map<Integer, List<Integer>> getInternalUnitMapping() throws SQLException {
        Map<Integer, List<Integer>> mapping = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement("SELECT internal_number, unit_number FROM internal_unit_mapping ORDER BY internal_number, unit_number");
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                mapping.computeIfAbsent(r.getInt("internal_number"), k -> new ArrayList<>())
                        .add(r.getInt("unit_number"));
            }
        }
        return mapping;
    }

    public void setInternalUnitMapping(int internal, List<Integer> units) throws SQLException {
        if (internal < 1 || internal > 2 || units == null || units.isEmpty() || units.stream().anyMatch(u -> u < 1) || units.stream().distinct().count() != units.size()) {
            throw new IllegalArgumentException("Provide unique positive unit numbers for internal 1 or 2.");
        }
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try (PreparedStatement d = c.prepareStatement("DELETE FROM internal_unit_mapping WHERE internal_number = ?")) {
                d.setInt(1, internal);
                d.executeUpdate();
                try (PreparedStatement p = c.prepareStatement("INSERT INTO internal_unit_mapping(internal_number, unit_number) VALUES (?,?)")) {
                    for (int unit : units) {
                        p.setInt(1, internal);
                        p.setInt(2, unit);
                        p.addBatch();
                    }
                    p.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
