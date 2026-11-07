package studentperformance.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studentperformance.dto.SubjectAnalyticsDto;
import studentperformance.service.StudentService;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final StudentService studentService;

    public AnalyticsController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<SubjectAnalyticsDto>> getSubjectAnalytics() {
        return ResponseEntity.ok(studentService.getClassSubjectAnalytics());
    }
}
