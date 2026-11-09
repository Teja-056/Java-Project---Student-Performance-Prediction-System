package studentperformance.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PredictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPredictionReturns200WithScenariosForValidStudent() throws Exception {
        mockMvc.perform(get("/api/prediction/1602-25-733-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollNumber").value("1602-25-733-001"))
                .andExpect(jsonPath("$.studentName").isNotEmpty())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.scenarioName").value("Consistent Performance"))
                .andExpect(jsonPath("$.scenarios.IMPROVED.scenarioName").value("Improved Performance"))
                .andExpect(jsonPath("$.scenarios.DECLINING.scenarioName").value("Declining Performance"))
                .andExpect(jsonPath("$.scenarios.CONSISTENT.subjects.length()").value(8))
                .andExpect(jsonPath("$.scenarios.IMPROVED.subjects.length()").value(8))
                .andExpect(jsonPath("$.scenarios.DECLINING.subjects.length()").value(8))
                .andExpect(jsonPath("$.scenarios.CONSISTENT.subjects[0].subjectKey").exists())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.subjects[0].credits").isNumber())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.subjects[0].actualCie").isNumber())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.subjects[0].predictedSee").isNumber())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.subjects[0].finalMarks").isNumber())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.subjects[0].percentage").isNumber())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.subjects[0].grade").isString())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.subjects[0].gradePoints").isNumber())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.predictedSgpa").isNumber())
                .andExpect(jsonPath("$.scenarios.CONSISTENT.predictedCgpa").isNumber());
    }

    @Test
    void calculateCustomPredictionRecalculatesWithAssumptions() throws Exception {
        String body = """
                {
                    "improvementPercent": 15.0,
                    "declinePercent": 10.0,
                    "attendanceOverride": 90.0
                }
                """;
        mockMvc.perform(post("/api/prediction/1602-25-733-001/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredImprovementPercent").value(15.0))
                .andExpect(jsonPath("$.configuredDeclinePercent").value(10.0))
                .andExpect(jsonPath("$.scenarios.IMPROVED.predictedSgpa").isNumber());
    }

    @Test
    void getAiInsightsReturnsStructuredExplanation() throws Exception {
        mockMvc.perform(get("/api/prediction/1602-25-733-001/ai-insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioDivergenceExplanation").isString())
                .andExpect(jsonPath("$.prioritySubjects").isArray())
                .andExpect(jsonPath("$.actionableAdvice").isArray());
    }

    @Test
    void returns404ForNonexistentStudent() throws Exception {
        mockMvc.perform(get("/api/prediction/1602-25-733-888"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns400ForMalformedRoll() throws Exception {
        mockMvc.perform(get("/api/prediction/INVALID_ROLL"))
                .andExpect(status().isBadRequest());
    }
}
