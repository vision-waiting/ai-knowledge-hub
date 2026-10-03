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
