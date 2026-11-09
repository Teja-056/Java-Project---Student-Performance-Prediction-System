package studentperformance.syllabus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import studentperformance.EnvConfig;
import studentperformance.GeminiService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import org.springframework.stereotype.Component;

/**
 * Production Gemini Syllabus Extraction Engine.
 * Uses Google Gemini API with native multimodal PDF ingestion (inlineData)
 * and Apache PDFBox for document inspection, structure analysis, and page boundary preservation.
 */
@Component
public class GeminiSyllabusExtractor {

    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_BATCH_PAGES = 15;
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_RETRY_BACKOFF_MS = 2500;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiSyllabusExtractor() {
        this(null, EnvConfig.get("GEMINI_API_KEY"), EnvConfig.get("GEMINI_MODEL", GeminiService.DEFAULT_MODEL));
    }

    public GeminiSyllabusExtractor(HttpClient client, String apiKey, String model) {
        this.httpClient = client != null ? client : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.apiKey = (apiKey == null || apiKey.isBlank()) ? null : apiKey.trim();
        this.model = normalizeModel(model);
        this.objectMapper = new ObjectMapper();
    }

    private static String normalizeModel(String raw) {
        if (raw == null || raw.isBlank()) return GeminiService.DEFAULT_MODEL;
        String m = raw.trim();
        if (m.startsWith("models/")) m = m.substring("models/".length());
        if (m.equalsIgnoreCase("gemini-1.5-flash") || m.equalsIgnoreCase("gemini-2.5-flash")) {
            return GeminiService.DEFAULT_MODEL;
        }
        return m;
    }

    public String getModel() { return model; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Extracts structured curriculum from a syllabus PDF.
     */
    public ExtractedCurriculum extract(Path pdfPath) throws IOException, InterruptedException {
        if (pdfPath == null) throw new IllegalArgumentException("PDF path cannot be null.");
        if (!Files.exists(pdfPath)) throw new IOException("PDF file does not exist: " + pdfPath);
        if (!Files.isRegularFile(pdfPath)) throw new IOException("Path is not a regular file: " + pdfPath);
        if (Files.size(pdfPath) == 0) throw new IOException("PDF file is empty (0 bytes): " + pdfPath);
        if (!pdfPath.toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("File must be a PDF document.");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured in the environment.");
        }

        long startTime = System.currentTimeMillis();

        try (PDDocument doc = loadPdfSafely(pdfPath)) {
            int totalPages = doc.getNumberOfPages();
            if (totalPages == 0) throw new IOException("The PDF document contains 0 pages.");

            ExtractedCurriculum curriculum;
            if (totalPages <= MAX_BATCH_PAGES) {
                // Process document in a single multimodal request
                byte[] pdfBytes = Files.readAllBytes(pdfPath);
                curriculum = extractPdfBatch(pdfBytes, 1, totalPages, totalPages);
            } else {
                // Discover relevant Semester III CSE sections and process in batches
                curriculum = extractBatchedDocument(doc, totalPages);
            }

            curriculum.setProcessedPages(totalPages);
            curriculum.setExtractionTimeMs(System.currentTimeMillis() - startTime);
            return curriculum;
        }
    }

    /**
     * Extracts structured curriculum from raw PDF bytes with a given filename and known page offset.
     */
    public ExtractedCurriculum extractFromBytes(byte[] pdfBytes, String filename) throws IOException, InterruptedException {
        if (pdfBytes == null || pdfBytes.length == 0) throw new IllegalArgumentException("PDF bytes cannot be empty.");
        if (!isConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured in the environment.");
        }

        long startTime = System.currentTimeMillis();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int totalPages = doc.getNumberOfPages();
            if (totalPages == 0) throw new IOException("The PDF document contains 0 pages.");

            ExtractedCurriculum curriculum;
            if (totalPages <= MAX_BATCH_PAGES) {
                curriculum = extractPdfBatch(pdfBytes, 1, totalPages, totalPages);
            } else {
                curriculum = extractBatchedDocument(doc, totalPages);
            }

            curriculum.setProcessedPages(totalPages);
            curriculum.setExtractionTimeMs(System.currentTimeMillis() - startTime);
            return curriculum;
        }
    }

