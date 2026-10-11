package com.polon.evomemory.memory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "semantic_memory")
public class SemanticMemory {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryKind kind;

    @Column(name = "subject_key", nullable = false)
    private String subjectKey;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryStatus status;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(nullable = false)
    private int version;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_confirmed_at", nullable = false)
    private Instant lastConfirmedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "source_episode_id")
    private UUID sourceEpisodeId;

    @Column(name = "wing", nullable = false)
    private String wing;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    protected SemanticMemory() {
    }

    public SemanticMemory(UUID id, String userId, MemoryKind kind, String subjectKey, String content,
                          double confidence, UUID sourceEpisodeId, Instant now) {
        this(id, userId, kind, subjectKey, content, confidence, sourceEpisodeId, now, "general");
    }

    public SemanticMemory(UUID id, String userId, MemoryKind kind, String subjectKey, String content,
                          double confidence, UUID sourceEpisodeId, Instant now, String wing) {
        this.id = id;
        this.userId = userId;
        this.kind = kind;
        this.subjectKey = subjectKey;
        this.content = content;
        this.confidence = confidence;
        this.status = MemoryStatus.ACTIVE;
        this.evidenceCount = 1;
        this.version = 1;
        this.firstSeenAt = now;
        this.lastConfirmedAt = now;
        this.updatedAt = now;
        this.sourceEpisodeId = sourceEpisodeId;
        this.wing = wing != null ? wing : "general";
    }

    void confirm(Instant now) {
        this.confidence = Math.min(1.0, confidence + 0.1 * (1 - confidence));
        this.evidenceCount++;
        this.lastConfirmedAt = now;
        bump(now);
    }

    void revise(String newContent, double newConfidence, Instant now) {
        this.content = newContent;
        this.confidence = newConfidence;
        this.evidenceCount = 1;
        this.lastConfirmedAt = now;
        bump(now);
    }

    void correct(String newContent, Instant now) {
        this.content = newContent;
        this.confidence = 1.0;
        this.status = MemoryStatus.ACTIVE;
        this.lastConfirmedAt = now;
        bump(now);
    }

    void retract(Instant now) {
        this.status = MemoryStatus.RETRACTED;
        bump(now);
    }

    private void bump(Instant now) {
        this.version++;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public MemoryKind getKind() {
        return kind;
    }

    public String getSubjectKey() {
        return subjectKey;
    }

    public String getContent() {
        return content;
    }

    public double getConfidence() {
        return confidence;
    }

    public MemoryStatus getStatus() {
        return status;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public int getVersion() {
        return version;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastConfirmedAt() {
        return lastConfirmedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getSourceEpisodeId() {
        return sourceEpisodeId;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public String getWing() {
        return wing;
    }

    public void setWing(String wing) {
        this.wing = wing != null ? wing : "general";
    }
}
