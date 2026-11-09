-- Migration V4: Add sem1_sgpa and attendance_percent to students, and support OE in internal_assessments.
-- Daily Study Hours is completely omitted.

USE student_performance;

-- Add sem1_sgpa and attendance_percent to students table
ALTER TABLE students
    ADD COLUMN sem1_sgpa DECIMAL(4,2) NULL AFTER name,
    ADD COLUMN attendance_percent DECIMAL(5,2) NULL AFTER last_sgpa,
    ADD CONSTRAINT chk_student_sem1_sgpa CHECK (sem1_sgpa IS NULL OR (sem1_sgpa BETWEEN 0.00 AND 10.00)),
    ADD CONSTRAINT chk_student_attendance CHECK (attendance_percent IS NULL OR (attendance_percent BETWEEN 0.00 AND 100.00));

-- Update exam marks check constraint on internal_assessments to accommodate Open Elective (OE)
ALTER TABLE internal_assessments DROP CHECK chk_exam_marks;
ALTER TABLE internal_assessments ADD CONSTRAINT chk_exam_marks CHECK (
    (subject_name IN ('JAVA', 'DS', 'CA', 'TTPS', 'OE') AND exam_marks BETWEEN 0 AND 30)
    OR (subject_name IN ('CS', 'CT', 'HVPE') AND exam_marks BETWEEN 0 AND 20)
);

-- Record migration in schema_migrations
INSERT INTO schema_migrations (version, detail)
VALUES ('V4', 'Added sem1_sgpa and attendance_percent to students table; added OE to internal_assessments constraint.');
