package studentperformance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import studentperformance.dto.SyllabusDocumentDto;
import studentperformance.service.SyllabusService;

import java.util.List;

@RestController
@RequestMapping("/api/syllabus")
public class SyllabusController {

    private final SyllabusService syllabusService;

    public SyllabusController(SyllabusService syllabusService) {
        this.syllabusService = syllabusService;
    }

    @GetMapping
    public ResponseEntity<List<SyllabusDocumentDto>> getAllSyllabuses() {
        return ResponseEntity.ok(syllabusService.getAllDocuments());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SyllabusDocumentDto> uploadSyllabus(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "autoApprove", defaultValue = "true") boolean autoApprove) {
        SyllabusDocumentDto dto = syllabusService.uploadSyllabus(file, autoApprove);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SyllabusDocumentDto> getSyllabusById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(syllabusService.getSyllabusById(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<SyllabusDocumentDto> approveSyllabus(@PathVariable("id") Long id) {
        return ResponseEntity.ok(syllabusService.approveSyllabus(id));
    }
}
