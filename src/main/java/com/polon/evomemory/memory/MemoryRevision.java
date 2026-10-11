package com.polon.evomemory.memory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "memory_revision")
public class MemoryRevision {

    @Id
    private UUID id;

    @Column(name = "memory_id", nullable = false)
    private UUID memoryId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ChangeType changeType;

    @Column(name = "previous_content")
    private String previousContent;

    @Column(name = "new_content", nullable = false)
    private String newContent;

    @Column(name = "previous_confidence")
    private Double previousConfidence;

    @Column(name = "new_confidence", nullable = false)
    private double newConfidence;

    @Column(name = "episode_id")
    private UUID episodeId;

    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected MemoryRevision() {
    }

    public MemoryRevision(UUID id, UUID memoryId, int version, ChangeType changeType, String previousContent,
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
