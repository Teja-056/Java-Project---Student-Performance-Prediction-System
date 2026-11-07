package studentperformance;

import java.util.List;
import java.util.Locale;

/** Filters syllabus subjects before review/import so unrelated departments and open electives are not selected by default. */
public final class SyllabusSelector {
    private SyllabusSelector() { }

    public static List<SyllabusSubject> relevantSubjects(SyllabusDocument document, String department, String semester) {
        String dept = department.trim().toUpperCase(Locale.ROOT);
        String sem = normalizeSemester(semester);
        return document.getSubjects().stream()
                .filter(subject -> subject.getDepartmentCode().equalsIgnoreCase(dept))
                .filter(subject -> normalizeSemester(subject.getSemester()).equalsIgnoreCase(sem))
                .filter(subject -> !subject.isOpenElective())
                .toList();
    }

    /** Returns a visible reason when a subject will not be included in the current automatic selection. */
    public static String exclusionReason(SyllabusSubject subject, String department, String semester) {
        if (subject.isOpenElective()) return subject.getClassificationReason();
        if (subject.getDepartmentCode().equals("UNKNOWN")) return "Department could not be determined; held for manual review.";
        if (!subject.getDepartmentCode().equalsIgnoreCase(department.trim()))
            return "Detected department is " + subject.getDepartmentCode() + ", not requested department " + department + ".";
        if (subject.getSemester().equals("UNKNOWN")) return "Semester could not be determined; held for manual review.";
        if (!normalizeSemester(subject.getSemester()).equalsIgnoreCase(normalizeSemester(semester)))
            return "Detected semester is " + subject.getSemester() + ", not requested semester " + semester + ".";
        return null;
    }

    public static String normalizeSemester(String sem) {
        if (sem == null) return "UNKNOWN";
        String s = sem.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "1", "I" -> "I";
            case "2", "II" -> "II";
            case "3", "III" -> "III";
            case "4", "IV" -> "IV";
            case "5", "V" -> "V";
            case "6", "VI" -> "VI";
            case "7", "VII" -> "VII";
            case "8", "VIII" -> "VIII";
            default -> s;
        };
    }
}

