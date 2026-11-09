package studentperformance.prediction;

public class SubjectPrediction {
    private final String subjectKey;
    private final String displayName;
    private final String fullName;
    private final int credits;
    private final int cieMax;
    private final int seeMax;
    private final int totalMax;
    private final Double actualCie;
    private final Integer predictedSee;
    private final Double finalMarks;
    private final Double percentage;
    private final String grade;
    private final Integer gradePoints;
    private final Double trendPercentage;
    private final boolean assessed;

    public SubjectPrediction(String subjectKey, String displayName, String fullName, int credits,
                             int cieMax, int seeMax, int totalMax, Double actualCie, Integer predictedSee,
                             Double finalMarks, Double percentage, String grade, Integer gradePoints,
                             Double trendPercentage) {
        this(subjectKey, displayName, fullName, credits, cieMax, seeMax, totalMax,
                actualCie, predictedSee, finalMarks, percentage, grade, gradePoints, trendPercentage,
                actualCie != null);
    }

    public SubjectPrediction(String subjectKey, String displayName, String fullName, int credits,
                             int cieMax, int seeMax, int totalMax, Double actualCie, Integer predictedSee,
                             Double finalMarks, Double percentage, String grade, Integer gradePoints,
                             Double trendPercentage, boolean assessed) {
        this.subjectKey = subjectKey;
        this.displayName = displayName;
        this.fullName = fullName;
        this.credits = credits;
        this.cieMax = cieMax;
        this.seeMax = seeMax;
        this.totalMax = totalMax;
        this.actualCie = actualCie;
        this.predictedSee = predictedSee;
        this.finalMarks = finalMarks;
        this.percentage = percentage;
        this.grade = grade;
        this.gradePoints = gradePoints;
        this.trendPercentage = trendPercentage;
        this.assessed = assessed;
    }

    public String getSubjectKey() { return subjectKey; }
    public String getDisplayName() { return displayName; }
    public String getFullName() { return fullName; }
    public int getCredits() { return credits; }
    public int getCieMax() { return cieMax; }
    public int getSeeMax() { return seeMax; }
    public int getTotalMax() { return totalMax; }
    public Double getActualCie() { return actualCie; }
    public Integer getPredictedSee() { return predictedSee; }
    public Double getFinalMarks() { return finalMarks; }
    public Double getPercentage() { return percentage; }
    public String getGrade() { return grade; }
    public Integer getGradePoints() { return gradePoints; }
    public Double getTrendPercentage() { return trendPercentage; }
    public boolean isAssessed() { return assessed; }
}
