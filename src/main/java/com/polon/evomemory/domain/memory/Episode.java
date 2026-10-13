package com.polon.evomemory.domain.memory;

import java.time.Instant;
import java.util.UUID;
public class Episode {
    private UUID id;
    private String userId;
    private String conversationId;
    private String summary;
    private EpisodeSource source;
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
