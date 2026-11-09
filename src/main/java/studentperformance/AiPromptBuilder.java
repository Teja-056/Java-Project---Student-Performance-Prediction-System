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
        return "You are an expert academic diagnostic advisor for Vasavi College of Engineering (Autonomous). "
                + "You evaluate student internal performance data deterministically calculated by the Java backend and provide constructive, personalized, and syllabus-grounded academic analysis.\n"
                + "STRICT CONSTRAINTS & GROUNDING RULES:\n"
                + "1. DATA INTEGRITY: Do NOT invent, alter, or round numerical scores, marks, percentages, or SGPA/CGPA values. Only reference the exact numbers provided.\n"
                + "2. SUBJECT CODES & NAMES: CS stands for 'Communication Skills-I' (Technical English, presentation, professional writing, listening/speaking skills). It is NEVER Computer Science or Cyber Security. Do NOT mention cryptography, network security, or malware for CS.\n"
                + "3. SYLLABUS GROUNDING RULES: Under 'VERIFIED COLLEGE SYLLABUS', only subjects with approved syllabus text have verified topics. For subjects explicitly marked 'No approved syllabus is available for this subject', YOU MUST NOT invent, assume, or hallucinate syllabus topics. Explicitly state in the report: 'No approved syllabus is available for this subject; recommendations are based on internal exam performance trends.'\n"
                + "4. NO CROSS-SUBJECT CONTAMINATION: Never associate syllabus topics from one subject (e.g. Data Structures trees/graphs) with any other subject (e.g. Java, CA, or CS).\n"
                + "5. CITATION: When recommending focus topics or remedial study actions, cite the verified source unit (e.g., '[Data Structures: Unit 3 - Trees]').\n"
                + "6. NO STUDY HOURS: Do NOT recommend or mention daily study hours.\n"
                + "7. ACTIONABLE & SPECIFIC: Avoid generic clichés like 'study regularly' or 'work hard'. Provide specific, topic-targeted remedial plans grounded in the actual marks and trends.\n"
                + "8. OUTPUT FORMAT: Return ONLY a valid JSON object matching the requested schema.";
    }

    public static String buildAnonymizedPrompt(Student student, PerformanceAnalyzer analyzer, List<SyllabusDocument> syllabuses) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please analyze the following anonymized academic performance profile for a Semester III student in B.E. Computer Science and Engineering:\n\n");
        sb.append("OVERALL ACADEMIC METRICS:\n");
        sb.append(String.format(Locale.ROOT, "- Current CGPA: %.2f / 10.0\n", student.getCgpa()));
        sb.append(String.format(Locale.ROOT, "- Previous Semester SGPA: %.2f / 10.0\n", student.getLastSemesterSgpa()));
        sb.append(String.format(Locale.ROOT, "- Internal 1 Total Percentage: %.2f%%\n", analyzer.percentage(student, 1)));
        sb.append(String.format(Locale.ROOT, "- Internal 2 Total Percentage: %.2f%%\n", analyzer.percentage(student, 2)));
        sb.append(String.format(Locale.ROOT, "- Combined Internal Percentage: %.2f%%\n", analyzer.currentInternalPercentage(student)));
        sb.append(String.format(Locale.ROOT, "- Faculty Classification Status: %s\n", analyzer.prediction(student)));

        Subject strongest = analyzer.strongestSubject(student);
        Subject weakest = analyzer.weakestSubject(student);
        sb.append(String.format(Locale.ROOT, "- Strongest CIE Subject: %s (%s)\n",
                strongest == null ? "None" : strongest.getDisplayName(),
                strongest == null ? "" : strongest.getFullName()));
        sb.append(String.format(Locale.ROOT, "- Weakest CIE Subject: %s (%s)\n",
                weakest == null ? "None" : weakest.getDisplayName(),
                weakest == null ? "" : weakest.getFullName()));

        List<String> improvingList = new ArrayList<>();
        List<String> decliningList = new ArrayList<>();
        sb.append("\nSUBJECT-WISE INTERNAL ASSESSMENT METRICS (Exact 8 Semester III Courses):\n");
        for (Subject s : Subject.values()) {
            double p1 = analyzer.subjectPercentage(student, 1, s);
            double p2 = analyzer.subjectPercentage(student, 2, s);
            double diff = analyzer.subjectChange(student, s);
            double total1 = student.getMarks(1).get(s).totalMarks();
            double total2 = student.getMarks(2).get(s).totalMarks();

            if (diff > 0.0) {
                improvingList.add(String.format(Locale.ROOT, "%s (%s): %+.1f%%", s.getDisplayName(), s.getFullName(), diff));
            } else if (diff < 0.0) {
                decliningList.add(String.format(Locale.ROOT, "%s (%s): %+.1f%%", s.getDisplayName(), s.getFullName(), diff));
            }

            sb.append(String.format(Locale.ROOT, "  * [%s] %s (%s) [Max CIE: %d, Credits: %d]: Internal 1 = %.1f (%.1f%%), Internal 2 = %.1f (%.1f%%), Trend = %+.1f%%\n",
                    s.getCourseCode(), s.getDisplayName(), s.getFullName(), s.getCieMaxMarks(), s.getCredits(), total1, p1, total2, p2, diff));
        }

        sb.append(String.format(Locale.ROOT, "\nPERFORMANCE TRAJECTORY SUMMARY:\n- Subjects showing improvement: %s\n- Subjects showing decline: %s\n",
                improvingList.isEmpty() ? "None" : String.join("; ", improvingList),
                decliningList.isEmpty() ? "None" : String.join("; ", decliningList)));

        // Organize approved syllabuses strictly by matched subject
        Map<Subject, List<SyllabusUnit>> approvedUnitsBySubject = new EnumMap<>(Subject.class);
        Map<Subject, String> sourceDocBySubject = new EnumMap<>(Subject.class);
        if (syllabuses != null) {
            for (SyllabusDocument doc : syllabuses) {
                for (SyllabusSubject subj : doc.getSubjects()) {
                    Subject matched = subj.getMatchedSemesterSubject();
                    if (matched != null && !subj.getUnits().isEmpty()) {
                        approvedUnitsBySubject.put(matched, subj.getUnits());
                        sourceDocBySubject.put(matched, doc.getFilename());
                    }
                }
            }
        }

        sb.append("\nOFFICIAL COLLEGE SYLLABUS GROUNDING (Vasavi College of Engineering):\n");
        sb.append("CRITICAL: You must strictly adhere to the verified syllabus records below. Do NOT invent syllabus units or topics for subjects that have no approved syllabus available.\n");
        for (Subject s : Subject.values()) {
            List<SyllabusUnit> units = approvedUnitsBySubject.get(s);
            if (units != null && !units.isEmpty()) {
                String docName = sourceDocBySubject.getOrDefault(s, "college_syllabus.pdf");
                sb.append(String.format(Locale.ROOT, "  [APPROVED SYLLABUS AVAILABLE] %s (%s - %s) [Source: %s]:\n",
                        s.getDisplayName(), s.getFullName(), s.getCourseCode(), docName));
                for (SyllabusUnit u : units) {
                    List<String> topicSnippets = u.getTopics().stream().limit(4).map(SyllabusTopic::getText).toList();
                    sb.append(String.format(Locale.ROOT, "    - Unit %d: %s -> Topics: %s\n",
                            u.getNumber(), u.getTitle(), String.join(", ", topicSnippets)));
                }
            } else {
                sb.append(String.format(Locale.ROOT, "  [NO APPROVED SYLLABUS AVAILABLE] %s (%s - %s): No approved syllabus is available for this subject. Do NOT invent syllabus topics for this subject; base advice solely on marks and internal trends.\n",
                        s.getDisplayName(), s.getFullName(), s.getCourseCode()));
            }
        }

        sb.append("\nGROUNDING & NAMING REQUIREMENTS:\n");
        sb.append("- Subject 'CS' is STRICTLY 'Communication Skills-I' (verbal presentations, technical writing, professional communication). It is NOT Computer Security or Cyber Security. Never mention cryptography or network defense for CS.\n");
        sb.append("- Only cite syllabus topics that are explicitly listed under [APPROVED SYLLABUS AVAILABLE] above.\n");
        sb.append("- When recommending focus topics, prefix with the subject name and unit, e.g., '[Data Structures: Unit 4 - Graphs]'.\n");
        sb.append("- Do NOT include any daily study hours.\n");

        sb.append("\nREQUIRED JSON OUTPUT SCHEMA:\n");
        sb.append("{\n");
        sb.append("  \"academic_summary\": \"Thorough executive summary of observed performance, internal marks, and semester standing\",\n");
        sb.append("  \"strengths\": [\"Detailed strength citing subject name and specific metric\", \"...\"],\n");
        sb.append("  \"weaknesses\": [\"Detailed weakness citing subject name and trend/mark drop\", \"...\"],\n");
        sb.append("  \"subjects_requiring_improvement\": [\"SubjectName (metric)\", \"...\"],\n");
        sb.append("  \"trend_analysis\": \"Comprehensive analysis of score shifts between Internal 1 and Internal 2, detailing improving vs declining subjects\",\n");
        sb.append("  \"estimated_future_performance\": \"Estimated examination prognosis with explicitly stated uncertainty and reliance on addressing weak topics\",\n");
        sb.append("  \"risk_level\": \"LOW\" | \"MEDIUM\" | \"HIGH\",\n");
        sb.append("  \"risk_factors\": [\"Specific academic risk factor\", \"...\"],\n");
        sb.append("  \"recommended_interventions\": [\"High-impact remedial intervention. For subjects with approved syllabus, cite unit/topic. For ungrounded subjects, base on score trends.\", \"...\"],\n");
        sb.append("  \"subject_wise_improvement_plan\": {\"SubjectName\": \"Actionable remedial plan\"},\n");
        sb.append("  \"revision_strategies\": [\"Specific study strategy\", \"...\"],\n");
        sb.append("  \"focus_topics\": [\"[Subject: Unit - Topic] Grounded from approved syllabus only\", \"...\"]\n");
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

        int hours = 0;
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
