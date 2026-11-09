package studentperformance;

import java.util.List;
import java.util.Map;

/** Contains the project's score calculations and transparent rule-based analysis. */
public class PerformanceAnalyzer {
    private final double supportThresholdPercent = readThreshold();

    private static double readThreshold() {
        try {
            double value = Double.parseDouble(System.getenv().getOrDefault("SUPPORT_THRESHOLD_PERCENT", "50"));
            return Double.isFinite(value) && value >= 0 && value <= 100 ? value : 50;
        } catch (NumberFormatException ex) { return 50; }
    }

    public double getSupportThresholdPercent() { return supportThresholdPercent; }

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
        return hasBothInternals(student) && currentInternalPercentage(student) < supportThresholdPercent;
    }

    public String prediction(Student student) {
        if (!hasBothInternals(student)) return "Incomplete assessment data";
        double internal = currentInternalPercentage(student);
        double recentSgpa = student.getLastSemesterSgpa();
        if (internal < 50 || recentSgpa < 5)
            return "May Need Additional Support";
        if (internal < 75 || recentSgpa < 7.5)
            return "Monitor Progress";
        return "Good Progress";
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
