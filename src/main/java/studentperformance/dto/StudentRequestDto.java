package studentperformance.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

public class StudentRequestDto {

    @NotBlank(message = "Roll number cannot be blank")
    @Pattern(regexp = "^1602-25-733-\\d{3}$", message = "Roll number must follow format 1602-25-733-XXX")
    private String rollNumber;

    @NotBlank(message = "Student name cannot be blank")
    private String name;

    @DecimalMin(value = "0.0", message = "CGPA must be between 0.0 and 10.0")
    @DecimalMax(value = "10.0", message = "CGPA must be between 0.0 and 10.0")
    private double cgpa;

    @DecimalMin(value = "0.0", message = "SGPA must be between 0.0 and 10.0")
    @DecimalMax(value = "10.0", message = "SGPA must be between 0.0 and 10.0")
    private double lastSemesterSgpa;

    private Map<String, SubjectAssessmentDto> internal1Marks;
    private Map<String, SubjectAssessmentDto> internal2Marks;

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getCgpa() { return cgpa; }
    public void setCgpa(double cgpa) { this.cgpa = cgpa; }
    public double getLastSemesterSgpa() { return lastSemesterSgpa; }
    public void setLastSemesterSgpa(double lastSemesterSgpa) { this.lastSemesterSgpa = lastSemesterSgpa; }
    public Map<String, SubjectAssessmentDto> getInternal1Marks() { return internal1Marks; }
    public void setInternal1Marks(Map<String, SubjectAssessmentDto> internal1Marks) { this.internal1Marks = internal1Marks; }
    public Map<String, SubjectAssessmentDto> getInternal2Marks() { return internal2Marks; }
    public void setInternal2Marks(Map<String, SubjectAssessmentDto> internal2Marks) { this.internal2Marks = internal2Marks; }
}
