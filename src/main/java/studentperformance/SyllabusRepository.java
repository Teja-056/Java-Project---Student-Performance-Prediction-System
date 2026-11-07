package studentperformance;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC persistence for syllabus structures and approval lifecycle. */
public class SyllabusRepository {
    private final String url, user, password;

    public record DocumentSummary(long documentId, String filename, String status, String uploadedAt) {}

    public SyllabusRepository() {
        this(setting("DB_URL", "jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC"),
                setting("DB_USER", "root"), setting("DB_PASSWORD", ""));
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

    public long saveDocument(SyllabusDocument document, String status) throws SQLException {
        if (status == null || (!status.equals("REVIEW") && !status.equals("APPROVED"))) {
            status = "APPROVED";
        }
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO syllabus_documents(original_filename, status) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, document.getFilename());
                    p.setString(2, status);
                    p.executeUpdate();
                    try (ResultSet keys = p.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Database did not return the syllabus ID.");
                        id = keys.getLong(1);
                    }
                }
                for (int si = 0; si < document.getSubjects().size(); si++) {
                    SyllabusSubject subject = document.getSubjects().get(si);
                    long subjectId;
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT INTO syllabus_subjects(document_id, subject_name, department_code, semester, course_code, credits, total_marks, is_open_elective, course_category, sort_order) VALUES (?,?,?,?,?,?,?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        p.setLong(1, id);
                        p.setString(2, subject.getName());
                        p.setString(3, subject.getDepartmentCode());
                        p.setString(4, subject.getSemester());
                        p.setString(5, subject.getCourseCode());
                        p.setString(6, subject.getCredits());
                        p.setString(7, subject.getTotalMarks());
                        p.setBoolean(8, subject.isOpenElective());
                        p.setString(9, subject.getCategory().name());
                        p.setInt(10, si);
                        p.executeUpdate();
                        try (ResultSet k = p.getGeneratedKeys()) {
                            k.next();
                            subjectId = k.getLong(1);
                        }
                    }
                    for (int ui = 0; ui < subject.getUnits().size(); ui++) {
                        SyllabusUnit unit = subject.getUnits().get(ui);
                        long unitId;
                        try (PreparedStatement p = c.prepareStatement(
                                "INSERT INTO syllabus_units(subject_id, unit_number, unit_title, sort_order) VALUES (?,?,?,?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                            p.setLong(1, subjectId);
                            p.setInt(2, unit.getNumber());
                            p.setString(3, unit.getTitle());
                            p.setInt(4, ui);
                            p.executeUpdate();
                            try (ResultSet k = p.getGeneratedKeys()) {
                                k.next();
                                unitId = k.getLong(1);
                            }
                        }
                        for (int ti = 0; ti < unit.getTopics().size(); ti++) {
                            saveTopic(c, unitId, null, unit.getTopics().get(ti), ti);
                        }
                    }
                }
                c.commit();
                return id;
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    private void saveTopic(Connection c, long unitId, Long parent, SyllabusTopic topic, int order) throws SQLException {
        long id;
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO syllabus_topics(unit_id, parent_topic_id, topic_text, sort_order) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, unitId);
            if (parent == null) p.setNull(2, Types.BIGINT);
            else p.setLong(2, parent);
            p.setString(3, topic.getText());
            p.setInt(4, order);
            p.executeUpdate();
            try (ResultSet k = p.getGeneratedKeys()) {
                k.next();
                id = k.getLong(1);
            }
        }
        for (int i = 0; i < topic.getSubtopics().size(); i++) {
            saveTopic(c, unitId, id, topic.getSubtopics().get(i), i);
        }
    }

    public List<DocumentSummary> findAllDocumentSummaries() throws SQLException {
        List<DocumentSummary> list = new ArrayList<>();
        String sql = "SELECT document_id, original_filename, status, uploaded_at FROM syllabus_documents ORDER BY document_id DESC";
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                list.add(new DocumentSummary(
                        r.getLong("document_id"),
                        r.getString("original_filename"),
                        r.getString("status"),
                        r.getString("uploaded_at")
                ));
            }
        }
        return list;
    }

    public SyllabusDocument findById(long targetId) throws SQLException {
        Map<Long, SyllabusDocument> docs = new LinkedHashMap<>();
        Map<Long, SyllabusSubject> subjects = new LinkedHashMap<>();
        Map<Long, SyllabusUnit> units = new LinkedHashMap<>();
        Map<Long, Long> unitByTopic = new LinkedHashMap<>();
        Map<Long, SyllabusTopic> topics = new LinkedHashMap<>();
        Map<Long, Long> parentByTopic = new LinkedHashMap<>();

        String sql = "SELECT d.document_id, d.original_filename, s.subject_id, s.subject_name, s.department_code, s.semester, s.course_code, s.credits, s.total_marks, s.is_open_elective, s.course_category, u.unit_id, u.unit_number, u.unit_title, "
                + "t.topic_id, t.parent_topic_id, t.unit_id AS topic_unit, t.topic_text FROM syllabus_documents d "
                + "LEFT JOIN syllabus_subjects s ON s.document_id = d.document_id LEFT JOIN syllabus_units u ON u.subject_id = s.subject_id "
                + "LEFT JOIN syllabus_topics t ON t.unit_id = u.unit_id WHERE d.document_id = ? "
                + "ORDER BY d.document_id, s.sort_order, u.sort_order, t.sort_order";

        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, targetId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    long did = r.getLong("document_id");
                    docs.computeIfAbsent(did, k -> new SyllabusDocument(rString(r, "original_filename")));
                    long sid = r.getLong("subject_id");
                    if (!r.wasNull()) {
                        subjects.computeIfAbsent(sid, k -> {
                            SyllabusSubject s = new SyllabusSubject(rString(r, "subject_name"));
                            s.setDepartmentCode(rString(r, "department_code"));
                            s.setSemester(rString(r, "semester"));
                            s.setCourseCode(rString(r, "course_code"));
                            s.setCredits(rString(r, "credits"));
                            s.setTotalMarks(rString(r, "total_marks"));
                            try {
                                s.setCategory(SyllabusCategory.valueOf(r.getString("course_category")));
                            } catch (Exception e) {
                                s.setCategory(SyllabusCategory.OTHER);
                            }
                            try {
                                s.setOpenElective(r.getBoolean("is_open_elective"));
                            } catch (SQLException e) {
                                throw new IllegalStateException(e);
                            }
                            docs.get(did).getSubjects().add(s);
                            return s;
                        });
                    }
                    long uid = r.getLong("unit_id");
                    if (!r.wasNull()) {
                        units.computeIfAbsent(uid, k -> {
                            SyllabusUnit u = new SyllabusUnit(rInt(r, "unit_number"), rString(r, "unit_title"));
                            subjects.get(sid).getUnits().add(u);
                            return u;
                        });
                    }
                    long tid = r.getLong("topic_id");
                    if (!r.wasNull() && !topics.containsKey(tid)) {
                        SyllabusTopic t = new SyllabusTopic(rString(r, "topic_text"));
                        topics.put(tid, t);
                        unitByTopic.put(tid, uid);
                        long par = r.getLong("parent_topic_id");
                        parentByTopic.put(tid, r.wasNull() ? null : par);
                    }
                }
            }
        }
        for (var entry : topics.entrySet()) {
            Long parent = parentByTopic.get(entry.getKey());
            if (parent == null) {
                if (units.containsKey(unitByTopic.get(entry.getKey()))) {
                    units.get(unitByTopic.get(entry.getKey())).getTopics().add(entry.getValue());
                }
            } else if (topics.containsKey(parent)) {
                topics.get(parent).getSubtopics().add(entry.getValue());
            }
        }
        return docs.get(targetId);
    }

    public boolean approveDocument(long documentId) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement("UPDATE syllabus_documents SET status = 'APPROVED' WHERE document_id = ?")) {
            p.setLong(1, documentId);
            return p.executeUpdate() > 0;
        }
    }

    public List<SyllabusDocument> findApproved() throws SQLException {
        Map<Long, SyllabusDocument> docs = new LinkedHashMap<>();
        Map<Long, SyllabusSubject> subjects = new LinkedHashMap<>();
        Map<Long, SyllabusUnit> units = new LinkedHashMap<>();
        Map<Long, Long> unitByTopic = new LinkedHashMap<>();
        Map<Long, SyllabusTopic> topics = new LinkedHashMap<>();
        Map<Long, Long> parentByTopic = new LinkedHashMap<>();

        String sql = "SELECT d.document_id, d.original_filename, s.subject_id, s.subject_name, s.department_code, s.semester, s.course_code, s.credits, s.total_marks, s.is_open_elective, s.course_category, u.unit_id, u.unit_number, u.unit_title, "
                + "t.topic_id, t.parent_topic_id, t.unit_id AS topic_unit, t.topic_text FROM syllabus_documents d "
                + "LEFT JOIN syllabus_subjects s ON s.document_id = d.document_id LEFT JOIN syllabus_units u ON u.subject_id = s.subject_id "
                + "LEFT JOIN syllabus_topics t ON t.unit_id = u.unit_id WHERE d.status = 'APPROVED' "
                + "ORDER BY d.document_id, s.sort_order, u.sort_order, t.sort_order";

        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                long did = r.getLong("document_id");
                docs.computeIfAbsent(did, k -> new SyllabusDocument(rString(r, "original_filename")));
                long sid = r.getLong("subject_id");
                if (!r.wasNull()) {
                    subjects.computeIfAbsent(sid, k -> {
                        SyllabusSubject s = new SyllabusSubject(rString(r, "subject_name"));
                        s.setDepartmentCode(rString(r, "department_code"));
                        s.setSemester(rString(r, "semester"));
                        s.setCourseCode(rString(r, "course_code"));
                        s.setCredits(rString(r, "credits"));
                        s.setTotalMarks(rString(r, "total_marks"));
                        try {
                            s.setCategory(SyllabusCategory.valueOf(r.getString("course_category")));
                        } catch (Exception e) {
                            s.setCategory(SyllabusCategory.OTHER);
                        }
                        try {
                            s.setOpenElective(r.getBoolean("is_open_elective"));
                        } catch (SQLException e) {
                            throw new IllegalStateException(e);
                        }
                        docs.get(did).getSubjects().add(s);
                        return s;
                    });
                }
                long uid = r.getLong("unit_id");
                if (!r.wasNull()) {
                    units.computeIfAbsent(uid, k -> {
                        SyllabusUnit u = new SyllabusUnit(rInt(r, "unit_number"), rString(r, "unit_title"));
                        subjects.get(sid).getUnits().add(u);
                        return u;
                    });
                }
                long tid = r.getLong("topic_id");
                if (!r.wasNull() && !topics.containsKey(tid)) {
                    SyllabusTopic t = new SyllabusTopic(rString(r, "topic_text"));
                    topics.put(tid, t);
                    unitByTopic.put(tid, uid);
                    long par = r.getLong("parent_topic_id");
                    parentByTopic.put(tid, r.wasNull() ? null : par);
                }
            }
        }
        for (var entry : topics.entrySet()) {
            Long parent = parentByTopic.get(entry.getKey());
            if (parent == null) {
                if (units.containsKey(unitByTopic.get(entry.getKey()))) {
                    units.get(unitByTopic.get(entry.getKey())).getTopics().add(entry.getValue());
                }
            } else if (topics.containsKey(parent)) {
                topics.get(parent).getSubtopics().add(entry.getValue());
            }
        }
        return new ArrayList<>(docs.values());
    }

    private static String rString(ResultSet r, String col) {
        try {
            return r.getString(col);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int rInt(ResultSet r, String col) {
        try {
            return r.getInt(col);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<Integer, List<Integer>> getInternalUnitMapping() throws SQLException {
        Map<Integer, List<Integer>> mapping = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement p = c.prepareStatement("SELECT internal_number, unit_number FROM internal_unit_mapping ORDER BY internal_number, unit_number");
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                mapping.computeIfAbsent(r.getInt(1), k -> new ArrayList<>()).add(r.getInt(2));
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
