package studentperformance;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** Exam score plus the three assignment and three quiz scores for one subject/internal. */
public class SubjectAssessment {
    private final Integer examMarks;
    private final List<Integer> assignmentMarks;
    private final List<Integer> quizMarks;

    private final boolean importedLegacy;

    public SubjectAssessment(Integer examMarks, List<Integer> assignmentMarks, List<Integer> quizMarks) {
        this(examMarks, assignmentMarks, quizMarks, false);
    }

    public SubjectAssessment(Integer examMarks, List<Integer> assignmentMarks, List<Integer> quizMarks, boolean importedLegacy) {
        if (assignmentMarks == null || assignmentMarks.size() != 3 || quizMarks == null || quizMarks.size() != 3)
            throw new IllegalArgumentException("Enter exactly three assignment and three quiz marks.");
        if (examMarks != null && examMarks < 0) throw new IllegalArgumentException("Exam marks cannot be negative.");
        this.examMarks = examMarks;
        this.assignmentMarks = Collections.unmodifiableList(new ArrayList<>(assignmentMarks));
        this.quizMarks = Collections.unmodifiableList(new ArrayList<>(quizMarks));
        this.importedLegacy = importedLegacy;
        for (Integer mark : this.assignmentMarks) if (mark != null) checkFive(mark, "Assignment");
        for (Integer mark : this.quizMarks) if (mark != null) checkFive(mark, "Quiz");
    }

    private static void checkFive(int mark, String type) {
        if (mark < 0 || mark > 5) throw new IllegalArgumentException(type + " marks must be from 0 to 5.");
    }

    public Integer getExamMarks() { return examMarks; }
    public List<Integer> getAssignmentMarks() { return assignmentMarks; }
    public List<Integer> getQuizMarks() { return quizMarks; }
    public boolean isImportedLegacy() { return importedLegacy; }
    public double assignmentAverage() { return assignmentMarks.stream().filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0); }
    public double quizAverage() { return quizMarks.stream().filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0); }
    public double totalMarks() {
        return (examMarks == null ? 0 : examMarks)
                + (hasAssignmentMarks() ? assignmentAverage() : 0)
                + (hasQuizMarks() ? quizAverage() : 0);
    }
    public boolean hasAssignmentMarks() { return assignmentMarks.stream().anyMatch(java.util.Objects::nonNull); }
    public boolean hasQuizMarks() { return quizMarks.stream().anyMatch(java.util.Objects::nonNull); }
    public boolean isComplete() {
        return examMarks != null && assignmentMarks.stream().allMatch(java.util.Objects::nonNull)
                && quizMarks.stream().allMatch(java.util.Objects::nonNull);
    }
}
