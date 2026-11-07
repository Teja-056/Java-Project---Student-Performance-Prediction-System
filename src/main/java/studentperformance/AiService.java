package studentperformance;

import java.io.IOException;
import java.util.List;

/**
 * Clean abstraction for AI educational diagnostic providers (e.g. Google Gemini, OpenAI).
 * Strict architectural rule: Java retains full deterministic authority over all numeric marks,
 * percentages, and GPA metrics. AI providers receive strictly anonymized metrics (zero PII)
 * and generate qualitative diagnostic insights, risk factor analysis, and syllabus-aligned
 * learning guidance.
 */
public interface AiService {

    /** Returns human-readable provider name, e.g. "Google Gemini" or "OpenAI". */
    String getProviderName();

    /** Returns the model identifier currently in use (e.g. "gemini-1.5-flash", "gpt-4o-mini"). */
    String getModel();

    /** Checks whether the service has active credentials to communicate with the provider. */
    boolean isConfigured();

    /**
     * Performs structured AI performance analysis for the given student.
     * All numerical calculations are deterministic in Java; the AI model provides
     * academic diagnosis, risk assessment, and personalized learning guidance.
     *
     * @param student The student record containing academic performance metrics.
     * @param analyzer The performance analyzer with calculated metrics and trends.
     * @param syllabuses List of college syllabus documents providing curriculum context.
     * @return Structured analysis results containing summary, strengths, weaknesses, and study plans.
     * @throws IOException If a network or parsing failure occurs.
     * @throws InterruptedException If the network request is interrupted.
     */
    AiAnalysisResult analyzeStudent(Student student, PerformanceAnalyzer analyzer, List<SyllabusDocument> syllabuses)
            throws IOException, InterruptedException;
}
