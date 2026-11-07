package studentperformance;

import java.io.IOException;
import java.util.*;

/**
 * Shared prompt builder and structured response parser for AI providers (Google Gemini & OpenAI).
 * Strict architectural rule: Java retains 100% deterministic authority over numbers and marks.
 * Student PII (name, roll number) is strictly scrubbed.
 */
public final class AiPromptBuilder {

    private AiPromptBuilder() {}

    public static String buildSystemPrompt() {
        return "You are an expert academic advisor and AI educational diagnostic system. "
                + "You evaluate student performance data deterministically calculated by Java and provide constructive, "
                + "actionable, and realistic guidance. "
                + "RULES:\n"
                + "1. Do NOT fabricate or alter numerical scores, percentages, or GPA values.\n"
                + "2. Distinguish observed facts from estimates. Future performance forecasts MUST explicitly state uncertainty.\n"
                + "3. Provide practical, high-impact study recommendations.\n"
                + "4. Return ONLY a valid JSON object adhering strictly to the requested schema.";
    }

    public static String buildAnonymizedPrompt(Student student, PerformanceAnalyzer analyzer, List<SyllabusDocument> syllabuses) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please analyze the following anonymized academic performance profile:\n\n");
        sb.append("ACADEMIC METRICS:\n");
        sb.append(String.format(Locale.ROOT, "- Current CGPA: %.2f / 10.0\n", student.getCgpa()));
        sb.append(String.format(Locale.ROOT, "- Previous Semester SGPA: %.2f / 10.0\n", student.getLastSemesterSgpa()));
        sb.append(String.format(Locale.ROOT, "- Internal 1 Total Percentage: %.2f%%\n", analyzer.percentage(student, 1)));
        sb.append(String.format(Locale.ROOT, "- Internal 2 Total Percentage: %.2f%%\n", analyzer.percentage(student, 2)));
        sb.append(String.format(Locale.ROOT, "- Combined Internal Percentage: %.2f%%\n", analyzer.currentInternalPercentage(student)));
        sb.append(String.format(Locale.ROOT, "- Deterministic Category: %s\n", analyzer.prediction(student)));

        Subject strongest = analyzer.strongestSubject(student);
        Subject weakest = analyzer.weakestSubject(student);
        sb.append(String.format(Locale.ROOT, "- Strongest Subject: %s\n", strongest == null ? "None" : strongest.getDisplayName()));
        sb.append(String.format(Locale.ROOT, "- Weakest Subject: %s\n", weakest == null ? "None" : weakest.getDisplayName()));

        sb.append("\nSUBJECT-WISE MARKS & TRENDS:\n");
        for (Subject s : Subject.values()) {
            double p1 = analyzer.subjectPercentage(student, 1, s);
            double p2 = analyzer.subjectPercentage(student, 2, s);
            double diff = analyzer.subjectChange(student, s);
            double total1 = student.getMarks(1).get(s).totalMarks();
            double total2 = student.getMarks(2).get(s).totalMarks();
            sb.append(String.format(Locale.ROOT, "  * %s (Max %d): Int1=%.1f (%.1f%%), Int2=%.1f (%.1f%%), Trend=%+.1f%%\n",
                    s.getDisplayName(), s.getMaximumTotalMarks(), total1, p1, total2, p2, diff));
        }

        // Add syllabus context if available
        if (syllabuses != null && !syllabuses.isEmpty()) {
            sb.append("\nCOLLEGE SYLLABUS TOPIC CONTEXT (Vasavi College):\n");
            for (SyllabusDocument doc : syllabuses) {
                for (SyllabusSubject subj : doc.getSubjects()) {
                    if (!subj.getUnits().isEmpty()) {
                        sb.append("  Subject: ").append(subj.getName()).append(":\n");
                        for (SyllabusUnit u : subj.getUnits()) {
                            sb.append("    Unit ").append(u.getNumber()).append(" (").append(u.getTitle()).append("): ");
                            List<String> topicSnippets = u.getTopics().stream().limit(3).map(SyllabusTopic::getText).toList();
                            sb.append(String.join(", ", topicSnippets)).append("\n");
                        }
                    }
                }
            }
        }

