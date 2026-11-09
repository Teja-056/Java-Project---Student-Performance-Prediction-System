-- Phase 3 additive migration: Expanded syllabus document status lifecycle.
ALTER TABLE syllabus_documents DROP CHECK chk_syllabus_status;
ALTER TABLE syllabus_documents ADD CONSTRAINT chk_syllabus_status CHECK (status IN ('UPLOADED', 'PROCESSING', 'APPROVED', 'FAILED', 'REVIEW'));

INSERT IGNORE INTO schema_migrations(version, detail)
VALUES ('V5_SYLLABUS_STATUS', 'Added status lifecycle: UPLOADED, PROCESSING, APPROVED, FAILED, REVIEW.');
