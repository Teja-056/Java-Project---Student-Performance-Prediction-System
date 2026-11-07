package studentperformance.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import studentperformance.ExcelImporter;
import studentperformance.dto.ExcelUploadResponseDto;
import studentperformance.service.ExcelImportService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/excel")
public class ExcelController {

    private final ExcelImportService excelImportService;
    private final ExcelImporter excelImporter;

    public ExcelController(ExcelImportService excelImportService, ExcelImporter excelImporter) {
        this.excelImportService = excelImportService;
        this.excelImporter = excelImporter;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExcelUploadResponseDto> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "commit", defaultValue = "false") boolean commit) {
        ExcelUploadResponseDto response = excelImportService.processUpload(file, commit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate() throws IOException {
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("student_import_template_", ".xlsx");
        try {
            excelImporter.generateTemplate(tempFile);
            byte[] bytes = java.nio.file.Files.readAllBytes(tempFile);
            ByteArrayResource resource = new ByteArrayResource(bytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student_import_template.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(resource);
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }
}
