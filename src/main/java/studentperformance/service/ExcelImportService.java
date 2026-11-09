package studentperformance.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import studentperformance.*;
import studentperformance.dto.ExcelUploadResponseDto;
import studentperformance.dto.StudentResponseDto;
import studentperformance.exception.BadRequestException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExcelImportService {

    private final StudentRepository studentRepository;
    private final DatasetRepository datasetRepository;
    private final ExcelImporter excelImporter;
    private final PerformanceAnalyzer performanceAnalyzer;

    public ExcelImportService(StudentRepository studentRepository,
                              DatasetRepository datasetRepository,
                              ExcelImporter excelImporter,
                              PerformanceAnalyzer performanceAnalyzer) {
        this.studentRepository = studentRepository;
        this.datasetRepository = datasetRepository;
        this.excelImporter = excelImporter;
        this.performanceAnalyzer = performanceAnalyzer;
    }

    public ExcelUploadResponseDto processUpload(MultipartFile file, boolean commit) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select an Excel (.xlsx or .xls) file to upload.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || (!originalName.toLowerCase().endsWith(".xlsx") && !originalName.toLowerCase().endsWith(".xls"))) {
            throw new BadRequestException("Invalid file format. Only .xlsx and .xls spreadsheets are supported.");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("excel-upload-", originalName);
            file.transferTo(tempFile.toFile());

            // 1. Fetch existing roll numbers from database to prevent overwrite
            Set<String> existingRolls = studentRepository.findAll().stream()
                    .map(Student::getRollNumber)
                    .collect(Collectors.toSet());

            // 2. Validate spreadsheet
            ExcelImporter.ExcelImportResult result = excelImporter.parseAndValidate(tempFile, existingRolls);

            List<String> errors = result.getInvalidRows().stream()
                    .map(ExcelImporter.RowValidationError::format)
                    .toList();

            List<StudentResponseDto> preview = result.getValidStudents().stream()
                    .limit(25) // preview first 25 valid records
                    .map(s -> new StudentResponseDto(s, performanceAnalyzer))
                    .toList();

            boolean actuallyCommitted = false;
            String message;

            if (commit) {
                if (!result.getValidStudents().isEmpty()) {
                    Dataset dataset = new Dataset();
                    dataset.setDatasetCode("DS-" + System.currentTimeMillis());
                    dataset.setName("Imported Cohort - " + originalName.replaceFirst("\\.[^.]+$", ""));
                    dataset.setOriginalFilename(originalName);
                    dataset.setTotalRows(result.getTotalRowsRead());
                    dataset.setValidCount(result.getValidCount());
                    dataset.setInvalidCount(result.getInvalidCount());
                    dataset.setStatus("READY");
                    dataset.setActive(false);
                    Long newDatasetId = datasetRepository.save(dataset);

                    studentRepository.saveBatch(newDatasetId, result.getValidStudents());
                    datasetRepository.activate(newDatasetId);
                    actuallyCommitted = true;
                    message = "Successfully created dataset #" + newDatasetId + " with " + result.getValidCount() + " student records and activated it.";
                } else {
                    message = "Import failed: Zero valid student records were found in the file.";
                }
            } else {
                message = "Validation complete: " + result.getValidCount() + " valid records ready to commit. "
                        + result.getInvalidCount() + " invalid rows detected.";
            }

            return new ExcelUploadResponseDto(
                    originalName,
                    result.getTotalRowsRead(),
                    result.getValidCount(),
                    result.getInvalidCount(),
                    result.getDuplicateRollsInFile(),
                    errors,
                    preview,
                    actuallyCommitted,
                    message
            );
        } catch (IOException e) {
            throw new BadRequestException("Failed to read or parse Excel file: " + e.getMessage());
        } catch (SQLException e) {
            throw new RuntimeException("Database error saving imported students: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
