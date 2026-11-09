package studentperformance.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import studentperformance.dto.SubjectAnalyticsDto;
import studentperformance.dto.SubjectCategoryStatsDto;
import studentperformance.dto.SubjectPerformanceResponseDto;
import studentperformance.dto.SubjectStudentEntryDto;
import studentperformance.exception.GlobalExceptionHandler;
import studentperformance.service.StudentService;
import studentperformance.service.SubjectPerformanceService;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@Import(GlobalExceptionHandler.class)
public class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentService studentService;

    @MockBean
    private SubjectPerformanceService subjectPerformanceService;

    @Test
    @DisplayName("GET /api/analytics/subjects - returns analytics for all theory subjects")
    void testGetSubjectAnalytics() throws Exception {
        SubjectAnalyticsDto javaDto = new SubjectAnalyticsDto(
                "JAVA", "Java Programming", 100, 85, 8, 76.8,
                "Strong performance with high engagement across internals."
        );
        SubjectAnalyticsDto dsDto = new SubjectAnalyticsDto(
                "DS", "Data Structures", 100, 62, 18, 69.7,
                "Moderate performance; students require reinforcement in graph algorithms."
        );

        when(studentService.getClassSubjectAnalytics()).thenReturn(List.of(javaDto, dsDto));

        mockMvc.perform(get("/api/analytics/subjects")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subjectCode").value("JAVA"))
                .andExpect(jsonPath("$[0].subjectName").value("Java Programming"))
                .andExpect(jsonPath("$[0].totalStudentsEvaluated").value(100))
                .andExpect(jsonPath("$[0].performingWellCount").value(85))
                .andExpect(jsonPath("$[0].requiringImprovementCount").value(8))
                .andExpect(jsonPath("$[0].averagePercentage").value(76.8))
                .andExpect(jsonPath("$[1].subjectCode").value("DS"));
    }

    @Test
    @DisplayName("GET /api/analytics/subject-performance - returns detailed subject analytics")
    void testGetSubjectPerformance() throws Exception {
        SubjectPerformanceResponseDto dto = new SubjectPerformanceResponseDto();
        dto.setSubjectCode("DS");
        dto.setSubjectName("Data Structures");
        dto.setCieMaxMarks(40);
        dto.setAssessmentType("COMBINED");
        dto.setTotalStudents(101);
        dto.setAssessedCount(101);
        dto.setUnassessedCount(0);
        dto.setAveragePercentage(78.5);
        dto.setAverageMarks(31.4);
        dto.setHighestScore(39.5);
        dto.setHighestStudentRoll("1602-25-733-001");
        dto.setHighestStudentName("Pooja Mishra");
        dto.setLowestScore(18.0);
        dto.setLowestStudentRoll("1602-25-733-099");
        dto.setLowestStudentName("Student 99");

        dto.setCategoryStats(List.of(
                new SubjectCategoryStatsDto("Excellent", "90–100%", 15, 14.9),
                new SubjectCategoryStatsDto("Very Good", "80–<90%", 30, 29.7),
                new SubjectCategoryStatsDto("Good", "70–<80%", 35, 34.7),
                new SubjectCategoryStatsDto("Average", "60–<70%", 15, 14.9),
                new SubjectCategoryStatsDto("Needs Improvement", "40–<60%", 5, 5.0),
                new SubjectCategoryStatsDto("At Risk", "Below 40%", 1, 1.0)
        ));

        SubjectStudentEntryDto student1 = new SubjectStudentEntryDto(
                "1602-25-733-001", "Pooja Mishra", 39.5, 40, 98.75, "Excellent", 39.0, 40.0, 2.5
        );
        dto.setStudents(List.of(student1));

        when(subjectPerformanceService.getSubjectPerformance(eq("DS"), eq("COMBINED"))).thenReturn(dto);

        mockMvc.perform(get("/api/analytics/subject-performance?subject=DS&assessment=COMBINED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectCode").value("DS"))
                .andExpect(jsonPath("$.subjectName").value("Data Structures"))
                .andExpect(jsonPath("$.totalStudents").value(101))
                .andExpect(jsonPath("$.assessedCount").value(101))
                .andExpect(jsonPath("$.unassessedCount").value(0))
                .andExpect(jsonPath("$.averagePercentage").value(78.5))
                .andExpect(jsonPath("$.highestScore").value(39.5))
                .andExpect(jsonPath("$.highestStudentName").value("Pooja Mishra"))
                .andExpect(jsonPath("$.categoryStats.length()").value(6))
                .andExpect(jsonPath("$.categoryStats[0].category").value("Excellent"))
                .andExpect(jsonPath("$.students[0].rollNumber").value("1602-25-733-001"));
    }
}
