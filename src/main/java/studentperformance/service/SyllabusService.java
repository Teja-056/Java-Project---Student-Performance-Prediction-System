package studentperformance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import studentperformance.SyllabusCategory;
import studentperformance.SyllabusDocument;
import studentperformance.SyllabusParser;
import studentperformance.SyllabusRepository;
import studentperformance.SyllabusSubject;
import studentperformance.SyllabusTopic;
import studentperformance.SyllabusUnit;
import studentperformance.dto.SyllabusDocumentDto;
import studentperformance.exception.BadRequestException;
import studentperformance.exception.ResourceNotFoundException;
import studentperformance.syllabus.ExtractedCurriculum;
import studentperformance.syllabus.GeminiSyllabusExtractor;
import studentperformance.syllabus.SyllabusValidator;
import studentperformance.syllabus.ValidationReport;

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
    private final GeminiSyllabusExtractor geminiExtractor;
    private final SyllabusValidator syllabusValidator;
    private final ObjectMapper objectMapper;

    public SyllabusService(SyllabusRepository syllabusRepository,
                           SyllabusParser syllabusParser,
                           GeminiSyllabusExtractor geminiExtractor,
                           SyllabusValidator syllabusValidator,
                           ObjectMapper objectMapper) {
        this.syllabusRepository = syllabusRepository;
        this.syllabusParser = syllabusParser;
        this.geminiExtractor = geminiExtractor;
        this.syllabusValidator = syllabusValidator;
        this.objectMapper = objectMapper;
    }

    public List<SyllabusDocumentDto> getAllDocuments() {
        try {
            List<SyllabusRepository.DocumentSummary> summaries = syllabusRepository.findAllDocumentSummaries();
            List<SyllabusDocumentDto> dtos = new ArrayList<>();
            for (SyllabusRepository.DocumentSummary s : summaries) {
                SyllabusDocument doc = syllabusRepository.findById(s.documentId());
                if (doc != null) {
                    SyllabusDocumentDto dto = new SyllabusDocumentDto(s.documentId(), doc, s.status());
                    dto.setInstitution(s.institution());
                    dto.setSemester(s.semester());
                    dto.setExtractionEngine(s.extractionEngine() != null ? s.extractionEngine() : doc.getExtractionEngine());
                    dto.setPdfPath(s.pdfPath() != null ? s.pdfPath() : doc.getPdfPath());
                    dto.setParentDocumentId(s.parentDocumentId() != null ? s.parentDocumentId() : doc.getParentDocumentId());
                    boolean hasPdf = SyllabusDocumentDto.checkPdfExistsOnDisk(dto.getPdfPath(), dto.getFilename());
                    dto.setHasPdfOnDisk(hasPdf);
                    dto.setReprocessAvailable(hasPdf);
                    attachParsedValidationReport(dto, doc.getValidationReportJson());
                    dtos.add(dto);
                } else {
                    SyllabusDocument empty = new SyllabusDocument(s.filename());
                    empty.setDocumentId(s.documentId());
                    empty.setStatus(s.status());
                    empty.setInstitution(s.institution());
                    empty.setSemester(s.semester());
                    empty.setExtractionEngine(s.extractionEngine());
                    empty.setPdfPath(s.pdfPath());
                    empty.setParentDocumentId(s.parentDocumentId());
                    SyllabusDocumentDto dto = new SyllabusDocumentDto(s.documentId(), empty, s.status());
                    boolean hasPdf = SyllabusDocumentDto.checkPdfExistsOnDisk(s.pdfPath(), s.filename());
                    dto.setHasPdfOnDisk(hasPdf);
                    dto.setReprocessAvailable(hasPdf);
                    dtos.add(dto);
                }
            }
            return dtos;
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading syllabus documents: " + e.getMessage(), e);
        }
    }

    public List<SyllabusDocumentDto> getApprovedDocuments() {
        try {
            List<SyllabusDocument> approvedDocs = syllabusRepository.findApproved();
            List<SyllabusDocumentDto> dtos = new ArrayList<>();
            for (SyllabusDocument doc : approvedDocs) {
                SyllabusDocumentDto dto = new SyllabusDocumentDto(doc.getDocumentId(), doc, "APPROVED");
                attachParsedValidationReport(dto, doc.getValidationReportJson());
                dtos.add(dto);
            }
            return dtos;
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading approved syllabuses: " + e.getMessage(), e);
        }
    }

    public List<SyllabusDocumentDto> getReviewRequiredDocuments() {
        try {
            List<SyllabusDocument> reviewDocs = syllabusRepository.findReviewRequired();
            List<SyllabusDocumentDto> dtos = new ArrayList<>();
            for (SyllabusDocument doc : reviewDocs) {
                SyllabusDocumentDto dto = new SyllabusDocumentDto(doc.getDocumentId(), doc, doc.getStatus());
                attachParsedValidationReport(dto, doc.getValidationReportJson());
                dtos.add(dto);
            }
            return dtos;
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading review-required syllabuses: " + e.getMessage(), e);
        }
    }

    public SyllabusDocumentDto getSyllabusById(Long id) {
        try {
            SyllabusDocument doc = syllabusRepository.findById(id);
            if (doc == null) {
                throw new ResourceNotFoundException("Syllabus document with ID " + id + " was not found.");
            }
            SyllabusDocumentDto dto = new SyllabusDocumentDto(id, doc, doc.getStatus());
            attachParsedValidationReport(dto, doc.getValidationReportJson());
            return dto;
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

        if (file.getSize() > 25 * 1024 * 1024) {
            throw new BadRequestException("File size exceeds 25 MB limit.");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("syllabus-upload-", ".pdf");
            file.transferTo(tempFile.toFile());

            // 1. PDF Validation using Apache PDFBox
            try (PDDocument pdDoc = Loader.loadPDF(tempFile.toFile())) {
                int pages = pdDoc.getNumberOfPages();
                if (pages <= 0 || pages > 100) {
                    throw new BadRequestException("Invalid PDF page count: " + pages + " (must be between 1 and 100).");
                }
            } catch (IOException e) {
                throw new BadRequestException("Invalid or corrupted PDF file: " + e.getMessage());
            }

            // 2. Gemini Semantic Extraction
            ExtractedCurriculum curriculum;
            try {
                curriculum = geminiExtractor.extract(tempFile);
            } catch (Exception e) {
                try {
                    syllabusRepository.saveFailedDocument(originalName, "Gemini extraction failed: " + e.getMessage(), null);
                } catch (Exception ignored) {}
                throw new BadRequestException("Gemini syllabus extraction failed: " + e.getMessage());
            }

            // 3. Java Syllabus Validation
            ValidationReport validationReport = syllabusValidator.validate(curriculum);

            // 4. Transform to SyllabusDocument entity
            SyllabusDocument doc = syllabusRepository.fromExtractedCurriculum(originalName, curriculum, validationReport);

            // Persist uploaded file to permanent syllabus folder for reproducible reprocessing
            try {
                Path uploadDir = Path.of("uploads", "syllabus");
                Files.createDirectories(uploadDir);
                Path savedFile = uploadDir.resolve(originalName);
                Files.copy(tempFile, savedFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                doc.setPdfPath(savedFile.toAbsolutePath().toString());
            } catch (Exception ignored) {}
            doc.setExtractionEngine("GEMINI_FLASH_LITE");

            // 5. Save as REVIEW_REQUIRED (Never auto-approve immediately; requires explicit faculty approval)
            String targetStatus = "REVIEW_REQUIRED";
            doc.setStatus(targetStatus);

            long savedId = syllabusRepository.saveDocument(doc, targetStatus);
            doc.setDocumentId(savedId);

            SyllabusDocumentDto dto = new SyllabusDocumentDto(savedId, doc, targetStatus);
            dto.setValidationReport(validationReport);
            return dto;

        } catch (SQLException e) {
            throw new BadRequestException("Failed to persist syllabus document: " + e.getMessage());
        } catch (IOException e) {
            throw new BadRequestException("File I/O error during syllabus processing: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
        }
    }

    public SyllabusDocumentDto approveSyllabus(Long id) {
        try {
            SyllabusDocument doc = syllabusRepository.findById(id);
            if (doc == null) {
                throw new ResourceNotFoundException("Syllabus document with ID " + id + " was not found.");
            }

            if (!"REVIEW_REQUIRED".equalsIgnoreCase(doc.getStatus()) && !"REVIEW".equalsIgnoreCase(doc.getStatus())) {
                throw new BadRequestException("Only documents in REVIEW_REQUIRED status can be approved. Current status: " + doc.getStatus());
            }

            boolean approved = syllabusRepository.approveDocument(id);
            if (!approved) {
                throw new ResourceNotFoundException("Failed to approve syllabus document with ID " + id);
            }
            return getSyllabusById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Database error approving syllabus: " + e.getMessage(), e);
        }
    }

    public SyllabusDocumentDto rejectSyllabus(Long id, String reason) {
        try {
            SyllabusDocument doc = syllabusRepository.findById(id);
            if (doc == null) {
                throw new ResourceNotFoundException("Syllabus document with ID " + id + " was not found.");
            }
            boolean rejected = syllabusRepository.rejectDocument(id, reason);
            if (!rejected) {
                throw new ResourceNotFoundException("Failed to reject syllabus document with ID " + id);
            }
            return getSyllabusById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Database error rejecting syllabus: " + e.getMessage(), e);
        }
    }

    public SyllabusDocumentDto updateSyllabusCurriculum(Long id, SyllabusDocumentDto dto) {
        if (id == null || dto == null) {
            throw new BadRequestException("Document ID and curriculum update payload must not be null.");
        }
        try {
            SyllabusDocument existing = syllabusRepository.findById(id);
            if (existing == null) {
                throw new ResourceNotFoundException("Syllabus document with ID " + id + " was not found.");
            }
            if ("APPROVED".equalsIgnoreCase(existing.getStatus())) {
                throw new BadRequestException("Cannot edit an already APPROVED document. Please create or upload a new draft revision.");
            }

            SyllabusDocument updatedDoc = convertDtoToDocument(dto, existing);
            ExtractedCurriculum extracted = syllabusRepository.toExtractedCurriculum(updatedDoc);
            ValidationReport validationReport = syllabusValidator.validate(extracted);

            try {
                updatedDoc.setValidationReportJson(objectMapper.writeValueAsString(validationReport));
            } catch (Exception e) {
                updatedDoc.setValidationReportJson(validationReport.formatConsoleSummary());
            }

            syllabusRepository.updateDocumentCurriculum(id, updatedDoc, validationReport);
            return getSyllabusById(id);
        } catch (SQLException e) {
            throw new BadRequestException("Failed to update syllabus curriculum: " + e.getMessage());
        }
    }

    public SyllabusDocumentDto revalidateSyllabus(Long id) {
        if (id == null) {
            throw new BadRequestException("Document ID must not be null.");
        }
        try {
            SyllabusDocument existing = syllabusRepository.findById(id);
            if (existing == null) {
                throw new ResourceNotFoundException("Syllabus document with ID " + id + " was not found.");
            }
            ExtractedCurriculum extracted = syllabusRepository.toExtractedCurriculum(existing);
            ValidationReport validationReport = syllabusValidator.validate(extracted);

            String reportJson;
            try {
                reportJson = objectMapper.writeValueAsString(validationReport);
            } catch (Exception e) {
                reportJson = validationReport.formatConsoleSummary();
            }

            syllabusRepository.updateValidationReport(id, reportJson);
            return getSyllabusById(id);
        } catch (SQLException e) {
            throw new BadRequestException("Failed to revalidate syllabus: " + e.getMessage());
        }
    }

    public SyllabusDocumentDto reprocessDocument(Long id) {
        if (id == null) {
            throw new BadRequestException("Document ID must not be null.");
        }
        try {
            SyllabusDocument existing = syllabusRepository.findById(id);
            if (existing == null) {
                throw new ResourceNotFoundException("Syllabus document with ID " + id + " was not found.");
            }

            Path pdfPath = findPdfPath(existing);
            if (pdfPath == null || !Files.exists(pdfPath)) {
                throw new BadRequestException("Original PDF file for document " + id + " ('" + existing.getFilename() + "') was not found on disk. Document requires manual correction.");
            }

            ExtractedCurriculum curriculum;
            try {
                curriculum = geminiExtractor.extract(pdfPath);
            } catch (Exception e) {
                throw new BadRequestException("Reprocessing failed during Gemini extraction: " + e.getMessage());
            }

            ValidationReport report = syllabusValidator.validate(curriculum);

            if ("APPROVED".equalsIgnoreCase(existing.getStatus())) {
                // Never overwrite an approved document directly; create a new review revision
                SyllabusDocument newRevision = syllabusRepository.fromExtractedCurriculum(existing.getFilename(), curriculum, report);
                newRevision.setStatus("REVIEW_REQUIRED");
                newRevision.setParentDocumentId(id);
                newRevision.setExtractionEngine("GEMINI_FLASH_LITE");
                newRevision.setPdfPath(pdfPath.toAbsolutePath().toString());
                long newDocId = syllabusRepository.saveDocument(newRevision, "REVIEW_REQUIRED");
                return getSyllabusById(newDocId);
            } else {
                // Update existing REVIEW_REQUIRED or FAILED document
                SyllabusDocument updatedDoc = syllabusRepository.fromExtractedCurriculum(existing.getFilename(), curriculum, report);
                updatedDoc.setDocumentId(id);
                updatedDoc.setStatus(existing.getStatus());
                updatedDoc.setExtractionEngine("GEMINI_FLASH_LITE");
                updatedDoc.setPdfPath(pdfPath.toAbsolutePath().toString());
                syllabusRepository.updateDocumentCurriculum(id, updatedDoc, report);
                return getSyllabusById(id);
            }
        } catch (SQLException e) {
            throw new BadRequestException("Failed to reprocess syllabus document: " + e.getMessage());
        }
    }

    public Path findPdfPath(SyllabusDocument doc) {
        if (doc == null) return null;
        if (doc.getPdfPath() != null && !doc.getPdfPath().isBlank()) {
            try {
                Path p = Path.of(doc.getPdfPath());
                if (Files.exists(p)) return p;
            } catch (Exception ignored) {}
        }
        String name = doc.getFilename();
        if (name == null || name.isBlank()) return null;
        try {
            Path inUploads = Path.of("uploads", "syllabus", name);
            if (Files.exists(inUploads)) return inUploads;
            Path inRoot = Path.of(name);
            if (Files.exists(inRoot)) return inRoot;
            Path inDownloads = Path.of(System.getProperty("user.home"), "Downloads", name);
            if (Files.exists(inDownloads)) return inDownloads;
        } catch (Exception ignored) {}
        return null;
    }

    private SyllabusDocument convertDtoToDocument(SyllabusDocumentDto dto, SyllabusDocument existing) {
        SyllabusDocument doc = new SyllabusDocument(existing.getFilename());
        doc.setDocumentId(existing.getDocumentId());
        doc.setStatus(existing.getStatus());
        doc.setInstitution(dto.getInstitution() != null ? dto.getInstitution() : existing.getInstitution());
        doc.setAcademicScheme(dto.getAcademicScheme() != null ? dto.getAcademicScheme() : existing.getAcademicScheme());
        doc.setAcademicYear(dto.getAcademicYear() != null ? dto.getAcademicYear() : existing.getAcademicYear());
        doc.setBranch(dto.getBranch() != null ? dto.getBranch() : existing.getBranch());
        doc.setSemester(dto.getSemester() != null ? dto.getSemester() : existing.getSemester());

        if (dto.getSubjects() != null) {
            for (SyllabusDocumentDto.SubjectDetailDto subDto : dto.getSubjects()) {
                SyllabusSubject s = new SyllabusSubject(subDto.getSubjectName());
                s.setDepartmentCode(subDto.getDepartmentCode() != null ? subDto.getDepartmentCode() : "CSE");
                s.setSemester(subDto.getSemester() != null ? subDto.getSemester() : doc.getSemester());
                s.setCourseCode(subDto.getCourseCode() != null ? subDto.getCourseCode() : "");
                s.setCredits(subDto.getCredits() != null ? subDto.getCredits() : "");
                s.setTotalMarks(subDto.getTotalMarks() != null ? subDto.getTotalMarks() : "40");
                s.setOpenElective(subDto.isOpenElective());
                try {
                    s.setCategory(SyllabusCategory.valueOf(subDto.getCategory()));
                } catch (Exception e) {
                    s.setCategory(subDto.isOpenElective() ? SyllabusCategory.OPEN_ELECTIVE : SyllabusCategory.THEORY);
                }

                if (subDto.getUnits() != null) {
                    for (SyllabusDocumentDto.UnitDetailDto uDto : subDto.getUnits()) {
                        SyllabusUnit u = new SyllabusUnit(uDto.getUnitNumber(), uDto.getUnitTitle(), uDto.getPageNumber());
                        if (uDto.getTopicDetails() != null && !uDto.getTopicDetails().isEmpty()) {
                            for (SyllabusDocumentDto.TopicDetailDto tDto : uDto.getTopicDetails()) {
                                if (tDto.getText() != null && !tDto.getText().isBlank()) {
                                    u.getTopics().add(new SyllabusTopic(tDto.getText().trim(), tDto.getPageNumber()));
                                }
                            }
                        } else if (uDto.getTopics() != null) {
                            for (String topicStr : uDto.getTopics()) {
                                if (topicStr != null && !topicStr.isBlank()) {
                                    u.getTopics().add(new SyllabusTopic(topicStr.trim(), uDto.getPageNumber()));
                                }
                            }
                        }
                        s.getUnits().add(u);
                    }
                }
                doc.getSubjects().add(s);
            }
        }
        return doc;
    }

    private void attachParsedValidationReport(SyllabusDocumentDto dto, String reportJson) {
        if (reportJson != null && !reportJson.isBlank()) {
            dto.setValidationReportJson(reportJson);
            try {
                if (reportJson.trim().startsWith("{")) {
                    dto.setValidationReport(objectMapper.readTree(reportJson));
                }
            } catch (Exception ignored) {}
        }
    }
}
