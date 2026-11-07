package studentperformance.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import studentperformance.SyllabusDocument;
import studentperformance.SyllabusParser;
import studentperformance.SyllabusRepository;
import studentperformance.dto.SyllabusDocumentDto;
import studentperformance.exception.BadRequestException;
import studentperformance.exception.ResourceNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SyllabusService {

    private final SyllabusRepository syllabusRepository;
    private final SyllabusParser syllabusParser;

    public SyllabusService(SyllabusRepository syllabusRepository, SyllabusParser syllabusParser) {
        this.syllabusRepository = syllabusRepository;
        this.syllabusParser = syllabusParser;
    }

    public List<SyllabusDocumentDto> getAllDocuments() {
        try {
            List<SyllabusRepository.DocumentSummary> summaries = syllabusRepository.findAllDocumentSummaries();
            List<SyllabusDocumentDto> dtos = new ArrayList<>();
            for (SyllabusRepository.DocumentSummary s : summaries) {
                SyllabusDocument doc = syllabusRepository.findById(s.documentId());
                if (doc != null) {
                    dtos.add(new SyllabusDocumentDto(s.documentId(), doc, s.status()));
                } else {
                    SyllabusDocument empty = new SyllabusDocument(s.filename());
                    dtos.add(new SyllabusDocumentDto(s.documentId(), empty, s.status()));
                }
            }
            return dtos;
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading syllabus documents: " + e.getMessage(), e);
        }
    }

    public SyllabusDocumentDto getSyllabusById(Long id) {
        try {
            SyllabusDocument doc = syllabusRepository.findById(id);
            if (doc == null) {
                throw new ResourceNotFoundException("Syllabus document with ID " + id + " was not found.");
            }
            return new SyllabusDocumentDto(id, doc, "APPROVED");
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading syllabus document " + id + ": " + e.getMessage(), e);
        }
    }

    public SyllabusDocumentDto uploadSyllabus(MultipartFile file, boolean autoApprove) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a syllabus PDF file to upload.");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new BadRequestException("Invalid file format. Only PDF files are supported for syllabus upload.");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("syllabus-upload-", ".pdf");
            file.transferTo(tempFile.toFile());

            SyllabusDocument parsedDoc = syllabusParser.parse(tempFile);
            if (originalName != null && !originalName.isBlank()) {
                parsedDoc.setFilename(originalName);
            }

            String status = autoApprove ? "APPROVED" : "REVIEW";
            long savedId = syllabusRepository.saveDocument(parsedDoc, status);

            return new SyllabusDocumentDto(savedId, parsedDoc, status);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read or parse syllabus PDF: " + e.getMessage());
        } catch (SQLException e) {
            throw new RuntimeException("Database error saving syllabus document: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public SyllabusDocumentDto approveSyllabus(Long id) {
        try {
            boolean approved = syllabusRepository.approveDocument(id);
            if (!approved) {
                throw new ResourceNotFoundException("Syllabus document with ID " + id + " was not found.");
            }
            return getSyllabusById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Database error approving syllabus: " + e.getMessage(), e);
        }
    }
}
