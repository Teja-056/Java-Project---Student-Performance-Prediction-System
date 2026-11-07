package studentperformance.dto;

import studentperformance.SyllabusDocument;
import studentperformance.SyllabusSubject;
import studentperformance.SyllabusTopic;
import studentperformance.SyllabusUnit;

import java.util.ArrayList;
import java.util.List;

public class SyllabusDocumentDto {
    private Long documentId;
    private String filename;
    private String status;
    private int subjectCount;
    private List<SubjectDetailDto> subjects;

    public SyllabusDocumentDto() {}

    public SyllabusDocumentDto(Long id, SyllabusDocument doc, String status) {
        this.documentId = id;
        this.filename = doc.getFilename();
        this.status = status;
        this.subjectCount = doc.getSubjects().size();
        this.subjects = new ArrayList<>();
        for (SyllabusSubject s : doc.getSubjects()) {
            this.subjects.add(new SubjectDetailDto(s));
        }
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
        private List<UnitDetailDto> units;

        public SubjectDetailDto(SyllabusSubject subj) {
            this.subjectName = subj.getName();
            this.departmentCode = subj.getDepartmentCode();
            this.semester = subj.getSemester();
            this.courseCode = subj.getCourseCode();
            this.credits = subj.getCredits();
            this.totalMarks = subj.getTotalMarks();
            this.category = subj.getCategory() != null ? subj.getCategory().name() : "THEORY";
            this.isOpenElective = subj.isOpenElective();
            this.units = new ArrayList<>();
            for (SyllabusUnit u : subj.getUnits()) {
                this.units.add(new UnitDetailDto(u));
            }
        }

        public String getSubjectName() { return subjectName; }
        public String getDepartmentCode() { return departmentCode; }
        public String getSemester() { return semester; }
        public String getCourseCode() { return courseCode; }
        public String getCredits() { return credits; }
        public String getTotalMarks() { return totalMarks; }
        public String getCategory() { return category; }
        public boolean isOpenElective() { return isOpenElective; }
        public List<UnitDetailDto> getUnits() { return units; }
    }

    public static class UnitDetailDto {
        private int unitNumber;
        private String unitTitle;
        private List<String> topics;

        public UnitDetailDto(SyllabusUnit u) {
            this.unitNumber = u.getNumber();
            this.unitTitle = u.getTitle();
            this.topics = new ArrayList<>();
            for (SyllabusTopic t : u.getTopics()) {
                this.topics.add(t.getText());
            }
        }

        public int getUnitNumber() { return unitNumber; }
        public String getUnitTitle() { return unitTitle; }
        public List<String> getTopics() { return topics; }
    }

    public Long getId() { return documentId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getSubjectCount() { return subjectCount; }
    public void setSubjectCount(int subjectCount) { this.subjectCount = subjectCount; }
    public List<SubjectDetailDto> getSubjects() { return subjects; }
    public void setSubjects(List<SubjectDetailDto> subjects) { this.subjects = subjects; }
}
