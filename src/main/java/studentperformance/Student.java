package studentperformance;

import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

/** Stores one student's identity, academic history, and two internal mark sets. */
public class Student {
    private static final Pattern ROLL_PATTERN = Pattern.compile("1602-25-733-\\d{3}");
    private final String name;
    private final String rollNumber;
    private final double cgpa;
    private final double lastSemesterSgpa;
    private final EnumMap<Subject, SubjectAssessment> internal1;
    private final EnumMap<Subject, SubjectAssessment> internal2;
    private final List<LegacyMark> legacyMarks;

    public Student(String name, String rollNumber, double cgpa, double lastSemesterSgpa,
                   Map<Subject, SubjectAssessment> internal1, Map<Subject, SubjectAssessment> internal2) {
        this(name, rollNumber, cgpa, lastSemesterSgpa, internal1, internal2, List.of());
    }

    public Student(String name, String rollNumber, double cgpa, double lastSemesterSgpa,
                   Map<Subject, SubjectAssessment> internal1, Map<Subject, SubjectAssessment> internal2,
                   List<LegacyMark> legacyMarks) {
        this(name, rollNumber, cgpa, lastSemesterSgpa, internal1, internal2, legacyMarks, false);
    }

    private Student(String name, String rollNumber, double cgpa, double lastSemesterSgpa,
                    Map<Subject, SubjectAssessment> internal1, Map<Subject, SubjectAssessment> internal2,
                    List<LegacyMark> legacyMarks, boolean readingLegacyDatabaseRecord) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name cannot be blank.");
        if (rollNumber == null || rollNumber.isBlank() || (!readingLegacyDatabaseRecord && !ROLL_PATTERN.matcher(rollNumber).matches()))
            throw new IllegalArgumentException("Roll number must look like 1602-25-733-XXX (three digits at the end).");
        checkGpa("CGPA", cgpa);
        checkGpa("Last semester SGPA", lastSemesterSgpa);
        this.name = name.trim();
        this.rollNumber = rollNumber;
        this.cgpa = cgpa;
        this.lastSemesterSgpa = lastSemesterSgpa;
        this.internal1 = copyAndValidate(internal1, "Internal 1");
        this.internal2 = copyAndValidate(internal2, "Internal 2");
        this.legacyMarks = List.copyOf(legacyMarks);
    }

    /** Database reader may load historical roll formats; new user input remains strictly validated. */
    static Student fromDatabase(String name, String rollNumber, double cgpa, double lastSemesterSgpa,
                                Map<Subject, SubjectAssessment> internal1, Map<Subject, SubjectAssessment> internal2,
                                List<LegacyMark> legacyMarks) {
        return new Student(name, rollNumber, cgpa, lastSemesterSgpa, internal1, internal2, legacyMarks, true);
    }

    private static void checkGpa(String label, double value) {
        if (!Double.isFinite(value) || value < 0 || value > 10)
            throw new IllegalArgumentException(label + " must be between 0 and 10.");
    }

    private static EnumMap<Subject, SubjectAssessment> copyAndValidate(Map<Subject, SubjectAssessment> marks, String label) {
        if (marks == null) throw new IllegalArgumentException(label + " marks cannot be null.");
        EnumMap<Subject, SubjectAssessment> copy = new EnumMap<>(Subject.class);
        for (Subject subject : Subject.values()) {
            SubjectAssessment assessment = marks.get(subject);
            if (assessment == null) continue;
            if (assessment.getExamMarks() != null && assessment.getExamMarks() > subject.getMaximumExamMarks())
                throw new IllegalArgumentException(label + " exam marks for " + subject.getDisplayName()
                        + " must be between 0 and " + subject.getMaximumExamMarks() + ".");
            copy.put(subject, assessment);
        }
        return copy;
    }

    public String getName() { return name; }
    public String getRollNumber() { return rollNumber; }
    public double getCgpa() { return cgpa; }
    public double getLastSemesterSgpa() { return lastSemesterSgpa; }
    public List<LegacyMark> getLegacyMarks() { return legacyMarks; }
    public Student withLegacyMarks(List<LegacyMark> marks) {
        return fromDatabase(name, rollNumber, cgpa, lastSemesterSgpa, internal1, internal2, marks);
    }
    public Map<Subject, SubjectAssessment> getMarks(int internalNumber) {
        if (internalNumber == 1) return Map.copyOf(internal1);
        if (internalNumber == 2) return Map.copyOf(internal2);
        throw new IllegalArgumentException("Internal number must be 1 or 2.");
    }
}
