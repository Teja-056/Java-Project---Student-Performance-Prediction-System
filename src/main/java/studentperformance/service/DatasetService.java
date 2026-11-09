package studentperformance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import studentperformance.*;
import studentperformance.dto.DatasetDto;
import studentperformance.dto.DatasetUploadResponseDto;
import studentperformance.dto.StudentResponseDto;
import studentperformance.exception.BadRequestException;
import studentperformance.exception.ResourceNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Service for dataset lifecycle management: creation, file persistence, validation,
 * transactional activation switching, and dataset-isolated student operations.
 */
@Service
public class DatasetService {

    private static final Path UPLOADS_DIR = Paths.get("uploads", "datasets");

    private final DatasetRepository datasetRepository;
    private final StudentRepository studentRepository;
    private final ExcelImporter excelImporter;
    private final PerformanceAnalyzer performanceAnalyzer;
    private final ObjectMapper objectMapper;

    public DatasetService(DatasetRepository datasetRepository,
                          StudentRepository studentRepository,
                          ExcelImporter excelImporter,
                          PerformanceAnalyzer performanceAnalyzer,
                          ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.studentRepository = studentRepository;
        this.excelImporter = excelImporter;
        this.performanceAnalyzer = performanceAnalyzer;
        this.objectMapper = objectMapper;

        try {
            if (!Files.exists(UPLOADS_DIR)) {
                Files.createDirectories(UPLOADS_DIR);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not initialize uploads/datasets directory: " + e.getMessage());
        }
    }

    public List<DatasetDto> getAllDatasets() {
        try {
            return datasetRepository.findAll().stream()
                    .map(DatasetDto::new)
                    .toList();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load dataset history: " + e.getMessage(), e);
        }
    }

    public DatasetDto getActiveDataset() {
        try {
            Dataset active = datasetRepository.findActive();
            if (active == null) {
                // Fallback to dataset 1 if none active
                active = datasetRepository.findById(1L);
            }
            if (active == null) {
                throw new ResourceNotFoundException("No active dataset found in the system.");
            }
            return new DatasetDto(active);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active dataset: " + e.getMessage(), e);
        }
    }

    public Long getActiveDatasetId() {
        try {
            Dataset active = datasetRepository.findActive();
            if (active != null) return active.getDatasetId();
        } catch (SQLException ignored) {}
        return 1L;
    }

    public DatasetDto getDatasetById(Long id) {
        try {
            Dataset d = datasetRepository.findById(id);
            if (d == null) {
                throw new ResourceNotFoundException("Dataset with ID " + id + " was not found.");
            }
            return new DatasetDto(d);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load dataset details: " + e.getMessage(), e);
        }
    }

    public DatasetDto activateDataset(Long id) {
        if (id == null) {
            throw new BadRequestException("Dataset ID cannot be null.");
        }
        try {
            Dataset target = datasetRepository.findById(id);
            if (target == null) {
                throw new ResourceNotFoundException("Dataset with ID " + id + " was not found.");
            }
            if ("FAILED".equalsIgnoreCase(target.getStatus())) {
                throw new BadRequestException("Cannot activate a failed dataset (ID " + id + ").");
            }
            if (target.getValidCount() == 0) {
                throw new BadRequestException("Cannot activate dataset with zero valid student records.");
            }

            boolean activated = datasetRepository.activate(id);
            if (!activated) {
                throw new RuntimeException("Failed to activate dataset " + id);
            }
            return new DatasetDto(datasetRepository.findById(id));
        } catch (SQLException e) {
            throw new RuntimeException("Database error activating dataset: " + e.getMessage(), e);
        }
    }

    public void deleteDataset(Long id) {
        if (id == null) {
            throw new BadRequestException("Dataset ID cannot be null.");
        }
        try {
            Dataset target = datasetRepository.findById(id);
            if (target == null) {
                throw new ResourceNotFoundException("Dataset with ID " + id + " was not found.");
            }
            if (target.isActive()) {
                throw new BadRequestException("Cannot delete the currently active dataset. Please activate another dataset first.");
            }

            // Remove physical file if present
            if (target.getStoragePath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(target.getStoragePath()));
                } catch (IOException ignored) {}
            }

            // Delete record in database (cascades to students and assessments)
            datasetRepository.delete(id);
        } catch (SQLException e) {
            throw new RuntimeException("Database error deleting dataset: " + e.getMessage(), e);
        }
    }

    public DatasetUploadResponseDto uploadAndCreateDataset(MultipartFile file,
                                                          String customName,
                                                          String semester,
                                                          String academicYear,
                                                          boolean activateImmediately) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select an Excel (.xlsx or .xls) file to upload.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            throw new BadRequestException("Invalid file format. Only Microsoft Excel (.xlsx, .xls) files are supported.");
        }

        String sem = (semester != null && !semester.isBlank()) ? semester.trim() : "Semester III";
        String year = (academicYear != null && !academicYear.isBlank()) ? academicYear.trim() : (LocalDate.now().getYear() + "-" + (LocalDate.now().getYear() + 1));
        String name = (customName != null && !customName.isBlank()) ? customName.trim() : ("Cohort - " + originalFilename.replaceFirst("\\.[^.]+$", ""));

        String datasetCode = "DS-" + System.currentTimeMillis();
        Path storedFile = null;

        Dataset dataset = new Dataset();
        dataset.setDatasetCode(datasetCode);
        dataset.setName(name);
        dataset.setOriginalFilename(originalFilename);
        dataset.setSemester(sem);
        dataset.setAcademicYear(year);
        dataset.setActive(false);
        dataset.setStatus("PROCESSING");

        Long datasetId;
        try {
            datasetId = datasetRepository.save(dataset);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize dataset record: " + e.getMessage(), e);
        }

        try {
            // Persist original file in uploads/datasets/
            String safePrefix = "dataset_" + datasetId + "_";
            storedFile = UPLOADS_DIR.resolve(safePrefix + originalFilename);
            Files.copy(file.getInputStream(), storedFile, StandardCopyOption.REPLACE_EXISTING);
            dataset.setStoragePath(storedFile.toString());

            // Validate spreadsheet: note that existing rolls in OTHER datasets do not cause conflicts
            ExcelImporter.ExcelImportResult result = excelImporter.parseAndValidate(storedFile, Collections.emptySet());

            dataset.setTotalRows(result.getTotalRowsRead());
            dataset.setValidCount(result.getValidCount());
            dataset.setInvalidCount(result.getInvalidCount());

            List<String> errors = result.getInvalidRows().stream()
                    .map(ExcelImporter.RowValidationError::format)
                    .toList();

            Map<String, Object> reportMap = new LinkedHashMap<>();
            reportMap.put("totalRows", result.getTotalRowsRead());
            reportMap.put("validCount", result.getValidCount());
            reportMap.put("invalidCount", result.getInvalidCount());
            reportMap.put("duplicateRollsInFile", result.getDuplicateRollsInFile());
            reportMap.put("errors", errors);
            dataset.setValidationReport(objectMapper.writeValueAsString(reportMap));

            if (result.getValidStudents().isEmpty()) {
                dataset.setStatus("FAILED");
                dataset.setErrorMessage("Zero valid student records were found in the uploaded file.");
                datasetRepository.save(dataset);

                return new DatasetUploadResponseDto(
                        false,
                        "Upload processed with errors: Zero valid student records found.",
                        new DatasetDto(dataset),
                        Collections.emptyList(),
                        errors
                );
            }

            // Persist valid students into MySQL under this specific datasetId
            studentRepository.saveBatch(datasetId, result.getValidStudents());
            dataset.setStatus("READY");
            datasetRepository.save(dataset);

            // Activate if requested
            if (activateImmediately) {
                datasetRepository.activate(datasetId);
                dataset.setActive(true);
            }

            List<StudentResponseDto> preview = result.getValidStudents().stream()
                    .limit(25)
                    .map(s -> new StudentResponseDto(s, performanceAnalyzer))
                    .toList();

            String successMsg = "Dataset '" + name + "' uploaded successfully with " + result.getValidCount()
                    + " valid student records." + (activateImmediately ? " Set as currently ACTIVE dataset." : "");

            return new DatasetUploadResponseDto(
                    true,
                    successMsg,
                    new DatasetDto(dataset),
                    preview,
                    errors
            );
        } catch (Exception e) {
            // Mark dataset as failed without touching active dataset
            dataset.setStatus("FAILED");
            dataset.setErrorMessage(e.getMessage());
            try {
                datasetRepository.save(dataset);
            } catch (SQLException ignored) {}

            throw new RuntimeException("Failed to complete dataset upload: " + e.getMessage(), e);
        }
    }
}
