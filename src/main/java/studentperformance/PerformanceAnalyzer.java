package studentperformance;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contains the project's score calculations and transparent rule-based analysis. */
public class PerformanceAnalyzer {

    public static final double DEFAULT_ATTENDANCE_WARNING_THRESHOLD = 75.0;
    public static final double CRITICAL_CIE_THRESHOLD = 40.0;
    public static final double GOOD_PROGRESS_THRESHOLD = 75.0;
    public static final double MONITOR_PROGRESS_THRESHOLD = 60.0;

    private final double supportThresholdPercent = readThreshold();
    private final double attendanceWarningThreshold = readAttendanceThreshold();

    private static double readThreshold() {
        try {
            double value = Double.parseDouble(System.getenv().getOrDefault("SUPPORT_THRESHOLD_PERCENT", "50"));
            return Double.isFinite(value) && value >= 0 && value <= 100 ? value : 50;
        } catch (NumberFormatException ex) { return 50; }
    }

    private static double readAttendanceThreshold() {
        try {
            double value = Double.parseDouble(System.getenv().getOrDefault("ATTENDANCE_WARNING_THRESHOLD", "75"));
            return Double.isFinite(value) && value >= 0 && value <= 100 ? value : DEFAULT_ATTENDANCE_WARNING_THRESHOLD;
        } catch (NumberFormatException ex) { return DEFAULT_ATTENDANCE_WARNING_THRESHOLD; }
    }

    public double getSupportThresholdPercent() { return supportThresholdPercent; }
    public double getAttendanceWarningThreshold() { return attendanceWarningThreshold; }

    public double totalMarks(Student student, int internalNumber) {
        double total = 0;
        for (SubjectAssessment assessment : student.getMarks(internalNumber).values()) total += assessment.totalMarks();
        return total;
    }

    public double percentage(Student student, int internalNumber) {
        double possible = 0;
        for (var entry : student.getMarks(internalNumber).entrySet()) {
            Subject subject = entry.getKey(); SubjectAssessment assessment = entry.getValue();
            if (assessment.getExamMarks() != null) possible += subject.getMaximumExamMarks();
            if (assessment.hasAssignmentMarks()) possible += 5;
            if (assessment.hasQuizMarks()) possible += 5;
        }
        return possible == 0 ? 0 : totalMarks(student, internalNumber) * 100.0 / possible;
    }

    public boolean isInternalComplete(Student student, int internalNumber) {
        var marks = student.getMarks(internalNumber);
        if (marks.size() != Subject.values().length) return false;
        for (Subject subject : Subject.values()) {
            SubjectAssessment assessment = marks.get(subject);
            if (assessment == null || !assessment.isComplete()) return false;
        }
        return true;
    }

    public boolean hasBothInternals(Student student) {
        return isInternalComplete(student, 1) && isInternalComplete(student, 2);
    }

    public double currentInternalPercentage(Student student) {
        double possible = possibleMarks(student, 1) + possibleMarks(student, 2);
        return possible == 0 ? 0 : (totalMarks(student, 1) + totalMarks(student, 2)) * 100.0 / possible;
    }

    public boolean hasAnyAssessment(Student student, int internalNumber) {
        return possibleMarks(student, internalNumber) > 0;
    }

    public double possibleMarks(Student student, int internalNumber) {
        double possible = 0;
        for (var entry : student.getMarks(internalNumber).entrySet()) {
            SubjectAssessment assessment = entry.getValue();
            if (assessment.getExamMarks() != null) possible += entry.getKey().getMaximumExamMarks();
            if (assessment.hasAssignmentMarks()) possible += 5;
            if (assessment.hasQuizMarks()) possible += 5;
        }
        return possible;
    }

    public double subjectPercentage(Student student, int internalNumber, Subject subject) {
        SubjectAssessment assessment = student.getMarks(internalNumber).get(subject);
        if (assessment == null) return Double.NaN;
        double possible = (assessment.getExamMarks() == null ? 0 : subject.getMaximumExamMarks())
                + (assessment.hasAssignmentMarks() ? 5 : 0) + (assessment.hasQuizMarks() ? 5 : 0);
        return possible == 0 ? Double.NaN : assessment.totalMarks() * 100.0 / possible;
    }

    public double subjectChange(Student student, Subject subject) {
        double first = subjectPercentage(student, 1, subject), second = subjectPercentage(student, 2, subject);
        return Double.isNaN(first) || Double.isNaN(second) ? Double.NaN : second - first;
    }

