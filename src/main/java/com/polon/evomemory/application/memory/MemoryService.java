package com.polon.evomemory.application.memory;
import com.polon.evomemory.domain.memory.MemoryStatus;
import com.polon.evomemory.domain.memory.CandidateMemory;
import com.polon.evomemory.domain.memory.ChangeType;
import com.polon.evomemory.domain.memory.SubjectMatcher;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.application.ports.EmbeddingPort;
import com.polon.evomemory.application.MemoryEvolutionService;
import com.polon.evomemory.application.ports.ChangeClassifier.Decision;
import com.polon.evomemory.application.ports.MemoryStorePort;
import com.polon.evomemory.application.ports.ChangeClassifier;
import com.polon.evomemory.application.ports.VectorStorePort;
import com.polon.evomemory.domain.memory.EpisodeSource;
import com.polon.evomemory.domain.memory.Episode;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.MemoryRevisionDomain;
import com.polon.evomemory.application.model.response.ReindexResult;

@Service
public class MemoryService implements com.polon.evomemory.application.ports.MemoryRecallPort {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final int NEIGHBORS = 10;
    /** Limite de probes de juiz por observação quando a chave exata falha (contenção de custo LLM). */
    private static final int MAX_ALIAS_PROBES = 3;

    private final MemoryStorePort store;
    private final ChangeClassifier classifier;
    private final Clock clock;
    private final EmbeddingPort embeddingClient;
    private final VectorStorePort vectorStore;
    private final LlmProperties properties;
    private final MemoryEvolutionService evolution;

    public MemoryService(MemoryStorePort store, ChangeClassifier classifier, Clock clock,
                         EmbeddingPort embeddingClient, VectorStorePort vectorStore, LlmProperties properties,
                         MemoryEvolutionService evolution) {
        this.store = store;
        this.classifier = classifier;
        this.clock = clock;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.evolution = evolution;
    }

    @Transactional
    public Episode recordEpisode(String userId, String conversationId, String summary, EpisodeSource source) {
        Episode episode = new Episode(UUID.randomUUID(), userId, conversationId, summary, source, now());
        return store.saveEpisode(episode);
    }

    @Transactional
    public Memory observe(String userId, Episode episode, CandidateMemory candidate) {
        Instant now = now();
        Optional<Memory> existing = store.findBySubject(userId, candidate.subjectKey());
        if (existing.isPresent() && existing.get().getStatus() == MemoryStatus.RETRACTED) {
            // Respeita a decisão do usuário de esquecer esta informação (antes de gastar juiz).
            return existing.get();
        }
        boolean exactSubject = existing.isPresent();

        // Resolução de alias (dossiê 2026-10-20): o extrator gera chaves de
        // assunto por nome de entidade, então trocas reais de estado ("Tobi" ->
        // "Nina") criavam linhas paralelas sem cadeia de contradição. Candidatos
        // determinísticos (mesmo tipo + sobreposição léxica) entram na fila do
        // juiz; o primeiro veredito não-UNRELATED vence.
        Decision decision = null;
        if (exactSubject) {
            decision = classifier.classify(existing.get(), candidate);
        } else {
            for (Memory alias : aliasCandidates(userId, candidate)) {
                Decision probe = classifier.classify(alias, candidate);
                if (probe.type() != ChangeType.UNRELATED) {
                    existing = Optional.of(alias);
                    decision = probe;
                    break;
                }
            }
        }

        if (existing.isEmpty()) {
            Memory created = new Memory(UUID.randomUUID(), userId, candidate.kind(),
                    candidate.subjectKey(), candidate.content(), candidate.confidence(), episode.getId(), now, "general");
            created.setValidUntil(candidate.validUntil());
            store.saveMemory(created);
            recordRevision(created, ChangeType.CREATED, null, null, episode.getId(),
                    "primeira observação", now);
            indexEmbedding(created);
            return created;
        }

        Memory memory = existing.get();

        String previousContent = memory.getContent();
        double previousConfidence = memory.getConfidence();
        decision = decision == null ? classifier.classify(memory, candidate) : decision;

        if (decision.type() == ChangeType.UNRELATED) {
            // Só alcançável na chave exata: contrato antigo não conhecia o veredito.
            // Complemento sem troca de conteúdo preserva a memória e reforça a confiança.
            log.warn("Juiz marcou observacao como nao relacionada na chave exata; memória preservada");
            decision = new Decision(ChangeType.COMPLEMENTED, previousContent,
                    "juiz marcou nao relacionado na chave exata; memoria preservada");
        }
        if (decision.type() != ChangeType.CONFIRMED && decision.type() != ChangeType.COMPLEMENTED
                && decision.type() != ChangeType.CONTRADICTED
                && decision.type() != ChangeType.CLOSED) {
            throw new IllegalStateException("Unexpected observation change: " + decision.type());
        }
        if (decision.type() == ChangeType.CLOSED) {
            return closePhase(memory, candidate, decision, previousContent, previousConfidence,
                    episode.getId(), now);
        }
        double confidence = decision.type() == ChangeType.COMPLEMENTED
                ? Math.max(previousConfidence, candidate.confidence()) : candidate.confidence();
        // Atenção OLS apenas onde muda a regra de reforço (confirmação); demais mudanças não a usam.
        double attention = decision.type() == ChangeType.CONFIRMED
                ? attentionReading(userId, candidate) : 0.0;
        memory.apply(evolution.evolve(memory.state(), decision.type(), decision.content(), confidence, now,
                attention));

        if (decision.type() == ChangeType.CONTRADICTED) {
            // Uma nova contradição substitui a validade; sem data nova, o plano anterior deixa de ter prazo.
            memory.setValidUntil(candidate.validUntil());
        } else if (candidate.validUntil() != null) {
            memory.setValidUntil(candidate.validUntil());
        }

        store.saveMemory(memory);
        recordRevision(memory, decision.type(), previousContent, previousConfidence, episode.getId(),
                decision.reason(), now);
        if (decision.type() != ChangeType.CONFIRMED) {
            indexEmbedding(memory);
        }
        return memory;
    }

