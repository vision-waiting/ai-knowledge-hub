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
