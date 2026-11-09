package studentperformance.service;

import org.springframework.stereotype.Service;
import studentperformance.*;
import studentperformance.dto.AiPredictionInsightsDto;
import studentperformance.dto.CalculatePredictionRequest;
import studentperformance.exception.BadRequestException;
import studentperformance.exception.ResourceNotFoundException;
import studentperformance.prediction.PredictionEngine;
import studentperformance.prediction.ScenarioPrediction;
import studentperformance.prediction.StudentPredictionResult;
import studentperformance.prediction.SubjectPrediction;

import java.sql.SQLException;
import java.util.*;

@Service
public class PredictionService {

    private final StudentRepository studentRepository;
    private final SyllabusRepository syllabusRepository;
    private final PerformanceAnalyzer analyzer;
    private final PredictionEngine predictionEngine;
    private final AiService aiService;

    public PredictionService(StudentRepository studentRepository,
                             SyllabusRepository syllabusRepository,
                             PerformanceAnalyzer analyzer,
                             AiService aiService) {
        this.studentRepository = studentRepository;
        this.syllabusRepository = syllabusRepository;
        this.analyzer = analyzer;
        this.predictionEngine = new PredictionEngine(analyzer);
        this.aiService = aiService;
    }

    public StudentPredictionResult getPrediction(String rollNumber) {
        Student student = loadStudent(rollNumber);
        return predictionEngine.predict(student);
    }

    public StudentPredictionResult calculateCustomPrediction(String rollNumber, CalculatePredictionRequest request) {
        Student student = loadStudent(rollNumber);
        double imp = request != null && request.getImprovementPercent() != null ? request.getImprovementPercent() : 12.0;
        double dec = request != null && request.getDeclinePercent() != null ? request.getDeclinePercent() : 12.0;
        Double attOverride = request != null ? request.getAttendanceOverride() : null;

        return predictionEngine.predict(student, imp, dec, attOverride);
    }

