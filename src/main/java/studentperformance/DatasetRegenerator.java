package studentperformance;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Regenerates the synthetic demonstration dataset for all 101 students from scratch
 * using a fixed random seed for reproducibility.
 *
 * Implements:
 * - 6 student info columns (Roll, Name, Sem1 SGPA, Sem2 SGPA, Previous CGPA, Attendance %)
 * - 8 Semester III subjects (JAVA, DS, CA, TTPS, OE, CS, CT, HVPE) = 19 credits
 * - Previous CGPA = (Sem1 SGPA * 16 + Sem2 SGPA * 13) / 29
 * - Realistic student profiles (High, Average, Low, Improving, Declining, Inconsistent)
 * - Outputs to MySQL and creates the 118-column Excel files.
 */
public class DatasetRegenerator {

    private static final long RANDOM_SEED = 20261108L;

    private static final String[] FIRST_NAMES = {
            "Aarav", "Diya", "Rohan", "Ananya", "Sai Teja", "Sneha", "Karthik", "Meera", "Aditi", "Vikram",
            "Rahul", "Pooja", "Arjun", "Kavya", "Varun", "Ishita", "Siddharth", "Neha", "Abhishek", "Priya",
            "Harsh", "Divya", "Gautam", "Tanvi", "Nikhil", "Rhea", "Manish", "Shreya", "Pranav", "Swati",
            "Akash", "Bhavna", "Deepak", "Aishwarya", "Chirag", "Nandini", "Kunal", "Simran", "Tarun", "Anjali",
            "Yash", "Ritu", "Mohit", "Preeti", "Suresh", "Pallavi", "Rajesh", "Sunita", "Amit", "Monika"
    };

    private static final String[] LAST_NAMES = {
            "Sharma", "Patel", "Verma", "Iyer", "Reddy", "Rao", "Nair", "Joshi", "Singh", "Gupta",
            "Kumar", "Chopra", "Deshmukh", "Kulkarni", "Bhat", "Mehta", "Shah", "Agarwal", "Mishra", "Pandey"
    };

    public static class GeneratedStudent {
        public final String rollNumber;
        public final String name;
        public final double sem1Sgpa;
        public final double sem2Sgpa;
        public final double previousCgpa;
        public final double attendancePercent;
        public final EnumMap<Subject, SubjectAssessment> internal1;
        public final EnumMap<Subject, SubjectAssessment> internal2;
        public final String profile;

        public GeneratedStudent(String rollNumber, String name, double sem1Sgpa, double sem2Sgpa,
                                double previousCgpa, double attendancePercent,
                                EnumMap<Subject, SubjectAssessment> internal1,
                                EnumMap<Subject, SubjectAssessment> internal2,
                                String profile) {
            this.rollNumber = rollNumber;
            this.name = name;
            this.sem1Sgpa = sem1Sgpa;
            this.sem2Sgpa = sem2Sgpa;
            this.previousCgpa = previousCgpa;
            this.attendancePercent = attendancePercent;
            this.internal1 = internal1;
            this.internal2 = internal2;
            this.profile = profile;
        }

        public Student toStudent() {
            return new Student(name, rollNumber, sem1Sgpa, previousCgpa, sem2Sgpa, attendancePercent, internal1, internal2);
        }
    }

