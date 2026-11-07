package studentperformance;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Handles bulk import of student academic records from Excel spreadsheets (.xlsx and .xls).
 * Validates all student information and internal marks, provides import preview,
 * generates downloadable templates, and exports error reports.
 */
public class ExcelImporter {
    private static final Pattern ROLL_PATTERN = Pattern.compile("1602-25-733-\\d{3}");

    public record RowValidationError(int rowNumber, String rollNumber, String studentName, List<String> errors) {
        public String format() {
            String roll = (rollNumber == null || rollNumber.isBlank()) ? "UNKNOWN" : rollNumber;
            String name = (studentName == null || studentName.isBlank()) ? "UNKNOWN" : studentName;
            return "Row " + rowNumber + " [" + roll + " - " + name + "]: " + String.join("; ", errors);
        }
    }

    public static class ExcelImportResult {
        private final int totalRowsRead;
        private final List<Student> validStudents;
        private final List<RowValidationError> invalidRows;
        private final List<String> duplicateRollsInFile;
        private final List<String> existingRollsInDatabase;

        public ExcelImportResult(int totalRowsRead, List<Student> validStudents, List<RowValidationError> invalidRows,
                                 List<String> duplicateRollsInFile, List<String> existingRollsInDatabase) {
            this.totalRowsRead = totalRowsRead;
            this.validStudents = Collections.unmodifiableList(validStudents);
            this.invalidRows = Collections.unmodifiableList(invalidRows);
            this.duplicateRollsInFile = Collections.unmodifiableList(duplicateRollsInFile);
            this.existingRollsInDatabase = Collections.unmodifiableList(existingRollsInDatabase);
        }

        public int getTotalRowsRead() { return totalRowsRead; }
        public List<Student> getValidStudents() { return validStudents; }
        public List<RowValidationError> getInvalidRows() { return invalidRows; }
        public List<String> getDuplicateRollsInFile() { return duplicateRollsInFile; }
        public List<String> getExistingRollsInDatabase() { return existingRollsInDatabase; }
        public int getValidCount() { return validStudents.size(); }
        public int getInvalidCount() { return invalidRows.size(); }
        public boolean hasErrors() { return !invalidRows.isEmpty(); }
    }

    /**
     * Reads and validates an Excel spreadsheet against the database and required scoring rules.
     */
    public ExcelImportResult parseAndValidate(Path excelFile, Set<String> existingDatabaseRolls) throws IOException {
        if (excelFile == null || !Files.isRegularFile(excelFile)) {
            throw new IOException("Excel file does not exist: " + excelFile);
        }
        String filename = excelFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new IOException("Supported Excel formats are .xlsx and .xls");
        }

        List<Student> validStudents = new ArrayList<>();
        List<RowValidationError> invalidRows = new ArrayList<>();
        List<String> duplicateRollsInFile = new ArrayList<>();
        List<String> existingRollsInDb = new ArrayList<>();
        Set<String> seenRollsInFile = new HashSet<>();
        int totalRowsRead = 0;

