package studentperformance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with OpenAI API to provide structured student performance
 * analysis, personalized study recommendations, risk identification, and study plans.
 * All PII (names, roll numbers) is strictly scrubbed before sending.
 */
public class OpenAIService implements AiService {
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String API_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final HttpClient client;

    public OpenAIService() {
        this(null, EnvConfig.get("OPENAI_API_KEY"), EnvConfig.get("OPENAI_MODEL", DEFAULT_MODEL));
    }

    public OpenAIService(HttpClient client, String apiKey, String model) {
        this.client = client;
        this.apiKey = (apiKey == null || apiKey.isBlank()) ? null : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
    }

    @Override
    public String getProviderName() {
        return "OpenAI";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getModel() {
        return model;
    }

    private synchronized HttpClient getHttpClient() {
        return client != null ? client : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /**
     * Performs structured AI performance analysis for a student.
     */
    @Override
    public AiAnalysisResult analyzeStudent(Student student, PerformanceAnalyzer analyzer, List<SyllabusDocument> syllabuses)
            throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenAI API key is not configured. Set the OPENAI_API_KEY environment variable.");
        }
        if (!analyzer.hasBothInternals(student)) {
            throw new IllegalArgumentException("Complete modern assessment marks for both Internal 1 and Internal 2 are required for AI analysis.");
        }

        String userPrompt = buildAnonymizedPrompt(student, analyzer, syllabuses);
        String systemPrompt = buildSystemPrompt();

        String requestBody = buildRequestBody(systemPrompt, userPrompt);

        HttpRequest request = HttpRequest.newBuilder(URI.create(API_ENDPOINT))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        if (statusCode == 401) {
            throw new IOException("OpenAI authentication failed (HTTP 401). Please verify your OPENAI_API_KEY.");
        } else if (statusCode == 429) {
            throw new IOException("OpenAI rate limit or quota exceeded (HTTP 429). Check your OpenAI account billing or quota limits.");
        } else if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("OpenAI API returned HTTP " + statusCode + ": " + response.body());
        }

        return parseAiResponse(response.body());
    }

    public String buildSystemPrompt() {
        return AiPromptBuilder.buildSystemPrompt();
    }

    public String buildAnonymizedPrompt(Student student, PerformanceAnalyzer analyzer, List<SyllabusDocument> syllabuses) {
        return AiPromptBuilder.buildAnonymizedPrompt(student, analyzer, syllabuses);
    }

    public String buildRequestBody(String systemPrompt, String userPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(AiPromptBuilder.escapeJson(model)).append("\",");
        sb.append("\"response_format\":{\"type\":\"json_object\"},");
        sb.append("\"temperature\":0.3,");
        sb.append("\"messages\":[");
        sb.append("{\"role\":\"system\",\"content\":\"").append(AiPromptBuilder.escapeJson(systemPrompt)).append("\"},");
        sb.append("{\"role\":\"user\",\"content\":\"").append(AiPromptBuilder.escapeJson(userPrompt)).append("\"}");
        sb.append("]}");
        return sb.toString();
    }

    public AiAnalysisResult parseAiResponse(String responseJson) throws IOException {
        Map<String, Object> root = SimpleJsonParser.parseObject(responseJson);

        // Check for OpenAI API error structure
        if (root.containsKey("error")) {
            Object errorObj = root.get("error");
            if (errorObj instanceof Map<?, ?> errMap) {
                String message = String.valueOf(errMap.get("message"));
                throw new IOException("OpenAI API error: " + message);
            }
            throw new IOException("OpenAI API returned an error: " + errorObj);
        }

        // Extract choices[0].message.content
        Object choicesObj = root.get("choices");
        if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) {
            throw new IOException("No choices returned in OpenAI response.");
        }
        Object firstChoice = choicesList.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new IOException("Invalid choice structure in OpenAI response.");
        }
        Object msgObj = choiceMap.get("message");
        if (!(msgObj instanceof Map<?, ?> msgMap)) {
            throw new IOException("Invalid message structure in OpenAI response.");
        }
        Object contentObj = msgMap.get("content");
        if (contentObj == null) {
            throw new IOException("Empty content in OpenAI response message.");
        }

        String content = contentObj.toString();
        return AiPromptBuilder.parseStructuredJson(content);
    }
}
