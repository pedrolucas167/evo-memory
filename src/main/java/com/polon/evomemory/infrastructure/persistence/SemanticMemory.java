package com.polon.evomemory.infrastructure.persistence;
import com.polon.evomemory.domain.memory.MemoryKind;
import com.polon.evomemory.domain.memory.MemoryStatus;
import com.polon.evomemory.domain.memory.ChangeType;
import com.polon.evomemory.domain.memory.MemoryEvolution;

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

    public static SemanticMemory fromDomain(com.polon.evomemory.domain.memory.Memory memory) {
        SemanticMemory entity = new SemanticMemory(memory.getId(), memory.getUserId(), memory.getKind(),
                memory.getSubjectKey(), memory.getContent(), memory.getConfidence(), memory.getSourceEpisodeId(),
                memory.getFirstSeenAt(), memory.getWing());
        entity.apply(memory.state());
        entity.setValidUntil(memory.getValidUntil());
        return entity;
    }

    public com.polon.evomemory.domain.memory.Memory toDomain() {
        var memory = new com.polon.evomemory.domain.memory.Memory(id, userId, kind, subjectKey, content,
                confidence, sourceEpisodeId, firstSeenAt, wing);
        memory.apply(state());
        memory.setValidUntil(validUntil);
        return memory;
    }

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
