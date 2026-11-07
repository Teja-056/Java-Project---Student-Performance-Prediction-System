package studentperformance;

/** Exam and overall maximum marks for each subject in one internal assessment. */
public enum Subject {
    JAVA("Java", 30),
    DS("DS", 30),
    CA("CA", 30),
    TTPS("TTPS", 30),
    CS("CS", 20),
    CT("CT", 20),
    HVPE("HVPE", 20);

    private final String displayName;
    private final int maximumMarks;

    Subject(String displayName, int maximumMarks) {
        this.displayName = displayName;
        this.maximumMarks = maximumMarks;
    }

    public String getDisplayName() { return displayName; }
    public int getMaximumExamMarks() { return maximumMarks; }
    public int getMaximumTotalMarks() { return maximumMarks + 10; }
    public static int maximumTotal() {
        int total = 0;
        for (Subject subject : values()) total += subject.getMaximumTotalMarks();
        return total;
    }
}
