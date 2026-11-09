package studentperformance.service;

import org.springframework.stereotype.Service;
import studentperformance.PerformanceAnalyzer;
import studentperformance.Student;
import studentperformance.StudentRepository;
import studentperformance.Subject;
import studentperformance.SubjectAssessment;
import studentperformance.dto.SubjectCategoryStatsDto;
import studentperformance.dto.SubjectPerformanceResponseDto;
import studentperformance.dto.SubjectStudentEntryDto;
import studentperformance.exception.BadRequestException;

import java.sql.SQLException;
import java.util.*;

@Service
public class SubjectPerformanceService {

    private final StudentRepository studentRepository;
    private final PerformanceAnalyzer performanceAnalyzer;

    public SubjectPerformanceService(StudentRepository studentRepository, PerformanceAnalyzer performanceAnalyzer) {
        this.studentRepository = studentRepository;
        this.performanceAnalyzer = performanceAnalyzer;
    }

    public SubjectPerformanceResponseDto getSubjectPerformance(String subjectKey, String assessmentTypeParam) {
        Subject subject = resolveSubject(subjectKey);
        String assessmentType = normalizeAssessmentType(assessmentTypeParam);

        List<Student> allStudents;
        try {
            allStudents = studentRepository.findAll();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load students for subject performance: " + e.getMessage(), e);
        }

        SubjectPerformanceResponseDto response = new SubjectPerformanceResponseDto();
        response.setSubjectCode(subject.name());
        response.setSubjectName(subject.getDisplayName());
        response.setFullName(subject.getFullName());
        response.setCourseCode(subject.getCourseCode());
        response.setCredits(subject.getCredits());
        response.setCieMaxMarks(subject.getCieMaxMarks());
        response.setAssessmentType(assessmentType);
        response.setTotalStudents(allStudents.size());

        List<SubjectStudentEntryDto> assessedStudents = new ArrayList<>();
        List<SubjectStudentEntryDto> unassessedStudents = new ArrayList<>();

        double sumObtained = 0.0;
        double sumPercentage = 0.0;

        // Internal 1 and Internal 2 cohort tracking
        double int1SumPct = 0.0;
        int int1AssessedCount = 0;
        double int2SumPct = 0.0;
        int int2AssessedCount = 0;

        int improvedCount = 0;
        int declinedCount = 0;
        int stableCount = 0;

        for (Student student : allStudents) {
            // Evaluate Internal 1
            SubjectAssessment a1 = student.getMarks(1).get(subject);
            boolean hasInt1 = a1 != null && (a1.getExamMarks() != null || a1.hasAssignmentMarks() || a1.hasQuizMarks());
            Double int1Marks = hasInt1 ? a1.totalMarks() : null;
            Double int1Pct = hasInt1 ? (int1Marks * 100.0 / subject.getCieMaxMarks()) : null;
            if (hasInt1) {
                int1SumPct += int1Pct;
                int1AssessedCount++;
            }

            // Evaluate Internal 2
            SubjectAssessment a2 = student.getMarks(2).get(subject);
            boolean hasInt2 = a2 != null && (a2.getExamMarks() != null || a2.hasAssignmentMarks() || a2.hasQuizMarks());
            Double int2Marks = hasInt2 ? a2.totalMarks() : null;
            Double int2Pct = hasInt2 ? (int2Marks * 100.0 / subject.getCieMaxMarks()) : null;
            if (hasInt2) {
                int2SumPct += int2Pct;
                int2AssessedCount++;
            }

            // Trend
            Double trend = null;
            if (hasInt1 && hasInt2) {
                trend = int2Pct - int1Pct;
                if (trend > 0.001) improvedCount++;
                else if (trend < -0.001) declinedCount++;
                else stableCount++;
            }

            // Determine assessment score according to requested filter
            boolean isAssessed;
            Double score;
            Double pct;

            if ("INTERNAL_1".equals(assessmentType)) {
                isAssessed = hasInt1;
                score = int1Marks;
                pct = int1Pct;
            } else if ("INTERNAL_2".equals(assessmentType)) {
                isAssessed = hasInt2;
                score = int2Marks;
                pct = int2Pct;
            } else { // COMBINED
                if (hasInt1 && hasInt2) {
                    isAssessed = true;
                    score = (int1Marks + int2Marks) / 2.0;
                    pct = (score * 100.0) / subject.getCieMaxMarks();
                } else if (hasInt1) {
                    isAssessed = true;
                    score = int1Marks;
                    pct = int1Pct;
                } else if (hasInt2) {
                    isAssessed = true;
                    score = int2Marks;
                    pct = int2Pct;
                } else {
                    isAssessed = false;
                    score = null;
                    pct = null;
                }
            }

            if (isAssessed) {
                String category = categorize(pct);
                double roundedScore = Math.round(score * 100.0) / 100.0;
                double roundedPct = Math.round(pct * 100.0) / 100.0;
                Double roundedInt1 = hasInt1 ? Math.round(int1Marks * 100.0) / 100.0 : null;
                Double roundedInt2 = hasInt2 ? Math.round(int2Marks * 100.0) / 100.0 : null;
                Double roundedTrend = trend != null ? Math.round(trend * 100.0) / 100.0 : null;

                SubjectStudentEntryDto entry = new SubjectStudentEntryDto(
                        student.getRollNumber(),
                        student.getName(),
                        roundedScore,
                        subject.getCieMaxMarks(),
                        roundedPct,
                        category,
                        roundedInt1,
                        roundedInt2,
                        roundedTrend
                );
                assessedStudents.add(entry);
                sumObtained += roundedScore;
                sumPercentage += roundedPct;
            } else {
                SubjectStudentEntryDto unassessedEntry = new SubjectStudentEntryDto(
                        student.getRollNumber(),
                        student.getName(),
                        null,
                        subject.getCieMaxMarks(),
                        null,
                        "Unassessed",
                        hasInt1 ? Math.round(int1Marks * 100.0) / 100.0 : null,
                        hasInt2 ? Math.round(int2Marks * 100.0) / 100.0 : null,
                        null
                );
                unassessedStudents.add(unassessedEntry);
            }
        }

        int assessedCount = assessedStudents.size();
        int unassessedCount = unassessedStudents.size();
        response.setAssessedCount(assessedCount);
        response.setUnassessedCount(unassessedCount);

        // Sort assessed students by percentage descending (secondary sort by rollNumber)
        assessedStudents.sort((a, b) -> {
            int cmp = Double.compare(b.getPercentage(), a.getPercentage());
            return cmp != 0 ? cmp : a.getRollNumber().compareTo(b.getRollNumber());
        });
        response.setStudents(assessedStudents);
        response.setUnassessedStudents(unassessedStudents);

        if (assessedCount > 0) {
            double avgMarks = Math.round((sumObtained / assessedCount) * 100.0) / 100.0;
            double avgPct = Math.round((sumPercentage / assessedCount) * 100.0) / 100.0;
            response.setAverageMarks(avgMarks);
            response.setAveragePercentage(avgPct);

            double maxScore = assessedStudents.get(0).getObtainedMarks();
            double minScore = assessedStudents.get(assessedStudents.size() - 1).getObtainedMarks();
            response.setHighestScore(maxScore);
            response.setLowestScore(minScore);

            List<SubjectStudentEntryDto> highestList = new ArrayList<>();
            List<SubjectStudentEntryDto> lowestList = new ArrayList<>();
            for (SubjectStudentEntryDto st : assessedStudents) {
                if (Math.abs(st.getObtainedMarks() - maxScore) < 0.001) highestList.add(st);
                if (Math.abs(st.getObtainedMarks() - minScore) < 0.001) lowestList.add(st);
            }
            response.setHighestStudents(highestList);
            response.setHighestStudentRoll(highestList.get(0).getRollNumber());
            response.setHighestStudentName(highestList.get(0).getStudentName());

            response.setLowestStudents(lowestList);
            response.setLowestStudentRoll(lowestList.get(0).getRollNumber());
            response.setLowestStudentName(lowestList.get(0).getStudentName());
        } else {
            response.setAverageMarks(0.0);
            response.setAveragePercentage(0.0);
        }

        // Internal 1 vs 2 Metrics
        if (int1AssessedCount > 0) {
            response.setInternal1AveragePercentage(Math.round((int1SumPct / int1AssessedCount) * 100.0) / 100.0);
        }
        if (int2AssessedCount > 0) {
            response.setInternal2AveragePercentage(Math.round((int2SumPct / int2AssessedCount) * 100.0) / 100.0);
        }
        if (int1AssessedCount > 0 && int2AssessedCount > 0) {
            double change = response.getInternal2AveragePercentage() - response.getInternal1AveragePercentage();
            response.setAverageChangePercentage(Math.round(change * 100.0) / 100.0);
        }
        response.setImprovedCount(improvedCount);
        response.setDeclinedCount(declinedCount);
        response.setStableCount(stableCount);

        // Build Category Distributions for active filter
        response.setCategoryStats(buildCategoryStats(assessedStudents, assessedCount));

        // Build Category Distributions for Int 1 and Int 2 for comparison charts
        List<SubjectStudentEntryDto> int1Entries = new ArrayList<>();
        List<SubjectStudentEntryDto> int2Entries = new ArrayList<>();
        for (SubjectStudentEntryDto entry : assessedStudents) {
            if (entry.getInternal1Marks() != null) {
                double p1 = (entry.getInternal1Marks() * 100.0) / subject.getCieMaxMarks();
                int1Entries.add(new SubjectStudentEntryDto(entry.getRollNumber(), entry.getStudentName(),
                        entry.getInternal1Marks(), subject.getCieMaxMarks(), p1, categorize(p1), null, null, null));
            }
            if (entry.getInternal2Marks() != null) {
                double p2 = (entry.getInternal2Marks() * 100.0) / subject.getCieMaxMarks();
                int2Entries.add(new SubjectStudentEntryDto(entry.getRollNumber(), entry.getStudentName(),
                        entry.getInternal2Marks(), subject.getCieMaxMarks(), p2, categorize(p2), null, null, null));
            }
        }
        response.setInternal1CategoryStats(buildCategoryStats(int1Entries, int1Entries.size()));
        response.setInternal2CategoryStats(buildCategoryStats(int2Entries, int2Entries.size()));

        return response;
    }

