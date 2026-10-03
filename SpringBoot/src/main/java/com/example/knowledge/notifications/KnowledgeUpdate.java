package com.example.knowledge.notifications;

import java.time.LocalDateTime;

/** Immutable event snapshot: document deletion must not destroy the displayed filename. */
public record KnowledgeUpdate(String eventKey, Long knowledgeBaseId, Long documentId,
                              String documentName, String type, LocalDateTime occurredAt) {}