    /**
     * Produces an extraction verification report comparing extracted data against official expectations.
     */
    public ExtractionVerificationReport generateVerificationReport(String filename, ExtractedCurriculum curriculum) {
        ExtractionVerificationReport report = new ExtractionVerificationReport();
        report.setFilename(filename);
        report.setTotalPages(curriculum.getProcessedPages());
        report.setTotalSubjects(curriculum.getSubjects().size());
        report.setTotalUnits(curriculum.getTotalUnitsCount());
        report.setTotalTopics(curriculum.getTotalTopicsCount());
        report.setProcessingDurationMs(curriculum.getExtractionTimeMs());
        report.setModelName(model);
        report.setGlobalWarnings(new ArrayList<>(curriculum.getWarnings()));

        for (ExtractedSubject s : curriculum.getSubjects()) {
            List<Integer> unitNums = s.getUnits().stream().map(ExtractedUnit::getUnitNumber).toList();
            List<String> missingUnits = findMissingUnits(s);
            List<String> duplicateTopics = findDuplicateTopics(s);

            String pageRange = (s.getStartPage() != null ? String.valueOf(s.getStartPage()) : "?")
                    + (s.getEndPage() != null && !s.getEndPage().equals(s.getStartPage()) ? "-" + s.getEndPage() : "");

            int topicCount = s.getUnits().stream().mapToInt(u -> u.getTopics().size()).sum();

            report.getSubjectEntries().add(new ExtractionVerificationReport.SubjectVerificationEntry(
                    s.getSubjectName(),
                    s.getCourseCode(),
                    s.getCredits(),
                    s.getCategory(),
                    pageRange,
                    s.getUnits().size(),
                    topicCount,
                    unitNums,
                    missingUnits,
                    duplicateTopics,
                    new ArrayList<>(s.getUncertainFields()),
                    new ArrayList<>(s.getWarnings())
            ));
        }

        // Compare against official Semester III CSE catalog
        verifyAgainstOfficialCatalog(curriculum, report);
        return report;
    }

    private void verifyAgainstOfficialCatalog(ExtractedCurriculum curriculum, ExtractionVerificationReport report) {
        // Expected Semester III CSE subjects:
        // 1. Data Structures (4 credits, UI25PC310CS)
        // 2. OOP through Java (3 credits, UI25PC320CS)
        // 3. Computer Architecture (3 credits, UI25PC330CS)
        // 4. TTPS (4 credits, U25BS340MA)
        // 5. Open Elective-I (2 credits, U25OE3XXXX - unresolved)
        // 6. Critical Thinking (1 credit, U25HS310EH)
        // 7. Human Values and Professional Ethics-II (1 credit, U25HS030EH)
        // 8. Communication Skills-I (1 credit, U25HS320EH)

        Map<String, String> expectedCodes = Map.of(
                "DATA STRUCTURES", "UI25PC310CS",
                "OBJECT ORIENTED PROGRAMMING THROUGH JAVA", "UI25PC320CS",
                "COMPUTER ARCHITECTURE", "UI25PC330CS",
                "CRITICAL THINKING", "U25HS310EH",
                "HUMAN VALUES AND PROFESSIONAL ETHICS-II", "U25HS030EH",
                "SKILL DEVELOPMENT COURSE-III – COMMUNICATION SKILLS-I", "U25HS320EH"
        );

        for (ExtractedSubject s : curriculum.getSubjects()) {
            String nameUpper = s.getSubjectName().toUpperCase(Locale.ROOT).trim();
            for (var entry : expectedCodes.entrySet()) {
                if (nameUpper.contains(entry.getKey()) || entry.getKey().contains(nameUpper)) {
                    if (!s.getCourseCode().isBlank() && !s.getCourseCode().equalsIgnoreCase(entry.getValue())) {
                        report.getDiscrepancies().add(String.format(
                                "Course code discrepancy for '%s': extracted '%s', official syllabus lists '%s'",
                                s.getSubjectName(), s.getCourseCode(), entry.getValue()
                        ));
                    }
                }
            }
        }
    }

