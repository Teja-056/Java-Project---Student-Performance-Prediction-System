package studentperformance;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class PopulateAndImportStudents {

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

    public static void main(String[] args) {
        Path excelPath = Path.of("student_import_template.xlsx");
        System.out.println("Processing student records from: " + excelPath.toAbsolutePath());

        try {
            ExcelImporter importer = new ExcelImporter();
            ExcelImporter.ExcelImportResult result = null;

            if (Files.exists(excelPath)) {
                try {
                    result = importer.parseAndValidate(excelPath, Set.of());
                } catch (Exception ignored) { }
            }

            if (result == null || result.getValidCount() < 100) {
                System.out.println("Populating 100 student records into Excel...");
                try {
                    populateExcelWith100Students(excelPath);
                } catch (java.nio.file.FileSystemException fse) {
                    System.out.println("Notice: " + excelPath.getFileName() + " is open in another app. Using student_records_100.xlsx instead.");
                    excelPath = Path.of("student_records_100.xlsx");
                    populateExcelWith100Students(excelPath);
                }
                result = importer.parseAndValidate(excelPath, Set.of());
            }

            System.out.println("\nExcel Validation Report:");
            System.out.println("  Source File: " + excelPath.getFileName());
            System.out.println("  Total rows read: " + result.getTotalRowsRead());
            System.out.println("  Valid records: " + result.getValidCount());
            System.out.println("  Invalid records: " + result.getInvalidCount());
            if (result.hasErrors()) {
                System.err.println("Validation errors found:");
                result.getInvalidRows().forEach(e -> System.err.println("  " + e.format()));
                return;
            }

            // Attempt to connect and import into MySQL
            System.out.println("\nChecking MySQL database connectivity...");
            String dbUser = System.getenv().getOrDefault("DB_USER", "root");
            String dbPass = System.getenv().getOrDefault("DB_PASSWORD", "");
            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/?serverTimezone=UTC", dbUser, dbPass);
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS student_performance");
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS student_performance_test");
            } catch (SQLException ignored) {
                // If connecting without db name is restricted, continue to direct db connection
            }

            StudentRepository repository = new StudentRepository();
            try {
                repository.initializeSchema();
                System.out.println("Connected to MySQL database 'student_performance' successfully!");

                Set<String> existingRolls = repository.findAllRollNumbers();
                System.out.println("Found " + existingRolls.size() + " existing student records in MySQL.");

                List<Student> toImport = new ArrayList<>();
                for (Student s : result.getValidStudents()) {
                    if (!existingRolls.contains(s.getRollNumber())) {
                        toImport.add(s);
                    }
                }

                if (toImport.isEmpty()) {
                    System.out.println("All 100 students already exist in MySQL. Database is up to date!");
                } else {
                    int imported = repository.saveBatch(toImport);
                    System.out.println("Successfully inserted " + imported + " new students into MySQL via transaction batch!");
                }

                List<Student> allInDb = repository.findAll();
                System.out.println("Total student records currently stored in MySQL: " + allInDb.size());

                PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
                long completeCount = allInDb.stream().filter(analyzer::hasBothInternals).count();
                long supportCount = allInDb.stream().filter(analyzer::requiresSupport).count();
                System.out.println("Complete internal assessments: " + completeCount);
                System.out.println("Students requiring academic support (<" + analyzer.getSupportThresholdPercent() + "%): " + supportCount);

            } catch (SQLException ex) {
                System.out.println("Could not connect to MySQL: " + ex.getMessage());
                System.out.println("The Excel sheet is populated and ready. When MySQL is running, configure your login and import via menu option 15:");
                System.out.println("  $env:DB_URL = \"jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC\"");
                System.out.println("  $env:DB_USER = \"root\"");
                System.out.println("  $env:DB_PASSWORD = \"your_password\"");
            }

        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void populateExcelWith100Students(Path excelPath) throws Exception {
        Workbook workbook;
        Sheet sheet;
        Sheet instructionsSheet;

        if (Files.exists(excelPath)) {
            try (InputStream in = Files.newInputStream(excelPath)) {
                workbook = WorkbookFactory.create(in);
            }
            sheet = workbook.getSheet("Student Data");
            if (sheet == null) sheet = workbook.getSheetAt(0);
            instructionsSheet = workbook.getSheet("Instructions");
        } else {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet("Student Data");
            instructionsSheet = workbook.createSheet("Instructions");
        }

        // Ensure headers exist
        new ExcelImporter().generateTemplate(excelPath);
        // Now reopen and populate rows 2 through 101
        try (InputStream in = Files.newInputStream(excelPath)) {
            workbook = WorkbookFactory.create(in);
        }
        sheet = workbook.getSheet("Student Data");

        // Clear previous sample rows if any
        int lastRow = sheet.getLastRowNum();
        for (int r = lastRow; r >= 2; r--) {
            Row row = sheet.getRow(r);
            if (row != null) sheet.removeRow(row);
        }

        Random rand = new Random(42); // deterministic seed for reproducible realistic data

        for (int i = 1; i <= 100; i++) {
            Row row = sheet.createRow(1 + i);
            String roll = String.format("1602-25-733-%03d", i);
            String name = FIRST_NAMES[(i - 1) % FIRST_NAMES.length] + " " + LAST_NAMES[(i - 1) % LAST_NAMES.length];

            // Tier distribution:
            // 15% High achievers (85%-98%)
            // 45% Above average (70%-84%)
            // 25% Average (50%-69%)
            // 15% Support needed (<50%)
            int tier;
            if (i <= 15) tier = 1; // Top
            else if (i <= 60) tier = 2; // Above avg
            else if (i <= 85) tier = 3; // Average
            else tier = 4; // Below threshold (<50%)

            double cgpa;
            double sgpa;
            if (tier == 1) {
                cgpa = 8.50 + rand.nextDouble() * 1.30;
                sgpa = 8.60 + rand.nextDouble() * 1.30;
            } else if (tier == 2) {
                cgpa = 7.20 + rand.nextDouble() * 1.25;
                sgpa = 7.10 + rand.nextDouble() * 1.35;
            } else if (tier == 3) {
                cgpa = 5.80 + rand.nextDouble() * 1.30;
                sgpa = 5.70 + rand.nextDouble() * 1.35;
            } else {
                cgpa = 4.50 + rand.nextDouble() * 1.20;
                sgpa = 4.20 + rand.nextDouble() * 1.40;
            }

            row.createCell(0).setCellValue(roll);
            row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(Math.round(cgpa * 100.0) / 100.0);
            row.createCell(3).setCellValue(Math.round(sgpa * 100.0) / 100.0);

            int col = 4;
            for (int internal = 1; internal <= 2; internal++) {
                for (Subject subject : Subject.values()) {
                    int maxExam = subject.getMaximumExamMarks();
                    int exam;
                    int a1, a2, a3, q1, q2, q3;

                    // Some subjects stronger, some weaker per student
                    boolean studentSubjectStrength = ((i + subject.ordinal()) % 3 == 0);
                    boolean studentSubjectWeakness = ((i + subject.ordinal()) % 5 == 0);

                    if (tier == 1) {
                        exam = maxExam - rand.nextInt(4); // 27-30 or 17-20
                        a1 = 4 + rand.nextInt(2); a2 = 4 + rand.nextInt(2); a3 = 5;
                        q1 = 4 + rand.nextInt(2); q2 = 4 + rand.nextInt(2); q3 = 5;
                    } else if (tier == 2) {
                        int deduction = studentSubjectStrength ? rand.nextInt(4) : (3 + rand.nextInt(6));
                        exam = Math.max(maxExam / 2, maxExam - deduction);
                        a1 = 3 + rand.nextInt(3); a2 = 3 + rand.nextInt(3); a3 = 4;
                        q1 = 3 + rand.nextInt(3); q2 = 3 + rand.nextInt(3); q3 = 4;
                    } else if (tier == 3) {
                        int deduction = studentSubjectStrength ? 4 : (6 + rand.nextInt(6));
                        exam = Math.max(maxExam / 3, maxExam - deduction);
                        a1 = 2 + rand.nextInt(3); a2 = 2 + rand.nextInt(3); a3 = 3;
                        q1 = 2 + rand.nextInt(3); q2 = 2 + rand.nextInt(3); q3 = 3;
                    } else { // Support tier (<50%)
                        int deduction = studentSubjectWeakness ? (maxExam - 4 - rand.nextInt(5)) : (maxExam / 2 + 2 + rand.nextInt(4));
                        exam = Math.max(2, maxExam - deduction);
                        a1 = 1 + rand.nextInt(3); a2 = 1 + rand.nextInt(3); a3 = 2;
                        q1 = 1 + rand.nextInt(3); q2 = 1 + rand.nextInt(3); q3 = 2;
                    }

                    // Slight improvement or decline trend between internals
                    if (internal == 2) {
                        if (i % 2 == 0) { // improved in internal 2
                            exam = Math.min(maxExam, exam + 1 + rand.nextInt(3));
                        } else { // slight decline
                            exam = Math.max(1, exam - rand.nextInt(3));
                        }
                    }

                    row.createCell(col++).setCellValue(exam);
                    row.createCell(col++).setCellValue(a1);
                    row.createCell(col++).setCellValue(a2);
                    row.createCell(col++).setCellValue(a3);
                    row.createCell(col++).setCellValue(q1);
                    row.createCell(col++).setCellValue(q2);
                    row.createCell(col++).setCellValue(q3);
                }
            }
        }

        try (OutputStream out = Files.newOutputStream(excelPath)) {
            workbook.write(out);
        }
        workbook.close();
    }
}
