package studentperformance.dto;

import java.util.List;

public class DatasetUploadResponseDto {
    private boolean success;
    private String message;
    private DatasetDto dataset;
    private List<StudentResponseDto> preview;
    private List<String> validationErrors;

    public DatasetUploadResponseDto() {}

    public DatasetUploadResponseDto(boolean success, String message, DatasetDto dataset,
                                    List<StudentResponseDto> preview, List<String> validationErrors) {
        this.success = success;
        this.message = message;
        this.dataset = dataset;
        this.preview = preview;
        this.validationErrors = validationErrors;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public DatasetDto getDataset() { return dataset; }
    public void setDataset(DatasetDto dataset) { this.dataset = dataset; }

    public List<StudentResponseDto> getPreview() { return preview; }
    public void setPreview(List<StudentResponseDto> preview) { this.preview = preview; }

    public List<String> getValidationErrors() { return validationErrors; }
    public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }
}