    private List<String> findMissingUnits(ExtractedSubject subject) {
        List<String> missing = new ArrayList<>();
        if (subject.isOpenElective()) return missing; // OE-I is unresolved by design

        Set<Integer> present = new HashSet<>(subject.getUnits().stream().map(ExtractedUnit::getUnitNumber).toList());
        String name = subject.getSubjectName().toUpperCase(Locale.ROOT);
        int expectedMax = 5;
        if (name.contains("HUMAN VALUES") || name.contains("HVPE")) {
            expectedMax = 3;
        } else if (name.contains("CRITICAL THINKING") || name.contains("COMMUNICATION")) {
            expectedMax = 4;
        }

        for (int i = 1; i <= expectedMax; i++) {
            if (!present.contains(i)) {
                missing.add("Unit " + i);
            }
        }
        return missing;
    }

    private List<String> findDuplicateTopics(ExtractedSubject subject) {
        List<String> duplicates = new ArrayList<>();
        for (ExtractedUnit u : subject.getUnits()) {
            Set<String> seen = new HashSet<>();
            for (ExtractedTopic t : u.getTopics()) {
                String norm = t.getTopicText().trim().toLowerCase(Locale.ROOT);
                if (!norm.isBlank() && !seen.add(norm)) {
                    duplicates.add("Unit " + u.getUnitNumber() + ": \"" + t.getTopicText() + "\"");
                }
            }
        }
        return duplicates;
    }

    private PDDocument loadPdfSafely(Path pdfPath) throws IOException {
        try {
            return Loader.loadPDF(pdfPath.toFile());
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
            throw new IOException("The PDF is password-protected and cannot be read.", ex);
        } catch (IOException ex) {
            throw new IOException("Failed to load PDF document: " + ex.getMessage(), ex);
        }
    }

    private ExtractedCurriculum extractBatchedDocument(PDDocument doc, int totalPages) throws IOException, InterruptedException {
        // Discovery: scan text page by page to find Semester III CSE relevant pages
        PDFTextStripper stripper = new PDFTextStripper();
        List<Integer> relevantPages = new ArrayList<>();

        for (int p = 1; p <= totalPages; p++) {
            stripper.setStartPage(p);
            stripper.setEndPage(p);
            String pageText = stripper.getText(doc).toUpperCase(Locale.ROOT);
            if (isSemester3CsePage(pageText)) {
                relevantPages.add(p);
            }
        }

        if (relevantPages.isEmpty()) {
            // If no specific page matched the filter, process the first 15 pages
            for (int p = 1; p <= Math.min(totalPages, MAX_BATCH_PAGES); p++) relevantPages.add(p);
        }

        // Group into contiguous blocks <= MAX_BATCH_PAGES
        List<List<Integer>> batches = groupPagesIntoBatches(relevantPages, MAX_BATCH_PAGES);
        ExtractedCurriculum combined = new ExtractedCurriculum();

        for (List<Integer> batch : batches) {
            int startPage = batch.get(0);
            int endPage = batch.get(batch.size() - 1);

            byte[] batchPdf = extractPagesToPdfBytes(doc, batch);
            ExtractedCurriculum batchResult = extractPdfBatch(batchPdf, startPage, endPage, totalPages);

            if (combined.getDocument().getInstitution().isBlank()) {
                combined.setDocument(batchResult.getDocument());
            }

            for (ExtractedSubject sub : batchResult.getSubjects()) {
                // Adjust unit and topic page numbers to match the true original PDF page numbers
                adjustPageNumbers(sub, batch);
                mergeOrAddSubject(combined, sub);
            }
            combined.getWarnings().addAll(batchResult.getWarnings());
        }

        return combined;
    }

    private void adjustPageNumbers(ExtractedSubject sub, List<Integer> batchPages) {
        if (sub.getStartPage() != null && sub.getStartPage() >= 1 && sub.getStartPage() <= batchPages.size()) {
            sub.setStartPage(batchPages.get(sub.getStartPage() - 1));
        }
        if (sub.getEndPage() != null && sub.getEndPage() >= 1 && sub.getEndPage() <= batchPages.size()) {
            sub.setEndPage(batchPages.get(sub.getEndPage() - 1));
        }
        for (ExtractedUnit u : sub.getUnits()) {
            if (u.getPageNumber() != null && u.getPageNumber() >= 1 && u.getPageNumber() <= batchPages.size()) {
                u.setPageNumber(batchPages.get(u.getPageNumber() - 1));
            }
            for (ExtractedTopic t : u.getTopics()) {
                if (t.getPageNumber() != null && t.getPageNumber() >= 1 && t.getPageNumber() <= batchPages.size()) {
                    t.setPageNumber(batchPages.get(t.getPageNumber() - 1));
                }
            }
        }
    }