    public AiPredictionInsightsDto getAiInsights(String rollNumber) {
        Student student = loadStudent(rollNumber);
        StudentPredictionResult pred = predictionEngine.predict(student);

        ScenarioPrediction con = pred.getScenarios().get("CONSISTENT");
        ScenarioPrediction imp = pred.getScenarios().get("IMPROVED");
        ScenarioPrediction dec = pred.getScenarios().get("DECLINING");

        List<SubjectPrediction> subjs = con.getSubjects();
        SubjectPrediction weakest = subjs.stream()
                .filter(SubjectPrediction::isAssessed)
                .filter(s -> s.getPercentage() != null)
                .min(Comparator.comparingDouble(SubjectPrediction::getPercentage))
                .orElse(null);

        List<String> prioritySubjects = new ArrayList<>();
        List<String> unitFocus = new ArrayList<>();
        List<String> advice = new ArrayList<>();

        for (SubjectPrediction sp : subjs) {
            if (!sp.isAssessed() || sp.getPercentage() == null) continue;
            if (sp.getPercentage() < 60.0 || (sp.getTrendPercentage() != null && sp.getTrendPercentage() < -5.0)) {
                double trend = sp.getTrendPercentage() != null ? sp.getTrendPercentage() : 0.0;
                prioritySubjects.add(String.format(Locale.ROOT, "%s (%s): Projected %s (%.1f%%, Trend: %+.1f%%)",
                        sp.getDisplayName(), sp.getFullName(), sp.getGrade(), sp.getPercentage(), trend));
            }
        }
        if (prioritySubjects.isEmpty() && weakest != null) {
            prioritySubjects.add(String.format(Locale.ROOT, "%s (%s): Projected %s (%.1f%%)",
                    weakest.getDisplayName(), weakest.getFullName(), weakest.getGrade(), weakest.getPercentage()));
        }

        // Unit guidance
        Map<Integer, List<Integer>> unitMapping = Map.of(1, List.of(1, 2), 2, List.of(3, 4, 5));
        String unitAdvice = analyzer.unitAdvice(student, unitMapping);
        if (unitAdvice != null && !unitAdvice.isBlank()) {
            unitFocus.add(unitAdvice);
        }

        // Identify most benefited subjects in Improved scenario
        Map<String, SubjectPrediction> conMap = new HashMap<>();
        for (SubjectPrediction sp : con.getSubjects()) {
            conMap.put(sp.getSubjectKey(), sp);
        }

        List<String> mostBenefitedSubjects = new ArrayList<>();
        List<SubjectBenefit> benefits = new ArrayList<>();
        for (SubjectPrediction impSub : imp.getSubjects()) {
            SubjectPrediction baseSub = conMap.get(impSub.getSubjectKey());
            if (baseSub != null && impSub.getPredictedSee() != null && baseSub.getPredictedSee() != null) {
                int marksDiff = impSub.getPredictedSee() - baseSub.getPredictedSee();
                int gradeDiff = (impSub.getGradePoints() != null && baseSub.getGradePoints() != null)
                        ? (impSub.getGradePoints() - baseSub.getGradePoints()) : 0;
                benefits.add(new SubjectBenefit(impSub.getDisplayName(), impSub.getFullName(), marksDiff, gradeDiff, baseSub.getPredictedSee(), impSub.getPredictedSee(), baseSub.getGrade(), impSub.getGrade()));
            }
        }
        benefits.sort(Comparator.comparingInt(SubjectBenefit::gradeDiff).reversed()
                .thenComparing(Comparator.comparingInt(SubjectBenefit::marksDiff).reversed()));
        for (SubjectBenefit b : benefits) {
            if (b.marksDiff() > 0 || b.gradeDiff() > 0) {
                if (b.gradeDiff() > 0) {
                    mostBenefitedSubjects.add(String.format(Locale.ROOT, "%s: Grade elevated %s -> %s (+%d GP, +%d SEE marks: %d -> %d)",
                            b.displayName(), b.baseGrade(), b.newGrade(), b.gradeDiff(), b.marksDiff(), b.baseSee(), b.newSee()));
                } else {
                    mostBenefitedSubjects.add(String.format(Locale.ROOT, "%s: +%d SEE marks (%d -> %d), Grade stays %s (within bracket)",
                            b.displayName(), b.marksDiff(), b.baseSee(), b.newSee(), b.baseGrade()));
                }
            }
        }

        // Identify most vulnerable subjects in Declining scenario
        List<String> mostVulnerableSubjects = new ArrayList<>();
        List<SubjectVulnerability> vulns = new ArrayList<>();
        for (SubjectPrediction decSub : dec.getSubjects()) {
            SubjectPrediction baseSub = conMap.get(decSub.getSubjectKey());
            if (baseSub != null && decSub.getPredictedSee() != null && baseSub.getPredictedSee() != null) {
                int marksDrop = baseSub.getPredictedSee() - decSub.getPredictedSee();
                int gradeDrop = (baseSub.getGradePoints() != null && decSub.getGradePoints() != null)
                        ? (baseSub.getGradePoints() - decSub.getGradePoints()) : 0;
                vulns.add(new SubjectVulnerability(decSub.getDisplayName(), decSub.getFullName(), marksDrop, gradeDrop, baseSub.getPredictedSee(), decSub.getPredictedSee(), baseSub.getGrade(), decSub.getGrade()));
            }
        }
        vulns.sort(Comparator.comparingInt(SubjectVulnerability::gradeDrop).reversed()
                .thenComparing(Comparator.comparingInt(SubjectVulnerability::marksDrop).reversed()));
        for (SubjectVulnerability v : vulns) {
            if (v.marksDrop() > 0 || v.gradeDrop() > 0) {
                if (v.gradeDrop() > 0) {
                    mostVulnerableSubjects.add(String.format(Locale.ROOT, "%s: Grade drops %s -> %s (-%d GP, -%d SEE marks: %d -> %d)",
                            v.displayName(), v.baseGrade(), v.newGrade(), v.gradeDrop(), v.marksDrop(), v.baseSee(), v.newSee()));
                } else {
                    mostVulnerableSubjects.add(String.format(Locale.ROOT, "%s: -%d SEE marks (%d -> %d), Grade stays %s (buffered)",
                            v.displayName(), v.marksDrop(), v.baseSee(), v.newSee(), v.baseGrade()));
                }
            }
        }

        // Actionable guidance
        advice.add("Focus revision on subjects with downward trend between Internal 1 and Internal 2.");
        advice.add(String.format(Locale.ROOT, "Maintain regular attendance (current: %.1f%%) to sustain preparation consistency.",
                student.getAttendancePercent() != null ? student.getAttendancePercent() : 75.0));
        if (imp.getPredictedSgpa() > con.getPredictedSgpa()) {
            advice.add(String.format(Locale.ROOT, "Target improving weakest subject (%s) to realize the +%.2f SGPA gain in the Improved scenario.",
                    weakest != null ? weakest.getDisplayName() : "priority subjects", imp.getPredictedSgpa() - con.getPredictedSgpa()));
        } else {
            advice.add(String.format(Locale.ROOT, "Consistently high base performance: Focus on borderline subjects like OE/DS to cross into A+ (10 GP) for maximum SGPA upside.",
                    weakest != null ? weakest.getDisplayName() : "priority subjects"));
        }

        double impDelta = imp.getPredictedSgpa() - con.getPredictedSgpa();
        double decDelta = con.getPredictedSgpa() - dec.getPredictedSgpa();
        int marksGained = imp.getTotalPredictedSeeMarks() - con.getTotalPredictedSeeMarks();
        int marksLost = con.getTotalPredictedSeeMarks() - dec.getTotalPredictedSeeMarks();

        String explanation;
        if (impDelta == 0.0 && marksGained > 0) {
            explanation = String.format(Locale.ROOT,
                    "Under steady preparation, the student is projected to achieve an SGPA of %.2f. "
                            + "A structured +%.1f%% preparation boost adds +%d total SEE marks across %d subjects. "
                            + "Because this student already performs in the upper tier of their letter grade brackets, these mark increases remain within existing grade bands, leaving the predicted SGPA at %.2f. "
                            + "Conversely, exam fatigue or lapses in preparation could depress performance by %d marks and reduce SGPA to %.2f (-%.2f points).",
                    con.getPredictedSgpa(),
                    pred.getConfiguredImprovementPercent(),
                    marksGained,
                    imp.getSubjectsWithMarksIncrease(),
                    con.getPredictedSgpa(),
                    marksLost,
                    dec.getPredictedSgpa(),
                    decDelta
            );
        } else {
            explanation = String.format(Locale.ROOT,
                    "Under steady preparation, the student is projected to attain an SGPA of %.2f. "
                            + "A structured +%.1f%% preparation boost enables an upside to %.2f SGPA (+%.2f points, +%d total SEE marks). "
                            + "Conversely, exam fatigue or unaddressed conceptual gaps in lower-scoring subjects could depress performance to %.2f SGPA (-%.2f points, -%d marks).",
                    con.getPredictedSgpa(),
                    pred.getConfiguredImprovementPercent(),
                    imp.getPredictedSgpa(),
                    impDelta,
                    marksGained,
                    dec.getPredictedSgpa(),
                    decDelta,
                    marksLost
            );
        }

        String providerName = "Deterministic Java Rule Engine";

        // If Gemini is configured, enrich with AI qualitative diagnosis
        if (aiService != null && aiService.isConfigured()) {
            try {
                List<SyllabusDocument> syllabuses = syllabusRepository.findApproved();
                AiAnalysisResult aiRes = aiService.analyzeStudent(student, analyzer, syllabuses);
                if (aiRes != null && aiRes.getAcademicSummary() != null && !aiRes.getAcademicSummary().isBlank()) {
                    explanation += " " + aiRes.getAcademicSummary();
                    if (aiRes.getRecommendedInterventions() != null && !aiRes.getRecommendedInterventions().isEmpty()) {
                        advice.addAll(aiRes.getRecommendedInterventions());
                    }
                    providerName = aiService.getProviderName() + " (" + aiService.getModel() + ")";
                }
            } catch (Exception ex) {
                System.err.println("Note: Gemini AI advisory note: " + ex.getMessage());
            }
        }

        return new AiPredictionInsightsDto(explanation, prioritySubjects, unitFocus, advice, providerName,
                mostBenefitedSubjects, mostVulnerableSubjects);
    }

    private record SubjectBenefit(String displayName, String fullName, int marksDiff, int gradeDiff, int baseSee, int newSee, String baseGrade, String newGrade) {}
    private record SubjectVulnerability(String displayName, String fullName, int marksDrop, int gradeDrop, int baseSee, int newSee, String baseGrade, String newGrade) {}

    private Student loadStudent(String rollNumber) {
        if (rollNumber == null || !rollNumber.matches("^1602-25-733-\\d{3}$")) {
            throw new BadRequestException("Invalid roll number format: " + rollNumber);
        }
        try {
            Student student = studentRepository.findByRollNumber(rollNumber);
            if (student == null) {
                throw new ResourceNotFoundException("Student with roll number " + rollNumber + " was not found.");
            }
            return student;
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading student: " + e.getMessage(), e);
        }
    }
}
