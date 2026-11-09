package studentperformance;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GeminiPdfConnectivityTest {

    @Test
    void testGeminiNativePdfInput() throws Exception {
        String apiKey = EnvConfig.get("GEMINI_API_KEY");
        assertNotNull(apiKey, "GEMINI_API_KEY must be configured in environment");
        assertFalse(apiKey.isBlank(), "GEMINI_API_KEY must not be blank");

        // 1. Generate a small 1-page sample PDF in memory using Apache PDFBox
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(50, 700);
                cs.showText("Vasavi College of Engineering (Autonomous)");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.showText("DATA STRUCTURES");
                cs.newLineAtOffset(0, -20);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.showText("Course Code: UI25PC310CS | Credits: 4 | CIE: 40 Marks");
                cs.newLineAtOffset(0, -25);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                cs.showText("UNIT-I: Basic Concepts and Arrays");
                cs.newLineAtOffset(0, -18);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("Algorithm specification, Data abstraction, Arrays ADT");
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        assertTrue(pdfBytes.length > 0, "Generated PDF bytes should not be empty");
        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);

        // 2. Build Gemini API request with inlineData for native PDF
        String configuredModel = EnvConfig.get("GEMINI_MODEL", GeminiService.DEFAULT_MODEL);
        String endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + configuredModel + ":generateContent";

        String prompt = "Extract the course information from this syllabus page. "
                + "Return a JSON object with keys: institution, subjectName, courseCode, credits, unit1Title, unit1Topics.";

        String requestBody = "{"
                + "\"contents\": [{"
                + "  \"role\": \"user\","
                + "  \"parts\": ["
                + "    {\"inlineData\": {\"mimeType\": \"application/pdf\", \"data\": \"" + base64Pdf + "\"}},"
                + "    {\"text\": \"" + prompt + "\"}"
                + "  ]"
                + "}],"
                + "\"generationConfig\": {"
                + "  \"responseMimeType\": \"application/json\","
                + "  \"temperature\": 0.1"
                + "}"
                + "}";

        // 3. Send request to Gemini API
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpointUrl))
                .timeout(Duration.ofSeconds(60))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                break;
            }
            if ((response.statusCode() == 503 || response.statusCode() == 429) && attempt < 3) {
                System.out.printf("Gemini PDF test attempt %d returned HTTP %d. Retrying after backoff...\n", attempt, response.statusCode());
                Thread.sleep(2000L * attempt);
            }
        }
        long duration = System.currentTimeMillis() - start;

        System.out.println("Gemini PDF test response code: " + response.statusCode() + " (duration: " + duration + " ms)");

        if (response.statusCode() != 200) {
            System.err.println("Gemini PDF error body: " + response.body());
        }

        assertEquals(200, response.statusCode(), "Gemini API must return HTTP 200 for native PDF input: " + response.body());

        // 4. Verify candidate text
        Map<String, Object> root = SimpleJsonParser.parseObject(response.body());
        assertFalse(root.containsKey("error"), "Response should not contain error block: " + response.body());

        GeminiService service = new GeminiService(client, apiKey, configuredModel);
        AiAnalysisResult dummy = null; // Just test response extraction
        String rawContent = extractContentText(root);
        assertNotNull(rawContent, "Extracted text content from Gemini candidate should not be null");
        System.out.println("Gemini extracted JSON from native PDF:\n" + rawContent);

        // Verify the extracted values
        assertTrue(rawContent.contains("DATA STRUCTURES") || rawContent.contains("Data Structures"), "Should extract course name");
        assertTrue(rawContent.contains("UI25PC310CS"), "Should extract course code UI25PC310CS");
    }

    private String extractContentText(Map<String, Object> root) {
        Object candidatesObj = root.get("candidates");
        if (candidatesObj instanceof java.util.List<?> list && !list.isEmpty()) {
            if (list.get(0) instanceof Map<?, ?> cand) {
                if (cand.get("content") instanceof Map<?, ?> content) {
                    if (content.get("parts") instanceof java.util.List<?> parts && !parts.isEmpty()) {
                        for (Object p : parts) {
                            if (p instanceof Map<?, ?> pMap) {
                                Object text = pMap.get("text");
                                if (text != null && !text.toString().isBlank()) {
                                    return text.toString();
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