    private void mergeOrAddSubject(ExtractedCurriculum target, ExtractedSubject newSub) {
        for (ExtractedSubject existing : target.getSubjects()) {
            if (existing.getSubjectName().equalsIgnoreCase(newSub.getSubjectName())
                    || (!existing.getCourseCode().isBlank() && existing.getCourseCode().equalsIgnoreCase(newSub.getCourseCode()))) {
                // Merge units into existing
                for (ExtractedUnit newUnit : newSub.getUnits()) {
                    ExtractedUnit existingUnit = existing.getUnits().stream()
                            .filter(u -> u.getUnitNumber() == newUnit.getUnitNumber())
                            .findFirst().orElse(null);
                    if (existingUnit == null) {
                        existing.getUnits().add(newUnit);
                    } else {
                        // Merge topics
                        for (ExtractedTopic t : newUnit.getTopics()) {
                            boolean exists = existingUnit.getTopics().stream()
                                    .anyMatch(et -> et.getTopicText().trim().equalsIgnoreCase(t.getTopicText().trim()));
                            if (!exists) existingUnit.getTopics().add(t);
                        }
                    }
                }
                return;
            }
        }
        target.getSubjects().add(newSub);
    }

    private byte[] extractPagesToPdfBytes(PDDocument source, List<Integer> pageNumbers) throws IOException {
        try (PDDocument subDoc = new PDDocument()) {
            for (int p : pageNumbers) {
                PDPage page = source.getPage(p - 1);
                subDoc.addPage(page);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            subDoc.save(baos);
            return baos.toByteArray();
        }
    }

    private List<List<Integer>> groupPagesIntoBatches(List<Integer> pages, int maxBatchSize) {
        List<List<Integer>> batches = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (int p : pages) {
            if (current.size() >= maxBatchSize) {
                batches.add(current);
                current = new ArrayList<>();
            }
            current.add(p);
        }
        if (!current.isEmpty()) batches.add(current);
        return batches;
    }

    private boolean isSemester3CsePage(String text) {
        boolean sem3 = text.contains("III-SEMESTER") || text.contains("III SEMESTER") || text.contains("SEMESTER III")
                || text.contains("3RD SEMESTER") || text.contains("THIRD SEMESTER") || text.contains("SEMESTER-III");
        boolean subjectMatch = text.contains("DATA STRUCTURE") || text.contains("OBJECT ORIENTED")
                || text.contains("COMPUTER ARCHITECTURE") || text.contains("TRANSFORM")
                || text.contains("PROBABILITY") || text.contains("CRITICAL THINKING")
                || text.contains("HUMAN VALUES") || text.contains("COMMUNICATION SKILLS")
                || text.contains("OPEN ELECTIVE");
        return (sem3 && subjectMatch) || (text.contains("CSE") && subjectMatch);
    }

    /**
     * Sends a PDF batch to Gemini and parses the structured curriculum JSON.
     */
    private ExtractedCurriculum extractPdfBatch(byte[] pdfBytes, int startPage, int endPage, int docTotalPages)
            throws IOException, InterruptedException {
        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);
        String prompt = buildPrompt(startPage, endPage, docTotalPages);
        String requestBody = buildRequestBody(base64Pdf, prompt);

        String endpointUrl = API_BASE_URL + model + ":generateContent";

        String responseBody = executeWithRetry(endpointUrl, requestBody);
        return parseGeminiResponse(responseBody, startPage, endPage);
    }

