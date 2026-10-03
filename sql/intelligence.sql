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
