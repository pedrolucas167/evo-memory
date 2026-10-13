package com.polon.evomemory.domain.memory;

import java.time.Instant;
import java.util.UUID;
public class MemoryRevisionDomain {
    private UUID id;
    private UUID memoryId;
    private int version;
    private ChangeType changeType;
    private String previousContent;
    private String newContent;
    private Double previousConfidence;
    private double newConfidence;
    private UUID episodeId;

    private String reason;
    private Instant changedAt;

    protected MemoryRevisionDomain() {
    }

    public MemoryRevisionDomain(UUID id, UUID memoryId, int version, ChangeType changeType, String previousContent,
                          String newContent, Double previousConfidence, double newConfidence, UUID episodeId,
                          String reason, Instant changedAt) {
        this.id = id;
        this.memoryId = memoryId;
        this.version = version;
        this.changeType = changeType;
        this.previousContent = previousContent;
        this.newContent = newContent;
        this.previousConfidence = previousConfidence;
        this.newConfidence = newConfidence;
        this.episodeId = episodeId;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public int getVersion() {
        return version;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getPreviousContent() {
        return previousContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public Double getPreviousConfidence() {
        return previousConfidence;
    }

    public double getNewConfidence() {
        return newConfidence;
    }

    public UUID getEpisodeId() {
        return episodeId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
