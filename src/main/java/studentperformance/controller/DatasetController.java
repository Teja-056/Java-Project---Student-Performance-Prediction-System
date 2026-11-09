package studentperformance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import studentperformance.dto.DatasetDto;
import studentperformance.dto.DatasetUploadResponseDto;
import studentperformance.service.DatasetService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public ResponseEntity<List<DatasetDto>> getAllDatasets() {
        return ResponseEntity.ok(datasetService.getAllDatasets());
    }

    @GetMapping("/active")
    public ResponseEntity<DatasetDto> getActiveDataset() {
        return ResponseEntity.ok(datasetService.getActiveDataset());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasetDto> getDatasetById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(datasetService.getDatasetById(id));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DatasetUploadResponseDto> uploadDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "semester", required = false) String semester,
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "activateImmediately", defaultValue = "false") boolean activateImmediately) {
        DatasetUploadResponseDto response = datasetService.uploadAndCreateDataset(file, name, semester, academicYear, activateImmediately);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<DatasetDto> activateDataset(@PathVariable("id") Long id) {
        DatasetDto activated = datasetService.activateDataset(id);
        return ResponseEntity.ok(activated);
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<DatasetDto> activateDatasetPut(@PathVariable("id") Long id) {
        DatasetDto activated = datasetService.activateDataset(id);
        return ResponseEntity.ok(activated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDataset(@PathVariable("id") Long id) {
        datasetService.deleteDataset(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Dataset #" + id + " was deleted successfully."
        ));
    }
}