    /**
     * Calculates the single subject's CIE percentage as the unweighted mean of Internal 1 and Internal 2 percentages.
     * Subject CIE = (Internal 1 % + Internal 2 %) / 2
     */
    public double subjectCie(Student student, Subject subject) {
        if (student == null || subject == null) return Double.NaN;
        double p1 = subjectPercentage(student, 1, subject);
        double p2 = subjectPercentage(student, 2, subject);
        if (Double.isNaN(p1) || Double.isNaN(p2)) return Double.NaN;
        return (p1 + p2) / 2.0;
    }

    /**
     * Calculates the credit-weighted overall CIE percentage across all curriculum subjects:
     * Overall CIE = SUM(Subject CIE * Subject Credits) / SUM(Subject Credits)
     */
    public double creditWeightedCiePercentage(Student student) {
        return creditWeightedCiePercentage(student, null);
    }

    public double creditWeightedCiePercentage(Student student, Map<Subject, Integer> customCredits) {
        if (student == null || !hasBothInternals(student)) return Double.NaN;
        double weightedSum = 0.0;
        int totalCredits = 0;
        for (Subject s : Subject.values()) {
            double sCie = subjectCie(student, s);
            if (Double.isNaN(sCie)) return Double.NaN;
            int credits = (customCredits != null && customCredits.containsKey(s)) ? customCredits.get(s) : s.getCredits();
            weightedSum += sCie * credits;
            totalCredits += credits;
        }
        return totalCredits == 0 ? 0.0 : weightedSum / totalCredits;
    }

    /** Alias for credit-weighted overall CIE percentage. */
    public double overallCiePercentage(Student student) {
        return creditWeightedCiePercentage(student, null);
    }

    public double overallCiePercentage(Student student, Map<Subject, Integer> customCredits) {
        return creditWeightedCiePercentage(student, customCredits);
    }

    /**
     * Calculates the Final Academic Score based on the 3-parameter weighting:
     * (0.70 * Current CIE %) + (0.20 * Previous SGPA * 10) + (0.10 * Attendance %)
     * Result is bounded between 0.0 and 100.0.
     */
    public double finalAcademicScore(Student student) {
        return finalAcademicScore(student, null);
    }

    public double finalAcademicScore(Student student, Map<Subject, Integer> customCredits) {
        if (student == null || !hasBothInternals(student)) return Double.NaN;
        if (student.getLastSemesterSgpa() < 0.0) return Double.NaN;
        if (student.getAttendancePercent() == null) return Double.NaN;

        double cie = creditWeightedCiePercentage(student, customCredits);
        if (Double.isNaN(cie)) return Double.NaN;

        double sgpa = student.getLastSemesterSgpa();
        double att = student.getAttendancePercent();

        return calculateAcademicScore(cie, sgpa, att);
    }

    public static double calculateAcademicScore(double ciePercent, double previousSgpa, double attendancePercent) {
        double score = (0.70 * ciePercent) + (0.20 * (previousSgpa * 10.0)) + (0.10 * attendancePercent);
        return Math.max(0.0, Math.min(100.0, score));
    }

    public static String classifyScore(double finalScore) {
        if (Double.isNaN(finalScore)) return "Incomplete assessment data";
        if (finalScore >= GOOD_PROGRESS_THRESHOLD) return "Good Progress";
        if (finalScore >= MONITOR_PROGRESS_THRESHOLD) return "Monitor Progress";
        return "May Need Additional Support";
    }

    public boolean hasCriticalAcademicWarning(Student student) {
        if (student == null || !hasBothInternals(student)) return false;
        double cie = overallCiePercentage(student);
        return !Double.isNaN(cie) && cie < CRITICAL_CIE_THRESHOLD;
    }

    public boolean hasAttendanceWarning(Student student) {
        return hasAttendanceWarning(student, attendanceWarningThreshold);
    }

    public boolean hasAttendanceWarning(Student student, double threshold) {
        if (student == null || student.getAttendancePercent() == null) return false;
        return student.getAttendancePercent() < threshold;
    }

    public boolean hasIncompleteAssessmentWarning(Student student) {
        if (student == null) return true;
        return !hasBothInternals(student) || student.getAttendancePercent() == null || student.getLastSemesterSgpa() < 0;
    }

    public Subject strongestSubject(Student student) { return strongestOrWeakest(student, true); }
    public Subject weakestSubject(Student student) { return strongestOrWeakest(student, false); }

    private Subject strongestOrWeakest(Student student, boolean strongest) {
        Subject chosen = null; double chosenScore = strongest ? -1 : Double.POSITIVE_INFINITY;
        for (Subject subject : Subject.values()) {
            double first = subjectPercentage(student, 1, subject), second = subjectPercentage(student, 2, subject);
            if (Double.isNaN(first) && Double.isNaN(second)) continue;
            double score = Double.isNaN(first) ? second : Double.isNaN(second) ? first : (first + second) / 2;
            if ((strongest && score > chosenScore) || (!strongest && score < chosenScore)) { chosen = subject; chosenScore = score; }
        }
        return chosen;
    }

