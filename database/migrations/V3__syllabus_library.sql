-- Phase 3 additive migration. Safe to run repeatedly; existing student data is untouched.
CREATE TABLE IF NOT EXISTS syllabus_documents (
    document_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_syllabus_status CHECK (status IN ('REVIEW', 'APPROVED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS syllabus_subjects (
    subject_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    subject_name VARCHAR(255) NOT NULL,
    department_code VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    semester VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN',
    course_code VARCHAR(40) NOT NULL DEFAULT '',
    credits VARCHAR(30) NOT NULL DEFAULT '',
    total_marks VARCHAR(30) NOT NULL DEFAULT '',
    is_open_elective BOOLEAN NOT NULL DEFAULT FALSE,
    course_category VARCHAR(30) NOT NULL DEFAULT 'THEORY',
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (document_id) REFERENCES syllabus_documents(document_id) ON DELETE CASCADE,
    INDEX idx_syllabus_subject_document(document_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS syllabus_units (
    unit_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    unit_number INT NOT NULL,
    unit_title TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (subject_id) REFERENCES syllabus_subjects(subject_id) ON DELETE CASCADE,
    UNIQUE KEY uq_syllabus_subject_unit(subject_id, unit_number),
    INDEX idx_syllabus_units_subject(subject_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS syllabus_topics (
    topic_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    parent_topic_id BIGINT NULL,
    topic_text TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (unit_id) REFERENCES syllabus_units(unit_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_topic_id) REFERENCES syllabus_topics(topic_id) ON DELETE CASCADE,
    INDEX idx_syllabus_topics_unit(unit_id),
    INDEX idx_syllabus_topics_parent(parent_topic_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS internal_unit_mapping (
    internal_number TINYINT NOT NULL,
    unit_number INT NOT NULL,
    PRIMARY KEY(internal_number, unit_number),
    CONSTRAINT chk_mapping_internal CHECK (internal_number IN (1, 2)),
    INDEX idx_mapping_unit(unit_number)
) ENGINE=InnoDB;

INSERT IGNORE INTO internal_unit_mapping(internal_number, unit_number)
VALUES (1, 1), (1, 2), (2, 3), (2, 4), (2, 5);

INSERT IGNORE INTO schema_migrations(version, detail)
VALUES ('V3_SYLLABUS', 'Added normalized syllabus library and configurable internal-unit mapping.');
