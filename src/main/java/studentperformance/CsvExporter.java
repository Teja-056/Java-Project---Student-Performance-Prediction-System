package studentperformance;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Small File Handling example: export database records as a CSV report. */
public class CsvExporter {
    private final PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

    public void export(List<Student> students, Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("Roll Number,Name,CGPA,Last SGPA,Internal 1 %,Internal 2 %,Current Internal %,Prediction,Unit Review,Subject Assessment Details");
            writer.newLine();
            for (Student student : students) {
                writer.write(csv(student.getRollNumber()) + "," + csv(student.getName()) + ","
                        + student.getCgpa() + "," + student.getLastSemesterSgpa() + ","
                        + analyzer.percentage(student, 1) + "," + analyzer.percentage(student, 2) + ","
                        + analyzer.currentInternalPercentage(student) + "," + csv(analyzer.prediction(student)) + ","
                        + csv(analyzer.unitAdvice(student)) + "," + csv(subjectDetails(student)));
                writer.newLine();
            }
        }
    }

    private String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }

    private String subjectDetails(Student student) {
        StringBuilder details = new StringBuilder();
        for (int exam = 1; exam <= 2; exam++) {
            for (Subject subject : Subject.values()) {
                SubjectAssessment assessment = student.getMarks(exam).get(subject);
                if (!details.isEmpty()) details.append("; ");
                details.append("Internal ").append(exam).append(' ').append(subject.getDisplayName()).append(' ');
                if (assessment == null) { details.append("missing"); continue; }
                details.append("exam ").append(assessment.getExamMarks())
                        .append('/').append(subject.getMaximumExamMarks()).append(" assignments ").append(assessment.getAssignmentMarks())
                        .append(" quizzes ").append(assessment.getQuizMarks()).append(" total ")
                        .append(String.format(java.util.Locale.ROOT, "%.2f/%d", assessment.totalMarks(), subject.getMaximumTotalMarks()));
            }
        }
        return details.toString();
    }
}
