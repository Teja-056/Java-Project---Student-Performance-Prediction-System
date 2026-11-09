-- V10: Dataset Management and Multi-Dataset Student Isolation
CREATE TABLE IF NOT EXISTS datasets (
    dataset_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    dataset_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NULL,
    semester VARCHAR(20) NOT NULL DEFAULT 'Semester III',
    academic_year VARCHAR(50) NULL,
    total_rows INT NOT NULL DEFAULT 0,
    valid_count INT NOT NULL DEFAULT 0,
    invalid_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'READY',
    validation_report LONGTEXT NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_datasets_active (is_active),
    INDEX idx_datasets_code (dataset_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO datasets (dataset_id, dataset_code, name, original_filename, semester, academic_year, total_rows, valid_count, invalid_count, is_active, status)
VALUES (1, 'DS-2026-BASELINE', 'Baseline Cohort (Semester III)', 'student_records_100.xlsx', 'Semester III', '2025-2026', 101, 101, 0, TRUE, 'READY')
ON DUPLICATE KEY UPDATE is_active = TRUE;

INSERT IGNORE INTO schema_migrations (version, detail) VALUES 
('V10_DATASET_ISOLATION', 'Added datasets table, dataset_id to students and internal_assessments, and composite primary/foreign keys.');