    public boolean requiresSupport(Student student) {
        if (student == null || !hasBothInternals(student) || student.getLastSemesterSgpa() < 0 || student.getAttendancePercent() == null) return false;
        double score = finalAcademicScore(student);
        return !Double.isNaN(score) && score < MONITOR_PROGRESS_THRESHOLD;
    }

    /**
     * Primary academic performance prediction / classification:
     * - Incomplete assessment data if missing assessments, SGPA or attendance
     * - Good Progress if Final Academic Score >= 75.0
     * - Monitor Progress if Final Academic Score >= 60.0 and < 75.0
     * - May Need Additional Support if Final Academic Score < 60.0
     */
    public String prediction(Student student) {
        if (student == null || !hasBothInternals(student) || student.getLastSemesterSgpa() < 0 || student.getAttendancePercent() == null) {
            return "Incomplete assessment data";
        }
        double score = finalAcademicScore(student);
        return classifyScore(score);
    }

    public record AcademicEvaluation(
            Double overallCiePercentage,
            Double previousSemesterSgpa,
            Double attendancePercentage,
            Double finalAcademicScore,
            String primaryClassification,
            boolean criticalAcademicWarning,
            boolean attendanceWarning,
            boolean incompleteAssessmentWarning,
            boolean isComplete,
            Map<Subject, Double> subjectCiePercentages,
            Map<Subject, Integer> subjectCredits
    ) {}

    public AcademicEvaluation evaluateStudent(Student student) {
        return evaluateStudent(student, null);
    }

    public AcademicEvaluation evaluateStudent(Student student, Map<Subject, Integer> customCredits) {
        boolean complete = student != null && hasBothInternals(student)
                && student.getLastSemesterSgpa() >= 0 && student.getAttendancePercent() != null;

        Map<Subject, Double> subjectCies = new EnumMap<>(Subject.class);
        Map<Subject, Integer> subjectCreds = new EnumMap<>(Subject.class);

        for (Subject s : Subject.values()) {
            subjectCies.put(s, subjectCie(student, s));
            int cred = (customCredits != null && customCredits.containsKey(s)) ? customCredits.get(s) : s.getCredits();
            subjectCreds.put(s, cred);
        }

        if (!complete) {
            return new AcademicEvaluation(
                    null,
                    student != null ? student.getLastSemesterSgpa() : null,
                    student != null ? student.getAttendancePercent() : null,
                    null,
                    "Incomplete assessment data",
                    false,
                    hasAttendanceWarning(student),
                    true,
                    false,
                    subjectCies,
                    subjectCreds
            );
        }

        double overallCie = overallCiePercentage(student, customCredits);
        double finalScore = finalAcademicScore(student, customCredits);
        String classification = classifyScore(finalScore);

        return new AcademicEvaluation(
                round2(overallCie),
                student.getLastSemesterSgpa(),
                student.getAttendancePercent(),
                round2(finalScore),
                classification,
                overallCie < CRITICAL_CIE_THRESHOLD,
                hasAttendanceWarning(student),
                false,
                true,
                subjectCies,
                subjectCreds
        );
    }

    public static double round2(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return val;
        return Math.round(val * 100.0) / 100.0;
    }

    public String unitAdvice(Student student) {
        return unitAdvice(student, Map.of(1, List.of(1, 2), 2, List.of(3, 4, 5)));
    }

    public String unitAdvice(Student student, Map<Integer, List<Integer>> internalUnitMapping) {
        StringBuilder advice = new StringBuilder();
        if (!isInternalComplete(student, 1)) advice.append("Internal 1 is incomplete; unit risk cannot be assessed. ");
        else if (percentage(student, 1) < supportThresholdPercent) advice.append("Internal 1 is below ").append(supportThresholdPercent)
                .append("%; review Units ").append(formatUnits(internalUnitMapping.get(1))).append(". ");
        if (!isInternalComplete(student, 2)) advice.append("Internal 2 is incomplete; unit risk cannot be assessed. ");
        else if (percentage(student, 2) < supportThresholdPercent) advice.append("Internal 2 is below ").append(supportThresholdPercent)
                .append("%; review Units ").append(formatUnits(internalUnitMapping.get(2))).append(". ");
        if (advice.isEmpty()) return "No internal is below the current " + supportThresholdPercent + "% review threshold.";
        return advice.toString().trim();
    }

    private String formatUnits(List<Integer> units) {
        if (units == null || units.isEmpty()) return "not configured";
        if (units.size() == 1) return String.valueOf(units.get(0));
        return String.join(", ", units.subList(0, units.size() - 1).stream().map(String::valueOf).toList())
                + " and " + units.get(units.size() - 1);
    }
}
