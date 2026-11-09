package studentperformance.dto;

/**
 * Statistics for a performance category in a subject.
 */
public class SubjectCategoryStatsDto {
    private String category;       // "Excellent", "Very Good", "Good", "Average", "Needs Improvement", "At Risk"
    private String range;          // "90–100%", "80–<90%", "70–<80%", "60–<70%", "40–<60%", "Below 40%"
    private int count;             // Number of students assessed in this category
    private double percentage;     // Percentage of assessed students in this category (0.0 to 100.0)

    public SubjectCategoryStatsDto() {}

    public SubjectCategoryStatsDto(String category, String range, int count, double percentage) {
        this.category = category;
        this.range = range;
        this.count = count;
        this.percentage = percentage;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { this.percentage = percentage; }
}
