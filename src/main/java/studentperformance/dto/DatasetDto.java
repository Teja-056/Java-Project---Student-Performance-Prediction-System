package studentperformance.dto;

import studentperformance.Dataset;

import java.time.format.DateTimeFormatter;

public class DatasetDto {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long datasetId;
    private String datasetCode;
    private String name;
    private String originalFilename;
    private String semester;
    private String academicYear;
    private int totalRows;
    private int validCount;
    private int invalidCount;
    private boolean active;
    private String status;
    private String validationReport;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;

    public DatasetDto() {}

    public DatasetDto(Dataset d) {
        if (d != null) {
            this.datasetId = d.getDatasetId();
            this.datasetCode = d.getDatasetCode();
            this.name = d.getName();
            this.originalFilename = d.getOriginalFilename();
            this.semester = d.getSemester();
            this.academicYear = d.getAcademicYear();
            this.totalRows = d.getTotalRows();
            this.validCount = d.getValidCount();
            this.invalidCount = d.getInvalidCount();
            this.active = d.isActive();
            this.status = d.getStatus();
            this.validationReport = d.getValidationReport();
            this.errorMessage = d.getErrorMessage();
            if (d.getCreatedAt() != null) {
                this.createdAt = d.getCreatedAt().toLocalDateTime().format(FORMATTER);
            }
            if (d.getUpdatedAt() != null) {
                this.updatedAt = d.getUpdatedAt().toLocalDateTime().format(FORMATTER);
            }
        }
    }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

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

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