    /**
     * Fechamento de assunto (pré-registro 22/10): o juiz marcou a memória de
     * referência como fase encerrada pela nova informação. A fase sai do recall
     * por retração (conteúdo e histórico preservados em revisão; o filtro de
     * status do armazém vetorial já a exclui) e o estado novo nasce como
     * memória própria com os campos do candidato — sem fundir a transição no
     * conteúdo vivo.
     */
    private Memory closePhase(Memory memory, CandidateMemory candidate, Decision decision,
                              String previousContent, double previousConfidence,
                              UUID episodeId, Instant now) {
        memory.apply(evolution.evolve(memory.state(), ChangeType.CLOSED, previousContent,
                previousConfidence, now));
        store.saveMemory(memory);
        recordRevision(memory, ChangeType.CLOSED, previousContent, previousConfidence,
                episodeId, decision.reason(), now);

        if (candidate.subjectKey().equals(memory.getSubjectKey())) {
            // O estado novo esbarraria na chave única da fase fechada; sem chave
            // própria, o estado novo aguarda um candidato futuro (a regra de
            // extração do pacote pede chave nova porque a fase antiga se fecha).
            log.warn("Estado novo fechou assunto com a mesma chave da fase encerrada; sem nova linha");
            return memory;
        }

        Memory created = new Memory(UUID.randomUUID(), memory.getUserId(), candidate.kind(),
                candidate.subjectKey(), candidate.content(), candidate.confidence(), episodeId, now, "general");
        created.setValidUntil(candidate.validUntil());
        store.saveMemory(created);
        recordRevision(created, ChangeType.CREATED, null, null, episodeId,
                "estado novo após fechamento de assunto", now);
        indexEmbedding(created);
        return created;
    }

    /**
     * Candidatos a alias para a observação: mesmo tipo, sobreposição léxica
     * mínima (determinística, sem LLM), ordenados por sobreposição e, no
     * empate, pela memória mais antiga (a mais estabelecida). A decisão final
     * é sempre do juiz, na fila de {@code observe}.
     */
    private List<Memory> aliasCandidates(String userId, CandidateMemory candidate) {
        List<Map.Entry<Memory, Integer>> scored = new ArrayList<>();
        for (Memory memory : store.findActiveByUser(userId)) {
            if (memory.getKind() != candidate.kind()) {
                continue;
            }
            if (!SubjectMatcher.related(memory, candidate)) {
                continue;
            }
            scored.add(Map.entry(memory, SubjectMatcher.overlapScore(memory, candidate)));
        }
        scored.sort((a, b) -> {
            int byScore = Integer.compare(b.getValue(), a.getValue());
            return byScore != 0 ? byScore
                    : a.getKey().getFirstSeenAt().compareTo(b.getKey().getFirstSeenAt());
        });
        return scored.stream().limit(MAX_ALIAS_PROBES).map(Map.Entry::getKey).toList();
    }