    private List<SubjectCategoryStatsDto> buildCategoryStats(List<SubjectStudentEntryDto> entries, int totalCount) {
        String[] categories = {"Excellent", "Very Good", "Good", "Average", "Needs Improvement", "At Risk"};
        String[] ranges = {"90–100%", "80–<90%", "70–<80%", "60–<70%", "40–<60%", "Below 40%"};

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String cat : categories) counts.put(cat, 0);

        for (SubjectStudentEntryDto st : entries) {
            String cat = st.getCategory();
            if (counts.containsKey(cat)) {
                counts.put(cat, counts.get(cat) + 1);
            }
        }

        List<SubjectCategoryStatsDto> stats = new ArrayList<>();
        for (int i = 0; i < categories.length; i++) {
            String cat = categories[i];
            int count = counts.get(cat);
            double pct = totalCount > 0 ? Math.round((count * 100.0 / totalCount) * 10.0) / 10.0 : 0.0;
            stats.add(new SubjectCategoryStatsDto(cat, ranges[i], count, pct));
        }
        return stats;
    }

    public static String categorize(Double percentage) {
        if (percentage == null || Double.isNaN(percentage)) return "Unassessed";
        if (percentage >= 90.0) return "Excellent";
        if (percentage >= 80.0) return "Very Good";
        if (percentage >= 70.0) return "Good";
        if (percentage >= 60.0) return "Average";
        if (percentage >= 40.0) return "Needs Improvement";
        return "At Risk";
    }

    private Subject resolveSubject(String key) {
        if (key == null || key.isBlank()) return Subject.DS;
        Subject s = Subject.matchByText(key);
        if (s != null) return s;
        try {
            return Subject.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown subject: " + key + ". Supported subjects: JAVA, DS, CA, TTPS, OE, CS, CT, HVPE");
        }
    }

    private String normalizeAssessmentType(String type) {
        if (type == null || type.isBlank()) return "COMBINED";
        String upper = type.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("1") || upper.equals("INTERNAL_1") || upper.equals("INT1")) {
            return "INTERNAL_1";
        }
        if (upper.contains("2") || upper.equals("INTERNAL_2") || upper.equals("INT2")) {
            return "INTERNAL_2";
        }
        return "COMBINED";
    }
}
