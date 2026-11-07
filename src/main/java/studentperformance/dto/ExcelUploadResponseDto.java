package studentperformance.dto;

import java.util.List;

public class ExcelUploadResponseDto {
    private String filename;
    private int totalRows;
    private int validCount;
    private int invalidCount;
    private List<String> duplicateRolls;
    private List<String> validationErrors;
    private List<StudentResponseDto> preview;
    private boolean committed;
    private String message;

    public ExcelUploadResponseDto() {}

    public ExcelUploadResponseDto(String filename, int totalRows, int validCount, int invalidCount,
                                  List<String> duplicateRolls, List<String> validationErrors,
                                  List<StudentResponseDto> preview, boolean committed, String message) {
        this.filename = filename;
        this.totalRows = totalRows;
        this.validCount = validCount;
        this.invalidCount = invalidCount;
        this.duplicateRolls = duplicateRolls == null ? List.of() : duplicateRolls;
        this.validationErrors = validationErrors == null ? List.of() : validationErrors;
        this.preview = preview == null ? List.of() : preview;
        this.committed = committed;
        this.message = message;
    }

    public String getFilename() { return filename; }
    public int getTotalRows() { return totalRows; }
    public int getValidCount() { return validCount; }
    public int getInvalidCount() { return invalidCount; }
    public List<String> getDuplicateRolls() { return duplicateRolls; }
    public List<String> getValidationErrors() { return validationErrors; }
    public List<StudentResponseDto> getPreview() { return preview; }
    public boolean isCommitted() { return committed; }
    public String getMessage() { return message; }
}
