package studentperformance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import studentperformance.SyllabusDocument;
import studentperformance.SyllabusSubject;
import studentperformance.SyllabusTopic;
import studentperformance.SyllabusUnit;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyllabusDocumentDto {
    private Long documentId;
    private String filename;
    private String status;
    private String uploadedAt;
    private String institution;
    private String academicScheme;
    private String academicYear;
    private String branch;
    private String semester;
    private int subjectCount;
    private int totalUnits;
    private int totalTopics;
    private int totalCredits;
    private String validationReportJson;
    private Object validationReport;
    private String errorMessage;
    private String extractionEngine;
    private String pdfPath;
    private Long parentDocumentId;
    private boolean hasPdfOnDisk;
    private boolean reprocessAvailable;
    private List<SubjectDetailDto> subjects = new ArrayList<>();

    public SyllabusDocumentDto() {}

    public SyllabusDocumentDto(Long id, SyllabusDocument doc, String status) {
        this.documentId = id;
        this.filename = doc.getFilename();
        this.status = status != null ? status : doc.getStatus();
        this.uploadedAt = doc.getUploadedAt();
        this.institution = doc.getInstitution();
        this.academicScheme = doc.getAcademicScheme();
        this.academicYear = doc.getAcademicYear();
        this.branch = doc.getBranch();
        this.semester = doc.getSemester();
        this.validationReportJson = doc.getValidationReportJson();
        this.errorMessage = doc.getErrorMessage();
        this.extractionEngine = doc.getExtractionEngine() != null ? doc.getExtractionEngine() : "LEGACY";
        this.pdfPath = doc.getPdfPath();
        this.parentDocumentId = doc.getParentDocumentId();
        this.hasPdfOnDisk = checkPdfExistsOnDisk(doc.getPdfPath(), doc.getFilename());
        this.reprocessAvailable = this.hasPdfOnDisk;
        this.subjectCount = doc.getSubjects().size();
        this.subjects = new ArrayList<>();

        int unitsCount = 0;
        int topicsCount = 0;
        int creditsSum = 0;

        for (SyllabusSubject s : doc.getSubjects()) {
            SubjectDetailDto subDto = new SubjectDetailDto(s);
            this.subjects.add(subDto);
            unitsCount += subDto.getUnits().size();
            for (UnitDetailDto u : subDto.getUnits()) {
                topicsCount += u.getTopics().size();
            }
            if (s.getCredits() != null && !s.getCredits().isBlank()) {
                try {
                    String clean = s.getCredits().replaceAll("[^0-9]", "");
                    if (!clean.isEmpty()) creditsSum += Integer.parseInt(clean);
                } catch (Exception ignored) {}
            }
        }
        this.totalUnits = unitsCount;
        this.totalTopics = topicsCount;
        this.totalCredits = creditsSum;
    }

    public static class SubjectDetailDto {
        private String subjectName;
        private String departmentCode;
        private String semester;
        private String courseCode;
        private String credits;
        private String totalMarks;
        private String category;
        private boolean isOpenElective;
        private String matchedSubjectKey;
        private String matchedSubjectDisplayName;
        private List<UnitDetailDto> units;

        public SubjectDetailDto() {}

        public SubjectDetailDto(SyllabusSubject subj) {
            this.subjectName = subj.getName();
            this.departmentCode = subj.getDepartmentCode();
            this.semester = subj.getSemester();
            this.courseCode = subj.getCourseCode();
            this.credits = subj.getCredits();
            this.totalMarks = subj.getTotalMarks();
            this.category = subj.getCategory() != null ? subj.getCategory().name() : "THEORY";
            this.isOpenElective = subj.isOpenElective();
            if (subj.getMatchedSemesterSubject() != null) {
                this.matchedSubjectKey = subj.getMatchedSemesterSubject().name();
                this.matchedSubjectDisplayName = subj.getMatchedSemesterSubject().getFullName();
                if (this.courseCode == null || this.courseCode.isBlank()) {
                    this.courseCode = subj.getMatchedSemesterSubject().getCourseCode();
                }
            }
            this.units = new ArrayList<>();
            for (SyllabusUnit u : subj.getUnits()) {
                this.units.add(new UnitDetailDto(u));
            }
        }

        public String getSubjectName() { return subjectName; }
        public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

        public String getDepartmentCode() { return departmentCode; }
        public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }

        public String getSemester() { return semester; }
        public void setSemester(String semester) { this.semester = semester; }

        public String getCourseCode() { return courseCode; }
        public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

        public String getCredits() { return credits; }
        public void setCredits(String credits) { this.credits = credits; }

        public String getTotalMarks() { return totalMarks; }
        public void setTotalMarks(String totalMarks) { this.totalMarks = totalMarks; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public boolean isOpenElective() { return isOpenElective; }
        public void setOpenElective(boolean openElective) { isOpenElective = openElective; }

        public String getMatchedSubjectKey() { return matchedSubjectKey; }
        public void setMatchedSubjectKey(String matchedSubjectKey) { this.matchedSubjectKey = matchedSubjectKey; }

        public String getMatchedSubjectDisplayName() { return matchedSubjectDisplayName; }
        public void setMatchedSubjectDisplayName(String matchedSubjectDisplayName) { this.matchedSubjectDisplayName = matchedSubjectDisplayName; }

        public List<UnitDetailDto> getUnits() { return units; }
        public void setUnits(List<UnitDetailDto> units) { this.units = units; }
    }

    public static class TopicDetailDto {
        private String text;
        private Integer pageNumber;

        public TopicDetailDto() {}

        public TopicDetailDto(String text, Integer pageNumber) {
            this.text = text;
            this.pageNumber = pageNumber;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public Integer getPageNumber() { return pageNumber; }
        public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    }

    public static class UnitDetailDto {
        private int unitNumber;
        private String unitTitle;
        private Integer pageNumber;
        private List<String> topics;
        private List<TopicDetailDto> topicDetails;

        public UnitDetailDto() {}

        public UnitDetailDto(SyllabusUnit u) {
            this.unitNumber = u.getNumber();
            this.unitTitle = u.getTitle();
            this.pageNumber = u.getPageNumber();
            this.topics = new ArrayList<>();
            this.topicDetails = new ArrayList<>();
            for (SyllabusTopic t : u.getTopics()) {
                this.topics.add(t.getText());
                this.topicDetails.add(new TopicDetailDto(t.getText(), t.getPageNumber()));
            }
        }

        public int getUnitNumber() { return unitNumber; }
        public void setUnitNumber(int unitNumber) { this.unitNumber = unitNumber; }

        public String getUnitTitle() { return unitTitle; }
        public void setUnitTitle(String unitTitle) { this.unitTitle = unitTitle; }

        public Integer getPageNumber() { return pageNumber; }
        public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

        public List<String> getTopics() { return topics; }
        public void setTopics(List<String> topics) { this.topics = topics; }

        public List<TopicDetailDto> getTopicDetails() { return topicDetails; }
        public void setTopicDetails(List<TopicDetailDto> topicDetails) { this.topicDetails = topicDetails; }
    }

    public Long getId() { return documentId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }
    public String getAcademicScheme() { return academicScheme; }
    public void setAcademicScheme(String academicScheme) { this.academicScheme = academicScheme; }
    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public int getSubjectCount() { return subjectCount; }
    public void setSubjectCount(int subjectCount) { this.subjectCount = subjectCount; }
    public int getTotalUnits() { return totalUnits; }
    public void setTotalUnits(int totalUnits) { this.totalUnits = totalUnits; }
    public int getTotalTopics() { return totalTopics; }
    public void setTotalTopics(int totalTopics) { this.totalTopics = totalTopics; }
    public int getTotalCredits() { return totalCredits; }
    public void setTotalCredits(int totalCredits) { this.totalCredits = totalCredits; }
    public String getValidationReportJson() { return validationReportJson; }
    public void setValidationReportJson(String validationReportJson) { this.validationReportJson = validationReportJson; }
    public Object getValidationReport() { return validationReport; }
    public void setValidationReport(Object validationReport) { this.validationReport = validationReport; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getExtractionEngine() { return extractionEngine; }
    public void setExtractionEngine(String extractionEngine) { this.extractionEngine = extractionEngine; }
    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }
    public Long getParentDocumentId() { return parentDocumentId; }
    public void setParentDocumentId(Long parentDocumentId) { this.parentDocumentId = parentDocumentId; }
    public boolean isHasPdfOnDisk() { return hasPdfOnDisk; }
    public void setHasPdfOnDisk(boolean hasPdfOnDisk) { this.hasPdfOnDisk = hasPdfOnDisk; }
    public boolean isReprocessAvailable() { return reprocessAvailable; }
    public void setReprocessAvailable(boolean reprocessAvailable) { this.reprocessAvailable = reprocessAvailable; }
    public List<SubjectDetailDto> getSubjects() { return subjects; }
    public void setSubjects(List<SubjectDetailDto> subjects) { this.subjects = subjects; }

    public static boolean checkPdfExistsOnDisk(String path, String filename) {
        if (path != null && !path.isBlank()) {
            try {
                if (java.nio.file.Files.exists(java.nio.file.Path.of(path))) return true;
            } catch (Exception ignored) {}
        }
        if (filename == null || filename.isBlank()) return false;
        try {
            if (java.nio.file.Files.exists(java.nio.file.Path.of("uploads", "syllabus", filename))) return true;
            if (java.nio.file.Files.exists(java.nio.file.Path.of(filename))) return true;
            if (java.nio.file.Files.exists(java.nio.file.Path.of(System.getProperty("user.home"), "Downloads", filename))) return true;
        } catch (Exception ignored) {}
        return false;
    }
}
