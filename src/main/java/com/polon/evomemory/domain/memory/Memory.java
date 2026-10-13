package com.polon.evomemory.domain.memory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public class Memory {
    private UUID id;
    private String userId;
    private MemoryKind kind;
    private String subjectKey;
    private String content;
    private double confidence;
    private MemoryStatus status;
    private int evidenceCount;
    private int version;
    private Instant firstSeenAt;
    private Instant lastConfirmedAt;
    private Instant updatedAt;
    private UUID sourceEpisodeId;
    private String wing;
    private LocalDate validUntil;

    protected Memory() {
    }

    public Memory(UUID id, String userId, MemoryKind kind, String subjectKey, String content,
                          double confidence, UUID sourceEpisodeId, Instant now) {
        this(id, userId, kind, subjectKey, content, confidence, sourceEpisodeId, now, "general");
    }

    public Memory(UUID id, String userId, MemoryKind kind, String subjectKey, String content,
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

    public void confirm(Instant now) {
        apply(MemoryEvolution.evolve(state(), ChangeType.CONFIRMED, content, confidence, now));
    }

    public void revise(String newContent, double newConfidence, Instant now) {
        apply(MemoryEvolution.evolve(state(), ChangeType.CONTRADICTED, newContent, newConfidence, now));
    }

    public void correct(String newContent, Instant now) {
        apply(MemoryEvolution.evolve(state(), ChangeType.CORRECTED, newContent, 1.0, now));
    }

    public void retract(Instant now) {
        apply(MemoryEvolution.evolve(state(), ChangeType.FORGOTTEN, content, confidence, now));
    }

    public MemoryEvolution.State state() {
        return new MemoryEvolution.State(content, confidence, status, evidenceCount, version,
                lastConfirmedAt, updatedAt);
    }

    public void apply(MemoryEvolution.State state) {
        this.content = state.content();
        this.confidence = state.confidence();
        this.status = state.status();
        this.evidenceCount = state.evidenceCount();
        this.version = state.version();
        this.lastConfirmedAt = state.lastConfirmedAt();
        this.updatedAt = state.updatedAt();
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
