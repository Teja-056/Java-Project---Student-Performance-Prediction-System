package studentperformance;

/** Read-only copy of a raw mark from the older internal_marks table. */
public record LegacyMark(int internalNumber, String subjectName, int marks) { }