    public static List<GeneratedStudent> generateAllStudents() {
        Random rng = new Random(RANDOM_SEED);
        List<GeneratedStudent> list = new ArrayList<>(101);

        Set<String> usedNames = new HashSet<>();

        // Generate 100 students: 1602-25-733-001 through 1602-25-733-100
        for (int i = 1; i <= 100; i++) {
            String roll = String.format(Locale.ROOT, "1602-25-733-%03d", i);

            // Generate unique realistic name
            String name;
            do {
                name = FIRST_NAMES[rng.nextInt(FIRST_NAMES.length)] + " " + LAST_NAMES[rng.nextInt(LAST_NAMES.length)];
            } while (!usedNames.add(name));

            String profile;
            double sem1, sem2, attendance;

            if (i <= 18) {
                // High Performers (18 students)
                profile = "High Performer";
                sem1 = round2(8.60 + rng.nextDouble() * 1.25); // 8.60 - 9.85
                sem2 = round2(8.50 + rng.nextDouble() * 1.30); // 8.50 - 9.80
                attendance = round1(88.0 + rng.nextDouble() * 10.0); // 88.0 - 98.0
            } else if (i <= 68) {
                // Average Performers (50 students)
                profile = "Average Performer";
                sem1 = round2(6.30 + rng.nextDouble() * 2.00); // 6.30 - 8.30
                sem2 = round2(6.20 + rng.nextDouble() * 2.10); // 6.20 - 8.30
                attendance = round1(72.0 + rng.nextDouble() * 16.0); // 72.0 - 88.0
            } else if (i <= 80) {
                // Low Performers / At-Risk (12 students)
                profile = "Low Performer";
                sem1 = round2(4.50 + rng.nextDouble() * 1.60); // 4.50 - 6.10
                sem2 = round2(4.30 + rng.nextDouble() * 1.70); // 4.30 - 6.00
                attendance = round1(52.0 + rng.nextDouble() * 16.0); // 52.0 - 68.0
            } else if (i <= 90) {
                // Improving Students (10 students)
                profile = "Improving";
                sem1 = round2(5.80 + rng.nextDouble() * 1.20); // 5.80 - 7.00
                sem2 = round2(sem1 + 0.60 + rng.nextDouble() * 0.90); // Improvement in Sem 2
                attendance = round1(80.0 + rng.nextDouble() * 12.0); // Good attendance supporting improvement
            } else {
                // Declining Students (10 students)
                profile = "Declining";
                sem1 = round2(7.80 + rng.nextDouble() * 1.10); // 7.80 - 8.90
                sem2 = round2(sem1 - 0.70 - rng.nextDouble() * 0.80); // Decline in Sem 2
                attendance = round1(60.0 + rng.nextDouble() * 14.0); // Reduced attendance
            }

            sem1 = Math.min(10.0, Math.max(0.0, sem1));
            sem2 = Math.min(10.0, Math.max(0.0, sem2));
            attendance = Math.min(100.0, Math.max(0.0, attendance));

            // Strictly calculate Previous CGPA = (Sem1 * 16 + Sem2 * 13) / 29
            double cgpa = round2((sem1 * 16.0 + sem2 * 13.0) / 29.0);

            // Generate Internal 1 and Internal 2 marks for all 8 subjects
            EnumMap<Subject, SubjectAssessment> int1 = new EnumMap<>(Subject.class);
            EnumMap<Subject, SubjectAssessment> int2 = new EnumMap<>(Subject.class);

            for (Subject s : Subject.values()) {
                double targetPct1, targetPct2;
                double subjectAffinity = (rng.nextDouble() - 0.5) * 0.16; // Subject-specific variation (+/- 8%)

                if ("High Performer".equals(profile)) {
                    targetPct1 = 0.82 + (rng.nextDouble() * 0.16) + subjectAffinity;
                    targetPct2 = 0.84 + (rng.nextDouble() * 0.14) + subjectAffinity;
                } else if ("Average Performer".equals(profile)) {
                    targetPct1 = 0.60 + (rng.nextDouble() * 0.22) + subjectAffinity;
                    targetPct2 = 0.60 + (rng.nextDouble() * 0.22) + subjectAffinity;
                } else if ("Low Performer".equals(profile)) {
                    targetPct1 = 0.40 + (rng.nextDouble() * 0.18) + subjectAffinity;
                    targetPct2 = 0.38 + (rng.nextDouble() * 0.20) + subjectAffinity;
                } else if ("Improving".equals(profile)) {
                    targetPct1 = 0.50 + (rng.nextDouble() * 0.15) + subjectAffinity;
                    targetPct2 = targetPct1 + 0.18 + (rng.nextDouble() * 0.12); // Marked improvement in Internal 2
                } else { // "Declining"
                    targetPct1 = 0.78 + (rng.nextDouble() * 0.14) + subjectAffinity;
                    targetPct2 = targetPct1 - 0.20 - (rng.nextDouble() * 0.10); // Marked decline in Internal 2
                }

                targetPct1 = Math.min(0.99, Math.max(0.25, targetPct1));
                targetPct2 = Math.min(0.99, Math.max(0.20, targetPct2));

                int1.put(s, generateAssessment(rng, s, targetPct1));
                int2.put(s, generateAssessment(rng, s, targetPct2));
            }

            list.add(new GeneratedStudent(roll, name, sem1, sem2, cgpa, attendance, int1, int2, profile));
        }

        // Add 101st Student: Verification student 1602-25-733-999
        String verifyRoll = "1602-25-733-999";
        String verifyName = "Aditya Varma";
        double vSem1 = 9.38;
        double vSem2 = 9.23;
        double vCgpa = round2((vSem1 * 16.0 + vSem2 * 13.0) / 29.0); // 9.31
        double vAtt = 94.5;
        EnumMap<Subject, SubjectAssessment> vInt1 = new EnumMap<>(Subject.class);
        EnumMap<Subject, SubjectAssessment> vInt2 = new EnumMap<>(Subject.class);
        for (Subject s : Subject.values()) {
            vInt1.put(s, generateAssessment(rng, s, 0.92));
            vInt2.put(s, generateAssessment(rng, s, 0.94));
        }
        list.add(new GeneratedStudent(verifyRoll, verifyName, vSem1, vSem2, vCgpa, vAtt, vInt1, vInt2, "Verification High Performer"));

        return list;
    }

