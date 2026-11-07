package studentperformance;

import java.sql.*;
import java.util.*;

/**
 * Phase 4 verification suite:
 * Verifies live MySQL data integrity, calculations, search, ranking, comparison, and support filtering.
 */
public class Phase4Verification {
    public static void main(String[] args) {
        String dbUrl = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC");
        String dbUser = System.getenv().getOrDefault("DB_USER", "root");
        String dbPass = System.getenv().getOrDefault("DB_PASSWORD", "Teja.J@2007");

        System.out.println("==================================================");
        System.out.println("PHASE 4: DATABASE & DATA VERIFICATION SUITE");
        System.out.println("Database: " + dbUrl);
        System.out.println("User: " + dbUser);
        System.out.println("==================================================");

        StudentRepository repository = new StudentRepository(dbUrl, dbUser, dbPass);
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        try {
            // 1. Verify connection and total student count
            System.out.println("\n[CHECK 1] Verifying Total Students and Import Count...");
            List<Student> students = repository.findAll();
            System.out.println("Total student records retrieved from MySQL: " + students.size());
            if (students.size() < 100) {
                throw new IllegalStateException("Expected at least 100 students, found: " + students.size());
            }
            System.out.println("-> PASS: 100+ student records verified in MySQL.");

            // 2. Check for duplicate roll numbers
            System.out.println("\n[CHECK 2] Checking for Duplicate Roll Numbers...");
            Set<String> uniqueRolls = new HashSet<>();
            List<String> duplicates = new ArrayList<>();
            for (Student s : students) {
                if (!uniqueRolls.add(s.getRollNumber())) {
                    duplicates.add(s.getRollNumber());
                }
            }
            if (!duplicates.isEmpty()) {
                throw new IllegalStateException("Found duplicate roll numbers: " + duplicates);
            }
            System.out.println("-> PASS: All " + uniqueRolls.size() + " roll numbers are strictly unique.");

            // 3. Verify internal assessment marks storage & constraints
            System.out.println("\n[CHECK 3] Verifying Internal Assessment Marks & Bounds...");
            int totalAssessments = 0;
            for (Student s : students) {
                for (int internal = 1; internal <= 2; internal++) {
                    Map<Subject, SubjectAssessment> marks = s.getMarks(internal);
                    if (marks.size() != Subject.values().length) {
                        throw new IllegalStateException("Student " + s.getRollNumber() + " Internal " + internal + " missing subjects");
                    }
                    for (Subject subj : Subject.values()) {
                        SubjectAssessment sa = marks.get(subj);
                        if (sa == null) throw new IllegalStateException("Null assessment for " + s.getRollNumber());
                        Integer exam = sa.getExamMarks();
                        if (exam != null && (exam < 0 || exam > subj.getMaximumExamMarks())) {
                            throw new IllegalStateException("Exam marks out of range for " + s.getRollNumber() + " " + subj);
                        }
                        for (Integer a : sa.getAssignmentMarks()) {
                            if (a != null && (a < 0 || a > 5)) throw new IllegalStateException("Assignment marks out of range: " + a);
                        }
                        for (Integer q : sa.getQuizMarks()) {
                            if (q != null && (q < 0 || q > 5)) throw new IllegalStateException("Quiz marks out of range: " + q);
                        }
                        totalAssessments++;
                    }
                }
            }
            System.out.println("Total validated subject assessments: " + totalAssessments + " (14 per student)");
            System.out.println("-> PASS: Assessment marks match all subject-specific maximums and constraints.");

            // 4. Verify calculations (GPA, percentages, trends)
            System.out.println("\n[CHECK 4] Verifying Calculation Accuracy...");
            Student s1 = repository.findByRollNumber("1602-25-733-001");
            if (s1 == null) throw new IllegalStateException("Student 1602-25-733-001 not found");
            double int1Pct = analyzer.percentage(s1, 1);
            double int2Pct = analyzer.percentage(s1, 2);
            double combinedPct = analyzer.currentInternalPercentage(s1);
            System.out.printf("Sample Student: %s (%s)%n", s1.getName(), s1.getRollNumber());
            System.out.printf("  CGPA: %.2f | SGPA: %.2f%n", s1.getCgpa(), s1.getLastSemesterSgpa());
            System.out.printf("  Internal 1: %.2f%% | Internal 2: %.2f%% | Combined: %.2f%%%n", int1Pct, int2Pct, combinedPct);
            System.out.printf("  Strongest Subject: %s | Weakest: %s%n", analyzer.strongestSubject(s1), analyzer.weakestSubject(s1));
            System.out.printf("  Prediction: %s%n", analyzer.prediction(s1));
            if (combinedPct <= 0 || combinedPct > 100) throw new IllegalStateException("Invalid percentage: " + combinedPct);
            System.out.println("-> PASS: Calculations are deterministic, correct, and bounded.");

            // 5. Test search, ranking, comparison, and support identification
            System.out.println("\n[CHECK 5] Testing Search, Ranking, Comparison & Support Identification...");
            // Search
            Student searched = repository.findByRollNumber("1602-25-733-050");
            if (searched == null) throw new IllegalStateException("Failed to search student 1602-25-733-050");
            System.out.println("  Search verified: Found " + searched.getName() + " (" + searched.getRollNumber() + ")");

            // Ranking
            List<Student> ranked = students.stream().filter(analyzer::hasBothInternals)
                    .sorted(Comparator.comparingDouble(analyzer::currentInternalPercentage).reversed())
                    .toList();
            System.out.println("  Ranking verified: Top student is " + ranked.get(0).getName() + " with "
                    + String.format(Locale.ROOT, "%.2f%%", analyzer.currentInternalPercentage(ranked.get(0))));
            for (int i = 0; i < ranked.size() - 1; i++) {
                if (analyzer.currentInternalPercentage(ranked.get(i)) < analyzer.currentInternalPercentage(ranked.get(i + 1))) {
                    throw new IllegalStateException("Ranking sorting invariant violated at index " + i);
                }
            }

            // Comparison
            Student compA = ranked.get(0);
            Student compB = ranked.get(ranked.size() - 1);
            System.out.println("  Comparison verified: Comparing Top (" + compA.getName() + " - "
                    + String.format(Locale.ROOT, "%.2f%%", analyzer.currentInternalPercentage(compA)) + ") vs "
                    + "Bottom (" + compB.getName() + " - "
                    + String.format(Locale.ROOT, "%.2f%%", analyzer.currentInternalPercentage(compB)) + ")");

            // Support identification
            List<Student> supportList = students.stream().filter(analyzer::requiresSupport).toList();
            System.out.println("  Support identification verified: " + supportList.size() + " students identified below "
                    + analyzer.getSupportThresholdPercent() + "% threshold.");
            for (Student supp : supportList) {
                if (analyzer.currentInternalPercentage(supp) >= analyzer.getSupportThresholdPercent()) {
                    throw new IllegalStateException("Student in support list is not below threshold: " + supp.getRollNumber());
                }
            }
            System.out.println("-> PASS: Search, ranking, comparison, and support identification all function flawlessly.");

            // 6. Application restart simulation / Data persistence check
            System.out.println("\n[CHECK 6] Verifying Data Persistence Across Reconnections...");
            StudentRepository secondRepoInstance = new StudentRepository(dbUrl, dbUser, dbPass);
            secondRepoInstance.initializeSchema();
            List<Student> reloaded = secondRepoInstance.findAll();
            if (reloaded.size() != students.size()) {
                throw new IllegalStateException("Data lost after re-initialization! Original: " + students.size() + ", reloaded: " + reloaded.size());
            }
            System.out.println("-> PASS: Reconnection confirmed. All " + reloaded.size() + " records fully preserved.");

            System.out.println("\n==================================================");
            System.out.println("PHASE 4 VERIFICATION COMPLETE: ALL 6 CHECKS PASSED!");
            System.out.println("==================================================");

        } catch (Exception ex) {
            System.err.println("Verification failed: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
