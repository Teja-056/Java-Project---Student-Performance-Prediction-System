package studentperformance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import studentperformance.dto.SyllabusDocumentDto;
import studentperformance.service.SyllabusService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/syllabus")
public class SyllabusController {

    private final SyllabusService syllabusService;

    public SyllabusController(SyllabusService syllabusService) {
        this.syllabusService = syllabusService;
    }

    @GetMapping
    public ResponseEntity<List<SyllabusDocumentDto>> getAllSyllabuses(
            @RequestParam(value = "status", required = false) String status) {
        if ("APPROVED".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(syllabusService.getApprovedDocuments());
        } else if ("REVIEW_REQUIRED".equalsIgnoreCase(status) || "REVIEW".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(syllabusService.getReviewRequiredDocuments());
        }
        return ResponseEntity.ok(syllabusService.getAllDocuments());
    }

    @GetMapping("/approved")
    public ResponseEntity<List<SyllabusDocumentDto>> getApprovedSyllabuses() {
        return ResponseEntity.ok(syllabusService.getApprovedDocuments());
    }

    @GetMapping("/review-required")
    public ResponseEntity<List<SyllabusDocumentDto>> getReviewRequiredSyllabuses() {
        return ResponseEntity.ok(syllabusService.getReviewRequiredDocuments());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SyllabusDocumentDto> uploadSyllabus(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "autoApprove", defaultValue = "false") boolean autoApprove) {
        SyllabusDocumentDto dto = syllabusService.uploadSyllabus(file, autoApprove);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SyllabusDocumentDto> getSyllabusById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(syllabusService.getSyllabusById(id));
    }

    @GetMapping("/{id}/validation-report")
    public ResponseEntity<Map<String, Object>> getValidationReport(@PathVariable("id") Long id) {
        SyllabusDocumentDto doc = syllabusService.getSyllabusById(id);
        return ResponseEntity.ok(Map.of(
                "documentId", id,
                "filename", doc.getFilename(),
                "status", doc.getStatus(),
                "validationReportJson", doc.getValidationReportJson() != null ? doc.getValidationReportJson() : "",
                "validationReport", doc.getValidationReport() != null ? doc.getValidationReport() : ""
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SyllabusDocumentDto> updateSyllabus(
            @PathVariable("id") Long id,
            @RequestBody SyllabusDocumentDto dto) {
        return ResponseEntity.ok(syllabusService.updateSyllabusCurriculum(id, dto));
    }

    @PostMapping("/{id}/revalidate")
    public ResponseEntity<SyllabusDocumentDto> revalidateSyllabus(@PathVariable("id") Long id) {
        return ResponseEntity.ok(syllabusService.revalidateSyllabus(id));
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<SyllabusDocumentDto> reprocessSyllabus(@PathVariable("id") Long id) {
        return ResponseEntity.ok(syllabusService.reprocessDocument(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<SyllabusDocumentDto> approveSyllabus(@PathVariable("id") Long id) {
        return ResponseEntity.ok(syllabusService.approveSyllabus(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<SyllabusDocumentDto> rejectSyllabus(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null && body.containsKey("reason") ? body.get("reason") : "Rejected by faculty";
        return ResponseEntity.ok(syllabusService.rejectSyllabus(id, reason));
    }
}
