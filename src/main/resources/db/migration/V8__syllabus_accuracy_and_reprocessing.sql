-- V8__syllabus_accuracy_and_reprocessing.sql
-- Additive migration for Syllabus accuracy, legacy reprocessing, and revision tracking

ALTER TABLE syllabus_documents
    ADD COLUMN IF NOT EXISTS extraction_engine VARCHAR(50) NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS pdf_path VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS parent_document_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_syllabus_parent_document ON syllabus_documents(parent_document_id);

-- Clean up legacy cross-subject contamination
DELETE t FROM syllabus_topics t
JOIN syllabus_units u ON u.unit_id = t.unit_id
JOIN syllabus_subjects s ON s.subject_id = u.subject_id
WHERE s.course_code = 'UI25PC310CS' AND (
    t.topic_text LIKE '%Logic and reasoning%' OR t.topic_text LIKE '%identifying assumptions%'
    OR t.topic_text LIKE '%bias and fallacies%' OR t.topic_text LIKE '%Evaluating evidence%'
    OR t.topic_text LIKE '%drawing logical conclusions%' OR t.topic_text LIKE '%differentiating facts from opinions%'
    OR t.topic_text LIKE '%Root Cause Analysis%' OR t.topic_text LIKE '%5 Whys%'
    OR t.topic_text LIKE '%Decision Trees%' OR t.topic_text LIKE '%SWOT Analysis%'
    OR t.topic_text LIKE '%ethical dilemmas%' OR t.topic_text LIKE '%Critical thinking in action%'
    OR t.topic_text LIKE '%debating complex engineering issues%');

DELETE t FROM syllabus_topics t
JOIN syllabus_units u ON u.unit_id = t.unit_id
JOIN syllabus_subjects s ON s.subject_id = u.subject_id
WHERE s.course_code = 'U25BS340MA' AND (
    t.topic_text LIKE '%communication: intrapersonal%' OR t.topic_text LIKE '%types of listening%'
    OR t.topic_text LIKE '%Team building: stages%' OR t.topic_text LIKE '%Paragraph writing%'
    OR t.topic_text LIKE '%Grammar focus%' OR t.topic_text LIKE '%Styles, channels and flow of communication%'
    OR t.topic_text LIKE '%Letter writing and email etiquette%' OR t.topic_text LIKE '%Reading comprehension%'
    OR t.topic_text LIKE '%Barriers to communication%' OR t.topic_text LIKE '%MODEL INGESTION%'
    OR t.topic_text LIKE '%Yesterday Was Beautiful%');

-- Record migration
INSERT IGNORE INTO schema_migrations(version, detail)
VALUES ('V8_SYLLABUS_ACCURACY_REPROCESSING', 'Added extraction_engine, pdf_path, and parent_document_id for safe legacy reprocessing and revision tracking.');
