CREATE DATABASE IF NOT EXISTS student_performance;
USE student_performance;

CREATE TABLE IF NOT EXISTS students (
    roll_number VARCHAR(20) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    cgpa DECIMAL(4,2) NOT NULL,
    last_sgpa DECIMAL(4,2) NOT NULL,
    CONSTRAINT chk_student_cgpa CHECK (cgpa BETWEEN 0 AND 10),
    CONSTRAINT chk_student_sgpa CHECK (last_sgpa BETWEEN 0 AND 10)
);

CREATE TABLE IF NOT EXISTS internal_assessments (
    roll_number VARCHAR(20) NOT NULL,
    internal_number TINYINT NOT NULL,
    subject_name VARCHAR(20) NOT NULL,
    exam_marks INT NOT NULL,
    assignment1 INT NULL,
    assignment2 INT NULL,
    assignment3 INT NULL,
    quiz1 INT NULL,
    quiz2 INT NULL,
    quiz3 INT NULL,
    imported_legacy BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (roll_number, internal_number, subject_name),
    CONSTRAINT fk_internal_marks_student FOREIGN KEY (roll_number)
        REFERENCES students(roll_number) ON DELETE CASCADE,
    CONSTRAINT chk_internal_number CHECK (internal_number IN (1, 2)),
    CONSTRAINT chk_exam_marks CHECK (
        (subject_name IN ('JAVA', 'DS', 'CA', 'TTPS') AND exam_marks BETWEEN 0 AND 30)
        OR (subject_name IN ('CS', 'CT', 'HVPE') AND exam_marks BETWEEN 0 AND 20)
    ),
    CONSTRAINT chk_assignment_quiz_marks CHECK (
        (assignment1 IS NULL OR assignment1 BETWEEN 0 AND 5) AND
        (assignment2 IS NULL OR assignment2 BETWEEN 0 AND 5) AND
        (assignment3 IS NULL OR assignment3 BETWEEN 0 AND 5) AND
        (quiz1 IS NULL OR quiz1 BETWEEN 0 AND 5) AND
        (quiz2 IS NULL OR quiz2 BETWEEN 0 AND 5) AND
        (quiz3 IS NULL OR quiz3 BETWEEN 0 AND 5)
    )
);

CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detail VARCHAR(500) NOT NULL
);

-- Read-only archival copy of older raw marks. Deliberately has no cascading FK,
-- so confirmation-based student deletion does not erase migration history.
CREATE TABLE IF NOT EXISTS legacy_internal_marks_archive (
    roll_number VARCHAR(20) NOT NULL,
    internal_number TINYINT NOT NULL,
    subject_name VARCHAR(64) NOT NULL,
    raw_marks INT NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (roll_number, internal_number, subject_name)
);
