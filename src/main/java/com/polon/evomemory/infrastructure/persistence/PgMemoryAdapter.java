package com.polon.evomemory.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.polon.evomemory.application.ports.MemoryStorePort;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.MemoryRevisionDomain;
import com.polon.evomemory.domain.memory.MemoryStatus;

@Component
public class PgMemoryAdapter implements MemoryStorePort {
    private final SemanticMemoryRepository memories;
    private final EpisodeRepository episodes;
    private final MemoryRevisionRepository revisions;

    public PgMemoryAdapter(SemanticMemoryRepository memories, EpisodeRepository episodes,
                           MemoryRevisionRepository revisions) {
        this.memories = memories;
        this.episodes = episodes;
        this.revisions = revisions;
    }

    @Override
    public Memory saveMemory(Memory memory) {
        return memories.save(SemanticMemory.fromDomain(memory)).toDomain();
    }

    @Override
    public com.polon.evomemory.domain.memory.Episode saveEpisode(com.polon.evomemory.domain.memory.Episode episode) {
        return episodes.save(new Episode(episode.getId(), episode.getUserId(), episode.getConversationId(),
                episode.getSummary(), episode.getSource(), episode.getOccurredAt())).toDomain();
    }

    @Override
    public void saveRevision(MemoryRevisionDomain revision) {
        revisions.save(new MemoryRevision(revision.getId(), revision.getMemoryId(), revision.getVersion(),
                revision.getChangeType(), revision.getPreviousContent(), revision.getNewContent(),
                revision.getPreviousConfidence(), revision.getNewConfidence(), revision.getEpisodeId(),
                revision.getReason(), revision.getChangedAt()));
    }

    @Override
    public Optional<Memory> findBySubject(String userId, String subjectKey) {
        return memories.findByUserIdAndSubjectKey(userId, subjectKey).map(SemanticMemory::toDomain);
    }

    @Override
    public Optional<Memory> findOwned(String userId, UUID memoryId) {
        return memories.findByIdAndUserId(memoryId, userId).map(SemanticMemory::toDomain);
    }

    @Override
    public List<Memory> findActiveByUser(String userId) {
        return memories.findByUserIdAndStatusOrderByConfidenceDesc(userId, MemoryStatus.ACTIVE)
                .stream().map(SemanticMemory::toDomain).toList();
    }

    @Override
    public List<MemoryRevisionDomain> history(UUID memoryId) {
        return revisions.findByMemoryIdOrderByVersionAsc(memoryId).stream().map(MemoryRevision::toDomain).toList();
    }

    @Override
    public void flush() { memories.flush(); }
}
