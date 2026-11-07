package studentperformance.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import studentperformance.ExcelImporter;
import studentperformance.dto.ExcelUploadResponseDto;
import studentperformance.exception.BadRequestException;
import studentperformance.exception.GlobalExceptionHandler;
import studentperformance.service.ExcelImportService;

import java.nio.file.Path;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExcelController.class)
@Import(GlobalExceptionHandler.class)
public class ExcelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExcelImportService excelImportService;

    @MockBean
    private ExcelImporter excelImporter;

    @Test
    @DisplayName("POST /api/excel/upload - preview mode returns parsed counts and preview")
    void testUploadExcelPreviewMode() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3, 4}
        );

        ExcelUploadResponseDto responseDto = new ExcelUploadResponseDto(
                "students.xlsx", 100, 100, 0,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                false,
                "Preview generated: 100 valid records detected out of 100 rows. No database changes were made."
        );

        when(excelImportService.processUpload(any(), eq(false))).thenReturn(responseDto);

        mockMvc.perform(multipart("/api/excel/upload")
                        .file(file)
                        .param("commit", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("students.xlsx"))
                .andExpect(jsonPath("$.committed").value(false))
                .andExpect(jsonPath("$.totalRows").value(100))
                .andExpect(jsonPath("$.validCount").value(100))
                .andExpect(jsonPath("$.invalidCount").value(0));
    }

    @Test
    @DisplayName("POST /api/excel/upload - reject non-excel files")
    void testUploadInvalidFileExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf",
                new byte[]{1, 2, 3}
        );

        when(excelImportService.processUpload(any(), eq(false)))
                .thenThrow(new BadRequestException("Invalid file format. Only Excel spreadsheets (.xlsx, .xls) are supported."));

        mockMvc.perform(multipart("/api/excel/upload")
                        .file(file)
                        .param("commit", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid file format. Only Excel spreadsheets (.xlsx, .xls) are supported."));
    }

    @Test
    @DisplayName("GET /api/excel/template - downloads student template")
    void testDownloadTemplate() throws Exception {
        doNothing().when(excelImporter).generateTemplate(any(Path.class));

        mockMvc.perform(get("/api/excel/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=student_import_template.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
