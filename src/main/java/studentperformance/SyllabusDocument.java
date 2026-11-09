package studentperformance;

import java.util.ArrayList;
import java.util.List;

public class SyllabusDocument {
    private Long documentId;
    private String filename;
    private String status = "REVIEW_REQUIRED";
    private String uploadedAt;
    private String rawExtractedText = "";
    private String institution = "";
    private String academicScheme = "";
    private String academicYear = "";
    private String branch = "";
    private String semester = "";
    private String validationReportJson = "";
    private String errorMessage = "";
    private String extractionEngine = "LEGACY";
    private String pdfPath = "";
    private Long parentDocumentId;
    private final List<SyllabusSubject> subjects = new ArrayList<>();

    public SyllabusDocument() {}

    public SyllabusDocument(String filename) {
        this.filename = filename;
    }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getRawExtractedText() { return rawExtractedText; }
    public void setRawExtractedText(String text) { rawExtractedText = text == null ? "" : text; }

    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution == null ? "" : institution.trim(); }

    public String getAcademicScheme() { return academicScheme; }
    public void setAcademicScheme(String academicScheme) { this.academicScheme = academicScheme == null ? "" : academicScheme.trim(); }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear == null ? "" : academicYear.trim(); }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch == null ? "" : branch.trim(); }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester == null ? "" : semester.trim(); }

    public String getValidationReportJson() { return validationReportJson; }
    public void setValidationReportJson(String validationReportJson) { this.validationReportJson = validationReportJson == null ? "" : validationReportJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage == null ? "" : errorMessage; }

    public String getExtractionEngine() { return extractionEngine; }
    public void setExtractionEngine(String extractionEngine) { this.extractionEngine = extractionEngine == null ? "LEGACY" : extractionEngine; }

    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath == null ? "" : pdfPath; }

    public Long getParentDocumentId() { return parentDocumentId; }
    public void setParentDocumentId(Long parentDocumentId) { this.parentDocumentId = parentDocumentId; }

    public List<SyllabusSubject> getSubjects() { return subjects; }
}
