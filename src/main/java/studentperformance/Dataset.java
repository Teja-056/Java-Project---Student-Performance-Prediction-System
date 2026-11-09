package studentperformance;

import java.sql.Timestamp;

/**
 * Domain model representing an isolated student dataset / cohort uploaded via Excel.
 */
public class Dataset {
    private Long datasetId;
    private String datasetCode;
    private String name;
    private String originalFilename;
    private String storagePath;
    private String semester;
    private String academicYear;
    private int totalRows;
    private int validCount;
    private int invalidCount;
    private boolean active;
    private String status;
    private String validationReport;
    private String errorMessage;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Dataset() {
        this.semester = "Semester III";
        this.status = "READY";
        this.active = false;
    }

    public Dataset(Long datasetId, String datasetCode, String name, String originalFilename,
                   String storagePath, String semester, String academicYear,
                   int totalRows, int validCount, int invalidCount,
                   boolean active, String status, String validationReport, String errorMessage,
                   Timestamp createdAt, Timestamp updatedAt) {
        this.datasetId = datasetId;
        this.datasetCode = datasetCode;
        this.name = name;
        this.originalFilename = originalFilename;
        this.storagePath = storagePath;
        this.semester = semester;
        this.academicYear = academicYear;
        this.totalRows = totalRows;
        this.validCount = validCount;
        this.invalidCount = invalidCount;
        this.active = active;
        this.status = status;
        this.validationReport = validationReport;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getValidCount() { return validCount; }
    public void setValidCount(int validCount) { this.validCount = validCount; }

    public int getInvalidCount() { return invalidCount; }
    public void setInvalidCount(int invalidCount) { this.invalidCount = invalidCount; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getValidationReport() { return validationReport; }
    public void setValidationReport(String validationReport) { this.validationReport = validationReport; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
