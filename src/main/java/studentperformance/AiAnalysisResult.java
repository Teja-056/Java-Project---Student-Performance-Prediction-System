package studentperformance;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Structured container for AI-generated academic analysis, risk factors,
 * and personalized learning recommendations.
 */
public class AiAnalysisResult {
    private final String academicSummary;
    private final List<String> strengths;
    private final List<String> weaknesses;
    private final List<String> subjectsRequiringImprovement;
    private final String trendAnalysis;
    private final String estimatedFuturePerformance;
    private final String riskLevel; // LOW, MEDIUM, HIGH
    private final List<String> riskFactors;
    private final List<String> recommendedInterventions;
    private final int suggestedDailyStudyHours;
    private final Map<String, String> subjectWiseImprovementPlan;
    private final List<String> revisionStrategies;
    private final List<String> focusTopics;
    private final String generatedAt;

    public AiAnalysisResult(String academicSummary, List<String> strengths, List<String> weaknesses,
                            List<String> subjectsRequiringImprovement, String trendAnalysis,
                            String estimatedFuturePerformance, String riskLevel,
                            List<String> riskFactors, List<String> recommendedInterventions,
                            int suggestedDailyStudyHours, Map<String, String> subjectWiseImprovementPlan,
                            List<String> revisionStrategies, List<String> focusTopics) {
        this.academicSummary = academicSummary == null ? "" : academicSummary.trim();
        this.strengths = strengths == null ? List.of() : Collections.unmodifiableList(strengths);
        this.weaknesses = weaknesses == null ? List.of() : Collections.unmodifiableList(weaknesses);
        this.subjectsRequiringImprovement = subjectsRequiringImprovement == null ? List.of() : Collections.unmodifiableList(subjectsRequiringImprovement);
        this.trendAnalysis = trendAnalysis == null ? "" : trendAnalysis.trim();
        this.estimatedFuturePerformance = estimatedFuturePerformance == null ? "" : estimatedFuturePerformance.trim();
        this.riskLevel = (riskLevel == null || riskLevel.isBlank()) ? "MEDIUM" : riskLevel.trim().toUpperCase();
        this.riskFactors = riskFactors == null ? List.of() : Collections.unmodifiableList(riskFactors);
        this.recommendedInterventions = recommendedInterventions == null ? List.of() : Collections.unmodifiableList(recommendedInterventions);
        this.suggestedDailyStudyHours = Math.max(1, suggestedDailyStudyHours);
        this.subjectWiseImprovementPlan = subjectWiseImprovementPlan == null ? Map.of() : Collections.unmodifiableMap(subjectWiseImprovementPlan);
        this.revisionStrategies = revisionStrategies == null ? List.of() : Collections.unmodifiableList(revisionStrategies);
        this.focusTopics = focusTopics == null ? List.of() : Collections.unmodifiableList(focusTopics);
        this.generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String getAcademicSummary() { return academicSummary; }
    public List<String> getStrengths() { return strengths; }
    public List<String> getWeaknesses() { return weaknesses; }
    public List<String> getSubjectsRequiringImprovement() { return subjectsRequiringImprovement; }
    public String getTrendAnalysis() { return trendAnalysis; }
    public String getEstimatedFuturePerformance() { return estimatedFuturePerformance; }
    public String getRiskLevel() { return riskLevel; }
    public List<String> getRiskFactors() { return riskFactors; }
    public List<String> getRecommendedInterventions() { return recommendedInterventions; }
    public int getSuggestedDailyStudyHours() { return suggestedDailyStudyHours; }
    public Map<String, String> getSubjectWiseImprovementPlan() { return subjectWiseImprovementPlan; }
    public List<String> getRevisionStrategies() { return revisionStrategies; }
    public List<String> getFocusTopics() { return focusTopics; }
    public String getGeneratedAt() { return generatedAt; }

    public String formatConsoleReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=======================================================\n");
        sb.append("   AI PERFORMANCE ANALYSIS & RECOMMENDATION REPORT\n");
        sb.append("   Generated at: ").append(generatedAt).append("\n");
        sb.append("=======================================================\n");
        sb.append("ACADEMIC SUMMARY:\n").append(academicSummary).append("\n\n");
        sb.append("RISK LEVEL: ").append(riskLevel).append("\n\n");

        if (!strengths.isEmpty()) {
            sb.append("IDENTIFIED STRENGTHS:\n");
            for (String s : strengths) sb.append("  * ").append(s).append("\n");
            sb.append("\n");
        }

        if (!weaknesses.isEmpty()) {
            sb.append("IDENTIFIED WEAKNESSES:\n");
            for (String w : weaknesses) sb.append("  * ").append(w).append("\n");
            sb.append("\n");
        }

        if (!subjectsRequiringImprovement.isEmpty()) {
            sb.append("PRIORITY SUBJECTS FOR IMPROVEMENT:\n");
            for (String sub : subjectsRequiringImprovement) sb.append("  * ").append(sub).append("\n");
            sb.append("\n");
        }

        sb.append("PERFORMANCE TRENDS:\n").append(trendAnalysis).append("\n\n");
        sb.append("ESTIMATED FUTURE PERFORMANCE (WITH UNCERTAINTY):\n").append(estimatedFuturePerformance).append("\n\n");

        if (!riskFactors.isEmpty()) {
            sb.append("ACADEMIC RISK FACTORS:\n");
            for (String rf : riskFactors) sb.append("  ! ").append(rf).append("\n");
            sb.append("\n");
        }

        if (!recommendedInterventions.isEmpty()) {
            sb.append("RECOMMENDED INTERVENTIONS:\n");
            for (String ri : recommendedInterventions) sb.append("  -> ").append(ri).append("\n");
            sb.append("\n");
        }

        sb.append("SUGGESTED DAILY STUDY HOURS: ").append(suggestedDailyStudyHours).append(" hours/day\n\n");

        if (!focusTopics.isEmpty()) {
            sb.append("KEY TOPICS TO FOCUS ON:\n");
            for (String topic : focusTopics) sb.append("  [x] ").append(topic).append("\n");
            sb.append("\n");
        }

        if (!subjectWiseImprovementPlan.isEmpty()) {
            sb.append("SUBJECT-WISE IMPROVEMENT PLAN:\n");
            subjectWiseImprovementPlan.forEach((sub, plan) -> sb.append("  * ").append(sub).append(": ").append(plan).append("\n"));
            sb.append("\n");
        }

        if (!revisionStrategies.isEmpty()) {
            sb.append("ACTIONABLE REVISION STRATEGIES:\n");
            for (String rev : revisionStrategies) sb.append("  # ").append(rev).append("\n");
        }
        sb.append("=======================================================\n");
        return sb.toString();
    }
}
