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
}
