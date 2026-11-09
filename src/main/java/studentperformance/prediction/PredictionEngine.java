package studentperformance.prediction;

import studentperformance.PerformanceAnalyzer;
import studentperformance.Student;
import studentperformance.Subject;
import studentperformance.SubjectAssessment;

import java.util.*;

/**
 * Pure Java deterministic Semester End Examination (SEE) marks prediction engine.
 * Calculates predicted SEE marks, subject totals, percentages, grades, grade points,
 * predicted SGPA (19 credits), and predicted CGPA (48 credits) across 3 scenarios:
 * - Consistent Performance
 * - Improved Performance
 * - Declining Performance
 *
 * Daily Study Hours is completely omitted from all calculations.
 */
public class PredictionEngine {

    private final PerformanceAnalyzer analyzer;

    public PredictionEngine() {
        this(new PerformanceAnalyzer());
    }

    public PredictionEngine(PerformanceAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public StudentPredictionResult predict(Student student) {
        return predict(student, 12.0, 12.0, null);
    }

    public StudentPredictionResult predict(Student student, double improvementPercent, double declinePercent, Double attendanceOverride) {
        if (student == null) throw new IllegalArgumentException("Student cannot be null.");

        // Bounded configuration parameters (5% to 25%)
        double alphaImp = Math.max(0.05, Math.min(0.25, improvementPercent / 100.0));
        double alphaDec = Math.max(0.05, Math.min(0.25, declinePercent / 100.0));

        double attendance = attendanceOverride != null ? attendanceOverride
                : (student.getAttendancePercent() != null ? student.getAttendancePercent() : 75.0);
        attendance = Math.max(0.0, Math.min(100.0, attendance));

        Double sem1 = student.getSem1Sgpa();
        double sem2 = student.getLastSemesterSgpa();
        double cgpa = student.getCgpa();
        double overallCiePct = analyzer.currentInternalPercentage(student);

        // Prior momentum (0.0 to 1.0)
        double priorMomentum;
        if (sem1 != null) {
            priorMomentum = (sem1 + sem2) / 20.0;
        } else {
            priorMomentum = sem2 / 10.0;
        }
        priorMomentum = Math.max(0.0, Math.min(1.0, priorMomentum));

        // Behavioral modifier based on attendance deviation from 75% baseline (bounded +/- 10%)
        double deltaAtt = (attendance - 75.0) / 100.0;
        double behavioralMod = Math.max(-0.10, Math.min(0.10, deltaAtt));

        // Generate predictions for all 3 scenarios
        Map<String, ScenarioPrediction> scenarioMap = new LinkedHashMap<>();

        ScenarioPrediction consistent = buildScenario(
                "CONSISTENT", "Consistent Performance",
                "Projects examination outcomes assuming current internal marks, historical SGPA momentum, and attendance habits remain steady.",
                student, sem1, sem2, 0.0, 0.0, priorMomentum, behavioralMod, null
        );

        ScenarioPrediction improved = buildScenario(
                "IMPROVED", "Improved Performance",
                String.format(Locale.ROOT, "Projects positive examination potential with a +%.1f%% preparation boost, remediating weak units and maximizing subject headroom.", improvementPercent),
                student, sem1, sem2, alphaImp, 0.0, priorMomentum, behavioralMod, consistent
        );

        ScenarioPrediction declining = buildScenario(
                "DECLINING", "Declining Performance",
                String.format(Locale.ROOT, "Projects at-risk examination outcomes assuming a -%.1f%% preparation decline, exam burnout, or unresolved conceptual gaps.", declinePercent),
                student, sem1, sem2, 0.0, alphaDec, priorMomentum, behavioralMod, consistent
        );

        scenarioMap.put("CONSISTENT", consistent);
        scenarioMap.put("IMPROVED", improved);
        scenarioMap.put("DECLINING", declining);

        return new StudentPredictionResult(
                student.getRollNumber(),
                student.getName(),
                sem1,
                sem2,
                cgpa,
                attendance,
                round2(overallCiePct),
                scenarioMap,
                round1(improvementPercent),
                round1(declinePercent)
        );
    }

    private ScenarioPrediction buildScenario(String type, String name, String description,
                                             Student student, Double sem1, double sem2,
                                             double alphaImp, double alphaDec,
                                             double priorMomentum, double behavioralMod,
                                             ScenarioPrediction baseline) {
        List<SubjectPrediction> subjectPredictions = new ArrayList<>(8);
        List<String> factors = new ArrayList<>();

        double weightedGradePointsSum = 0.0;
        int totalIncludedCredits = 0;

        for (Subject s : Subject.values()) {
            SubjectAssessment a1 = student.getMarks(1).get(s);
            SubjectAssessment a2 = student.getMarks(2).get(s);

            double cie1 = (a1 != null && a1.getExamMarks() != null) ? a1.totalMarks() : Double.NaN;
            double cie2 = (a2 != null && a2.getExamMarks() != null) ? a2.totalMarks() : Double.NaN;

            boolean hasCie1 = !Double.isNaN(cie1);
            boolean hasCie2 = !Double.isNaN(cie2);
            boolean isAssessed = hasCie1 || hasCie2;

            if (!isAssessed) {
                // Clearly indicate missing assessment data rather than treating NULL as zero
                subjectPredictions.add(new SubjectPrediction(
                        s.name(),
                        s.getDisplayName(),
                        s.getFullName(),
                        s.getCredits(),
                        s.getCieMaxMarks(),
                        s.getSeeMaxMarks(),
                        s.getTotalMaxMarks(),
                        null,
                        null,
                        null,
                        null,
                        "UNASSESSED",
                        null,
                        null,
                        false
                ));
                continue;
            }

            double actualCie;
            if (hasCie1 && hasCie2) {
                actualCie = (cie1 + cie2) / 2.0;
            } else if (hasCie1) {
                actualCie = cie1;
            } else {
                actualCie = cie2;
            }
            actualCie = Math.min((double) s.getCieMaxMarks(), Math.max(0.0, actualCie));

            double pCie = actualCie / (double) s.getCieMaxMarks();

            // Internal 1 vs 2 Trend
            double trendRaw = 0.0;
            if (hasCie1 && hasCie2) {
                trendRaw = (cie2 - cie1) / (double) s.getCieMaxMarks();
            }
            double trendMod = Math.max(-0.20, Math.min(0.20, trendRaw));

            // Base rate for Consistent Performance
            double baseRate = (0.70 * pCie) + (0.20 * priorMomentum) + (0.05 * trendMod) + (0.05 * behavioralMod);
            baseRate = Math.max(0.0, Math.min(1.0, baseRate));

            // Apply scenario modifier
            double effectiveRate;
            if (alphaImp > 0.0) {
                double headroom = 1.0 - baseRate;
                effectiveRate = baseRate + (alphaImp * headroom);
            } else if (alphaDec > 0.0) {
                effectiveRate = Math.max(0.0, baseRate - alphaDec);
            } else {
                effectiveRate = baseRate;
            }
            effectiveRate = Math.max(0.0, Math.min(1.0, effectiveRate));

            int seeMax = s.getSeeMaxMarks();
            int predictedSee = (int) Math.round(effectiveRate * seeMax);
            predictedSee = Math.max(0, Math.min(seeMax, predictedSee));

            double finalMarks = round2(actualCie + predictedSee);
            int totalMax = s.getTotalMaxMarks();
            double percentage = round2((finalMarks / (double) totalMax) * 100.0);

            GradingScheme.GradeResult gr = GradingScheme.evaluate(percentage);
            int credits = s.getCredits();

            weightedGradePointsSum += (credits * gr.gradePoints());
            totalIncludedCredits += credits;

            double displayTrend = round1(trendRaw * 100.0);

            subjectPredictions.add(new SubjectPrediction(
                    s.name(),
                    s.getDisplayName(),
                    s.getFullName(),
                    credits,
                    s.getCieMaxMarks(),
                    seeMax,
                    totalMax,
                    round2(actualCie),
                    predictedSee,
                    finalMarks,
                    percentage,
                    gr.grade(),
                    gr.gradePoints(),
                    displayTrend,
                    true
            ));
        }

        // Calculate Predicted SGPA across the included credits
        double predictedSgpa = totalIncludedCredits > 0 ? round2(weightedGradePointsSum / totalIncludedCredits) : 0.0;

        // Calculate Predicted CGPA across cumulative credits
        Double predictedCgpa = null;
        String cgpaStatus;
        if (sem1 != null) {
            double cumulativePoints = (sem1 * 16.0) + (sem2 * 13.0) + (predictedSgpa * totalIncludedCredits);
            int totalCumulativeCredits = 16 + 13 + totalIncludedCredits;
            predictedCgpa = round2(cumulativePoints / (double) totalCumulativeCredits);
            cgpaStatus = (totalIncludedCredits == 19) ? "VERIFIED" : "PARTIAL_ASSESSMENT_VERIFIED";
        } else {
            cgpaStatus = "SEMESTER_1_SGPA_REQUIRED";
        }

        // Total Predicted SEE Marks
        int totalPredictedSeeMarks = subjectPredictions.stream()
                .filter(s -> s.getPredictedSee() != null)
                .mapToInt(SubjectPrediction::getPredictedSee)
                .sum();

        // Comparison metrics against baseline (consistent)
        Double sgpaDeltaVsConsistent = 0.0;
        Double cgpaDeltaVsConsistent = 0.0;
        int marksIncrease = 0;
        int marksDecrease = 0;
        int gradeIncrease = 0;
        int gradeDecrease = 0;

        if (baseline != null) {
            sgpaDeltaVsConsistent = round2(predictedSgpa - baseline.getPredictedSgpa());
            if (predictedCgpa != null && baseline.getPredictedCgpa() != null) {
                cgpaDeltaVsConsistent = round2(predictedCgpa - baseline.getPredictedCgpa());
            } else {
                cgpaDeltaVsConsistent = null;
            }

            Map<String, SubjectPrediction> baseMap = new HashMap<>();
            for (SubjectPrediction bp : baseline.getSubjects()) {
                baseMap.put(bp.getSubjectKey(), bp);
            }

            for (SubjectPrediction sp : subjectPredictions) {
                SubjectPrediction bp = baseMap.get(sp.getSubjectKey());
                if (bp != null && sp.getPredictedSee() != null && bp.getPredictedSee() != null) {
                    if (sp.getPredictedSee() > bp.getPredictedSee()) {
                        marksIncrease++;
                    } else if (sp.getPredictedSee() < bp.getPredictedSee()) {
                        marksDecrease++;
                    }

                    if (sp.getGradePoints() != null && bp.getGradePoints() != null) {
                        if (sp.getGradePoints() > bp.getGradePoints()) {
                            gradeIncrease++;
                        } else if (sp.getGradePoints() < bp.getGradePoints()) {
                            gradeDecrease++;
                        }
                    }
                }
            }
        }

        // Summary contributing factors
        if (totalIncludedCredits < 19) {
            factors.add(String.format(Locale.ROOT, "Partial CIE assessment records (%d of 19 credits evaluated). Unassessed subjects marked as UNASSESSED rather than treating missing marks as zero.", totalIncludedCredits));
        }
        if ("CONSISTENT".equals(type)) {
            factors.add("Weighted 70% toward actual CIE scores achieved across Internal 1 and Internal 2.");
            factors.add(String.format(Locale.ROOT, "Incorporated prior academic momentum from verified semester SGPA (%.2f).", sem2));
            factors.add(String.format(Locale.ROOT, "Adjusted by attendance consistency baseline (%.1f%%).", student.getAttendancePercent() != null ? student.getAttendancePercent() : 75.0));
        } else if ("IMPROVED".equals(type)) {
            factors.add(String.format(Locale.ROOT, "+%.1f%% preparation boost applied across remaining subject headroom.", alphaImp * 100.0));
            if (baseline != null) {
                int marksDiff = totalPredictedSeeMarks - baseline.getTotalPredictedSeeMarks();
                if (gradeIncrease == 0 && marksIncrease > 0) {
                    factors.add(String.format(Locale.ROOT, "Raw SEE scores improve by +%d marks across %d subjects (%d vs %d total SEE marks). However, predicted SGPA remains %.2f because these mark increases fall within existing letter grade boundaries (no grade threshold crossed).", marksDiff, marksIncrease, totalPredictedSeeMarks, baseline.getTotalPredictedSeeMarks(), predictedSgpa));
                } else if (gradeIncrease > 0) {
                    factors.add(String.format(Locale.ROOT, "Boost elevates letter grades in %d subject(s), producing a net predicted SGPA increase of +%.2f (%.2f -> %.2f).", gradeIncrease, sgpaDeltaVsConsistent, baseline.getPredictedSgpa(), predictedSgpa));
                }
            }
            factors.add("Reflects focused revision on units identified from lower internal assessments.");
            factors.add("Assumes sustained examination preparation and adherence to academic recommendations.");
        } else {
            factors.add(String.format(Locale.ROOT, "-%.1f%% decline penalty applied to baseline projected rate.", alphaDec * 100.0));
            if (baseline != null) {
                int marksDiff = baseline.getTotalPredictedSeeMarks() - totalPredictedSeeMarks;
                if (gradeDecrease > 0) {
                    factors.add(String.format(Locale.ROOT, "Preparation decline drops letter grades in %d subject(s), reducing predicted SGPA by %.2f (%.2f -> %.2f).", gradeDecrease, Math.abs(sgpaDeltaVsConsistent), baseline.getPredictedSgpa(), predictedSgpa));
                } else if (marksDecrease > 0) {
                    factors.add(String.format(Locale.ROOT, "Raw SEE scores drop by %d marks across %d subjects, but scores stay above the minimum cutoff for their current grade brackets.", marksDiff, marksDecrease));
                }
            }
            factors.add("Reflects potential vulnerability in weaker subject areas and examination fatigue.");
            factors.add("Identifies critical subjects requiring immediate academic intervention.");
        }

        return new ScenarioPrediction(
                type, name, description,
                predictedSgpa, predictedCgpa, cgpaStatus,
                subjectPredictions, factors,
                sgpaDeltaVsConsistent, cgpaDeltaVsConsistent,
                totalPredictedSeeMarks,
                marksIncrease, marksDecrease,
                gradeIncrease, gradeDecrease
        );
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
