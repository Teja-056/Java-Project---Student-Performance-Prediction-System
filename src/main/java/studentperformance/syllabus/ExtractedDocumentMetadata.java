package studentperformance.syllabus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedDocumentMetadata {
    private String institution = "";
    private String academicScheme = "";
    private String academicYear = "";
    private String branch = "";
    private String semester = "";

    public ExtractedDocumentMetadata() {}

    public ExtractedDocumentMetadata(String institution, String academicScheme, String academicYear, String branch, String semester) {
        setInstitution(institution);
        setAcademicScheme(academicScheme);
        setAcademicYear(academicYear);
        setBranch(branch);
        setSemester(semester);
    }

    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution == null ? "" : institution.trim(); }

    public String getAcademicScheme() { return academicScheme; }
    public void setAcademicScheme(String academicScheme) { this.academicScheme = academicScheme == null ? "" : academicScheme.trim(); }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear == null ? "" : academicYear.trim(); }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch == null ? "" : branch.trim(); }

    public String getSemester() { return semester; }
    public void setSemester(String semester) {
        if (semester == null) {
            this.semester = "";
            return;
        }
        String s = semester.replaceAll("(?i)\\s*semester\\s*", "").trim();
        this.semester = s.isEmpty() ? semester.trim() : s;
    }

    @Override
    public String toString() {
        return "ExtractedDocumentMetadata{" +
                "institution='" + institution + '\'' +
                ", academicScheme='" + academicScheme + '\'' +
                ", academicYear='" + academicYear + '\'' +
                ", branch='" + branch + '\'' +
                ", semester='" + semester + '\'' +
                '}';
    }
}
