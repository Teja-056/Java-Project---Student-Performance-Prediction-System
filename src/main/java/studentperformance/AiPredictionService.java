package studentperformance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Optional OpenAI Responses API call. Sends score data only, never student identifiers. */
public class AiPredictionService {
    private final String apiKey = System.getenv("OPENAI_API_KEY");
    private final String model = setting("OPENAI_MODEL", "gpt-6-astra");
    private HttpClient client;

    public AiPredictionService() {
        this(null);
    }

    public AiPredictionService(HttpClient client) {
        this.client = client;
    }

    private synchronized HttpClient getClient() {
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        }
        return client;
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    public String predict(Student student, PerformanceAnalyzer analyzer) throws IOException, InterruptedException {
        if (!isConfigured()) throw new IllegalStateException("Set OPENAI_API_KEY to enable AI analysis.");
        String prompt = buildPrompt(student, analyzer);
        String body = "{\"model\":\"" + jsonEscape(model) + "\",\"input\":\"" + jsonEscape(prompt) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = getClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("OpenAI API returned HTTP " + response.statusCode() + ". Check the API key, model access, and API account.");
        String text = extractOutputText(response.body());
        if (text.isBlank()) throw new IOException("The AI service returned no text output.");
        return text;
    }

    private String buildPrompt(Student student, PerformanceAnalyzer analyzer) {
        StringBuilder data = new StringBuilder("Review anonymized academic scores and give a concise, cautious performance forecast. ");
        data.append("Do not claim certainty or diagnose the student. Use these fixed unit mappings: low Internal 1 means review Units 1-2; ");
        data.append("low Internal 2 means review Units 3-5. Low means under 50 percent. Explain the evidence and practical study steps. ");
        data.append("Do not invent thresholds or marks. Scores: CGPA=").append(student.getCgpa())
                .append("; last SGPA=").append(student.getLastSemesterSgpa())
                .append("; Internal 1=").append(String.format(java.util.Locale.ROOT, "%.2f", analyzer.percentage(student, 1))).append("%; ")
                .append("Internal 2=").append(String.format(java.util.Locale.ROOT, "%.2f", analyzer.percentage(student, 2))).append("%; ")
                .append("combined internal percentage=").append(String.format(java.util.Locale.ROOT, "%.2f", analyzer.currentInternalPercentage(student))).append("%. ");
        for (int exam = 1; exam <= 2; exam++) {
            data.append("Internal ").append(exam).append(" subject totals: ");
            for (Subject subject : Subject.values()) {
                if (subject != Subject.values()[0]) data.append(", ");
                data.append(subject.getDisplayName()).append("=")
                        .append(String.format(java.util.Locale.ROOT, "%.2f/%d", student.getMarks(exam).get(subject).totalMarks(), subject.getMaximumTotalMarks()));
            }
            data.append(". ");
        }
        return data.toString();
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // Extracts the first output_text content item and decodes JSON string escapes.
    private String extractOutputText(String json) throws IOException {
        int type = json.indexOf("\"type\":\"output_text\"");
        if (type < 0) type = json.indexOf("\"type\": \"output_text\"");
        if (type < 0) throw new IOException("No output_text item was present in the API response.");
        int key = json.indexOf("\"text\"", type);
        int colon = json.indexOf(':', key);
        int start = json.indexOf('"', colon + 1);
        if (key < 0 || colon < 0 || start < 0) throw new IOException("Could not read text from the API response.");
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> value.append('\n'); case 'r' -> value.append('\r'); case 't' -> value.append('\t');
                    case 'b' -> value.append('\b'); case 'f' -> value.append('\f');
                    case 'u' -> {
                        if (i + 4 >= json.length()) throw new IOException("Invalid Unicode escape in API response.");
                        try { value.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16)); }
                        catch (NumberFormatException ex) { throw new IOException("Invalid Unicode escape in API response."); }
                        i += 4;
                    }
                    default -> value.append(c);
                }
                escaped = false;
            } else if (c == '\\') escaped = true;
            else if (c == '"') return value.toString();
            else value.append(c);
        }
        throw new IOException("Unterminated text value in API response.");
    }
}