    private static SubjectAssessment generateAssessment(Random rng, Subject s, double targetPct) {
        int maxExam = s.getMaximumExamMarks(); // 30 or 20
        int exam = (int) Math.round(maxExam * targetPct);
        exam = Math.max(0, Math.min(maxExam, exam));

        // Generate 3 assignments (0-5)
        List<Integer> assignments = new ArrayList<>(3);
        for (int j = 0; j < 3; j++) {
            int a = (int) Math.round(5.0 * targetPct + (rng.nextDouble() * 1.5 - 0.75));
            assignments.add(Math.max(1, Math.min(5, a)));
        }

        // Generate 3 quizzes (0-5)
        List<Integer> quizzes = new ArrayList<>(3);
        for (int j = 0; j < 3; j++) {
            int q = (int) Math.round(5.0 * targetPct + (rng.nextDouble() * 1.5 - 0.75));
            quizzes.add(Math.max(1, Math.min(5, q)));
        }

        return new SubjectAssessment(exam, assignments, quizzes);
    }

    public static void persistToDatabase(List<GeneratedStudent> students) throws SQLException {
        String dbUrl = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC");
        String dbUser = System.getenv().getOrDefault("DB_USER", "root");
        String dbPass = System.getenv().getOrDefault("DB_PASSWORD", "Teja.J@2007");

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            conn.setAutoCommit(false);
            try {
                // Delete previous synthetic records
                try (PreparedStatement delAssess = conn.prepareStatement("DELETE FROM internal_assessments");
                     PreparedStatement delStudents = conn.prepareStatement("DELETE FROM students")) {
                    delAssess.executeUpdate();
                    delStudents.executeUpdate();
                }

                // Batch insert students
                String insertStudentSql = "INSERT INTO students (dataset_id, roll_number, name, sem1_sgpa, cgpa, last_sgpa, attendance_percent) VALUES (1, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertStudentSql)) {
                    for (GeneratedStudent s : students) {
                        ps.setString(1, s.rollNumber);
                        ps.setString(2, s.name);
                        ps.setDouble(3, s.sem1Sgpa);
                        ps.setDouble(4, s.previousCgpa);
                        ps.setDouble(5, s.sem2Sgpa);
                        ps.setDouble(6, s.attendancePercent);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Batch insert internal assessments
                String insertAssessSql = "INSERT INTO internal_assessments (dataset_id, roll_number, internal_number, subject_name, exam_marks, "
                        + "assignment1, assignment2, assignment3, quiz1, quiz2, quiz3, imported_legacy) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)";
                try (PreparedStatement ps = conn.prepareStatement(insertAssessSql)) {
                    for (GeneratedStudent s : students) {
                        for (int internal = 1; internal <= 2; internal++) {
                            EnumMap<Subject, SubjectAssessment> map = (internal == 1) ? s.internal1 : s.internal2;
                            for (Subject subj : Subject.values()) {
                                SubjectAssessment a = map.get(subj);
                                ps.setString(1, s.rollNumber);
                                ps.setInt(2, internal);
                                ps.setString(3, subj.name());
                                ps.setInt(4, a.getExamMarks());
                                ps.setInt(5, a.getAssignmentMarks().get(0));
                                ps.setInt(6, a.getAssignmentMarks().get(1));
                                ps.setInt(7, a.getAssignmentMarks().get(2));
                                ps.setInt(8, a.getQuizMarks().get(0));
                                ps.setInt(9, a.getQuizMarks().get(1));
                                ps.setInt(10, a.getQuizMarks().get(2));
                                ps.addBatch();
                            }
                        }
                    }
                    ps.executeBatch();
                }

                conn.commit();
                System.out.println("Successfully persisted " + students.size() + " students and " + (students.size() * 16) + " internal assessments to MySQL.");
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public static void exportToExcel(List<GeneratedStudent> students, Path targetPath) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Student Performance");

            // Cell Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle metaStyle = workbook.createCellStyle();
            Font metaFont = workbook.createFont();
            metaFont.setBold(true);
            metaFont.setColor(IndexedColors.WHITE.getIndex());
            metaStyle.setFont(metaFont);
            metaStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            metaStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Row 0: Section Category Banner
            Row topBanner = sheet.createRow(0);
            Cell b0 = topBanner.createCell(0);
            b0.setCellValue("STUDENT INFORMATION (6 COLUMNS)");
            b0.setCellStyle(metaStyle);

            Cell b1 = topBanner.createCell(6);
            b1.setCellValue("INTERNAL 1 ASSESSMENTS (8 SUBJECTS X 7 COLUMNS = 56 COLUMNS)");
            b1.setCellStyle(headerStyle);

            Cell b2 = topBanner.createCell(62);
            b2.setCellValue("INTERNAL 2 ASSESSMENTS (8 SUBJECTS X 7 COLUMNS = 56 COLUMNS)");
            b2.setCellStyle(headerStyle);

            // Row 1: 118 Column Headers
            Row headerRow = sheet.createRow(1);
            String[] metaCols = {
                    "Roll Number",
                    "Student Name",
                    "Semester I SGPA",
                    "Semester II SGPA",
                    "Previous CGPA",
                    "Attendance (%)"
            };

            for (int i = 0; i < metaCols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(metaCols[i]);
                cell.setCellStyle(metaStyle);
            }

            int col = 6;
            for (int internal = 1; internal <= 2; internal++) {
                for (Subject s : Subject.values()) {
                    String prefix = "Int" + internal + "_" + s.getDisplayName() + "_";
                    String[] cols = {
                            prefix + "Exam(0-" + s.getMaximumExamMarks() + ")",
                            prefix + "A1(0-5)", prefix + "A2(0-5)", prefix + "A3(0-5)",
                            prefix + "Q1(0-5)", prefix + "Q2(0-5)", prefix + "Q3(0-5)"
                    };
                    for (String header : cols) {
                        Cell cell = headerRow.createCell(col++);
                        cell.setCellValue(header);
                        cell.setCellStyle(headerStyle);
                    }
                }
            }

            // Data Rows (Starting at Row 2)
            int rowIdx = 2;
            for (GeneratedStudent s : students) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(s.rollNumber);
                row.createCell(1).setCellValue(s.name);
                row.createCell(2).setCellValue(s.sem1Sgpa);
                row.createCell(3).setCellValue(s.sem2Sgpa);
                row.createCell(4).setCellValue(s.previousCgpa);
                row.createCell(5).setCellValue(s.attendancePercent);

                int c = 6;
                for (int internal = 1; internal <= 2; internal++) {
                    EnumMap<Subject, SubjectAssessment> map = (internal == 1) ? s.internal1 : s.internal2;
                    for (Subject subj : Subject.values()) {
                        SubjectAssessment a = map.get(subj);
                        row.createCell(c++).setCellValue(a.getExamMarks());
                        row.createCell(c++).setCellValue(a.getAssignmentMarks().get(0));
                        row.createCell(c++).setCellValue(a.getAssignmentMarks().get(1));
                        row.createCell(c++).setCellValue(a.getAssignmentMarks().get(2));
                        row.createCell(c++).setCellValue(a.getQuizMarks().get(0));
                        row.createCell(c++).setCellValue(a.getQuizMarks().get(1));
                        row.createCell(c++).setCellValue(a.getQuizMarks().get(2));
                    }
                }
            }

            try (OutputStream out = Files.newOutputStream(targetPath)) {
                workbook.write(out);
            }
            System.out.println("Exported 118-column Excel dataset to: " + targetPath.toAbsolutePath() + " (Total columns: " + col + ")");
        }
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== REGENERATING SYNTHETIC DATASET FOR 101 STUDENTS ===");
        List<GeneratedStudent> students = generateAllStudents();
        System.out.println("Generated " + students.size() + " student records with seed " + RANDOM_SEED);

        // Verification of properties
        int high = 0, avg = 0, low = 0, imp = 0, dec = 0;
        for (GeneratedStudent s : students) {
            if (s.profile.contains("High")) high++;
            else if (s.profile.contains("Average")) avg++;
            else if (s.profile.contains("Low")) low++;
            else if (s.profile.contains("Improving")) imp++;
            else if (s.profile.contains("Declining")) dec++;

            // Strict formula check: Previous CGPA = (Sem1 * 16 + Sem2 * 13) / 29
            double expectedCgpa = round2((s.sem1Sgpa * 16.0 + s.sem2Sgpa * 13.0) / 29.0);
            if (Math.abs(s.previousCgpa - expectedCgpa) > 0.001) {
                throw new IllegalStateException("CGPA formula mismatch for roll " + s.rollNumber + ": found " + s.previousCgpa + ", expected " + expectedCgpa);
            }
        }
        System.out.println("Profile distribution: High=" + high + ", Average=" + avg + ", Low=" + low + ", Improving=" + imp + ", Declining=" + dec);

        // Persist to MySQL
        persistToDatabase(students);

        // Export to student_records_100.xlsx and student_import_template.xlsx
        exportToExcel(students, Path.of("student_records_100.xlsx"));
        exportToExcel(students, Path.of("student_import_template.xlsx"));

        System.out.println("=== DATASET REGENERATION COMPLETE ===");
    }
}
