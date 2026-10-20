
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
