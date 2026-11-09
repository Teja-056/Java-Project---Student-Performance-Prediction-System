package studentperformance.dto;

import studentperformance.SubjectAssessment;
import java.util.List;

public class SubjectAssessmentDto {
    private Integer examMarks;
    private Integer assignment1;
    private Integer assignment2;
    private Integer assignment3;
    private Integer quiz1;
    private Integer quiz2;
    private Integer quiz3;
    private double assignmentAverage;
    private double quizAverage;
    private double totalMarks;

    public SubjectAssessmentDto() {}

    public SubjectAssessmentDto(SubjectAssessment sa) {
        if (sa != null) {
            this.examMarks = sa.getExamMarks();
            List<Integer> a = sa.getAssignmentMarks();
            if (a.size() > 0) this.assignment1 = a.get(0);
            if (a.size() > 1) this.assignment2 = a.get(1);
            if (a.size() > 2) this.assignment3 = a.get(2);

            List<Integer> q = sa.getQuizMarks();
            if (q.size() > 0) this.quiz1 = q.get(0);
            if (q.size() > 1) this.quiz2 = q.get(1);
            if (q.size() > 2) this.quiz3 = q.get(2);

            this.assignmentAverage = sa.assignmentAverage();
            this.quizAverage = sa.quizAverage();
            this.totalMarks = sa.totalMarks();
        }
    }

    public Integer getExamMarks() { return examMarks; }
    public void setExamMarks(Integer examMarks) { this.examMarks = examMarks; }
    public Integer getAssignment1() { return assignment1; }
    public void setAssignment1(Integer assignment1) { this.assignment1 = assignment1; }
    public Integer getAssignment2() { return assignment2; }
    public void setAssignment2(Integer assignment2) { this.assignment2 = assignment2; }
    public Integer getAssignment3() { return assignment3; }
    public void setAssignment3(Integer assignment3) { this.assignment3 = assignment3; }
    public Integer getQuiz1() { return quiz1; }
    public void setQuiz1(Integer quiz1) { this.quiz1 = quiz1; }
    public Integer getQuiz2() { return quiz2; }
    public void setQuiz2(Integer quiz2) { this.quiz2 = quiz2; }
    public Integer getQuiz3() { return quiz3; }
    public void setQuiz3(Integer quiz3) { this.quiz3 = quiz3; }
    public double getAssignmentAverage() { return assignmentAverage; }
    public void setAssignmentAverage(double assignmentAverage) { this.assignmentAverage = assignmentAverage; }
    public double getQuizAverage() { return quizAverage; }
    public void setQuizAverage(double quizAverage) { this.quizAverage = quizAverage; }
    public double getTotalMarks() { return totalMarks; }
    public void setTotalMarks(double totalMarks) { this.totalMarks = totalMarks; }

    public SubjectAssessment toSubjectAssessment() {
        List<Integer> assigns = List.of(
                assignment1 == null ? 0 : assignment1,
                assignment2 == null ? 0 : assignment2,
                assignment3 == null ? 0 : assignment3
        );
        List<Integer> quizzes = List.of(
                quiz1 == null ? 0 : quiz1,
                quiz2 == null ? 0 : quiz2,
                quiz3 == null ? 0 : quiz3
        );
        return new SubjectAssessment(examMarks, assigns, quizzes);
    }
}
