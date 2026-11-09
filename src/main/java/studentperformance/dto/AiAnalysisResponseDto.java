package studentperformance.dto;

import studentperformance.AiAnalysisResult;

import java.util.List;
import java.util.Map;

public class AiAnalysisResponseDto {
    private String provider;
    private String model;
    private String generatedAt;
    private String syllabusGroundingStatus; // "GROUNDED" or "NO_APPROVED_SYLLABUS_AVAILABLE"
    private String academicSummary;
    private String riskLevel;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> subjectsRequiringImprovement;
    private String trendAnalysis;
    private String estimatedFuturePerformance;
    private List<String> riskFactors;
    private List<String> recommendedInterventions;
    private Map<String, String> subjectWiseImprovementPlan;
    private List<String> revisionStrategies;
    private List<String> focusTopics;
    private List<String> groundedSubjects;
    private List<String> missingSyllabusSubjects;

    public AiAnalysisResponseDto() {}

    public AiAnalysisResponseDto(AiAnalysisResult result, String provider, String model, String syllabusGroundingStatus) {
        this(result, provider, model, syllabusGroundingStatus, List.of(), List.of());
    }

    public AiAnalysisResponseDto(AiAnalysisResult result, String provider, String model,
                                 String syllabusGroundingStatus, List<String> groundedSubjects,
                                 List<String> missingSyllabusSubjects) {
        this.provider = provider;
        this.model = model;
        this.syllabusGroundingStatus = syllabusGroundingStatus;
        this.groundedSubjects = groundedSubjects != null ? groundedSubjects : List.of();
        this.missingSyllabusSubjects = missingSyllabusSubjects != null ? missingSyllabusSubjects : List.of();
        if (result != null) {
            this.generatedAt = result.getGeneratedAt();
            this.academicSummary = result.getAcademicSummary();
            this.riskLevel = result.getRiskLevel();
            this.strengths = result.getStrengths();
            this.weaknesses = result.getWeaknesses();
            this.subjectsRequiringImprovement = result.getSubjectsRequiringImprovement();
            this.trendAnalysis = result.getTrendAnalysis();
            this.estimatedFuturePerformance = result.getEstimatedFuturePerformance();
            this.riskFactors = result.getRiskFactors();
            this.recommendedInterventions = result.getRecommendedInterventions();
            this.subjectWiseImprovementPlan = result.getSubjectWiseImprovementPlan();
            this.revisionStrategies = result.getRevisionStrategies();
            this.focusTopics = result.getFocusTopics();
        }
    }

    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getGeneratedAt() { return generatedAt; }
    public String getSyllabusGroundingStatus() { return syllabusGroundingStatus; }
    public String getAcademicSummary() { return academicSummary; }
    public String getRiskLevel() { return riskLevel; }
    public List<String> getStrengths() { return strengths; }
    public List<String> getWeaknesses() { return weaknesses; }
    public List<String> getSubjectsRequiringImprovement() { return subjectsRequiringImprovement; }
    public String getTrendAnalysis() { return trendAnalysis; }
    public String getEstimatedFuturePerformance() { return estimatedFuturePerformance; }
    public List<String> getRiskFactors() { return riskFactors; }
    public List<String> getRecommendedInterventions() { return recommendedInterventions; }
    public Map<String, String> getSubjectWiseImprovementPlan() { return subjectWiseImprovementPlan; }
    public List<String> getRevisionStrategies() { return revisionStrategies; }
    public List<String> getFocusTopics() { return focusTopics; }
    public List<String> getGroundedSubjects() { return groundedSubjects; }
    public List<String> getMissingSyllabusSubjects() { return missingSyllabusSubjects; }
}
