package com.polon.evomemory.infrastructure.persistence;
import com.polon.evomemory.domain.memory.EpisodeSource;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "episode")
public class Episode {

    public com.polon.evomemory.domain.memory.Episode toDomain() {
        return new com.polon.evomemory.domain.memory.Episode(id, userId, conversationId, summary, source, occurredAt);
    }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(nullable = false)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EpisodeSource source;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected Episode() {
    }

    public Episode(UUID id, String userId, String conversationId, String summary, EpisodeSource source, Instant occurredAt) {
        this.id = id;
        this.userId = userId;
        this.conversationId = conversationId;
        this.summary = summary;
        this.source = source;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getSummary() {
        return summary;
    }

    public EpisodeSource getSource() {
        return source;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