    private String buildPrompt(int startPage, int endPage, int docTotalPages) {
        return """
                You are an authoritative academic curriculum extraction engine.
                Analyze the provided syllabus PDF document (pages %d to %d of %d).
                Extract the exact, structured curriculum information into JSON conforming strictly to the schema below.
                
                EXTRACTION RULES:
                1. INSTITUTION & METADATA: Extract institution name, academic scheme (e.g. R-25), academic year, branch (e.g. CSE), and semester (e.g. III).
                2. SUBJECTS:
                   Focus exclusively on the 8 Semester III CSE subjects:
                   - Data Structures
                   - Object Oriented Programming through Java
                   - Computer Architecture
                   - Transform Techniques, Probability & Statistics (or TTPS)
                   - Open Elective-I
                   - Critical Thinking
                   - Human Values and Professional Ethics-II
                   - Skill Development Course-III: Communication Skills-I
                   DO NOT include practical laboratories, technical skills SDC coding labs, environmental science, or NPTEL courses.
                3. OPEN ELECTIVE-I RULE:
                   Open Elective-I is a choice-based elective and the student's selected elective is not yet confirmed.
                   Extract Open Elective-I with credits=2, category="OPEN_ELECTIVE", and units=[] (EMPTY ARRAY). Do not invent an elective syllabus.
                4. EXACT TOPIC VERBATIM PRESERVATION:
                   Extract the exact topic wording and bullet points as written in the document.
                   DO NOT summarize, paraphrase, merge, or abbreviate topics.
                5. UNITS INTEGRITY:
                   Do not invent missing units. If a subject has 3 units or 4 units, extract only those units with unitNumber (1, 2, 3...).
                6. PAGE NUMBER ACCURACY:
                   For each unit and topic, specify the 1-based page number within this PDF where it appears.
                   If uncertain, set pageNumber to null.
                7. UNCERTAIN FIELDS:
                   If a course code, credit count, or unit title is ambiguous, add the field name to 'uncertainFields'.
                
                REQUIRED JSON SCHEMA:
                {
                  "document": {
                    "institution": "string",
                    "academicScheme": "string",
                    "academicYear": "string",
                    "branch": "string",
                    "semester": "string"
                  },
                  "subjects": [
                    {
                      "subjectName": "string",
                      "courseCode": "string",
                      "credits": "string",
                      "category": "THEORY" | "OPEN_ELECTIVE" | "SKILL_DEVELOPMENT",
                      "semester": "string",
                      "startPage": number or null,
                      "endPage": number or null,
                      "units": [
                        {
                          "unitNumber": number,
                          "unitTitle": "string",
                          "pageNumber": number or null,
                          "topics": [
                            {
                              "topicText": "string",
                              "pageNumber": number or null,
                              "subtopics": []
                            }
                          ]
                        }
                      ],
                      "uncertainFields": []
                    }
                  ]
                }
                """.formatted(startPage, endPage, docTotalPages);
    }