        sb.append("\nREQUIRED JSON OUTPUT SCHEMA:\n");
        sb.append("{\n");
        sb.append("  \"academic_summary\": \"Comprehensive summary of observed performance\",\n");
        sb.append("  \"strengths\": [\"Strength 1\", \"Strength 2\"],\n");
        sb.append("  \"weaknesses\": [\"Weakness 1\", \"Weakness 2\"],\n");
        sb.append("  \"subjects_requiring_improvement\": [\"Subject 1\", \"Subject 2\"],\n");
        sb.append("  \"trend_analysis\": \"Analysis of performance shifts between Internal 1 and Internal 2\",\n");
        sb.append("  \"estimated_future_performance\": \"Estimated forecast with clearly stated uncertainty\",\n");
        sb.append("  \"risk_level\": \"LOW\" | \"MEDIUM\" | \"HIGH\",\n");
        sb.append("  \"risk_factors\": [\"Factor 1\", \"Factor 2\"],\n");
        sb.append("  \"recommended_interventions\": [\"Intervention 1\", \"Intervention 2\"],\n");
        sb.append("  \"suggested_daily_study_hours\": 3,\n");
        sb.append("  \"subject_wise_improvement_plan\": {\"SubjectName\": \"Actionable plan\"},\n");
        sb.append("  \"revision_strategies\": [\"Strategy 1\", \"Strategy 2\"],\n");
        sb.append("  \"focus_topics\": [\"Topic 1\", \"Topic 2\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    public static String cleanJsonFence(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```json")) {
            s = s.substring(7);
        } else if (s.startsWith("```JSON")) {
            s = s.substring(7);
        } else if (s.startsWith("```")) {
            s = s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    public static AiAnalysisResult parseStructuredJson(String jsonContent) throws IOException {
        String clean = cleanJsonFence(jsonContent);
        Map<String, Object> parsed = SimpleJsonParser.parseObject(clean);

        String summary = getString(parsed, "academic_summary", "Academic performance evaluated.");
        List<String> strengths = getStringList(parsed, "strengths");
        List<String> weaknesses = getStringList(parsed, "weaknesses");
        List<String> improvementSubjects = getStringList(parsed, "subjects_requiring_improvement");
        String trend = getString(parsed, "trend_analysis", "Performance trends analyzed.");
        String forecast = getString(parsed, "estimated_future_performance", "Forecast subject to ongoing effort.");
        String risk = getString(parsed, "risk_level", "MEDIUM");
        List<String> riskFactors = getStringList(parsed, "risk_factors");
        List<String> interventions = getStringList(parsed, "recommended_interventions");

        int hours = 3;
        Object hObj = parsed.get("suggested_daily_study_hours");
        if (hObj instanceof Number n) hours = n.intValue();

        Map<String, String> planMap = new LinkedHashMap<>();
        Object planObj = parsed.get("subject_wise_improvement_plan");
        if (planObj instanceof Map<?, ?> map) {
            map.forEach((k, v) -> planMap.put(String.valueOf(k), String.valueOf(v)));
        }

        List<String> strategies = getStringList(parsed, "revision_strategies");
        List<String> focusTopics = getStringList(parsed, "focus_topics");

        return new AiAnalysisResult(summary, strengths, weaknesses, improvementSubjects,
                trend, forecast, risk, riskFactors, interventions, hours, planMap, strategies, focusTopics);
    }

    public static String getString(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return (val == null || val.toString().isBlank()) ? fallback : val.toString().trim();
    }

    public static List<String> getStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) result.add(item.toString().trim());
            }
            return result;
        }
        return List.of();
    }

    public static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