        try (InputStream in = Files.newInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new IOException("The Excel file contains no worksheets.");

            int headerRowIndex = findHeaderRow(sheet);
            int startRow = headerRowIndex + 1;

            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                totalRowsRead++;
                int displayRowNum = r + 1;
                List<String> rowErrors = new ArrayList<>();

                // Col 0: Roll Number
                String roll = getCellString(row.getCell(0));
                if (roll == null || roll.isBlank()) {
                    rowErrors.add("Roll number is missing or blank");
                } else {
                    roll = roll.trim();
                    if (!ROLL_PATTERN.matcher(roll).matches()) {
                        rowErrors.add("Roll number '" + roll + "' does not match format 1602-25-733-XXX (three digits at end)");
                    }
                    if (!seenRollsInFile.add(roll)) {
                        rowErrors.add("Duplicate roll number within Excel file: " + roll);
                        duplicateRollsInFile.add(roll);
                    }
                    if (existingDatabaseRolls != null && existingDatabaseRolls.contains(roll)) {
                        rowErrors.add("Roll number '" + roll + "' already exists in the database (overwrite prevented)");
                        existingRollsInDb.add(roll);
                    }
                }

                // Col 1: Name
                String name = getCellString(row.getCell(1));
                if (name == null || name.isBlank()) {
                    rowErrors.add("Student name is missing or blank");
                } else {
                    name = name.trim();
                }

                // Col 2: CGPA
                Double cgpa = getCellDouble(row.getCell(2));
                if (cgpa == null) {
                    rowErrors.add("CGPA is missing or not a valid number");
                } else if (cgpa < 0 || cgpa > 10) {
                    rowErrors.add("CGPA must be between 0 and 10 (found: " + cgpa + ")");
                }

                // Col 3: Previous SGPA
                Double sgpa = getCellDouble(row.getCell(3));
                if (sgpa == null) {
                    rowErrors.add("Previous Semester SGPA is missing or not a valid number");
                } else if (sgpa < 0 || sgpa > 10) {
                    rowErrors.add("Previous Semester SGPA must be between 0 and 10 (found: " + sgpa + ")");
                }

                // Internal 1 & Internal 2 marks
                EnumMap<Subject, SubjectAssessment> internal1 = new EnumMap<>(Subject.class);
                EnumMap<Subject, SubjectAssessment> internal2 = new EnumMap<>(Subject.class);

                int colOffset = 4;
                for (int internal = 1; internal <= 2; internal++) {
                    EnumMap<Subject, SubjectAssessment> map = (internal == 1) ? internal1 : internal2;
                    for (Subject subject : Subject.values()) {
                        SubjectAssessment assessment = readAssessment(row, colOffset, internal, subject, rowErrors);
                        if (assessment != null) {
                            map.put(subject, assessment);
                        }
                        colOffset += 7; // 1 exam + 3 assignments + 3 quizzes
                    }
                }

                if (!rowErrors.isEmpty()) {
                    invalidRows.add(new RowValidationError(displayRowNum, roll, name, rowErrors));
                } else {
                    try {
                        Student student = new Student(name, roll, cgpa, sgpa, internal1, internal2);
                        validStudents.add(student);
                    } catch (IllegalArgumentException ex) {
                        rowErrors.add(ex.getMessage());
                        invalidRows.add(new RowValidationError(displayRowNum, roll, name, rowErrors));
                    }
                }
            }
        }

        return new ExcelImportResult(totalRowsRead, validStudents, invalidRows, duplicateRollsInFile, existingRollsInDb);
    }

    private SubjectAssessment readAssessment(Row row, int startCol, int internalNum, Subject subject, List<String> errors) {
        // Col 0: Exam
        Integer exam = getCellInt(row.getCell(startCol));
        if (exam != null) {
            if (exam < 0 || exam > subject.getMaximumExamMarks()) {
                errors.add("Internal " + internalNum + " " + subject.getDisplayName() + " exam marks must be between 0 and "
                        + subject.getMaximumExamMarks() + " (found: " + exam + ")");
            }
        }

        // Col 1-3: Assignments
        List<Integer> assignments = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Integer a = getCellInt(row.getCell(startCol + i));
            if (a != null && (a < 0 || a > 5)) {
                errors.add("Internal " + internalNum + " " + subject.getDisplayName() + " Assignment " + i + " must be between 0 and 5 (found: " + a + ")");
            }
            assignments.add(a);
        }

        // Col 4-6: Quizzes
        List<Integer> quizzes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Integer q = getCellInt(row.getCell(startCol + 3 + i));
            if (q != null && (q < 0 || q > 5)) {
                errors.add("Internal " + internalNum + " " + subject.getDisplayName() + " Quiz " + i + " must be between 0 and 5 (found: " + q + ")");
            }
            quizzes.add(q);
        }

        try {
            return new SubjectAssessment(exam, assignments, quizzes);
        } catch (IllegalArgumentException ex) {
            if (!errors.contains(ex.getMessage())) {
                errors.add(ex.getMessage());
            }
            return null;
        }
    }

    private int findHeaderRow(Sheet sheet) {
        for (int r = 0; r <= Math.min(5, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c <= Math.min(3, row.getLastCellNum()); c++) {
                String text = getCellString(row.getCell(c));
                if (text != null && text.toUpperCase(Locale.ROOT).contains("ROLL")) {
                    return r;
                }
            }
        }
        return 0; // Default to first row
    }

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String s = getCellString(cell);
                if (s != null && !s.isBlank()) return false;
            }
        }
        return true;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) yield String.format(Locale.ROOT, "%.0f", val);
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) {
                    try { yield String.valueOf(cell.getNumericCellValue()); }
                    catch (Exception ex) { yield null; }
                }
            }
            default -> null;
        };
    }

    private Double getCellDouble(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try { yield Double.parseDouble(cell.getStringCellValue().trim()); }
                catch (NumberFormatException e) { yield null; }
            }
            case FORMULA -> {
                try { yield cell.getNumericCellValue(); }
                catch (Exception e) { yield null; }
            }
            default -> null;
        };
    }

    private Integer getCellInt(Cell cell) {
        Double val = getCellDouble(cell);
        if (val == null) return null;
        return (int) Math.round(val);
    }

    /**
     * Generates a documented Excel (.xlsx) template with all 102 columns, styling, and sample rows.
     */
    public void generateTemplate(Path destination) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Student Data");
            Sheet instructionsSheet = workbook.createSheet("Instructions");

            // Fonts & Styles
            Font groupFont = workbook.createFont();
            groupFont.setBold(true);
            groupFont.setFontHeightInPoints((short) 11);
            groupFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle groupStyle1 = workbook.createCellStyle();
            groupStyle1.setFont(groupFont);
            groupStyle1.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            groupStyle1.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            groupStyle1.setAlignment(HorizontalAlignment.CENTER);

            CellStyle groupStyle2 = workbook.createCellStyle();
            groupStyle2.setFont(groupFont);
            groupStyle2.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            groupStyle2.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            groupStyle2.setAlignment(HorizontalAlignment.CENTER);

            CellStyle groupStyle3 = workbook.createCellStyle();
            groupStyle3.setFont(groupFont);
            groupStyle3.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            groupStyle3.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            groupStyle3.setAlignment(HorizontalAlignment.CENTER);

            Font subHeaderFont = workbook.createFont();
            subHeaderFont.setBold(true);
            CellStyle subHeaderStyle = workbook.createCellStyle();
            subHeaderStyle.setFont(subHeaderFont);
            subHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            subHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Row 0: Section Group Headers
            Row groupRow = sheet.createRow(0);
            createStyledCell(groupRow, 0, "STUDENT PROFILE INFORMATION", groupStyle1);
            createStyledCell(groupRow, 4, "INTERNAL 1 ASSESSMENTS", groupStyle2);
            createStyledCell(groupRow, 53, "INTERNAL 2 ASSESSMENTS", groupStyle3);

            // Row 1: Detailed Column Headers
            Row headerRow = sheet.createRow(1);
            String[] studentCols = {"Roll Number", "Full Name", "CGPA (0-10)", "Previous Semester SGPA (0-10)"};
            for (int i = 0; i < studentCols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(studentCols[i]);
                cell.setCellStyle(subHeaderStyle);
            }

            int col = 4;
            for (int internal = 1; internal <= 2; internal++) {
                for (Subject s : Subject.values()) {
                    String prefix = "Int" + internal + "_" + s.getDisplayName() + "_";
                    String[] compNames = {
                            prefix + "Exam(0-" + s.getMaximumExamMarks() + ")",
                            prefix + "A1(0-5)", prefix + "A2(0-5)", prefix + "A3(0-5)",
                            prefix + "Q1(0-5)", prefix + "Q2(0-5)", prefix + "Q3(0-5)"
                    };
                    for (String comp : compNames) {
                        Cell cell = headerRow.createCell(col++);
                        cell.setCellValue(comp);
                        cell.setCellStyle(subHeaderStyle);
                    }
                }
            }

            // Sample Row 1 (Complete)
            Row sample1 = sheet.createRow(2);
            sample1.createCell(0).setCellValue("1602-25-733-001");
            sample1.createCell(1).setCellValue("Aarav Sharma");
            sample1.createCell(2).setCellValue(8.85);
            sample1.createCell(3).setCellValue(8.90);
            col = 4;
            for (int internal = 1; internal <= 2; internal++) {
                for (Subject s : Subject.values()) {
                    sample1.createCell(col++).setCellValue(s.getMaximumExamMarks() - 3); // Exam
                    sample1.createCell(col++).setCellValue(5); // A1
                    sample1.createCell(col++).setCellValue(4); // A2
                    sample1.createCell(col++).setCellValue(5); // A3
                    sample1.createCell(col++).setCellValue(4); // Q1
                    sample1.createCell(col++).setCellValue(5); // Q2
                    sample1.createCell(col++).setCellValue(4); // Q3
                }
            }

            // Sample Row 2 (Another valid student)
            Row sample2 = sheet.createRow(3);
            sample2.createCell(0).setCellValue("1602-25-733-002");
            sample2.createCell(1).setCellValue("Priya Patel");
            sample2.createCell(2).setCellValue(9.10);
            sample2.createCell(3).setCellValue(9.25);
            col = 4;
            for (int internal = 1; internal <= 2; internal++) {
                for (Subject s : Subject.values()) {
                    sample2.createCell(col++).setCellValue(s.getMaximumExamMarks() - 2);
                    sample2.createCell(col++).setCellValue(5);
                    sample2.createCell(col++).setCellValue(5);
                    sample2.createCell(col++).setCellValue(5);
                    sample2.createCell(col++).setCellValue(5);
                    sample2.createCell(col++).setCellValue(4);
                    sample2.createCell(col++).setCellValue(5);
                }
            }

            // Instructions Sheet
            populateInstructionsSheet(instructionsSheet);

            try (OutputStream out = Files.newOutputStream(destination)) {
                workbook.write(out);
            }
        }
    }

    private void createStyledCell(Row row, int colIndex, String value, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void populateInstructionsSheet(Sheet sheet) {
        String[][] instructions = {
                {"EXCEL BULK STUDENT IMPORT GUIDELINES", ""},
                {"", ""},
                {"1. Roll Number Format", "Must follow format: 1602-25-733-XXX (where XXX is a 3-digit number)."},
                {"2. Uniqueness", "Roll numbers must be unique within the sheet and must not already exist in MySQL."},
                {"3. CGPA and SGPA", "Values must be decimals or numbers between 0.00 and 10.00."},
                {"4. Seven Theory Subjects", "Java, Data Structures (DS), Computer Architecture (CA), TTPS, CS, CT, HVPE."},
                {"5. Exam Marks Maximums", "Java, DS, CA, TTPS: Maximum 30 marks. CS, CT, HVPE: Maximum 20 marks."},
                {"6. Assignment & Quiz Marks", "Every assignment (A1, A2, A3) and quiz (Q1, Q2, Q3) is out of 5 marks (0 to 5)."},
                {"7. Internal 1 & Internal 2", "Both internals follow the identical subject and mark structure."},
                {"8. Empty Assessment Cells", "Empty marks cells are permitted for partial records; missing values will not be converted to 0."},
                {"9. Error Handling", "The system validates every row, highlights any issues, and allows error report export."}
        };
        for (int i = 0; i < instructions.length; i++) {
            Row r = sheet.createRow(i);
            r.createCell(0).setCellValue(instructions[i][0]);
            r.createCell(1).setCellValue(instructions[i][1]);
        }
    }

    /**
     * Exports detailed validation errors to a CSV file.
     */
    public void exportErrorReport(List<RowValidationError> errors, Path destination) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
            writer.write("Row Number,Roll Number,Student Name,Validation Errors\n");
            for (RowValidationError err : errors) {
                String roll = err.rollNumber() == null ? "" : err.rollNumber();
                String name = err.studentName() == null ? "" : err.studentName();
                String joinedErrors = String.join(" | ", err.errors()).replace("\"", "\"\"");
                writer.write(String.format(Locale.ROOT, "%d,\"%s\",\"%s\",\"%s\"\n", err.rowNumber(), roll, name, joinedErrors));
            }
        }
    }
}
