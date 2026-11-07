package studentperformance;

import java.util.ArrayList;
import java.util.List;

public class SyllabusDocument {
    private String filename;
    private String rawExtractedText = "";
    private final List<SyllabusSubject> subjects = new ArrayList<>();
    public SyllabusDocument(String filename) { this.filename = filename; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getRawExtractedText() { return rawExtractedText; }
    public void setRawExtractedText(String text) { rawExtractedText = text == null ? "" : text; }
    public List<SyllabusSubject> getSubjects() { return subjects; }
}
