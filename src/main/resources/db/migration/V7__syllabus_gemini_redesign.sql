-- V7__syllabus_gemini_redesign.sql
-- Additive migration for Gemini-powered Syllabus Management redesign

-- 1. Document metadata and validation report storage
ALTER TABLE syllabus_documents
    ADD COLUMN institution VARCHAR(255) NULL,
    ADD COLUMN academic_scheme VARCHAR(50) NULL,
    ADD COLUMN academic_year VARCHAR(50) NULL,
    ADD COLUMN branch VARCHAR(100) NULL,
    ADD COLUMN semester VARCHAR(20) NULL,
    ADD COLUMN validation_report LONGTEXT NULL,
    ADD COLUMN error_message TEXT NULL;

-- 2. Source page number references
ALTER TABLE syllabus_units
    ADD COLUMN page_number INT NULL;

ALTER TABLE syllabus_topics
    ADD COLUMN page_number INT NULL;

-- 3. Document lifecycle status constraint update
-- Supports PROCESSING, REVIEW_REQUIRED, REVIEW (backwards compatibility), APPROVED, FAILED
ALTER TABLE syllabus_documents DROP CHECK chk_syllabus_status;
ALTER TABLE syllabus_documents ADD CONSTRAINT chk_syllabus_status
    CHECK (status IN ('PROCESSING', 'REVIEW_REQUIRED', 'REVIEW', 'APPROVED', 'FAILED'));

-- 4. Record migration
INSERT IGNORE INTO schema_migrations(version, detail)
VALUES ('V7_SYLLABUS_GEMINI_REDESIGN', 'Added document metadata, source page numbers, and extended lifecycle statuses (PROCESSING, REVIEW_REQUIRED, APPROVED, FAILED).');
