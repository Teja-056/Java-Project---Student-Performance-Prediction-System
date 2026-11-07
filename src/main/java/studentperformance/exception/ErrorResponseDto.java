package studentperformance.exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ErrorResponseDto {
    private final String timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;

    public ErrorResponseDto(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public String getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
}
