package com.polon.evomemory.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.llm.EmbeddingClient;
import com.polon.evomemory.memory.ChangeClassifier.Decision;
import com.polon.evomemory.web.ReindexResult;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final EpisodeRepository episodes;
    private final SemanticMemoryRepository memories;
    private final MemoryRevisionRepository revisions;
    private final ChangeClassifier classifier;
    private final Clock clock;
    private final EmbeddingClient embeddingClient;
    private final MemoryVectorStore vectorStore;
    private final LlmProperties properties;

    public MemoryService(EpisodeRepository episodes, SemanticMemoryRepository memories,
                         MemoryRevisionRepository revisions, ChangeClassifier classifier, Clock clock,
                         EmbeddingClient embeddingClient, MemoryVectorStore vectorStore, LlmProperties properties) {
        this.episodes = episodes;
        this.memories = memories;
        this.revisions = revisions;
        this.classifier = classifier;
        this.clock = clock;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @Transactional
    public Episode recordEpisode(String userId, String conversationId, String summary, EpisodeSource source) {
        Episode episode = new Episode(UUID.randomUUID(), userId, conversationId, summary, source, now());
        return episodes.save(episode);
    }

    @Transactional
    public SemanticMemory observe(String userId, Episode episode, CandidateMemory candidate) {
        Instant now = now();
        Optional<SemanticMemory> existing = memories.findByUserIdAndSubjectKey(userId, candidate.subjectKey());

        if (existing.isEmpty()) {
            SemanticMemory created = new SemanticMemory(UUID.randomUUID(), userId, candidate.kind(),
                    candidate.subjectKey(), candidate.content(), candidate.confidence(), episode.getId(), now);
            created.setValidUntil(candidate.validUntil());
            memories.save(created);
            recordRevision(created, ChangeType.CREATED, null, null, episode.getId(),
                    "primeira observação", now);
            indexEmbedding(created);
            return created;
        }

        SemanticMemory memory = existing.get();
        if (memory.getStatus() == MemoryStatus.RETRACTED) {
            // Respeita a decisão do usuário de esquecer esta informação.
            return memory;
        }

        String previousContent = memory.getContent();
        double previousConfidence = memory.getConfidence();
        Decision decision = classifier.classify(memory, candidate);

        switch (decision.type()) {
            case CONFIRMED -> memory.confirm(now);
            case COMPLEMENTED -> memory.revise(decision.content(),
                    Math.max(previousConfidence, candidate.confidence()), now);
            case CONTRADICTED -> memory.revise(decision.content(), candidate.confidence(), now);
            default -> throw new IllegalStateException("Tipo de mudança inesperado: " + decision.type());
        }

        if (decision.type() == ChangeType.CONTRADICTED) {
            // Uma nova contradição substitui a validade; sem data nova, o plano anterior deixa de ter prazo.
            memory.setValidUntil(candidate.validUntil());
        } else if (candidate.validUntil() != null) {
            memory.setValidUntil(candidate.validUntil());
        }

        memories.save(memory);
        recordRevision(memory, decision.type(), previousContent, previousConfidence, episode.getId(),
                decision.reason(), now);
        if (decision.type() != ChangeType.CONFIRMED) {
            indexEmbedding(memory);
        }
        return memory;
    }

    @Transactional
    public SemanticMemory correct(String userId, UUID memoryId, String newContent) {
        SemanticMemory memory = owned(userId, memoryId);
        String previousContent = memory.getContent();
        double previousConfidence = memory.getConfidence();
        Instant now = now();

        memory.correct(newContent, now);
        memories.save(memory);
        recordRevision(memory, ChangeType.CORRECTED, previousContent, previousConfidence, null,
                "correção feita pelo usuário", now);
        indexEmbedding(memory);
        return memory;
    }

    @Transactional
    public SemanticMemory forget(String userId, UUID memoryId) {
        SemanticMemory memory = owned(userId, memoryId);
        String previousContent = memory.getContent();
        double previousConfidence = memory.getConfidence();
        Instant now = now();

        memory.retract(now);
        memories.save(memory);
        recordRevision(memory, ChangeType.FORGOTTEN, previousContent, previousConfidence, null,
                "esquecido pelo usuário", now);
        return memory;
    }

    @Transactional(readOnly = true)
    public List<SemanticMemory> recallActive(String userId) {
        return memories.findByUserIdAndStatusOrderByConfidenceDesc(userId, MemoryStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<MemoryRevision> history(String userId, UUID memoryId) {
        owned(userId, memoryId);
        return revisions.findByMemoryIdOrderByVersionAsc(memoryId);
    }

    private SemanticMemory owned(String userId, UUID memoryId) {
        return memories.findByIdAndUserId(memoryId, userId)
                .orElseThrow(() -> new NoSuchElementException("Memória não encontrada: " + memoryId));
    }

    private void recordRevision(SemanticMemory memory, ChangeType type, String previousContent,
                                Double previousConfidence, UUID episodeId, String reason, Instant now) {
        revisions.save(new MemoryRevision(UUID.randomUUID(), memory.getId(), memory.getVersion(), type,
                previousContent, memory.getContent(), previousConfidence, memory.getConfidence(),
                episodeId, reason, now));
    }

    @Transactional
    public ReindexResult reindex(String userId) {
        List<SemanticMemory> active = memories.findByUserIdAndStatusOrderByConfidenceDesc(userId, MemoryStatus.ACTIVE);
        int reindexed = 0;
        for (SemanticMemory memory : active) {
            if (indexEmbedding(memory)) {
                reindexed++;
            }
        }
        return new ReindexResult(active.size(), reindexed);
    }

    private boolean indexEmbedding(SemanticMemory memory) {
        try {
            // Garante que o INSERT/UPDATE pendente chegue ao banco antes da escrita nativa do vetor.
            memories.flush();
            vectorStore.store(memory.getId(), embeddingClient.embed(memory.getContent()),
                    properties.getEmbeddingModel());
            return true;
        } catch (RuntimeException error) {
            log.warn("Não foi possível indexar a memória {}: {}", memory.getId(), error.getMessage());
            return false;
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