    private String buildRequestBody(String base64Pdf, String prompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"contents\":[{");
        sb.append("\"role\":\"user\",");
        sb.append("\"parts\":[");
        sb.append("{\"inlineData\":{\"mimeType\":\"application/pdf\",\"data\":\"").append(base64Pdf).append("\"}},");
        sb.append("{\"text\":\"").append(escapeJsonString(prompt)).append("\"}");
        sb.append("]");
        sb.append("}],");
        sb.append("\"generationConfig\":{");
        sb.append("\"responseMimeType\":\"application/json\",");
        sb.append("\"temperature\":0.1");
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    private String executeWithRetry(String endpointUrl, String requestBody) throws IOException, InterruptedException {
        int attempt = 0;
        long backoff = INITIAL_RETRY_BACKOFF_MS;

        while (true) {
            attempt++;
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) {
                return response.body();
            }

            // Retryable errors: 429 (Rate Limit) or 503 (Service Unavailable)
            boolean retryable = (status == 429 || status == 503 || status == 500);
            if (retryable && attempt < MAX_RETRIES) {
                System.out.printf("[GeminiSyllabusExtractor] HTTP %d encountered on attempt %d. Backing off for %d ms...\n",
                        status, attempt, backoff);
                Thread.sleep(backoff);
                backoff *= 2;
                continue;
            }

            String errorMsg = extractErrorMessage(response.body());
            if (status == 429) {
                throw new IOException("Gemini API rate limit exceeded after " + attempt + " attempts. Please retry shortly.");
            } else if (status == 401 || status == 403) {
                throw new IOException("Gemini API authentication failed (HTTP " + status + "). Verify GEMINI_API_KEY.");
            } else if (status == 404) {
                throw new IOException("Gemini model '" + model + "' not found (HTTP 404).");
            } else {
                throw new IOException("Gemini API returned HTTP " + status + ": " + errorMsg);
            }
        }
    }

    private ExtractedCurriculum parseGeminiResponse(String responseJson, int startPage, int endPage) throws IOException {
        JsonNode root = objectMapper.readTree(responseJson);

        if (root.has("error")) {
            throw new IOException("Gemini API returned an error: " + root.get("error").path("message").asText());
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IOException("No candidate responses returned by Gemini API.");
        }

        JsonNode firstCand = candidates.get(0);
        String finishReason = firstCand.path("finishReason").asText("");
        if ("SAFETY".equals(finishReason)) {
            throw new IOException("Gemini response was blocked by safety filters.");
        }

        JsonNode parts = firstCand.path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IOException("No content parts found in Gemini candidate response.");
        }

        // Locate text part (skipping thought parts)
        String rawText = null;
        for (JsonNode part : parts) {
            if (!part.path("thought").asBoolean(false) && part.has("text")) {
                rawText = part.get("text").asText();
                break;
            }
        }

        if (rawText == null || rawText.isBlank()) {
            throw new IOException("Empty text received from Gemini candidate.");
        }

        // Parse extracted curriculum JSON
        JsonNode curriculumNode = objectMapper.readTree(rawText);
        ExtractedCurriculum curriculum = new ExtractedCurriculum();
        curriculum.setRawJsonResponse(rawText);

        // Metadata
        JsonNode docNode = curriculumNode.path("document");
        if (!docNode.isMissingNode()) {
            curriculum.setDocument(new ExtractedDocumentMetadata(
                    docNode.path("institution").asText(""),
                    docNode.path("academicScheme").asText(""),
                    docNode.path("academicYear").asText(""),
                    docNode.path("branch").asText(""),
                    docNode.path("semester").asText("")
            ));
        }

        // Subjects
        JsonNode subjectsNode = curriculumNode.path("subjects");
        if (subjectsNode.isArray()) {
            for (JsonNode sNode : subjectsNode) {
                ExtractedSubject subject = new ExtractedSubject();
                subject.setSubjectName(sNode.path("subjectName").asText(""));
                subject.setCourseCode(sNode.path("courseCode").asText(""));
                subject.setCredits(sNode.path("credits").asText(""));
                subject.setCategory(sNode.path("category").asText("THEORY"));
                subject.setSemester(sNode.path("semester").asText("III"));

                if (sNode.hasNonNull("startPage")) subject.setStartPage(sNode.get("startPage").asInt());
                if (sNode.hasNonNull("endPage")) subject.setEndPage(sNode.get("endPage").asInt());

                // Uncertain fields
                JsonNode uncert = sNode.path("uncertainFields");
                if (uncert.isArray()) {
                    for (JsonNode u : uncert) subject.getUncertainFields().add(u.asText());
                }

                // Units
                JsonNode unitsNode = sNode.path("units");
                if (unitsNode.isArray()) {
                    for (JsonNode uNode : unitsNode) {
                        ExtractedUnit unit = new ExtractedUnit();
                        unit.setUnitNumber(uNode.path("unitNumber").asInt(1));
                        unit.setUnitTitle(uNode.path("unitTitle").asText(""));
                        if (uNode.hasNonNull("pageNumber")) unit.setPageNumber(uNode.get("pageNumber").asInt());

                        // Topics
                        JsonNode topicsNode = uNode.path("topics");
                        if (topicsNode.isArray()) {
                            for (JsonNode tNode : topicsNode) {
                                if (tNode.isObject()) {
                                    ExtractedTopic topic = new ExtractedTopic(tNode.path("topicText").asText(""));
                                    if (tNode.hasNonNull("pageNumber")) topic.setPageNumber(tNode.get("pageNumber").asInt());
                                    unit.getTopics().add(topic);
                                } else if (tNode.isTextual()) {
                                    unit.getTopics().add(new ExtractedTopic(tNode.asText(), unit.getPageNumber()));
                                }
                            }
                        }
                        subject.getUnits().add(unit);
                    }
                }

                if (subject.getSubjectName().toUpperCase(Locale.ROOT).contains("OPEN ELECTIVE")) {
                    subject.setOpenElective(true);
                    subject.setCategory("OPEN_ELECTIVE");
                }

                curriculum.getSubjects().add(subject);
            }
        }

        return curriculum;
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) return "Unknown error";
        try {
            JsonNode root = objectMapper.readTree(body);
            String msg = root.path("error").path("message").asText();
            if (!msg.isBlank()) return msg;
        } catch (Exception ignored) {
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private static String escapeJsonString(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
