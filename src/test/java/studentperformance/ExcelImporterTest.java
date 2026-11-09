package studentperformance;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelImporterTest {
    private final ExcelImporter importer = new ExcelImporter();

    @Test
    void generatesValidTemplateAndParsesSampleData(@TempDir Path tempDir) throws Exception {
        Path templatePath = tempDir.resolve("template.xlsx");
        importer.generateTemplate(templatePath);
        assertTrue(Files.exists(templatePath));
        assertTrue(Files.size(templatePath) > 0);

        // Parse the generated template - sample rows in the template should be valid
        ExcelImporter.ExcelImportResult result = importer.parseAndValidate(templatePath, Set.of());
        assertEquals(2, result.getTotalRowsRead());
        assertEquals(2, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertFalse(result.hasErrors());

        Student first = result.getValidStudents().get(0);
        assertEquals("1602-25-733-001", first.getRollNumber());
        assertEquals("Aarav Sharma", first.getName());
        assertEquals(8.87, first.getCgpa(), 0.001);
        assertEquals(8.90, first.getLastSemesterSgpa(), 0.001);
        assertEquals(8.85, first.getSem1Sgpa(), 0.001);
        assertEquals(92.5, first.getAttendancePercent(), 0.001);
        assertTrue(first.getMarks(1).containsKey(Subject.JAVA));
        assertEquals(27, first.getMarks(1).get(Subject.JAVA).getExamMarks());
    }

    @Test
    void detectsValidationErrorsAccurately(@TempDir Path tempDir) throws Exception {
        Path excelPath = tempDir.resolve("invalid_students.xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Test");
            // Header row
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Roll Number");
            header.createCell(1).setCellValue("Name");
            header.createCell(2).setCellValue("CGPA");
            header.createCell(3).setCellValue("SGPA");

            // Row 1: Invalid roll number & CGPA > 10
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("INVALID-ROLL");
            r1.createCell(1).setCellValue("John Doe");
            r1.createCell(2).setCellValue(11.5);
            r1.createCell(3).setCellValue(8.0);

            // Row 2: Missing name & negative SGPA
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("1602-25-733-101");
            r2.createCell(1).setCellValue(""); // missing name
            r2.createCell(2).setCellValue(7.5);
            r2.createCell(3).setCellValue(-1.0);

            // Row 3: Duplicate roll number (will be duplicated in Row 4) & exam marks out of bounds
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("1602-25-733-102");
            r3.createCell(1).setCellValue("Valid Name 1");
            r3.createCell(2).setCellValue(8.0);
            r3.createCell(3).setCellValue(8.0);
            // Col 4 is Java Exam (max 30), put 35
            r3.createCell(4).setCellValue(35);

            // Row 4: Duplicate roll number in file
            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue("1602-25-733-102");
            r4.createCell(1).setCellValue("Valid Name 2");
            r4.createCell(2).setCellValue(8.0);
            r4.createCell(3).setCellValue(8.0);

            // Row 5: Existing roll number in DB
            Row r5 = sheet.createRow(5);
            r5.createCell(0).setCellValue("1602-25-733-103");
            r5.createCell(1).setCellValue("Existing Student");
            r5.createCell(2).setCellValue(9.0);
            r5.createCell(3).setCellValue(9.0);

            // Row 6: Quiz marks > 5
            Row r6 = sheet.createRow(6);
            r6.createCell(0).setCellValue("1602-25-733-104");
            r6.createCell(1).setCellValue("Quiz Error Student");
            r6.createCell(2).setCellValue(9.0);
            r6.createCell(3).setCellValue(9.0);
            r6.createCell(4).setCellValue(25); // Java exam ok
            r6.createCell(5).setCellValue(5);  // A1
            r6.createCell(6).setCellValue(5);  // A2
            r6.createCell(7).setCellValue(5);  // A3
            r6.createCell(8).setCellValue(7);  // Q1 (invalid > 5)

            try (OutputStream out = Files.newOutputStream(excelPath)) {
                wb.write(out);
            }
        }

        Set<String> existingDbRolls = Set.of("1602-25-733-103");
        ExcelImporter.ExcelImportResult result = importer.parseAndValidate(excelPath, existingDbRolls);

        assertEquals(6, result.getTotalRowsRead());
        assertEquals(0, result.getValidCount());
        assertEquals(6, result.getInvalidCount());
        assertTrue(result.hasErrors());

        // Verify specific errors caught
        List<ExcelImporter.RowValidationError> errors = result.getInvalidRows();
        assertTrue(errors.get(0).errors().stream().anyMatch(e -> e.contains("does not match format")));
        assertTrue(errors.get(0).errors().stream().anyMatch(e -> e.contains("CGPA must be between 0 and 10")));
        assertTrue(errors.get(1).errors().stream().anyMatch(e -> e.contains("Student name is missing")));
        assertTrue(errors.get(1).errors().stream().anyMatch(e -> e.contains("SGPA must be between 0 and 10")));
        assertTrue(errors.get(2).errors().stream().anyMatch(e -> e.contains("exam marks must be between 0 and 30")));
        assertTrue(errors.get(3).errors().stream().anyMatch(e -> e.contains("Duplicate roll number within Excel file")));
        assertTrue(errors.get(4).errors().stream().anyMatch(e -> e.contains("already exists in the database")));
        assertTrue(errors.get(5).errors().stream().anyMatch(e -> e.contains("Quiz 1 must be between 0 and 5")));

        // Test error report export
        Path errorCsv = tempDir.resolve("errors.csv");
        importer.exportErrorReport(errors, errorCsv);
        assertTrue(Files.exists(errorCsv));
        String csvContent = Files.readString(errorCsv);
        assertTrue(csvContent.contains("INVALID-ROLL"));
        assertTrue(csvContent.contains("Quiz 1 must be between 0 and 5"));
    }

    @Test
    void handlesLargeBatchOfStudentsEfficiently(@TempDir Path tempDir) throws Exception {
        Path largeExcel = tempDir.resolve("large_class.xlsx");
        int count = 100;

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Class 100");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Roll Number");
            header.createCell(1).setCellValue("Name");
            header.createCell(2).setCellValue("CGPA");
            header.createCell(3).setCellValue("SGPA");

            for (int i = 1; i <= count; i++) {
                Row r = sheet.createRow(i);
                r.createCell(0).setCellValue(String.format("1602-25-733-%03d", i));
                r.createCell(1).setCellValue("Student " + i);
                r.createCell(2).setCellValue(7.50);
                r.createCell(3).setCellValue(7.80);
                int col = 4;
                for (int internal = 1; internal <= 2; internal++) {
                    for (Subject s : Subject.values()) {
                        r.createCell(col++).setCellValue(s.getMaximumExamMarks() - 5);
                        for (int a = 0; a < 3; a++) r.createCell(col++).setCellValue(4);
                        for (int q = 0; q < 3; q++) r.createCell(col++).setCellValue(4);
                    }
                }
            }

            try (OutputStream out = Files.newOutputStream(largeExcel)) {
                wb.write(out);
            }
        }

        ExcelImporter.ExcelImportResult result = importer.parseAndValidate(largeExcel, Set.of());
        assertEquals(count, result.getTotalRowsRead());
        assertEquals(count, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertFalse(result.hasErrors());
    }

    @Test
    void verifiesOpenElectiveIn118TemplateAndLegacyCompatibility(@TempDir Path tempDir) throws Exception {
        // 1. Verify 118-column template generation contains OE
        Path templatePath = tempDir.resolve("template_118.xlsx");
        importer.generateTemplate(templatePath);
        ExcelImporter.ExcelImportResult templateResult = importer.parseAndValidate(templatePath, Set.of());
        assertEquals(2, templateResult.getValidCount());

        Student sample1 = templateResult.getValidStudents().get(0);
        assertTrue(sample1.getMarks(1).containsKey(Subject.OE), "118 template must include OE in Internal 1");
        assertTrue(sample1.getMarks(2).containsKey(Subject.OE), "118 template must include OE in Internal 2");
        SubjectAssessment oe1 = sample1.getMarks(1).get(Subject.OE);
        assertEquals(27, oe1.getExamMarks()); // 30 - 3 = 27
        assertEquals(List.of(5, 4, 5), oe1.getAssignmentMarks());
        assertEquals(List.of(4, 5, 4), oe1.getQuizMarks());

        // 2. Verify legacy 102-column template (7 subjects without OE)
        Path legacyPath = tempDir.resolve("legacy_102.xlsx");
        List<Subject> legacySubjects = List.of(Subject.JAVA, Subject.DS, Subject.CA, Subject.TTPS, Subject.CS, Subject.CT, Subject.HVPE);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Legacy");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Roll Number");
            header.createCell(1).setCellValue("Name");
            header.createCell(2).setCellValue("CGPA");
            header.createCell(3).setCellValue("SGPA");

            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue("1602-25-733-010");
            r.createCell(1).setCellValue("Legacy Student");
            r.createCell(2).setCellValue(8.50);
            r.createCell(3).setCellValue(8.75);

            int col = 4;
            for (int internal = 1; internal <= 2; internal++) {
                for (Subject s : legacySubjects) {
                    r.createCell(col++).setCellValue(s.getMaximumExamMarks() - 2);
                    for (int a = 0; a < 3; a++) r.createCell(col++).setCellValue(4);
                    for (int q = 0; q < 3; q++) r.createCell(col++).setCellValue(4);
                }
            }
            try (OutputStream out = Files.newOutputStream(legacyPath)) {
                wb.write(out);
            }
        }

        ExcelImporter.ExcelImportResult legacyResult = importer.parseAndValidate(legacyPath, Set.of());
        assertEquals(1, legacyResult.getValidCount());
        Student legStudent = legacyResult.getValidStudents().get(0);
        // OE must remain NULL / not present, NOT converted to 0
        assertFalse(legStudent.getMarks(1).containsKey(Subject.OE), "Legacy format must keep OE NULL in Internal 1");
        assertFalse(legStudent.getMarks(2).containsKey(Subject.OE), "Legacy format must keep OE NULL in Internal 2");
        assertNull(legStudent.getMarks(1).get(Subject.OE));
        assertNull(legStudent.getMarks(2).get(Subject.OE));

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        assertFalse(analyzer.isInternalComplete(legStudent, 1), "Internal 1 without OE is incomplete");
        assertEquals("Incomplete assessment data", analyzer.prediction(legStudent));
    }
}
