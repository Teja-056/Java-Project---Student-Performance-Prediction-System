-- Safe, repeatable archival for the known legacy schema:
-- internal_marks(roll_number, internal_number, subject_name, marks).
-- Run only when that legacy table exists. Nothing is dropped or updated in it.

CREATE TABLE IF NOT EXISTS legacy_internal_marks_archive (
    roll_number VARCHAR(20) NOT NULL,
    internal_number TINYINT NOT NULL,
    subject_name VARCHAR(64) NOT NULL,
    raw_marks INT NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (roll_number, internal_number, subject_name)
);

INSERT IGNORE INTO legacy_internal_marks_archive
    (roll_number, internal_number, subject_name, raw_marks)
SELECT m.roll_number, m.internal_number, m.subject_name, m.marks
FROM internal_marks AS m
JOIN students AS s ON s.roll_number = m.roll_number;

-- The application intentionally does not copy these raw values into the new
-- assessment table: the old schema does not record their maximum or whether
-- they represent exam, assignment, or quiz marks. Review them and re-enter
-- modern marks through the application when their meaning is confirmed.
