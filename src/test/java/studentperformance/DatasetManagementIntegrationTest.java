package studentperformance;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import studentperformance.dto.DatasetDto;
import studentperformance.dto.DatasetUploadResponseDto;
import studentperformance.dto.StudentReportDto;
import studentperformance.dto.StudentResponseDto;
import studentperformance.dto.SubjectAnalyticsDto;
import studentperformance.dto.SubjectPerformanceResponseDto;
import studentperformance.exception.BadRequestException;
import studentperformance.prediction.PredictionEngine;
import studentperformance.prediction.StudentPredictionResult;
import studentperformance.service.DatasetService;
import studentperformance.service.PredictionService;
import studentperformance.service.StudentService;
import studentperformance.service.SubjectPerformanceService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DatasetManagementIntegrationTest {

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentService studentService;

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private SubjectPerformanceService subjectPerformanceService;

    private static Long secondDatasetId = null;

    @Test
    @Order(1)
    @DisplayName("1. Baseline dataset #1 exists, is active, and contains 101 students")
    void test1_BaselineDatasetExistsAndActive() throws SQLException {
        datasetService.activateDataset(1L);
        Dataset active = datasetRepository.findActive();
        assertNotNull(active, "Active dataset must exist");
        assertEquals(1L, active.getDatasetId());
        assertEquals("DS-2026-BASELINE", active.getDatasetCode());
        assertTrue(active.isActive());

        List<Student> activeStudents = studentRepository.findAll();
        assertEquals(101, activeStudents.size(), "Baseline dataset must have 101 students");
    }

    @Test
    @Order(2)
    @DisplayName("2. Upload second dataset with SAME roll number (1602-25-733-001) but different student info")
    void test2_UploadSecondIndependentDataset() throws Exception {
        byte[] excelBytes = createSampleExcelBytes(List.of(
                new TestStudentData("1602-25-733-001", "Cohort 2 Aarav", 9.50, 9.60, 9.55, 98.0, 29, 29),
                new TestStudentData("1602-25-733-002", "Cohort 2 Diya", 8.80, 8.90, 8.85, 94.0, 26, 27)
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "cohort_2026_test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        DatasetUploadResponseDto response = datasetService.uploadAndCreateDataset(
                file, "Spring 2026 Cohort", "Semester III", "2025-2026", false
        );

        assertTrue(response.isSuccess());
        assertNotNull(response.getDataset());
        secondDatasetId = response.getDataset().getDatasetId();
        assertNotNull(secondDatasetId);
        assertNotEquals(1L, secondDatasetId);
        assertEquals(2, response.getDataset().getValidCount());
        assertEquals(0, response.getDataset().getInvalidCount());

        // Verify Dataset 1 is still active
        Dataset currentActive = datasetRepository.findActive();
        assertEquals(1L, currentActive.getDatasetId());

        // Verify student 001 in Dataset 1 is "Pooja Mishra"
        Student d1Student001 = studentRepository.findByRollNumber(1L, "1602-25-733-001");
        assertNotNull(d1Student001);
        assertEquals("Pooja Mishra", d1Student001.getName());

        // Verify student 001 in Dataset 2 is "Cohort 2 Aarav"
        Student d2Student001 = studentRepository.findByRollNumber(secondDatasetId, "1602-25-733-001");
        assertNotNull(d2Student001);
        assertEquals("Cohort 2 Aarav", d2Student001.getName());
    }

    @Test
    @Order(3)
    @DisplayName("3. Reject duplicate roll number within the same Excel file")
    void test3_RejectDuplicateRollNumberWithinSameDataset() throws Exception {
        byte[] duplicateBytes = createSampleExcelBytes(List.of(
                new TestStudentData("1602-25-733-050", "Student 50", 8.0, 8.0, 8.0, 90.0, 25, 25),
                new TestStudentData("1602-25-733-050", "Duplicate Student 50", 8.0, 8.0, 8.0, 90.0, 25, 25)
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "duplicates_test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                duplicateBytes
        );

        DatasetUploadResponseDto response = datasetService.uploadAndCreateDataset(
                file, "Duplicates Cohort", "Semester III", "2025-2026", false
        );

        // 1 valid, 1 invalid duplicate
        assertEquals(1, response.getDataset().getValidCount());
        assertEquals(1, response.getDataset().getInvalidCount());
        assertTrue(response.getValidationErrors().stream().anyMatch(e -> e.contains("Duplicate roll number within Excel file")));

        // Clean up this test dataset
        datasetService.deleteDataset(response.getDataset().getDatasetId());
    }

    @Test
    @Order(4)
    @DisplayName("4. Switch between datasets and verify student listing isolation")
    void test4_SwitchBetweenDatasets() throws SQLException {
        assertNotNull(secondDatasetId);

        // Activate Dataset #2
        datasetService.activateDataset(secondDatasetId);
        assertEquals(secondDatasetId, datasetService.getActiveDatasetId());

        // Student listing should now return only 2 students
        List<Student> d2Students = studentRepository.findAll();
        assertEquals(2, d2Students.size());

        // StudentService should also return only 2 students
        assertEquals(2, studentService.getAllStudents().size());

        // Student 1602-25-733-099 exists in Dataset 1, but NOT in Dataset 2
        assertNull(studentRepository.findByRollNumber("1602-25-733-099"));

        // Switch back to Dataset #1
        datasetService.activateDataset(1L);
        assertEquals(1L, datasetService.getActiveDatasetId());
        assertEquals(101, studentRepository.findAll().size());
        assertEquals(101, studentService.getAllStudents().size());
        assertNotNull(studentRepository.findByRollNumber("1602-25-733-099"));
    }

    @Test
    @Order(5)
    @DisplayName("5. Performance predictions, reports, and analytics work strictly on the active dataset")
    void test5_PredictionsAndAnalyticsWorkOnActiveDataset() {
        // --- Dataset 1 verification ---
        StudentPredictionResult p1 = predictionService.getPrediction("1602-25-733-001");
        assertNotNull(p1);
        assertEquals("Pooja Mishra", p1.getStudentName());
        assertEquals(9.16, p1.getScenarios().get("CONSISTENT").getPredictedSgpa(), 0.05);

        // Student Profile & Report in Dataset 1
        StudentResponseDto profileD1 = studentService.getStudentByRoll("1602-25-733-001");
        assertEquals("Pooja Mishra", profileD1.getName());
        StudentReportDto reportD1 = studentService.getStudentReport("1602-25-733-001");
        assertEquals("Pooja Mishra", reportD1.getName());

        // Class Analytics & Subject Performance in Dataset 1 (101 students)
        List<SubjectAnalyticsDto> analyticsD1 = studentService.getClassSubjectAnalytics();
        assertFalse(analyticsD1.isEmpty());
        assertEquals(101, analyticsD1.get(0).getTotalStudentsEvaluated());
        SubjectPerformanceResponseDto perfD1 = subjectPerformanceService.getSubjectPerformance("DS", "ALL");
        assertEquals(101, perfD1.getTotalStudents());

        // --- Switch to Dataset 2 ---
        datasetService.activateDataset(secondDatasetId);

        StudentPredictionResult p2 = predictionService.getPrediction("1602-25-733-001");
        assertNotNull(p2);
        assertEquals("Cohort 2 Aarav", p2.getStudentName());
        assertEquals(9.55, p2.getPreviousCgpa(), 0.05);

        // Student Profile & Report in Dataset 2
        StudentResponseDto profileD2 = studentService.getStudentByRoll("1602-25-733-001");
        assertEquals("Cohort 2 Aarav", profileD2.getName());
        assertEquals(9.55, profileD2.getCgpa(), 0.01);
        StudentReportDto reportD2 = studentService.getStudentReport("1602-25-733-001");
        assertEquals("Cohort 2 Aarav", reportD2.getName());

        // Class Analytics & Subject Performance in Dataset 2 (2 students only)
        List<SubjectAnalyticsDto> analyticsD2 = studentService.getClassSubjectAnalytics();
        assertFalse(analyticsD2.isEmpty());
        assertEquals(2, analyticsD2.get(0).getTotalStudentsEvaluated());
        SubjectPerformanceResponseDto perfD2 = subjectPerformanceService.getSubjectPerformance("DS", "ALL");
        assertEquals(2, perfD2.getTotalStudents());

        // --- Switch back to Dataset 1 ---
        datasetService.activateDataset(1L);

        // Verify restoration of Dataset 1
        StudentResponseDto restoredProfile = studentService.getStudentByRoll("1602-25-733-001");
        assertEquals("Pooja Mishra", restoredProfile.getName());
        List<SubjectAnalyticsDto> restoredAnalytics = studentService.getClassSubjectAnalytics();
        assertEquals(101, restoredAnalytics.get(0).getTotalStudentsEvaluated());
    }

    @Test
    @Order(6)
    @DisplayName("6. Prevent deletion of the active dataset")
    void test6_PreventDeletionOfActiveDataset() {
        // Dataset 1 is active; deleting it must throw BadRequestException
        BadRequestException ex = assertThrows(BadRequestException.class, () -> datasetService.deleteDataset(1L));
        assertTrue(ex.getMessage().contains("Cannot delete the currently active dataset"));
    }

    @Test
    @Order(7)
    @DisplayName("7. Failed upload does not alter the active dataset")
    void test7_FailedUploadDoesNotAlterActiveDataset() throws Exception {
        byte[] emptyRows = createEmptyExcelBytes();
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", emptyRows);

        DatasetUploadResponseDto resp = datasetService.uploadAndCreateDataset(file, "Corrupted Cohort", "Semester III", "2025-2026", false);
        assertFalse(resp.isSuccess());
        assertEquals(0, resp.getDataset().getValidCount());

        // Active dataset must still be 1
        assertEquals(1L, datasetService.getActiveDatasetId());

        // Clean up failed dataset
        datasetService.deleteDataset(resp.getDataset().getDatasetId());
    }

    @Test
    @Order(8)
    @DisplayName("8. Clean up test dataset and preserve Baseline Dataset 1")
    void test8_CleanUpTestDataset() throws SQLException {
        if (secondDatasetId != null) {
            datasetService.activateDataset(1L);
            datasetService.deleteDataset(secondDatasetId);
        }

        List<DatasetDto> all = datasetService.getAllDatasets();
        assertFalse(all.stream().anyMatch(d -> d.getDatasetId().equals(secondDatasetId)), "Cleaned up test dataset should no longer exist");
        Dataset baseline = datasetRepository.findById(1L);
        assertNotNull(baseline);
        assertTrue(baseline.isActive());
        assertEquals(101, studentRepository.findAll().size());
    }

    private record TestStudentData(String roll, String name, double sem1, double sem2, double cgpa, double att, int exam1, int exam2) {}

    private byte[] createSampleExcelBytes(List<TestStudentData> students) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Student Performance");

            // Row 0 Group Headers
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("STUDENT INFORMATION (6 COLUMNS)");
            r0.createCell(6).setCellValue("INTERNAL 1 ASSESSMENTS (8 SUBJECTS X 7 COLUMNS = 56 COLUMNS)");
            r0.createCell(62).setCellValue("INTERNAL 2 ASSESSMENTS (8 SUBJECTS X 7 COLUMNS = 56 COLUMNS)");

            // Row 1 Column Headers
            Row r1 = sheet.createRow(1);
            String[] cols = {"Roll Number", "Student Name", "Semester I SGPA", "Semester II SGPA", "Previous CGPA", "Attendance (%)"};
            for (int i = 0; i < cols.length; i++) r1.createCell(i).setCellValue(cols[i]);

            int col = 6;
            for (int internal = 1; internal <= 2; internal++) {
                for (Subject s : Subject.values()) {
                    r1.createCell(col++).setCellValue("Int" + internal + "_" + s.getDisplayName() + "_Exam");
                    for (int a = 1; a <= 3; a++) r1.createCell(col++).setCellValue("Int" + internal + "_" + s.getDisplayName() + "_A" + a);
                    for (int q = 1; q <= 3; q++) r1.createCell(col++).setCellValue("Int" + internal + "_" + s.getDisplayName() + "_Q" + q);
                }
            }

            // Student rows
            int rowIdx = 2;
            for (TestStudentData s : students) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(s.roll);
                r.createCell(1).setCellValue(s.name);
                r.createCell(2).setCellValue(s.sem1);
                r.createCell(3).setCellValue(s.sem2);
                r.createCell(4).setCellValue(s.cgpa);
                r.createCell(5).setCellValue(s.att);

                col = 6;
                for (int internal = 1; internal <= 2; internal++) {
                    int examMarks = (internal == 1) ? s.exam1 : s.exam2;
                    for (Subject subj : Subject.values()) {
                        int marks = Math.min(examMarks, subj.getMaximumExamMarks());
                        r.createCell(col++).setCellValue(marks);
                        for (int a = 0; a < 3; a++) r.createCell(col++).setCellValue(5);
                        for (int q = 0; q < 3; q++) r.createCell(col++).setCellValue(4);
                    }
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createEmptyExcelBytes() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Empty");
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("Roll Number");
            r0.createCell(1).setCellValue("Student Name");
            wb.write(out);
            return out.toByteArray();
        }
    }
}
