package studentperformance;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeminiServiceTest {

    @Test
    void buildsAnonymizedPromptScrubbingPii() {
        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-042", 16, 23);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        GeminiService service = new GeminiService(null, "dummy-gemini-key", "gemini-1.5-flash");

        String prompt = AiPromptBuilder.buildAnonymizedPrompt(student, analyzer, List.of());

        // Verify PII is NOT present
        assertFalse(prompt.contains("1602-25-733-042"), "Prompt must not contain student roll number");
        assertFalse(prompt.contains(student.getName()), "Prompt must not contain student name");

        // Verify academic metrics are deterministically included
        assertTrue(prompt.contains("Current CGPA: 8.00"));
        assertTrue(prompt.contains("Previous Semester SGPA: 7.50"));
        assertTrue(prompt.contains("Internal 1 Total Percentage"));
        assertTrue(prompt.contains("Internal 2 Total Percentage"));
        assertTrue(prompt.contains("Java"));
        assertTrue(prompt.contains("REQUIRED JSON OUTPUT SCHEMA"));
    }

    @Test
    void parsesStructuredGeminiResponseCorrectly() throws Exception {
        String mockGeminiResponse = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "{\\"academic_summary\\": \\"Solid performance in programming with good growth between internals.\\", \\"strengths\\": [\\"Data Structures\\", \\"Java\\"], \\"weaknesses\\": [\\"Computer Architecture\\"], \\"subjects_requiring_improvement\\": [\\"Computer Architecture\\"], \\"trend_analysis\\": \\"Positive momentum in practical coding; CA theory requires review.\\", \\"estimated_future_performance\\": \\"Estimated 78-82% if CA revision is maintained (moderate uncertainty).\\", \\"risk_level\\": \\"LOW\\", \\"risk_factors\\": [\\"CA quiz marks dipped\\"], \\"recommended_interventions\\": [\\"Join weekly CA study circle\\"], \\"suggested_daily_study_hours\\": 3, \\"subject_wise_improvement_plan\\": {\\"CA\\": \\"Review memory hierarchy and pipelining\\"}, \\"revision_strategies\\": [\\"Spaced repetition flashcards\\"], \\"focus_topics\\": [\\"Pipeline hazards\\", \\"Cache mapping\\"]}"
                  }
                ],
                "role": "model"
              },
              "finishReason": "STOP",
              "index": 0
            }
          ],
          "usageMetadata": {
            "promptTokenCount": 420,
            "candidatesTokenCount": 210,
            "totalTokenCount": 630
          },
          "modelVersion": "gemini-1.5-flash"
        }
        """;

        MockHttpClient mockClient = new MockHttpClient(200, mockGeminiResponse);
        GeminiService service = new GeminiService(mockClient, "test-gemini-key", "gemini-1.5-flash");

        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-005", 19, 21);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        AiAnalysisResult result = service.analyzeStudent(student, analyzer, List.of());

        assertNotNull(result);
        assertEquals("Solid performance in programming with good growth between internals.", result.getAcademicSummary());
        assertEquals("LOW", result.getRiskLevel());
        assertEquals(3, result.getSuggestedDailyStudyHours());
        assertEquals(2, result.getStrengths().size());
        assertTrue(result.getStrengths().contains("Data Structures"));
        assertTrue(result.getWeaknesses().contains("Computer Architecture"));
        assertTrue(result.getFocusTopics().contains("Pipeline hazards"));
        assertEquals("Review memory hierarchy and pipelining", result.getSubjectWiseImprovementPlan().get("CA"));
        assertTrue(result.getEstimatedFuturePerformance().contains("moderate uncertainty"));

        String consoleReport = result.formatConsoleReport();
        assertTrue(consoleReport.contains("AI PERFORMANCE ANALYSIS"));
        assertTrue(consoleReport.contains("RISK LEVEL: LOW"));
    }

    @Test
    void parsesFencedJsonResponseFromGemini() throws Exception {
        String fencedText = "```json\\n{\\\"academic_summary\\\": \\\"Fenced JSON test passed.\\\", \\\"strengths\\\": [\\\"Algorithms\\\"], \\\"weaknesses\\\": [], \\\"subjects_requiring_improvement\\\": [], \\\"trend_analysis\\\": \\\"Steady\\\", \\\"estimated_future_performance\\\": \\\"Consistent (low uncertainty)\\\", \\\"risk_level\\\": \\\"LOW\\\", \\\"risk_factors\\\": [], \\\"recommended_interventions\\\": [], \\\"suggested_daily_study_hours\\\": 2, \\\"subject_wise_improvement_plan\\\": {}, \\\"revision_strategies\\\": [], \\\"focus_topics\\\": []}\\n```";
        String mockGeminiResponse = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "%s"
                  }
                ],
                "role": "model"
              },
              "finishReason": "STOP"
            }
          ]
        }
        """.formatted(fencedText);

        MockHttpClient mockClient = new MockHttpClient(200, mockGeminiResponse);
        GeminiService service = new GeminiService(mockClient, "test-gemini-key", "gemini-1.5-flash");

        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-006", 20, 20);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        AiAnalysisResult result = service.analyzeStudent(student, analyzer, List.of());
        assertNotNull(result);
        assertEquals("Fenced JSON test passed.", result.getAcademicSummary());
    }

    @Test
    void handlesGeminiHttpErrorsAppropriately() {
        // 400 Invalid Argument
        MockHttpClient client400 = new MockHttpClient(400, "{\"error\": {\"code\": 400, \"message\": \"API key not valid. Please pass a valid API key.\", \"status\": \"INVALID_ARGUMENT\"}}");
        GeminiService service400 = new GeminiService(client400, "invalid-key", "gemini-1.5-flash");
        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-007", 20, 20);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        IOException ex400 = assertThrows(IOException.class, () -> service400.analyzeStudent(student, analyzer, List.of()));
        assertTrue(ex400.getMessage().contains("400") || ex400.getMessage().contains("API key not valid"));

        // 403 Permission Denied
        MockHttpClient client403 = new MockHttpClient(403, "{\"error\": {\"code\": 403, \"message\": \"Method not allowed or key inactive\"}}");
        GeminiService service403 = new GeminiService(client403, "inactive-key", "gemini-1.5-flash");
        IOException ex403 = assertThrows(IOException.class, () -> service403.analyzeStudent(student, analyzer, List.of()));
        assertTrue(ex403.getMessage().contains("403"));

        // 404 Model Not Found
        MockHttpClient client404 = new MockHttpClient(404, "{\"error\": {\"code\": 404, \"message\": \"Model not found\"}}");
        GeminiService service404 = new GeminiService(client404, "key", "non-existent-model");
        IOException ex404 = assertThrows(IOException.class, () -> service404.analyzeStudent(student, analyzer, List.of()));
        assertTrue(ex404.getMessage().contains("404") || ex404.getMessage().contains("not found"));

        // 429 Rate Limit
        MockHttpClient client429 = new MockHttpClient(429, "{\"error\": {\"code\": 429, \"message\": \"Resource has been exhausted (e.g. check quota).\"}}");
        GeminiService service429 = new GeminiService(client429, "valid-key", "gemini-1.5-flash");
        IOException ex429 = assertThrows(IOException.class, () -> service429.analyzeStudent(student, analyzer, List.of()));
        assertTrue(ex429.getMessage().contains("429") || ex429.getMessage().contains("rate limit") || ex429.getMessage().contains("quota"));

        // 500 Service Error
        MockHttpClient client500 = new MockHttpClient(500, "Internal Server Error");
        GeminiService service500 = new GeminiService(client500, "valid-key", "gemini-1.5-flash");
        IOException ex500 = assertThrows(IOException.class, () -> service500.analyzeStudent(student, analyzer, List.of()));
        assertTrue(ex500.getMessage().contains("500") || ex500.getMessage().contains("temporarily unavailable"));
    }

    @Test
    void handlesMissingApiKeyGracefully() {
        GeminiService unconfigured = new GeminiService(null, null, "gemini-1.5-flash");
        assertFalse(unconfigured.isConfigured());

        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-008", 20, 20);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> unconfigured.analyzeStudent(student, analyzer, List.of()));
        assertTrue(ex.getMessage().contains("GEMINI_API_KEY"));
    }

    @Test
    void handlesIncompleteMarksGracefully() {
        GeminiService service = new GeminiService(null, "some-key", "gemini-1.5-flash");
        java.util.EnumMap<Subject, SubjectAssessment> first = new java.util.EnumMap<>(Subject.class);
        java.util.EnumMap<Subject, SubjectAssessment> second = new java.util.EnumMap<>(Subject.class);
        Student student = new Student("Incomplete Student", "1602-25-733-009", 7.0, 7.0, first, second,
                List.of(new LegacyMark(1, "JAVA", 15)));
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        assertThrows(IllegalArgumentException.class,
                () -> service.analyzeStudent(student, analyzer, List.of()));
    }

    @Test
    void handlesSafetyBlockedResponse() {
        String safetyResponse = """
        {
          "candidates": [
            {
              "finishReason": "SAFETY",
              "index": 0
            }
          ]
        }
        """;
        MockHttpClient mockClient = new MockHttpClient(200, safetyResponse);
        GeminiService service = new GeminiService(mockClient, "test-key", "gemini-1.5-flash");

        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-010", 20, 20);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        IOException ex = assertThrows(IOException.class,
                () -> service.analyzeStudent(student, analyzer, List.of()));
        assertTrue(ex.getMessage().contains("safety"));
    }

    @Test
    void testConnectivityWithMockClient() {
        MockHttpClient okClient = new MockHttpClient(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}");
        GeminiService serviceOk = new GeminiService(okClient, "test-key", "gemini-1.5-flash");
        GeminiService.ConnectivityResult resOk = serviceOk.testConnectivity();
        assertTrue(resOk.success());
        assertEquals(200, resOk.httpStatusCode());

        MockHttpClient failClient = new MockHttpClient(403, "{\"error\":{\"message\":\"API key inactive\"}}");
        GeminiService serviceFail = new GeminiService(failClient, "test-key", "gemini-1.5-flash");
        GeminiService.ConnectivityResult resFail = serviceFail.testConnectivity();
        assertFalse(resFail.success());
        assertEquals(403, resFail.httpStatusCode());
    }

    @Test
    void pdfGenerationSucceedsWithGeminiResult(@TempDir Path tempDir) throws Exception {
        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-011", 18, 22);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        AiAnalysisResult result = new AiAnalysisResult(
                "Strong student overall with excellent practical programming skills.",
                List.of("Object Oriented Programming", "Data Structures"),
                List.of("Human Values"),
                List.of("Human Values"),
                "Steady positive improvement across both terms.",
                "Predicted 80-85% at university end-semester examination (low uncertainty).",
                "LOW",
                List.of("Minor dip in HVPE quiz 2"),
                List.of("Maintain regular study hours"),
                3,
                Map.of("HVPE", "Review professional code of ethics case studies"),
                List.of("Active recall", "Timed mock exams"),
                List.of("Binary search trees", "Virtual memory")
        );

        PdfReportGenerator pdfGen = new PdfReportGenerator();
        Path outPdf = tempDir.resolve("gemini-test-report.pdf");
        pdfGen.generateReport(student, analyzer, result, outPdf);

        assertTrue(Files.exists(outPdf), "PDF report file must be created on disk");
        assertTrue(Files.size(outPdf) > 1000, "PDF file must not be empty");
    }

    // Mock HttpClient for offline unit testing without network calls
    private static class MockHttpClient extends HttpClient {
        private final int statusCode;
        private final String body;

        MockHttpClient(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override
        public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override
        public Redirect followRedirects() { return Redirect.NEVER; }
        @Override
        public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override
        public javax.net.ssl.SSLContext sslContext() { return null; }
        @Override
        public javax.net.ssl.SSLParameters sslParameters() { return null; }
        @Override
        public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override
        public Version version() { return Version.HTTP_2; }
        @Override
        public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return (HttpResponse<T>) new MockHttpResponse(statusCode, body, request);
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return java.util.concurrent.CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private static class MockHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;
        private final HttpRequest request;

        MockHttpResponse(int statusCode, String body, HttpRequest request) {
            this.statusCode = statusCode;
            this.body = body;
            this.request = request;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public HttpRequest request() { return request; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (k, v) -> true); }
        @Override public String body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    }
}
