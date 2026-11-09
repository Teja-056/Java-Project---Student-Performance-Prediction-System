package studentperformance.prediction;

/**
 * Project-standard percentage-based grading scheme.
 * 90-100% = A+ (10 GP)
 * 80-<90% = A (9 GP)
 * 70-<80% = B+ (8 GP)
 * 60-<70% = B (7 GP)
 * 50-<60% = C (6 GP)
 * 40-<50% = P (5 GP)
 * Below 40% = F (0 GP)
 */
public class GradingScheme {

    public record GradeResult(String grade, int gradePoints) {}

    public static GradeResult evaluate(double percentage) {
        if (percentage >= 90.0) return new GradeResult("A+", 10);
        if (percentage >= 80.0) return new GradeResult("A", 9);
        if (percentage >= 70.0) return new GradeResult("B+", 8);
        if (percentage >= 60.0) return new GradeResult("B", 7);
        if (percentage >= 50.0) return new GradeResult("C", 6);
        if (percentage >= 40.0) return new GradeResult("P", 5);
        return new GradeResult("F", 0);
    }
}
