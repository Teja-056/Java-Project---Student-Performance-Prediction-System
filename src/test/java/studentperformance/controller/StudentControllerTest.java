package studentperformance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import studentperformance.AiAnalysisResult;
import studentperformance.PerformanceAnalyzer;
import studentperformance.Student;
import studentperformance.dto.AiAnalysisResponseDto;
import studentperformance.dto.StudentReportDto;
import studentperformance.dto.StudentRequestDto;
import studentperformance.dto.StudentResponseDto;
import studentperformance.exception.GlobalExceptionHandler;
import studentperformance.exception.ResourceNotFoundException;
import studentperformance.service.AiAdvisoryService;
import studentperformance.service.StudentService;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentController.class)
@Import(GlobalExceptionHandler.class)
public class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StudentService studentService;

    @MockBean
    private AiAdvisoryService aiAdvisoryService;

    private final PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

    @Test
    @DisplayName("GET /api/students - should return all students")
    void testGetAllStudents() throws Exception {
        Student student = new Student("Aarav Sharma", "1602-25-733-001", 8.85, 8.90, Map.of(), Map.of());
        StudentResponseDto dto = new StudentResponseDto(student, analyzer);
        when(studentService.getAllStudents()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/students")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rollNumber").value("1602-25-733-001"))
                .andExpect(jsonPath("$[0].name").value("Aarav Sharma"))
                .andExpect(jsonPath("$[0].cgpa").value(8.85));
    }

    @Test
    @DisplayName("GET /api/students/{rollNo} - returns student when found")
    void testGetStudentByRollSuccess() throws Exception {
        Student student = new Student("Aarav Sharma", "1602-25-733-001", 8.85, 8.90, Map.of(), Map.of());
        StudentResponseDto dto = new StudentResponseDto(student, analyzer);
        when(studentService.getStudentByRoll("1602-25-733-001")).thenReturn(dto);

        mockMvc.perform(get("/api/students/1602-25-733-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollNumber").value("1602-25-733-001"))
                .andExpect(jsonPath("$.name").value("Aarav Sharma"));
    }

    @Test
    @DisplayName("GET /api/students/{rollNo} - returns 404 when student not found")
    void testGetStudentByRollNotFound() throws Exception {
        when(studentService.getStudentByRoll("1602-25-733-999"))
                .thenThrow(new ResourceNotFoundException("Student with roll number 1602-25-733-999 was not found."));

        mockMvc.perform(get("/api/students/1602-25-733-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Student with roll number 1602-25-733-999 was not found."));
    }

    @Test
    @DisplayName("POST /api/students - returns 400 for validation errors")
    void testCreateStudentValidationFailure() throws Exception {
        StudentRequestDto badRequest = new StudentRequestDto();
        badRequest.setRollNumber("invalid-roll");
        badRequest.setName("");
        badRequest.setCgpa(-1.0);
        badRequest.setLastSemesterSgpa(15.0);

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/students - successfully creates student")
    void testCreateStudentSuccess() throws Exception {
        StudentRequestDto request = new StudentRequestDto();
        request.setRollNumber("1602-25-733-102");
        request.setName("Pooja Reddy");
        request.setCgpa(8.5);
        request.setLastSemesterSgpa(8.7);

        Student student = new Student("Pooja Reddy", "1602-25-733-102", 8.5, 8.7, Map.of(), Map.of());
        StudentResponseDto created = new StudentResponseDto(student, analyzer);
        when(studentService.createStudent(any(StudentRequestDto.class))).thenReturn(created);

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rollNumber").value("1602-25-733-102"))
                .andExpect(jsonPath("$.name").value("Pooja Reddy"));
    }

    @Test
    @DisplayName("GET /api/students/{rollNo}/report - returns detailed report")
    void testGetStudentReport() throws Exception {
        Student student = new Student("Aarav Sharma", "1602-25-733-001", 8.85, 8.90, Map.of(), Map.of());
        StudentReportDto report = new StudentReportDto(student, analyzer, Map.of(1, List.of(1, 2), 2, List.of(3, 4, 5)));
        when(studentService.getStudentReport("1602-25-733-001")).thenReturn(report);

        mockMvc.perform(get("/api/students/1602-25-733-001/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollNumber").value("1602-25-733-001"))
                .andExpect(jsonPath("$.name").value("Aarav Sharma"));
    }

    @Test
    @DisplayName("POST /api/students/{rollNo}/ai-analysis - returns Gemini AI analysis")
    void testGetAiAnalysis() throws Exception {
        AiAnalysisResult result = new AiAnalysisResult(
                "The student demonstrates strong consistency.",
                List.of("Mastery of object-oriented concepts"),
                List.of("Needs focused practice on graph algorithms"),
                List.of("DS"),
                "Consistent positive trajectory",
                "Estimated SGPA > 8.5",
                "LOW",
                List.of(),
                List.of("Continue standard schedule"),
                2,
                Map.of("DS", "Practice recursion"),
                List.of("Weekend mock tests"),
                List.of("Trees", "Graphs")
        );

        AiAnalysisResponseDto analysis = new AiAnalysisResponseDto(result, "Gemini", "gemini-flash-lite-latest", "GROUNDED");
        when(aiAdvisoryService.analyzeStudent("1602-25-733-001")).thenReturn(analysis);

        mockMvc.perform(post("/api/students/1602-25-733-001/ai-analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("Gemini"))
                .andExpect(jsonPath("$.model").value("gemini-flash-lite-latest"))
                .andExpect(jsonPath("$.strengths[0]").value("Mastery of object-oriented concepts"));
    }
}
