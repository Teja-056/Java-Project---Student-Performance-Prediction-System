package studentperformance.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studentperformance.dto.SubjectAnalyticsDto;
import studentperformance.dto.SubjectPerformanceResponseDto;
import studentperformance.service.StudentService;
import studentperformance.service.SubjectPerformanceService;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final StudentService studentService;
    private final SubjectPerformanceService subjectPerformanceService;

    public AnalyticsController(StudentService studentService, SubjectPerformanceService subjectPerformanceService) {
        this.studentService = studentService;
        this.subjectPerformanceService = subjectPerformanceService;
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<SubjectAnalyticsDto>> getSubjectAnalytics() {
        return ResponseEntity.ok(studentService.getClassSubjectAnalytics());
    }

    @GetMapping("/subject-performance")
    public ResponseEntity<SubjectPerformanceResponseDto> getSubjectPerformance(
            @RequestParam(name = "subject", defaultValue = "DS") String subject,
            @RequestParam(name = "assessment", defaultValue = "COMBINED") String assessment) {
        return ResponseEntity.ok(subjectPerformanceService.getSubjectPerformance(subject, assessment));
    }
}
