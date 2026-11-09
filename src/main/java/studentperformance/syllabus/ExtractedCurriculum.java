package studentperformance.syllabus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedCurriculum {
    private ExtractedDocumentMetadata document = new ExtractedDocumentMetadata();
    private List<ExtractedSubject> subjects = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private String rawJsonResponse = "";
    private long extractionTimeMs;
    private int processedPages;

    public ExtractedCurriculum() {}

    public ExtractedDocumentMetadata getDocument() { return document; }
    public void setDocument(ExtractedDocumentMetadata document) { this.document = document == null ? new ExtractedDocumentMetadata() : document; }

    public List<ExtractedSubject> getSubjects() { return subjects; }
    public void setSubjects(List<ExtractedSubject> subjects) { this.subjects = subjects == null ? new ArrayList<>() : subjects; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings == null ? new ArrayList<>() : warnings; }

    public String getRawJsonResponse() { return rawJsonResponse; }
    public void setRawJsonResponse(String rawJsonResponse) { this.rawJsonResponse = rawJsonResponse == null ? "" : rawJsonResponse; }

    public long getExtractionTimeMs() { return extractionTimeMs; }
    public void setExtractionTimeMs(long extractionTimeMs) { this.extractionTimeMs = extractionTimeMs; }

    public int getProcessedPages() { return processedPages; }
    public void setProcessedPages(int processedPages) { this.processedPages = processedPages; }

    public int getTotalUnitsCount() {
        return subjects.stream().mapToInt(s -> s.getUnits().size()).sum();
    }

    public int getTotalTopicsCount() {
        return subjects.stream().flatMap(s -> s.getUnits().stream()).mapToInt(u -> u.getTopics().size()).sum();
    }

    @Override
    public String toString() {
        return "ExtractedCurriculum{" +
                "document=" + document +
                ", subjects=" + subjects.size() +
                ", totalUnits=" + getTotalUnitsCount() +
                ", totalTopics=" + getTotalTopicsCount() +
                ", pages=" + processedPages +
                ", timeMs=" + extractionTimeMs +
                '}';
    }
}
