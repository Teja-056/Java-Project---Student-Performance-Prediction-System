-- V9__syllabus_relational_repair.sql
-- Drop hazardous CASCADE foreign key on syllabus_subjects(document_id)
-- Canonical subjects are referenced via the junction table syllabus_document_subjects.

ALTER TABLE syllabus_subjects DROP FOREIGN KEY IF EXISTS syllabus_subjects_ibfk_1;
ALTER TABLE syllabus_subjects MODIFY COLUMN document_id BIGINT NULL;

INSERT IGNORE INTO schema_migrations(version, detail)
VALUES ('V9_SYLLABUS_RELATIONAL_REPAIR', 'Dropped hazardous CASCADE foreign key on syllabus_subjects(document_id) to prevent canonical subject deletion.');
