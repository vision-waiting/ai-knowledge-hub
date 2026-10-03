-- Immutable document versions retain original file and chunk IDs for historical citations.
ALTER TABLE document ADD COLUMN version_root_id BIGINT NULL;
ALTER TABLE document ADD COLUMN previous_version_id BIGINT NULL;
ALTER TABLE document ADD COLUMN version_number INT NOT NULL DEFAULT 1;
ALTER TABLE document ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE document ADD COLUMN deleted_status VARCHAR(20) NULL;
CREATE INDEX idx_document_versions ON document(version_root_id, version_number);
CREATE INDEX idx_document_trash ON document(knowledge_base_id, deleted_at);
