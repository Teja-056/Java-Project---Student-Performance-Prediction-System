-- Phase 3 additive migration: Junction table for document-subject mapping.
CREATE TABLE IF NOT EXISTS syllabus_document_subjects (
    document_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    PRIMARY KEY (document_id, subject_id),
    FOREIGN KEY (document_id) REFERENCES syllabus_documents(document_id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES syllabus_subjects(subject_id) ON DELETE CASCADE,
    INDEX idx_doc_sub_doc(document_id),
    INDEX idx_doc_sub_sub(subject_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO syllabus_document_subjects(document_id, subject_id)
SELECT document_id, subject_id FROM syllabus_subjects WHERE document_id IS NOT NULL;

INSERT IGNORE INTO schema_migrations(version, detail)
VALUES ('V6_DOCUMENT_SUBJECTS', 'Added syllabus_document_subjects junction table to support duplicate-safe course reuse.');
