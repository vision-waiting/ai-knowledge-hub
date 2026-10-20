CREATE DATABASE IF NOT EXISTS ai_knowledge_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_knowledge_hub;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status TINYINT NOT NULL DEFAULT 1,
    token_version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, description VARCHAR(500), owner_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1, document_count INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX idx_kb_owner(owner_id)
);
CREATE TABLE IF NOT EXISTS document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, knowledge_base_id BIGINT NOT NULL, file_name VARCHAR(255) NOT NULL, file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(50), file_size BIGINT, page_count INT, status VARCHAR(30) NOT NULL, error_message VARCHAR(1000), summary TEXT, summary_mode VARCHAR(32),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_kb(knowledge_base_id), INDEX idx_doc_status(status)
);
CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL, knowledge_base_id BIGINT NOT NULL, content LONGTEXT NOT NULL,
    chunk_index INT NOT NULL, page_number INT, token_count INT, vector_id VARCHAR(100), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk_kb(knowledge_base_id), INDEX idx_chunk_doc(document_id)
);
CREATE TABLE IF NOT EXISTS document_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL, tag_name VARCHAR(100) NOT NULL, INDEX idx_tag_doc(document_id)
);
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, knowledge_base_id BIGINT NOT NULL, title VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_user(user_id), INDEX idx_session_kb(knowledge_base_id)
);
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL, role VARCHAR(20) NOT NULL, content LONGTEXT NOT NULL,
    token_count INT, mode VARCHAR(32), model VARCHAR(255), citations_json LONGTEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_message_session(session_id)
);
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, operation VARCHAR(100), method VARCHAR(20), request_uri VARCHAR(500), ip VARCHAR(100),
    success TINYINT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_log_created(created_at)
);
CREATE TABLE IF NOT EXISTS ai_usage_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, mode VARCHAR(30) NOT NULL, model VARCHAR(100), duration_ms BIGINT,
    prompt_tokens INT, completion_tokens INT, success TINYINT NOT NULL, error_message VARCHAR(1000), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_log_created(created_at)
);


-- GitHub-style knowledge collaboration (additive, no existing data reset).
CREATE TABLE IF NOT EXISTS knowledge_star (
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, knowledge_base_id)
);
CREATE TABLE IF NOT EXISTS content_suggestion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    base_version INT NOT NULL,
    author_id BIGINT NOT NULL,
    original_title VARCHAR(200) NOT NULL,
    original_body LONGTEXT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body LONGTEXT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reviewer_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    INDEX idx_suggestion_scope (knowledge_base_id, status, id)
);
CREATE TABLE IF NOT EXISTS content_revision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    version INT NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    actor_id BIGINT,
    contributor_id BIGINT,
    action VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (content_id, version),
    INDEX idx_revision_scope (knowledge_base_id, content_id)
);

