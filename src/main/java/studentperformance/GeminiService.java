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
 * AI Service implementation for Google Gemini API (v1beta).
 * Supports official free-tier models (default: gemini-flash-lite-latest).
 * Strictly preserves student privacy by sending anonymized metrics only.
 * Java deterministically computes all marks and GPA values.
 */
public class GeminiService implements AiService {
    public static final String DEFAULT_MODEL = "gemini-flash-lite-latest";
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final HttpClient client;

    public GeminiService() {
        this(null, EnvConfig.get("GEMINI_API_KEY"), EnvConfig.get("GEMINI_MODEL", DEFAULT_MODEL));
    }

    public GeminiService(HttpClient client, String apiKey, String model) {
        this.client = client;
        this.apiKey = (apiKey == null || apiKey.isBlank()) ? null : apiKey.trim();
        this.model = normalizeModelName(model);
    }

    private static String normalizeModelName(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_MODEL;
        String m = raw.trim();
        if (m.startsWith("models/")) m = m.substring("models/".length());
        // Handle models that Google sunset for new API users
        if (m.equalsIgnoreCase("gemini-1.5-flash") || m.equalsIgnoreCase("gemini-2.5-flash")) {
            return DEFAULT_MODEL;
        }
        return m;
    }

    @Override
    public String getProviderName() {
        return "Google Gemini";
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private synchronized HttpClient getHttpClient() {
        return client != null ? client : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    private String getEndpointUrl() {
        return API_BASE_URL + model + ":generateContent";
    }

    /**
     * Diagnostic result for connectivity check.
     */
    public record ConnectivityResult(boolean success, int httpStatusCode, String message, long latencyMs) {}

    /**
     * Executes a lightweight test ping to verify Gemini API credentials and endpoint accessibility.
     */
    public ConnectivityResult testConnectivity() {
        if (!isConfigured()) {
            return new ConnectivityResult(false, 0, "GEMINI_API_KEY is not configured.", 0);
        }

        long start = System.currentTimeMillis();
        String pingBody = "{\"contents\":[{\"parts\":[{\"text\":\"Hello Gemini. Reply with OK.\"}]}]}";

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(getEndpointUrl()))
                    .timeout(Duration.ofSeconds(20))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(pingBody))
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                return new ConnectivityResult(true, status, "Successfully connected to Google Gemini (" + model + ")", latency);
            } else {
                String errorMsg = extractErrorMessage(response.body());
                return new ConnectivityResult(false, status, "Gemini API error (HTTP " + status + "): " + errorMsg, latency);
            }
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            return new ConnectivityResult(false, -1, "Connection failed: " + ex.getMessage(), latency);
        }
    }

    @Override
    public AiAnalysisResult analyzeStudent(Student student, PerformanceAnalyzer analyzer, List<SyllabusDocument> syllabuses)
            throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured. Set the GEMINI_API_KEY environment variable or configure it in .env.");
        }
        if (!analyzer.hasBothInternals(student)) {
            throw new IllegalArgumentException("Complete modern assessment marks for both Internal 1 and Internal 2 are required for AI analysis.");
        }

        String userPrompt = AiPromptBuilder.buildAnonymizedPrompt(student, analyzer, syllabuses);
        String systemPrompt = AiPromptBuilder.buildSystemPrompt();
        String requestBody = buildRequestBody(systemPrompt, userPrompt);

        HttpRequest request = HttpRequest.newBuilder(URI.create(getEndpointUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        if (statusCode == 400) {
            String errorMsg = extractErrorMessage(response.body());
            throw new IOException("Gemini API request invalid (HTTP 400): " + errorMsg);
        } else if (statusCode == 401 || statusCode == 403) {
            String errorMsg = extractErrorMessage(response.body());
            throw new IOException("Gemini API authentication failed (HTTP " + statusCode + "). Please verify your GEMINI_API_KEY: " + errorMsg);
        } else if (statusCode == 404) {
            throw new IOException("Gemini model '" + model + "' not found (HTTP 404). Check GEMINI_MODEL setting.");
        } else if (statusCode == 429) {
            throw new IOException("Gemini API rate limit or quota exceeded (HTTP 429). The free tier allows up to 15 requests per minute. Please retry shortly.");
        } else if (statusCode >= 500) {
            throw new IOException("Google Gemini service temporarily unavailable (HTTP " + statusCode + "). Please retry.");
        } else if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Gemini API returned HTTP " + statusCode + ": " + extractErrorMessage(response.body()));
        }

        return parseGeminiResponse(response.body());
    }

    public String buildRequestBody(String systemPrompt, String userPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"systemInstruction\":{\"parts\":[{\"text\":\"").append(AiPromptBuilder.escapeJson(systemPrompt)).append("\"}]},");
        sb.append("\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"").append(AiPromptBuilder.escapeJson(userPrompt)).append("\"}]}],");
        sb.append("\"generationConfig\":{\"responseMimeType\":\"application/json\",\"temperature\":0.2}");
        sb.append("}");
        return sb.toString();
    }

    public AiAnalysisResult parseGeminiResponse(String responseJson) throws IOException {
        Map<String, Object> root = SimpleJsonParser.parseObject(responseJson);

        // Check for error block
        if (root.containsKey("error")) {
            Object errorObj = root.get("error");
            if (errorObj instanceof Map<?, ?> errMap) {
                String message = String.valueOf(errMap.get("message"));
                throw new IOException("Gemini API error: " + message);
            }
            throw new IOException("Gemini API returned an error: " + errorObj);
        }

        // Check candidates
        Object candidatesObj = root.get("candidates");
        if (!(candidatesObj instanceof List<?> candidatesList) || candidatesList.isEmpty()) {
            if (root.containsKey("promptFeedback")) {
                throw new IOException("Gemini request was blocked by content filters: " + root.get("promptFeedback"));
            }
            throw new IOException("No candidates returned in Gemini response.");
        }

        Object firstCand = candidatesList.get(0);
        if (!(firstCand instanceof Map<?, ?> candMap)) {
            throw new IOException("Invalid candidate structure in Gemini response.");
        }

        Object finishReason = candMap.get("finishReason");
        if ("SAFETY".equals(finishReason)) {
            throw new IOException("Gemini response was blocked due to safety settings.");
        }

        Object contentObj = candMap.get("content");
        if (!(contentObj instanceof Map<?, ?> contentMap)) {
            throw new IOException("Invalid content structure in Gemini candidate.");
        }

        Object partsObj = contentMap.get("parts");
        if (!(partsObj instanceof List<?> partsList) || partsList.isEmpty()) {
            throw new IOException("No content parts found in Gemini candidate response.");
        }

        // Extract response text from parts, ignoring internal thought/thinking parts if present
        String rawContent = null;
        for (Object p : partsList) {
            if (p instanceof Map<?, ?> partMap) {
                boolean isThought = Boolean.TRUE.equals(partMap.get("thought"));
                Object textObj = partMap.get("text");
                if (textObj != null && !textObj.toString().isBlank()) {
                    if (!isThought || rawContent == null) {
                        rawContent = textObj.toString();
                    }
                }
            }
        }

        if (rawContent == null || rawContent.isBlank()) {
            throw new IOException("Empty text part in Gemini response.");
        }

        return AiPromptBuilder.parseStructuredJson(rawContent);
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) return "Unknown error";
        try {
            Map<String, Object> root = SimpleJsonParser.parseObject(body);
            if (root.get("error") instanceof Map<?, ?> errMap) {
                Object msg = errMap.get("message");
                if (msg != null && !msg.toString().isBlank()) return msg.toString();
            }
        } catch (Exception ignored) {
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
