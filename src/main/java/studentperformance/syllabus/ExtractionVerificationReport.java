package studentperformance.syllabus;

import java.util.ArrayList;
import java.util.List;

public class ExtractionVerificationReport {
    private String filename = "";
    private int totalPages;
    private int totalSubjects;
    private int totalUnits;
    private int totalTopics;
    private List<SubjectVerificationEntry> subjectEntries = new ArrayList<>();
    private List<String> globalWarnings = new ArrayList<>();
    private List<String> discrepancies = new ArrayList<>();
    private long processingDurationMs;
    private String modelName = "";

    public record SubjectVerificationEntry(
            String subjectName,
            String courseCode,
            String credits,
            String category,
            String pageRange,
            int unitCount,
            int topicCount,
            List<Integer> unitNumbers,
            List<String> missingUnits,
            List<String> duplicateTopics,
            List<String> uncertainFields,
            List<String> warnings
    ) {}

    public ExtractionVerificationReport() {}

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public int getTotalSubjects() { return totalSubjects; }
    public void setTotalSubjects(int totalSubjects) { this.totalSubjects = totalSubjects; }

    public int getTotalUnits() { return totalUnits; }
    public void setTotalUnits(int totalUnits) { this.totalUnits = totalUnits; }

    public int getTotalTopics() { return totalTopics; }
    public void setTotalTopics(int totalTopics) { this.totalTopics = totalTopics; }

    public List<SubjectVerificationEntry> getSubjectEntries() { return subjectEntries; }
    public void setSubjectEntries(List<SubjectVerificationEntry> subjectEntries) { this.subjectEntries = subjectEntries == null ? new ArrayList<>() : subjectEntries; }

    public List<String> getGlobalWarnings() { return globalWarnings; }
    public void setGlobalWarnings(List<String> globalWarnings) { this.globalWarnings = globalWarnings == null ? new ArrayList<>() : globalWarnings; }

    public List<String> getDiscrepancies() { return discrepancies; }
    public void setDiscrepancies(List<String> discrepancies) { this.discrepancies = discrepancies == null ? new ArrayList<>() : discrepancies; }

    public long getProcessingDurationMs() { return processingDurationMs; }
    public void setProcessingDurationMs(long processingDurationMs) { this.processingDurationMs = processingDurationMs; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String formatConsoleSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================================\n");
        sb.append("         GEMINI SYLLABUS EXTRACTION & VERIFICATION REPORT        \n");
        sb.append("=================================================================\n");
        sb.append(String.format("File: %s | Pages Processed: %d | Time: %d ms | Model: %s\n",
                filename, totalPages, processingDurationMs, modelName));
        sb.append(String.format("Extracted Subjects: %d | Total Units: %d | Total Topics: %d\n",
                totalSubjects, totalUnits, totalTopics));
        sb.append("-----------------------------------------------------------------\n");

        for (int i = 0; i < subjectEntries.size(); i++) {
            SubjectVerificationEntry e = subjectEntries.get(i);
            sb.append(String.format("[%d] %s\n", i + 1, e.subjectName()));
            sb.append(String.format("    Code: %s | Credits: %s | Category: %s | Pages: %s\n",
                    e.courseCode(), e.credits(), e.category(), e.pageRange()));
            sb.append(String.format("    Units: %d %s | Topics: %d\n",
                    e.unitCount(), e.unitNumbers(), e.topicCount()));
            if (!e.missingUnits().isEmpty()) {
                sb.append(String.format("    Missing Units: %s\n", e.missingUnits()));
            }
            if (!e.duplicateTopics().isEmpty()) {
                sb.append(String.format("    Duplicate Topics: %s\n", e.duplicateTopics()));
            }
            if (!e.uncertainFields().isEmpty()) {
                sb.append(String.format("    Uncertain Fields: %s\n", e.uncertainFields()));
            }
            if (!e.warnings().isEmpty()) {
                sb.append(String.format("    Warnings: %s\n", e.warnings()));
            }
            sb.append("\n");
        }

        if (!globalWarnings.isEmpty()) {
            sb.append("Global Warnings:\n");
            for (String w : globalWarnings) sb.append("  - ").append(w).append("\n");
        }
        if (!discrepancies.isEmpty()) {
            sb.append("Discrepancies with Reference Catalog:\n");
            for (String d : discrepancies) sb.append("  * ").append(d).append("\n");
        }
        sb.append("=================================================================\n");
        return sb.toString();
    }
}
