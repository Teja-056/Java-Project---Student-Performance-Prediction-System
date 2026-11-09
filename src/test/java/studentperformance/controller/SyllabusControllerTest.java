package studentperformance.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import studentperformance.SyllabusDocument;
import studentperformance.dto.SyllabusDocumentDto;
import studentperformance.exception.BadRequestException;
import studentperformance.exception.GlobalExceptionHandler;
import studentperformance.exception.ResourceNotFoundException;
import studentperformance.service.SyllabusService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SyllabusController.class)
@Import(GlobalExceptionHandler.class)
public class SyllabusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SyllabusService syllabusService;

    @Test
    @DisplayName("GET /api/syllabus - returns list of syllabus documents")
    void testGetAllSyllabuses() throws Exception {
        SyllabusDocument doc = new SyllabusDocument("cse_sem4_syllabus.pdf");
        SyllabusDocumentDto dto = new SyllabusDocumentDto(1L, doc, "APPROVED");

        when(syllabusService.getAllDocuments()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/syllabus")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].filename").value("cse_sem4_syllabus.pdf"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /api/syllabus/upload - uploads syllabus PDF")
    void testUploadSyllabus() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "syllabus.pdf", "application/pdf",
                "%PDF-1.4 sample content".getBytes()
        );

        SyllabusDocument doc = new SyllabusDocument("syllabus.pdf");
        SyllabusDocumentDto dto = new SyllabusDocumentDto(1L, doc, "APPROVED");

        when(syllabusService.uploadSyllabus(any(), eq(true))).thenReturn(dto);

        mockMvc.perform(multipart("/api/syllabus/upload")
                        .file(file)
                        .param("autoApprove", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /api/syllabus/{id}/approve - approves document")
    void testApproveSyllabus() throws Exception {
        SyllabusDocument doc = new SyllabusDocument("syllabus.pdf");
        SyllabusDocumentDto dto = new SyllabusDocumentDto(1L, doc, "APPROVED");

        when(syllabusService.approveSyllabus(1L)).thenReturn(dto);

        mockMvc.perform(post("/api/syllabus/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/syllabus/{id} - returns 404 for missing document")
    void testGetSyllabusNotFound() throws Exception {
        when(syllabusService.getSyllabusById(999L))
                .thenThrow(new ResourceNotFoundException("Syllabus document with ID 999 was not found."));

        mockMvc.perform(get("/api/syllabus/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Syllabus document with ID 999 was not found."));
    }

    @Test
    @DisplayName("GET /api/syllabus/approved - returns approved syllabuses")
    void testGetApprovedSyllabuses() throws Exception {
        SyllabusDocument doc = new SyllabusDocument("approved_syllabus.pdf");
        SyllabusDocumentDto dto = new SyllabusDocumentDto(10L, doc, "APPROVED");

        when(syllabusService.getApprovedDocuments()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/syllabus/approved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/syllabus/review-required - returns review-required syllabuses")
    void testGetReviewRequiredSyllabuses() throws Exception {
        SyllabusDocument doc = new SyllabusDocument("draft_syllabus.pdf");
        SyllabusDocumentDto dto = new SyllabusDocumentDto(11L, doc, "REVIEW_REQUIRED");

        when(syllabusService.getReviewRequiredDocuments()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/syllabus/review-required"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].status").value("REVIEW_REQUIRED"));
    }

    @Test
    @DisplayName("GET /api/syllabus/{id}/validation-report - returns validation report details")
    void testGetValidationReport() throws Exception {
        SyllabusDocument doc = new SyllabusDocument("syllabus.pdf");
        doc.setStatus("REVIEW_REQUIRED");
        doc.setValidationReportJson("{\"status\":\"PASSED\",\"errors\":[]}");
        SyllabusDocumentDto dto = new SyllabusDocumentDto(12L, doc, "REVIEW_REQUIRED");

        when(syllabusService.getSyllabusById(12L)).thenReturn(dto);

        mockMvc.perform(get("/api/syllabus/12/validation-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(12))
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.validationReportJson").value("{\"status\":\"PASSED\",\"errors\":[]}"));
    }

    @Test
    @DisplayName("POST /api/syllabus/{id}/reject - marks syllabus as failed/rejected")
    void testRejectSyllabus() throws Exception {
        SyllabusDocument doc = new SyllabusDocument("syllabus.pdf");
        doc.setStatus("FAILED");
        doc.setErrorMessage("Unit 2 missing topics");
        SyllabusDocumentDto dto = new SyllabusDocumentDto(13L, doc, "FAILED");

        when(syllabusService.rejectSyllabus(eq(13L), any())).thenReturn(dto);

        mockMvc.perform(post("/api/syllabus/13/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Unit 2 missing topics\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(13))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("PUT /api/syllabus/{id} - updates syllabus curriculum")
    void testUpdateSyllabus() throws Exception {
        SyllabusDocument doc = new SyllabusDocument("syllabus.pdf");
        doc.setStatus("REVIEW_REQUIRED");
        SyllabusDocumentDto dto = new SyllabusDocumentDto(14L, doc, "REVIEW_REQUIRED");

        when(syllabusService.updateSyllabusCurriculum(eq(14L), any())).thenReturn(dto);

        mockMvc.perform(put("/api/syllabus/14")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":14,\"subjects\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(14))
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"));
    }

    @Test
    @DisplayName("POST /api/syllabus/{id}/revalidate - revalidates syllabus curriculum")
    void testRevalidateSyllabus() throws Exception {
        SyllabusDocument doc = new SyllabusDocument("syllabus.pdf");
        doc.setStatus("REVIEW_REQUIRED");
        SyllabusDocumentDto dto = new SyllabusDocumentDto(15L, doc, "REVIEW_REQUIRED");

        when(syllabusService.revalidateSyllabus(15L)).thenReturn(dto);

        mockMvc.perform(post("/api/syllabus/15/revalidate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"));
    }
}
