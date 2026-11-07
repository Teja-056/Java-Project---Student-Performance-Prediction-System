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

class OpenAIServiceTest {

    @Test
    void buildsAnonymizedPromptScrubbingPii() {
        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-001", 15, 24);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        OpenAIService service = new OpenAIService(null, "dummy-key", "gpt-4o-mini");

        String prompt = service.buildAnonymizedPrompt(student, analyzer, List.of());

        // Verify PII is NOT present
        assertFalse(prompt.contains("1602-25-733-001"), "Prompt must not contain student roll number");
        assertFalse(prompt.contains(student.getName()), "Prompt must not contain student name");

        // Verify academic metrics are present
        assertTrue(prompt.contains("Current CGPA: 8.00"));
        assertTrue(prompt.contains("Previous Semester SGPA: 7.50"));
        assertTrue(prompt.contains("Internal 1 Total Percentage"));
        assertTrue(prompt.contains("Internal 2 Total Percentage"));
        assertTrue(prompt.contains("Java"));
        assertTrue(prompt.contains("REQUIRED JSON OUTPUT SCHEMA"));
    }

    @Test
    void parsesStructuredOpenAiResponseCorrectly() throws Exception {
        String mockJsonResponse = """
        {
          "id": "chatcmpl-test12345",
          "object": "chat.completion",
          "created": 1700000000,
          "model": "gpt-4o-mini",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "{\\"academic_summary\\": \\"Strong foundation in core computing, slight decline in theoretical subjects.\\", \\"strengths\\": [\\"Object Oriented Programming\\", \\"Data Structures\\"], \\"weaknesses\\": [\\"Computer Architecture\\", \\"HVPE\\"], \\"subjects_requiring_improvement\\": [\\"Computer Architecture\\"], \\"trend_analysis\\": \\"Positive momentum in practical coding; theory requires structured revision.\\", \\"estimated_future_performance\\": \\"Estimated to maintain 75-80% if revision schedule is adhered to (moderate uncertainty). \\", \\"risk_level\\": \\"LOW\\", \\"risk_factors\\": [\\"Drop in Internal 2 CA quiz scores\\"], \\"recommended_interventions\\": [\\"Join weekly CA peer-review circles\\"], \\"suggested_daily_study_hours\\": 3, \\"subject_wise_improvement_plan\\": {\\"CA\\": \\"Revise CPU instruction formats and addressing modes\\", \\"HVPE\\": \\"Review ethics case studies\\"}, \\"revision_strategies\\": [\\"Active recall with flashcards\\", \\"Implement stack/tree problems directly in Java\\"], \\"focus_topics\\": [\\"Addressing Modes\\", \\"Memory Hierarchy\\"]}"
              },
              "finish_reason": "stop"
            }
          ]
        }
        """;

        MockHttpClient mockClient = new MockHttpClient(200, mockJsonResponse);
        OpenAIService service = new OpenAIService(mockClient, "test-api-key", "gpt-4o-mini");

        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-002", 18, 22);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        AiAnalysisResult result = service.analyzeStudent(student, analyzer, List.of());

        assertNotNull(result);
        assertEquals("Strong foundation in core computing, slight decline in theoretical subjects.", result.getAcademicSummary());
        assertEquals("LOW", result.getRiskLevel());
        assertEquals(3, result.getSuggestedDailyStudyHours());
        assertEquals(2, result.getStrengths().size());
        assertTrue(result.getStrengths().contains("Object Oriented Programming"));
        assertTrue(result.getWeaknesses().contains("Computer Architecture"));
        assertTrue(result.getFocusTopics().contains("Addressing Modes"));
        assertEquals("Revise CPU instruction formats and addressing modes", result.getSubjectWiseImprovementPlan().get("CA"));
        assertTrue(result.getEstimatedFuturePerformance().contains("moderate uncertainty"));

        String consoleReport = result.formatConsoleReport();
        assertTrue(consoleReport.contains("AI PERFORMANCE ANALYSIS"));
        assertTrue(consoleReport.contains("RISK LEVEL: LOW"));
    }

    @Test
    void handlesHttpErrorsAppropriately() {
        // 401 Unauthorized
        MockHttpClient client401 = new MockHttpClient(401, "{\"error\": {\"message\": \"Incorrect API key provided\"}}");
        OpenAIService service401 = new OpenAIService(client401, "invalid-key", "gpt-4o-mini");
        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-003", 20, 20);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        IOException ex401 = assertThrows(IOException.class, () -> service401.analyzeStudent(student, analyzer, List.of()));
        assertTrue(ex401.getMessage().contains("401"));

        // 429 Rate Limit
        MockHttpClient client429 = new MockHttpClient(429, "{\"error\": {\"message\": \"Rate limit exceeded\"}}");
        OpenAIService service429 = new OpenAIService(client429, "test-key", "gpt-4o-mini");
        IOException ex429 = assertThrows(IOException.class, () -> service429.analyzeStudent(student, analyzer, List.of()));
        assertTrue(ex429.getMessage().contains("429"));
    }

    @Test
    void generatesValidPdfReport(@TempDir Path tempDir) throws Exception {
        Student student = PerformanceAnalyzerTest.fullStudent("1602-25-733-004", 17, 21);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        AiAnalysisResult aiResult = new AiAnalysisResult(
                "Consistent academic performance across subjects with room for growth in architecture.",
                List.of("Strong Java programming", "Regular assignment submissions"),
                List.of("Computer Architecture memory concepts"),
                List.of("CA"),
                "Steady improvement of +4.2% in Internal 2.",
                "Expected to secure distinction with dedicated revision (moderate uncertainty).",
                "LOW",
                List.of("Slight dip in quiz scores"),
                List.of("Consult faculty for CA unit 4 doubts"),
                3,
                Map.of("CA", "Review cache mapping techniques", "Java", "Practice multithreading"),
                List.of("Solve past question papers", "Use Pomodoro technique"),
                List.of("Cache memory", "Virtual memory paging")
        );

        Path pdfFile = tempDir.resolve("test_student_report.pdf");
        PdfReportGenerator pdfGen = new PdfReportGenerator();
        pdfGen.generateReport(student, analyzer, aiResult, pdfFile);

        assertTrue(Files.exists(pdfFile));
        assertTrue(Files.size(pdfFile) > 1000, "PDF should contain binary content");
    }

    @Test
    void simpleJsonParserHandlesPrimitivesArraysAndNestedObjects() throws Exception {
        String json = """
        {
          "name": "Test \\"Student\\"",
          "age": 21,
          "gpa": 8.75,
          "active": true,
          "suspended": false,
          "middleName": null,
          "subjects": ["Java", "DS", "CA"],
          "details": {
             "college": "Vasavi",
             "year": 2026
          }
        }
        """;

        Map<String, Object> map = SimpleJsonParser.parseObject(json);
        assertEquals("Test \"Student\"", map.get("name"));
        assertEquals(21, ((Number) map.get("age")).intValue());
        assertEquals(8.75, ((Number) map.get("gpa")).doubleValue(), 0.001);
        assertEquals(Boolean.TRUE, map.get("active"));
        assertEquals(Boolean.FALSE, map.get("suspended"));
        assertNull(map.get("middleName"));

        @SuppressWarnings("unchecked")
        List<Object> subjects = (List<Object>) map.get("subjects");
        assertEquals(3, subjects.size());
        assertEquals("Java", subjects.get(0));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) map.get("details");
        assertEquals("Vasavi", details.get("college"));
        assertEquals(2026, ((Number) details.get("year")).intValue());
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
