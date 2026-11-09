package studentperformance.dto;

import java.util.List;

public class AiPredictionInsightsDto {
    private String scenarioDivergenceExplanation;
    private List<String> prioritySubjects;
    private List<String> recommendedUnitFocus;
    private List<String> actionableAdvice;
    private String providerName;

    private List<String> mostBenefitedSubjects;
    private List<String> mostVulnerableSubjects;

    public AiPredictionInsightsDto() {}

    public AiPredictionInsightsDto(String scenarioDivergenceExplanation, List<String> prioritySubjects,
                                  List<String> recommendedUnitFocus, List<String> actionableAdvice,
                                  String providerName) {
        this(scenarioDivergenceExplanation, prioritySubjects, recommendedUnitFocus, actionableAdvice, providerName,
                List.of(), List.of());
    }

    public AiPredictionInsightsDto(String scenarioDivergenceExplanation, List<String> prioritySubjects,
                                  List<String> recommendedUnitFocus, List<String> actionableAdvice,
                                  String providerName, List<String> mostBenefitedSubjects,
                                  List<String> mostVulnerableSubjects) {
        this.scenarioDivergenceExplanation = scenarioDivergenceExplanation;
        this.prioritySubjects = prioritySubjects;
        this.recommendedUnitFocus = recommendedUnitFocus;
        this.actionableAdvice = actionableAdvice;
        this.providerName = providerName;
        this.mostBenefitedSubjects = mostBenefitedSubjects;
        this.mostVulnerableSubjects = mostVulnerableSubjects;
    }

    public List<String> getMostBenefitedSubjects() { return mostBenefitedSubjects; }
    public void setMostBenefitedSubjects(List<String> mostBenefitedSubjects) { this.mostBenefitedSubjects = mostBenefitedSubjects; }

    public List<String> getMostVulnerableSubjects() { return mostVulnerableSubjects; }
    public void setMostVulnerableSubjects(List<String> mostVulnerableSubjects) { this.mostVulnerableSubjects = mostVulnerableSubjects; }

    public String getScenarioDivergenceExplanation() { return scenarioDivergenceExplanation; }
    public void setScenarioDivergenceExplanation(String scenarioDivergenceExplanation) { this.scenarioDivergenceExplanation = scenarioDivergenceExplanation; }

    public List<String> getPrioritySubjects() { return prioritySubjects; }
    public void setPrioritySubjects(List<String> prioritySubjects) { this.prioritySubjects = prioritySubjects; }

    public List<String> getRecommendedUnitFocus() { return recommendedUnitFocus; }
    public void setRecommendedUnitFocus(List<String> recommendedUnitFocus) { this.recommendedUnitFocus = recommendedUnitFocus; }

    public List<String> getActionableAdvice() { return actionableAdvice; }
    public void setActionableAdvice(List<String> actionableAdvice) { this.actionableAdvice = actionableAdvice; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
}
