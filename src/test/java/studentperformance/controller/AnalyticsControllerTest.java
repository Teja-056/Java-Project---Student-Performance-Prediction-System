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
import studentperformance.exception.GlobalExceptionHandler;
import studentperformance.service.StudentService;

import java.util.List;

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
}
