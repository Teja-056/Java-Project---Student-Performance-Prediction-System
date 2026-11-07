package studentperformance.service;

import org.springframework.stereotype.Service;
import studentperformance.*;
import studentperformance.dto.AiAnalysisResponseDto;
import studentperformance.exception.AiServiceException;
import studentperformance.exception.BadRequestException;
import studentperformance.exception.ResourceNotFoundException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@Service
public class AiAdvisoryService {

    private final StudentRepository studentRepository;
    private final SyllabusRepository syllabusRepository;
    private final PerformanceAnalyzer performanceAnalyzer;
    private final AiService aiService;

    public AiAdvisoryService(StudentRepository studentRepository,
                             SyllabusRepository syllabusRepository,
                             PerformanceAnalyzer performanceAnalyzer,
                             AiService aiService) {
        this.studentRepository = studentRepository;
        this.syllabusRepository = syllabusRepository;
        this.performanceAnalyzer = performanceAnalyzer;
        this.aiService = aiService;
    }

    public AiAnalysisResponseDto analyzeStudent(String rollNumber) {
        if (rollNumber == null || !rollNumber.matches("^1602-25-733-\\d{3}$")) {
            throw new BadRequestException("Invalid roll number format: " + rollNumber);
        }

        Student student;
        try {
            student = studentRepository.findByRollNumber(rollNumber);
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading student: " + e.getMessage(), e);
        }

        if (student == null) {
            throw new ResourceNotFoundException("Student with roll number " + rollNumber + " was not found.");
        }

        if (!performanceAnalyzer.hasBothInternals(student)) {
            throw new BadRequestException("Complete marks for both Internal 1 and Internal 2 are required before generating an AI diagnosis.");
        }

        if (!aiService.isConfigured()) {
            throw new AiServiceException("AI provider (" + aiService.getProviderName() + ") is not configured with an API key. Please configure GEMINI_API_KEY.");
        }

        // Check for approved college syllabus documents in MySQL
        List<SyllabusDocument> syllabuses = List.of();
        String groundingStatus = "NO_APPROVED_SYLLABUS_AVAILABLE";
        try {
            syllabuses = syllabusRepository.findApproved();
            if (syllabuses != null && !syllabuses.isEmpty()) {
                groundingStatus = "GROUNDED (" + syllabuses.size() + " approved syllabus document(s) loaded)";
            }
        } catch (Exception e) {
            System.err.println("Note: Syllabus lookup: " + e.getMessage());
        }

        try {
            // Strictly anonymized: name and roll number are never sent to Gemini
            AiAnalysisResult result = aiService.analyzeStudent(student, performanceAnalyzer, syllabuses);
            return new AiAnalysisResponseDto(result, aiService.getProviderName(), aiService.getModel(), groundingStatus);
        } catch (IOException e) {
            throw new AiServiceException("Failed to obtain AI analysis from " + aiService.getProviderName() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("AI analysis request was interrupted.", e);
        }
    }
}
