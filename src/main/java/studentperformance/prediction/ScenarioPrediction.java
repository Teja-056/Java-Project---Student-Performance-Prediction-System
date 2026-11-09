package studentperformance.prediction;

import java.util.Collections;
import java.util.List;

public class ScenarioPrediction {
    private final String scenarioType; // CONSISTENT, IMPROVED, DECLINING
    private final String scenarioName;
    private final String description;
    private final double predictedSgpa;
    private final Double predictedCgpa;
    private final String cgpaStatus; // VERIFIED, SEMESTER_1_SGPA_REQUIRED
    private final List<SubjectPrediction> subjects;
    private final List<String> contributingFactors;

    // Rich scenario comparison metrics
    private final Double sgpaDeltaVsConsistent;
    private final Double cgpaDeltaVsConsistent;
    private final int totalPredictedSeeMarks;
    private final int subjectsWithMarksIncrease;
    private final int subjectsWithMarksDecrease;
    private final int subjectsWithGradeIncrease;
    private final int subjectsWithGradeDecrease;

    public ScenarioPrediction(String scenarioType, String scenarioName, String description,
                              double predictedSgpa, Double predictedCgpa, String cgpaStatus,
                              List<SubjectPrediction> subjects, List<String> contributingFactors) {
        this(scenarioType, scenarioName, description, predictedSgpa, predictedCgpa, cgpaStatus,
                subjects, contributingFactors, 0.0, 0.0,
                subjects != null ? subjects.stream().filter(s -> s.getPredictedSee() != null).mapToInt(SubjectPrediction::getPredictedSee).sum() : 0,
                0, 0, 0, 0);
    }

    public ScenarioPrediction(String scenarioType, String scenarioName, String description,
                              double predictedSgpa, Double predictedCgpa, String cgpaStatus,
                              List<SubjectPrediction> subjects, List<String> contributingFactors,
                              Double sgpaDeltaVsConsistent, Double cgpaDeltaVsConsistent,
                              int totalPredictedSeeMarks, int subjectsWithMarksIncrease,
                              int subjectsWithMarksDecrease, int subjectsWithGradeIncrease,
                              int subjectsWithGradeDecrease) {
        this.scenarioType = scenarioType;
        this.scenarioName = scenarioName;
        this.description = description;
        this.predictedSgpa = predictedSgpa;
        this.predictedCgpa = predictedCgpa;
        this.cgpaStatus = cgpaStatus;
        this.subjects = subjects != null ? Collections.unmodifiableList(subjects) : List.of();
        this.contributingFactors = contributingFactors != null ? Collections.unmodifiableList(contributingFactors) : List.of();
        this.sgpaDeltaVsConsistent = sgpaDeltaVsConsistent;
        this.cgpaDeltaVsConsistent = cgpaDeltaVsConsistent;
        this.totalPredictedSeeMarks = totalPredictedSeeMarks;
        this.subjectsWithMarksIncrease = subjectsWithMarksIncrease;
        this.subjectsWithMarksDecrease = subjectsWithMarksDecrease;
        this.subjectsWithGradeIncrease = subjectsWithGradeIncrease;
        this.subjectsWithGradeDecrease = subjectsWithGradeDecrease;
    }

    public String getScenarioType() { return scenarioType; }
    public String getScenarioName() { return scenarioName; }
    public String getDescription() { return description; }
    public double getPredictedSgpa() { return predictedSgpa; }
    public Double getPredictedCgpa() { return predictedCgpa; }
    public String getCgpaStatus() { return cgpaStatus; }
    public List<SubjectPrediction> getSubjects() { return subjects; }
    public List<String> getContributingFactors() { return contributingFactors; }
    public Double getSgpaDeltaVsConsistent() { return sgpaDeltaVsConsistent; }
    public Double getCgpaDeltaVsConsistent() { return cgpaDeltaVsConsistent; }
    public int getTotalPredictedSeeMarks() { return totalPredictedSeeMarks; }
    public int getSubjectsWithMarksIncrease() { return subjectsWithMarksIncrease; }
    public int getSubjectsWithMarksDecrease() { return subjectsWithMarksDecrease; }
    public int getSubjectsWithGradeIncrease() { return subjectsWithGradeIncrease; }
    public int getSubjectsWithGradeDecrease() { return subjectsWithGradeDecrease; }
}
