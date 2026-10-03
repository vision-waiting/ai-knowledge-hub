CREATE TABLE IF NOT EXISTS knowledge_member (
    knowledge_base_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (knowledge_base_id,user_id),
    INDEX idx_member_user (user_id)
);

CREATE TABLE IF NOT EXISTS web_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    url VARCHAR(2000) NOT NULL,
    url_hash VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    document_id BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (knowledge_base_id,url_hash)
);

CREATE TABLE IF NOT EXISTS review_card (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    study_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    card_index INT NOT NULL,
    front TEXT NOT NULL,
    back LONGTEXT NOT NULL,
    interval_days INT NOT NULL DEFAULT 0,
    repetitions INT NOT NULL DEFAULT 0,
    due_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (study_id,user_id,card_index),
    INDEX idx_review_due (user_id,due_at)
);

CREATE TABLE IF NOT EXISTS retrieval_evaluation_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    top_k INT NOT NULL,
    case_count INT NOT NULL,
    answerable_count INT NOT NULL,
    unanswerable_count INT NOT NULL,
    hit_at_k DOUBLE NULL,
    mrr DOUBLE NULL,
    source_recall DOUBLE NULL,
    rejection_rate DOUBLE NULL,
    latency_ms BIGINT NOT NULL,
    dataset_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evaluation_run_kb (knowledge_base_id,created_at)
);

CREATE TABLE IF NOT EXISTS knowledge_content (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body LONGTEXT NOT NULL,
    aliases VARCHAR(500),
    source_document_id BIGINT,
    sort_order INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_content_base (knowledge_base_id,kind,sort_order)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status TINYINT NOT NULL DEFAULT 1,
    token_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    owner_id BIGINT NOT NULL,
    shared BOOLEAN NOT NULL DEFAULT FALSE,
    status TINYINT NOT NULL DEFAULT 1,
    document_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_owner (owner_id)
);

CREATE TABLE IF NOT EXISTS document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(50),
    file_size BIGINT,
    page_count INT,
    status VARCHAR(30) NOT NULL,
    error_message VARCHAR(1000),
    summary TEXT,
    summary_mode VARCHAR(32),
    version_root_id BIGINT,
    previous_version_id BIGINT,
    version_number INT NOT NULL DEFAULT 1,
    deleted_at TIMESTAMP,
    deleted_status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_kb (knowledge_base_id),
    INDEX idx_doc_status (status)
);

CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    content LONGTEXT NOT NULL,
    chunk_index INT NOT NULL,
    page_number INT,
    token_count INT,
    vector_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk_kb (knowledge_base_id),
    INDEX idx_chunk_doc (document_id)
);

CREATE TABLE IF NOT EXISTS document_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    INDEX idx_tag_doc (document_id)
);

CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    title VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_user (user_id),
    INDEX idx_session_kb (knowledge_base_id)
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    token_count INT,
    mode VARCHAR(32),
    citations_json LONGTEXT,
    feedback VARCHAR(20),
    feedback_reason VARCHAR(200),
    feedback_status VARCHAR(20),
    retrieval_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_message_session (session_id)
);

CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    operation VARCHAR(100),
    method VARCHAR(20),
    request_uri VARCHAR(500),
    ip VARCHAR(100),
    success TINYINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_created (created_at)
);

CREATE TABLE IF NOT EXISTS ai_usage_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    mode VARCHAR(30) NOT NULL,
    model VARCHAR(100),
    duration_ms BIGINT,
    prompt_tokens INT,
    completion_tokens INT,
    success TINYINT NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_log_created (created_at)
);


-- Feature: notes
CREATE TABLE IF NOT EXISTS personal_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    category VARCHAR(80) NOT NULL DEFAULT '未分类',
    source_session_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_note_user_updated (user_id, updated_at),
    INDEX idx_note_user_category (user_id, category)
);


-- Feature: notifications
CREATE TABLE IF NOT EXISTS knowledge_subscription (
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    followed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id, knowledge_base_id)
);

CREATE TABLE IF NOT EXISTS knowledge_update_event (
    event_key VARCHAR(64) PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT,
    document_name VARCHAR(255) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_key VARCHAR(64) NOT NULL,
    read_at TIMESTAMP(6),
    UNIQUE (user_id, event_key)
);


-- Feature: intelligence
CREATE TABLE IF NOT EXISTS document_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    analysis_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    mode VARCHAR(32) NOT NULL,
    focus VARCHAR(500),
    document_ids_json TEXT NOT NULL,
    sources_json LONGTEXT NOT NULL,
    source_notice TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_analysis_owner (user_id, knowledge_base_id)
);

CREATE TABLE IF NOT EXISTS study_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    document_ids_json TEXT NOT NULL,
    sources_json LONGTEXT NOT NULL,
    source_notice TEXT NOT NULL,
    cards_json LONGTEXT NOT NULL,
    questions_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_study_owner (user_id, knowledge_base_id)
);

CREATE TABLE IF NOT EXISTS study_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    study_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL,
    total INT NOT NULL,
    results_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_attempt_owner (user_id, study_id)
);

CREATE TABLE IF NOT EXISTS study_mistake (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    study_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    question_id INT NOT NULL,
    result_json LONGTEXT NOT NULL,
    resolved TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_mistake_question (study_id, user_id, question_id),
    INDEX idx_mistake_owner (user_id, resolved)
);
