package studentperformance;

import java.util.Locale;

/** Exam, CIE, SEE, course code, and credit specification for Semester III subjects. */
public enum Subject {
    JAVA("Java", 30, 3, 40, 60, "OOP through Java", "UI25PC320CS"),
    DS("DS", 30, 4, 40, 60, "Data Structures", "UI25PC310CS"),
    CA("CA", 30, 3, 40, 60, "Computer Architecture", "UI25PC330CS"),
    TTPS("TTPS", 30, 4, 40, 60, "Transform Techniques, Probability & Statistics", "U25BS340MA"),
    OE("OE", 30, 2, 40, 60, "Open Elective-I", "U25OE3XXXX"),
    CS("CS", 20, 1, 30, 40, "Communication Skills-I", "U25HS320EH"),
    CT("CT", 20, 1, 30, 40, "Critical Thinking", "U25HS310EH"),
    HVPE("HVPE", 20, 1, 30, 40, "HVPE-II", "U25HS030EH");

    private final String displayName;
    private final int maximumMarks;
    private final int credits;
    private final int cieMaxMarks;
    private final int seeMaxMarks;
    private final String fullName;
    private final String courseCode;

    Subject(String displayName, int maximumMarks, int credits, int cieMaxMarks, int seeMaxMarks, String fullName, String courseCode) {
        this.displayName = displayName;
        this.maximumMarks = maximumMarks;
        this.credits = credits;
        this.cieMaxMarks = cieMaxMarks;
        this.seeMaxMarks = seeMaxMarks;
        this.fullName = fullName;
        this.courseCode = courseCode;
    }

    public String getDisplayName() { return displayName; }
    public int getMaximumExamMarks() { return maximumMarks; }
    public int getMaximumTotalMarks() { return maximumMarks + 10; }
    public int getCredits() { return credits; }
    public int getCieMaxMarks() { return cieMaxMarks; }
    public int getSeeMaxMarks() { return seeMaxMarks; }
    public int getTotalMaxMarks() { return cieMaxMarks + seeMaxMarks; }
    public String getFullName() { return fullName; }
    public String getCourseCode() { return courseCode; }

    public static Subject matchByText(String text) {
        if (text == null || text.isBlank()) return null;
        String upper = text.trim().toUpperCase(Locale.ROOT);

        try {
            return Subject.valueOf(upper);
        } catch (IllegalArgumentException ignored) {
        }

        if (upper.contains("DATA STRUCTURE") || upper.contains("310CS") || upper.equals("DS")) {
            return DS;
        }
        if (upper.contains("JAVA") || upper.contains("OOP") || upper.contains("301CS") || upper.contains("320CS")) {
            return JAVA;
        }
        if (upper.contains("ARCHITECTURE") || upper.contains("302CS") || upper.contains("330CS") || upper.equals("CA")) {
            return CA;
        }
        if (upper.contains("TRANSFORM") || upper.contains("PROBABILITY") || upper.contains("STATISTICS") || upper.contains("301MA") || upper.contains("340MA") || upper.equals("TTPS")) {
            return TTPS;
        }
        if (upper.contains("OPEN ELECTIVE") || upper.equals("OE") || upper.contains("OE3")) {
            return OE;
        }
        if (upper.contains("COMMUNICATION") || upper.contains("301EH") || upper.contains("320EH") || upper.equals("COMMUNICATION SKILLS") || upper.equals("COMMUNICATION SKILLS-I") || upper.equals("CS")) {
            return CS;
        }
        if (upper.contains("CRITICAL THINKING") || upper.contains("302EH") || upper.contains("310EH") || upper.equals("CT")) {
            return CT;
        }
        if (upper.contains("HVPE") || upper.contains("HUMAN VALUE") || upper.contains("301HS") || upper.contains("030EH")) {
            return HVPE;
        }
        return null;
    }

    public static int totalCredits() {
        int total = 0;
        for (Subject s : values()) total += s.credits;
        return total;
    }

    public static int maximumTotal() {
        int total = 0;
        for (Subject subject : values()) total += subject.getMaximumTotalMarks();
        return total;
    }
}
