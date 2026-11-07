package studentperformance.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import studentperformance.dto.AiAnalysisResponseDto;
import studentperformance.dto.StudentReportDto;
import studentperformance.dto.StudentRequestDto;
import studentperformance.dto.StudentResponseDto;
import studentperformance.service.AiAdvisoryService;
import studentperformance.service.StudentService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;
    private final AiAdvisoryService aiAdvisoryService;

    public StudentController(StudentService studentService, AiAdvisoryService aiAdvisoryService) {
        this.studentService = studentService;
        this.aiAdvisoryService = aiAdvisoryService;
    }

    @GetMapping
    public ResponseEntity<List<StudentResponseDto>> getAllStudents() {
        return ResponseEntity.ok(studentService.getAllStudents());
    }

    @GetMapping("/{rollNo}")
    public ResponseEntity<StudentResponseDto> getStudentByRoll(@PathVariable("rollNo") String rollNo) {
        return ResponseEntity.ok(studentService.getStudentByRoll(rollNo));
    }

    @PostMapping
    public ResponseEntity<StudentResponseDto> createStudent(@Valid @RequestBody StudentRequestDto request) {
        StudentResponseDto created = studentService.createStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{rollNo}")
    public ResponseEntity<StudentResponseDto> updateStudent(@PathVariable("rollNo") String rollNo,
                                                           @Valid @RequestBody StudentRequestDto request) {
        return ResponseEntity.ok(studentService.updateStudent(rollNo, request));
    }

    @DeleteMapping("/{rollNo}")
    public ResponseEntity<Map<String, Object>> deleteStudent(@PathVariable("rollNo") String rollNo) {
        studentService.deleteStudent(rollNo);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Student " + rollNo + " was successfully deleted."
        ));
    }

    @GetMapping("/{rollNo}/report")
    public ResponseEntity<StudentReportDto> getStudentReport(@PathVariable("rollNo") String rollNo) {
        return ResponseEntity.ok(studentService.getStudentReport(rollNo));
    }

    @PostMapping("/{rollNo}/ai-analysis")
    public ResponseEntity<AiAnalysisResponseDto> getAiAnalysis(@PathVariable("rollNo") String rollNo) {
        return ResponseEntity.ok(aiAdvisoryService.analyzeStudent(rollNo));
    }
}
