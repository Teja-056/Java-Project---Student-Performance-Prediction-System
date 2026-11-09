package studentperformance;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudentPerformanceFormattingTest {
    @Test
    void profileDisplaysInternalTotalsWithTheCorrectTypes() throws Exception {
        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-201", 15, 24);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        Locale oldLocale = Locale.getDefault();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            Locale.setDefault(Locale.ROOT);
            Method display = StudentPerformanceSystem.class.getDeclaredMethod("displayStudent", Student.class);
            display.setAccessible(true);
            display.invoke(new StudentPerformanceSystem(), student);
        } finally {
            System.setOut(oldOut);
            Locale.setDefault(oldLocale);
        }
        String report = output.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Internal 1 total: 211.00/290 (72.76%)"), report);
        assertTrue(report.contains("Internal 2 total: 220.00/290 (75.86%)"), report);
    }

    @Test
    void csvAndAiPromptFormatIntegerMaximumMarksWithoutExceptions(@TempDir Path tempDirectory) throws Exception {
        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-202", 15, 24);
        Path csv = tempDirectory.resolve("student-report.csv");
        new CsvExporter().export(java.util.List.of(student), csv);
        assertTrue(java.nio.file.Files.readString(csv).contains("total 22.00/40"));

        Method promptBuilder = AiPredictionService.class.getDeclaredMethod("buildPrompt", Student.class, PerformanceAnalyzer.class);
        promptBuilder.setAccessible(true);
        String prompt = (String) promptBuilder.invoke(new AiPredictionService(), student, new PerformanceAnalyzer());
        assertTrue(prompt.contains("Java=22.00/40"));
    }
}
