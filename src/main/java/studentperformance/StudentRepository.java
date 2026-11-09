package studentperformance;

import java.sql.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** JDBC database operations. Credentials are read from environment variables. */
public class StudentRepository {
    private final String url;
    private final String user;
    private final String password;
    private final DatabaseMigrator migrator = new DatabaseMigrator();
    private DatabaseMigrator.MigrationReport migrationReport = new DatabaseMigrator.MigrationReport(0);

    public StudentRepository() {
        this(setting("DB_URL", "jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC"),
                setting("DB_USER", "root"), setting("DB_PASSWORD", ""));
    }

    public StudentRepository(String url, String user, String password) {
        this.url = url; this.user = user; this.password = password;
    }

    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public void initializeSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            migrationReport = migrator.migrate(connection);
        }
    }

    public DatabaseMigrator.MigrationReport getMigrationReport() { return migrationReport; }

    public void save(Student student) throws SQLException {
        saveBatch(List.of(student));
    }

    public int saveBatch(List<Student> batch) throws SQLException {
        if (batch == null || batch.isEmpty()) return 0;
        String studentSql = "INSERT INTO students (roll_number,name,sem1_sgpa,cgpa,last_sgpa,attendance_percent) VALUES (?,?,?,?,?,?)";
        String deleteSql = "DELETE FROM internal_assessments WHERE roll_number=?";
        String assessmentSql = "INSERT INTO internal_assessments (roll_number,internal_number,subject_name,exam_marks,"
                + "assignment1,assignment2,assignment3,quiz1,quiz2,quiz3,imported_legacy) VALUES (?,?,?,?,?,?,?,?,?,?,FALSE)";

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement studentStmt = connection.prepareStatement(studentSql)) {
                    for (Student student : batch) {
                        studentStmt.setString(1, student.getRollNumber());
                        studentStmt.setString(2, student.getName());
                        if (student.getSem1Sgpa() == null) studentStmt.setNull(3, Types.DECIMAL);
                        else studentStmt.setDouble(3, student.getSem1Sgpa());
                        studentStmt.setDouble(4, student.getCgpa());
                        studentStmt.setDouble(5, student.getLastSemesterSgpa());
                        if (student.getAttendancePercent() == null) studentStmt.setNull(6, Types.DECIMAL);
                        else studentStmt.setDouble(6, student.getAttendancePercent());
                        studentStmt.addBatch();
                    }
                    studentStmt.executeBatch();
                }

                try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                    for (Student student : batch) {
                        deleteStmt.setString(1, student.getRollNumber());
                        deleteStmt.addBatch();
                    }
                    deleteStmt.executeBatch();
                }

                try (PreparedStatement assessStmt = connection.prepareStatement(assessmentSql)) {
                    for (Student student : batch) {
                        for (int exam = 1; exam <= 2; exam++) {
                            for (Subject subject : Subject.values()) {
                                SubjectAssessment assessment = student.getMarks(exam).get(subject);
                                if (assessment == null || assessment.getExamMarks() == null) {
                                    continue;
                                }
                                assessStmt.setString(1, student.getRollNumber());
                                assessStmt.setInt(2, exam);
                                assessStmt.setString(3, subject.name());
                                assessStmt.setInt(4, assessment.getExamMarks());
                                for (int i = 0; i < 3; i++) {
                                    Integer a = (assessment == null) ? null : assessment.getAssignmentMarks().get(i);
                                    if (a == null) assessStmt.setNull(5 + i, Types.INTEGER);
                                    else assessStmt.setInt(5 + i, a);
                                }
                                for (int i = 0; i < 3; i++) {
                                    Integer q = (assessment == null) ? null : assessment.getQuizMarks().get(i);
                                    if (q == null) assessStmt.setNull(8 + i, Types.INTEGER);
                                    else assessStmt.setInt(8 + i, q);
                                }
                                assessStmt.addBatch();
                            }
                        }
                    }
                    assessStmt.executeBatch();
                }

                connection.commit();
                return batch.size();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    public Set<String> findAllRollNumbers() throws SQLException {
        Set<String> rolls = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement("SELECT roll_number FROM students");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rolls.add(rs.getString("roll_number"));
            }
        }
        return rolls;
    }

    public List<Student> findAll() throws SQLException {
        return findStudents(null);
    }

    public Student findByRollNumber(String rollNumber) throws SQLException {
        List<Student> result = findStudents(rollNumber);
        return result.isEmpty() ? null : result.get(0);
    }

    private List<Student> findStudents(String requestedRoll) throws SQLException {
        String sql = "SELECT s.roll_number,s.name,s.sem1_sgpa,s.cgpa,s.last_sgpa,s.attendance_percent,m.internal_number,m.subject_name,"
                + "m.exam_marks,m.assignment1,m.assignment2,m.assignment3,m.quiz1,m.quiz2,m.quiz3,m.imported_legacy "
                + "FROM students s LEFT JOIN internal_assessments m ON s.roll_number=m.roll_number "
                + (requestedRoll == null ? "" : "WHERE s.roll_number=? ") + "ORDER BY s.roll_number,m.internal_number";
        List<Student> students = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql);
             ) {
            if (requestedRoll != null) statement.setString(1, requestedRoll);
            try (ResultSet rows = statement.executeQuery()) {
            String roll = null, name = null;
            Double sem1 = null;
            double cgpa = 0, sgpa = 0;
            Double att = null;
            EnumMap<Subject, SubjectAssessment> first = new EnumMap<>(Subject.class);
            EnumMap<Subject, SubjectAssessment> second = new EnumMap<>(Subject.class);
            while (rows.next()) {
                String nextRoll = rows.getString("roll_number");
                if (roll != null && !roll.equals(nextRoll)) {
                    students.add(Student.fromDatabase(name, roll, sem1, cgpa, sgpa, att, first, second, List.of()));
                    first = new EnumMap<>(Subject.class); second = new EnumMap<>(Subject.class);
                }
                if (!nextRoll.equals(roll)) {
                    roll = nextRoll; name = rows.getString("name");
                    double rawSem1 = rows.getDouble("sem1_sgpa");
                    sem1 = rows.wasNull() ? null : rawSem1;
                    cgpa = rows.getDouble("cgpa"); sgpa = rows.getDouble("last_sgpa");
                    double rawAtt = rows.getDouble("attendance_percent");
                    att = rows.wasNull() ? null : rawAtt;
                }
                String subjectName = rows.getString("subject_name");
                if (subjectName != null) {
                    boolean imported = rows.getBoolean("imported_legacy");
                    List<Integer> assignments = imported ? java.util.Arrays.asList(null, null, null)
                            : nullableMarks(rows, "assignment1", "assignment2", "assignment3");
                    List<Integer> quizzes = imported ? java.util.Arrays.asList(null, null, null)
                            : nullableMarks(rows, "quiz1", "quiz2", "quiz3");
                    int rawExam = rows.getInt("exam_marks"); Integer examMark = rows.wasNull() ? null : rawExam;
                    SubjectAssessment assessment = new SubjectAssessment(examMark, assignments, quizzes, imported);
                    Subject subject = subjectFromDatabase(subjectName);
                    if (subject != null) (rows.getInt("internal_number") == 1 ? first : second).put(subject, assessment);
                }
            }
            if (roll != null) students.add(Student.fromDatabase(name, roll, sem1, cgpa, sgpa, att, first, second, List.of()));
            }
            Map<String, List<LegacyMark>> legacyByRoll = new HashMap<>();
            try (PreparedStatement legacy = connection.prepareStatement("SELECT roll_number,internal_number,subject_name,raw_marks FROM legacy_internal_marks_archive"
                    + (requestedRoll == null ? "" : " WHERE roll_number=?") + " ORDER BY roll_number,internal_number,subject_name")) {
                if (requestedRoll != null) legacy.setString(1, requestedRoll);
                try (ResultSet rows = legacy.executeQuery()) {
                    while (rows.next()) legacyByRoll.computeIfAbsent(rows.getString("roll_number"), key -> new ArrayList<>())
                            .add(new LegacyMark(rows.getInt("internal_number"), rows.getString("subject_name"), rows.getInt("raw_marks")));
                }
            }
            for (int i = 0; i < students.size(); i++) {
                Student student = students.get(i);
                students.set(i, student.withLegacyMarks(legacyByRoll.getOrDefault(student.getRollNumber(), List.of())));
            }
        }
        return students;
    }

    private List<Integer> nullableMarks(ResultSet rows, String... columns) throws SQLException {
        List<Integer> marks = new ArrayList<>();
        for (String column : columns) { int mark = rows.getInt(column); marks.add(rows.wasNull() ? null : mark); }
        return marks;
    }

    private Subject subjectFromDatabase(String name) {
        if (name == null) return null;
        try { return Subject.valueOf(name); }
        catch (IllegalArgumentException ex) { return null; }
    }

    public boolean updateDetails(String rollNumber, String name, double cgpa, double sgpa) throws SQLException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name cannot be blank.");
        if (!Double.isFinite(cgpa) || cgpa < 0 || cgpa > 10 || !Double.isFinite(sgpa) || sgpa < 0 || sgpa > 10)
            throw new IllegalArgumentException("CGPA and SGPA must be between 0 and 10.");
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement("UPDATE students SET name=?,cgpa=?,last_sgpa=? WHERE roll_number=?")) {
            statement.setString(1, name.trim()); statement.setDouble(2, cgpa); statement.setDouble(3, sgpa); statement.setString(4, rollNumber);
            return statement.executeUpdate() > 0;
        }
    }

    public void updateMarks(Student student) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM internal_assessments WHERE roll_number=?")) {
                    delete.setString(1, student.getRollNumber()); delete.executeUpdate();
                }
                insertMarks(connection, student);
                connection.commit();
            } catch (SQLException | RuntimeException ex) { connection.rollback(); throw ex; }
        }
    }

    private void insertMarks(Connection connection, Student student) throws SQLException {
        String sql = "INSERT INTO internal_assessments (roll_number,internal_number,subject_name,exam_marks,"
                + "assignment1,assignment2,assignment3,quiz1,quiz2,quiz3,imported_legacy) VALUES (?,?,?,?,?,?,?,?,?,?,FALSE)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int exam = 1; exam <= 2; exam++) for (Subject subject : Subject.values()) {
                SubjectAssessment mark = student.getMarks(exam).get(subject);
                if (mark == null) throw new IllegalArgumentException("All subject marks are required to save an update.");
                statement.setString(1, student.getRollNumber()); statement.setInt(2, exam); statement.setString(3, subject.name());
                statement.setInt(4, mark.getExamMarks());
                for (int i = 0; i < 3; i++) statement.setInt(5 + i, mark.getAssignmentMarks().get(i));
                for (int i = 0; i < 3; i++) statement.setInt(8 + i, mark.getQuizMarks().get(i));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public boolean delete(String rollNumber) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement("DELETE FROM students WHERE roll_number=?")) {
            statement.setString(1, rollNumber); return statement.executeUpdate() > 0;
        }
    }
}