    @Transactional
    public Memory correct(String userId, UUID memoryId, String newContent) {
        Memory memory = owned(userId, memoryId);
        String previousContent = memory.getContent();
        double previousConfidence = memory.getConfidence();
        Instant now = now();

        memory.apply(evolution.evolve(memory.state(), ChangeType.CORRECTED, newContent, 1.0, now));
        store.saveMemory(memory);
        recordRevision(memory, ChangeType.CORRECTED, previousContent, previousConfidence, null,
                "correção feita pelo usuário", now);
        indexEmbedding(memory);
        return memory;
    }

    @Transactional
    public Memory forget(String userId, UUID memoryId) {
        Memory memory = owned(userId, memoryId);
        String previousContent = memory.getContent();
        double previousConfidence = memory.getConfidence();
        Instant now = now();

        memory.apply(evolution.evolve(memory.state(), ChangeType.FORGOTTEN,
                previousContent, previousConfidence, now));
        store.saveMemory(memory);
        recordRevision(memory, ChangeType.FORGOTTEN, previousContent, previousConfidence, null,
                "esquecido pelo usuário", now);
        return memory;
    }

    @Transactional(readOnly = true)
    public List<Memory> recallActive(String userId) {
        return store.findActiveByUser(userId);
    }

    @Transactional(readOnly = true)
    public List<MemoryRevisionDomain> history(String userId, UUID memoryId) {
        owned(userId, memoryId);
        return store.history(memoryId);
    }

    private Memory owned(String userId, UUID memoryId) {
        return store.findOwned(userId, memoryId)
                .orElseThrow(() -> new NoSuchElementException("Memória não encontrada: " + memoryId));
    }

    private void recordRevision(Memory memory, ChangeType type, String previousContent,
                                Double previousConfidence, UUID episodeId, String reason, Instant now) {
        store.saveRevision(new MemoryRevisionDomain(UUID.randomUUID(), memory.getId(), memory.getVersion(), type,
                previousContent, memory.getContent(), previousConfidence, memory.getConfidence(),
                episodeId, reason, now));
    }

    @Transactional
    public ReindexResult reindex(String userId) {
        List<Memory> active = store.findActiveByUser(userId);
        int reindexed = 0;
        for (Memory memory : active) {
            if (indexEmbedding(memory)) {
                reindexed++;
            }
        }
        return new ReindexResult(active.size(), reindexed);
    }

    private boolean indexEmbedding(Memory memory) {
        try {
            // Garante que o INSERT/UPDATE pendente chegue ao banco antes da escrita nativa do vetor.
            store.flush();
            vectorStore.store(memory.getId(), embeddingClient.embed(memory.getContent()),
                    properties.getEmbeddingModel());
            return true;
        } catch (RuntimeException error) {
            log.warn("Nao foi possivel indexar uma memoria");
            return false;
        }
    }

    /**
     * Leitura de atenção OLS (vista de similaridade, arXiv:2504.09663): a evidência nova é a
     * consulta Q_j; as memórias ativas do usuário são as chaves K; suas confianças são os
     * valores V; g é a identidade, no lugar do softmax. Os pesos usam similaridades do
     * armazém vetorial como aproximação de ⟨F_j, F_i⟩; o embedding ótimo F = X U Λ^{-1/2}
     * completo está implementado em OlsAttention para quando houver acesso aos vetores brutos.
     * Falha empírica ou falta de índice devolve 0, preservando a regra legada de reforço.
     */
    private double attentionReading(String userId, CandidateMemory candidate) {
        try {
            Map<UUID, Double> similarities = new HashMap<>();
            for (VectorStorePort.Match match : vectorStore.nearest(userId,
                    embeddingClient.embed(candidate.content()), NEIGHBORS)) {
                similarities.put(match.memoryId(), match.similarity());
            }
            double reading = 0.0;
            for (Memory memory : store.findActiveByUser(userId)) {
                Double similarity = similarities.get(memory.getId());
                if (similarity != null) {
                    // ŷ_j = Σ_i ⟨F_j, F_i⟩ y_i, com y_i igual à confiança da memória i.
                    reading += similarity * memory.getConfidence();
                }
            }
            return reading;
        } catch (RuntimeException error) {
            return 0.0;
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
