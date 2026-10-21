package com.polon.evomemory.application.assistant;
import com.polon.evomemory.domain.memory.MemoryKind;
import com.polon.evomemory.domain.memory.MemoryStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.config.RetrievalProperties;
import com.polon.evomemory.application.ports.EmbeddingClient;
import com.polon.evomemory.application.ports.LlmPort;
import com.polon.evomemory.application.ports.LlmException;
import com.polon.evomemory.application.ports.LlmRequest;
import com.polon.evomemory.application.memory.ConversationService;
import com.polon.evomemory.application.memory.MemoryService;
import com.polon.evomemory.application.ports.VectorStorePort;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.application.model.response.ChatResponse;
import com.polon.evomemory.application.model.response.MemoryView;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock
    private MemoryService memoryService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private VectorStorePort vectorStore;

    @Mock
    private JudgeReranker judgeReranker;

    private final AtomicReference<LlmRequest> captured = new AtomicReference<>();

    private AssistantService assistant(EmbeddingClient embeddings) {
        return assistant(embeddings, new RetrievalProperties());
    }

    private AssistantService assistant(EmbeddingClient embeddings, RetrievalProperties retrievalProperties) {
        LlmProperties properties = new LlmProperties();
        properties.setChatModel("chat-model");
        LlmPort client = request -> {
            captured.set(request);
            return "resposta";
        };
        return new AssistantService(memoryService, conversationService, client, properties,
                embeddings, vectorStore, retrievalProperties, judgeReranker, FIXED_CLOCK);
    }

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void expiredPlanIsNotUsedAsContext() {
        Memory plan = memory(MemoryKind.PLAN, "viagem-japao", "Vai viajar ao Japão em março.");
        plan.setValidUntil(LocalDate.of(2026, 3, 31));
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(plan));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Quero planejar uma viagem ao Japão.");

        assertThat(captured.get().userPrompt()).doesNotContain("Vai viajar ao Japão em março.");
    }

    @Test
    void planWithoutDateIsDroppedOnlyAfterNinetyDaysWithoutConfirmation() {
        Memory recent = memory(MemoryKind.PLAN, "curso", "Pretende fazer um curso de Rust.");
        Memory old = new Memory(UUID.randomUUID(), "user", MemoryKind.PLAN, "academia",
                "Pretende voltar para a academia.", 0.9, null, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(AssistantService.isStalePlan(recent, LocalDate.of(2026, 10, 10))).isFalse();
        assertThat(AssistantService.isStalePlan(old, LocalDate.of(2026, 10, 10))).isTrue();
    }

    @Test
    void healthMemoriesAreMarkedForCautiousUse() {
        Memory allergy = memory(MemoryKind.HEALTH, "alergia-amendoim", "É alérgico a amendoim.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(allergy));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Qual receita de doce você sugere?");

        assertThat(captured.get().userPrompt()).contains("- [HEALTH] É alérgico a amendoim.");
        assertThat(captured.get().systemPrompt()).contains("[HEALTH]");
    }

    private static EmbeddingClient embeddingsUnavailable() {
        return text -> {
            throw new LlmException("embeddings indisponíveis");
        };
    }

    private static Memory memory(MemoryKind kind, String subjectKey, String content) {
        return new Memory(UUID.randomUUID(), "user", kind, subjectKey, content, 0.9, null, Instant.now());
    }

    @Test
    void includesRelevantMemoryAndLeavesOutUnrelatedOnes() {
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(
                memory(MemoryKind.INTEREST, "sistemas-distribuidos", "Está estudando sistemas distribuídos."),
                memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.")));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Quero revisar consistência em sistemas distribuídos.");

        String prompt = captured.get().userPrompt();
        assertThat(prompt).contains("Está estudando sistemas distribuídos.");
        assertThat(prompt).doesNotContain("culinária");
        assertThat(captured.get().model()).isEqualTo("chat-model");
    }

    @Test
    void fallsBackToTopMemoriesWhenNothingMatches() {
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(
                memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.")));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Oi, tudo bem?");

        assertThat(captured.get().userPrompt()).contains("Gosta de culinária italiana.");
    }

    @Test
    void includesSemanticMatchWithoutSharedWords() {
        Memory studying = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        Memory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(studying, cooking));
        when(vectorStore.nearest(eq("user"), any(), anyInt()))
                .thenReturn(List.of(new VectorStorePort.Match(studying.getId(), 0.7)));

        assistant(text -> new float[] {0.1f, 0.2f}).chat("user", "conv", "Como faço replicação de dados?");

        assertThat(captured.get().userPrompt()).contains("Está estudando sistemas distribuídos.");
        assertThat(captured.get().userPrompt()).doesNotContain("culinária");
    }

    @Test
    void dropsWeakMatchesFarFromTheBestOne() {
        Memory replication = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        Memory react = memory(MemoryKind.PROJECT, "app-financas",
                "Está construindo um app de finanças pessoais em React.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(replication, react));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(replication.getId(), 0.70),
                new VectorStorePort.Match(react.getId(), 0.55)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Quero entender replicação de dados.");

        assertThat(captured.get().userPrompt()).contains("Está estudando sistemas distribuídos.");
        assertThat(captured.get().userPrompt()).doesNotContain("React");
    }

    @Test
    void ignoresSemanticMatchesBelowThreshold() {
        Memory studying = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        Memory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(studying, cooking));
        when(vectorStore.nearest(eq("user"), any(), anyInt()))
                .thenReturn(List.of(new VectorStorePort.Match(cooking.getId(), 0.2)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Oi, tudo bem?");

        // Nada passou do limiar nem por palavras: cai no fallback, que inclui as memórias de maior confiança.
        assertThat(captured.get().userPrompt()).contains("Gosta de culinária italiana.");
        assertThat(captured.get().userPrompt()).contains("Está estudando sistemas distribuídos.");
    }

    @Test
    void keepsUpdatedMemoryInContextBecauseItHoldsOlderKnowledge() {
        // A mensagem foi absorvida por uma memória existente (versão 2): ela guarda o que se sabia
        // antes, então não pode ser descartada junto com a repetição da mensagem.
        Memory distributed = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos e quer entender replicação de dados.");
        MemoryView updated = new MemoryView(distributed.getId(), MemoryKind.INTEREST, "sistemas-distribuidos",
                distributed.getContent(), 0.9, MemoryStatus.ACTIVE, 2, Instant.now());
        Memory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of(updated)));
        when(memoryService.recallActive("user")).thenReturn(List.of(distributed, cooking));
        when(vectorStore.nearest(eq("user"), any(), anyInt()))
                .thenReturn(List.of(new VectorStorePort.Match(distributed.getId(), 0.91)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Quero entender replicação de dados entre servidores.");

        assertThat(captured.get().userPrompt()).contains("sistemas distribuídos");
    }

    @Test
    void doesNotAdmitFailedMatchThroughSharedStopword() {
        Memory running = memory(MemoryKind.INTEREST, "corrida", "Gosta de corrida de longa distância.");
        // Compartilha apenas a preposição "para" com a pergunta, e reprova no limiar semântico.
        Memory trip = memory(MemoryKind.PLAN, "viagem-japao", "Vai viajar para o Japão em abril.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(running, trip));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(running.getId(), 0.61),
                new VectorStorePort.Match(trip.getId(), 0.50)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Vou começar a treinar para uma maratona.");

        assertThat(captured.get().userPrompt()).contains("Gosta de corrida de longa distância.");
        assertThat(captured.get().userPrompt()).doesNotContain("Japão");
    }

    @Test
    void ignoresSharedStopwordsWhenEmbeddingsAreUnavailable() {
        Memory studying = memory(MemoryKind.INTEREST, "sistemas-distribuidos", "Está estudando sistemas distribuídos.");
        Memory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        Memory cat = memory(MemoryKind.FACT, "gato", "Tem um gato chamado Miau.");
        // Compartilha só a preposição "para", e está fora do top 3 do fallback.
        Memory trip = memory(MemoryKind.PLAN, "viagem-japao", "Vai viajar para o Japão em abril.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(studying, cooking, cat, trip));

        assistant(embeddingsUnavailable()).chat("user", "conv", "Quero treinar para uma maratona.");

        assertThat(captured.get().userPrompt()).doesNotContain("Japão");
    }

    @Test
    void doesNotEchoMemoryJustLearnedFromTheMessage() {
        Memory echoed = memory(MemoryKind.INTEREST, "replicacao",
                "Quer entender replicação de dados.");
        Memory studying = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        MemoryView learnedNow = new MemoryView(echoed.getId(), MemoryKind.INTEREST, "replicacao",
                echoed.getContent(), 0.9, MemoryStatus.ACTIVE, 1, Instant.now());
        Memory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        Memory cat = memory(MemoryKind.FACT, "gato", "Tem um gato chamado Miau.");
        Memory coffee = memory(MemoryKind.PREFERENCE, "cafe", "Prefere café sem açúcar.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of(learnedNow)));
        // A memória de interesse fica por último: se a seleção caísse no fallback (top 3), ela ficaria de fora.
        when(memoryService.recallActive("user")).thenReturn(List.of(echoed, cooking, cat, coffee, studying));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(echoed.getId(), 0.99),
                new VectorStorePort.Match(studying.getId(), 0.62),
                new VectorStorePort.Match(cooking.getId(), 0.20)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Quer entender replicação de dados.");

        assertThat(captured.get().userPrompt()).contains("- [INTEREST] Está estudando sistemas distribuídos.");
        assertThat(captured.get().userPrompt()).doesNotContain("- [INTEREST] Quer entender replicação de dados.");
    }

    @Test
    void learnsFromMessageBeforeAnswering() {        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive(anyString())).thenReturn(List.of());

        assistant(embeddingsUnavailable()).chat("user", "conv", "Oi");

        verify(conversationService).process("user", "conv", List.of("Oi"));
    }

    @Test
    void emptyContextIsSignaledToTheModel() {
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of());

        assistant(embeddingsUnavailable()).chat("user", "conv", "Oi");

        assertThat(captured.get().userPrompt()).contains("nada registrado ainda");
    }

    @Test
    void attentionRerankPushesIsolatedDistractorOutOfContext() {
        // Três evidências mutuamente correlatas (cosseno 0,6 entre si) e um distrator
        // ortogonal aos três. Cru: distrator 0,54 fica dentro da margem relativa
        // (corte 0,54). Com a atenção OLS (peso 1), o cluster ganha apoio mútuo e o
        // distrator, sem vizinhança no Gram, cai para 0,54/1,1 = 0,49 — fora do corte.
        Memory distributed = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        Memory parallel = memory(MemoryKind.INTEREST, "sistemas-paralelos",
                "Quer dominar sistemas paralelos na universidade.");
        Memory book = memory(MemoryKind.INTEREST, "livro-distribuidos",
                "Está lendo um livro de computação distribuída.");
        Memory kids = memory(MemoryKind.FACT, "filhos", "Tem dois filhos pequenos.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(distributed, parallel, book, kids));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(distributed.getId(), 0.62),
                new VectorStorePort.Match(parallel.getId(), 0.62),
                new VectorStorePort.Match(book.getId(), 0.62),
                new VectorStorePort.Match(kids.getId(), 0.54)));
        when(vectorStore.vectors(any())).thenReturn(Map.of(
                distributed.getId(), new float[] {1f, 0f, 0f, 0f},
                parallel.getId(), new float[] {0.6f, 0.8f, 0f, 0f},
                book.getId(), new float[] {0.6f, 0.3f, 0.7416f, 0f},
                kids.getId(), new float[] {0f, 0f, 0f, 1f}));

        RetrievalProperties withAttention = new RetrievalProperties();
        withAttention.setAttentionRerankWeight(1.0);
        withAttention.setAttentionRerankRidge(0.1);
        assistant(text -> new float[] {0.1f}, withAttention)
                .chat("user", "conv", "Quero entender replicação de dados entre servidores.");

        String prompt = captured.get().userPrompt();
        assertThat(prompt).contains("Está estudando sistemas distribuídos.");
        assertThat(prompt).contains("sistemas paralelos");
        assertThat(prompt).contains("computação distribuída");
        assertThat(prompt).doesNotContain("Tem dois filhos pequenos.");
    }

    @Test
    void missingVectorsDegenerateToRawScores() {
        Memory distributed = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        Memory parallel = memory(MemoryKind.INTEREST, "sistemas-paralelos",
                "Quer dominar sistemas paralelos na universidade.");
        Memory kids = memory(MemoryKind.FACT, "filhos", "Tem dois filhos pequenos.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(distributed, parallel, kids));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(distributed.getId(), 0.62),
                new VectorStorePort.Match(parallel.getId(), 0.62),
                new VectorStorePort.Match(kids.getId(), 0.54)));
        when(vectorStore.vectors(any())).thenReturn(Map.of());

        RetrievalProperties withAttention = new RetrievalProperties();
        withAttention.setAttentionRerankWeight(1.0);
        assistant(text -> new float[] {0.1f}, withAttention)
                .chat("user", "conv", "Quero entender replicação de dados entre servidores.");

        // Score cru preserva o comportamento histórico: o distrator entra pela margem.
        assertThat(captured.get().userPrompt()).contains("Tem dois filhos pequenos.");
    }

    @Test
    void doesNotFetchVectorsWhenRerankIsDisabled() {
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of());
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of());

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Quero entender replicação de dados.");

        verify(vectorStore, never()).vectors(any());
    }

    @Test
    void judgeFilterExcludesDistractorWhileKeepingRelevant() {
        Memory distributed = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        Memory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        Memory kids = memory(MemoryKind.FACT, "filhos", "Tem dois filhos pequenos.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(distributed, cooking, kids));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(distributed.getId(), 0.70),
                new VectorStorePort.Match(cooking.getId(), 0.68),
                new VectorStorePort.Match(kids.getId(), 0.66)));
        when(judgeReranker.select(eq("Quero revisar consistência em sistemas distribuídos."), any()))
                .thenReturn(java.util.Optional.of(List.of(distributed.getId())));

        RetrievalProperties withJudge = new RetrievalProperties();
        withJudge.setJudgeRerank(true);
        withJudge.setJudgeRerankMaxCandidates(8);
        assistant(text -> new float[] {0.1f}, withJudge)
                .chat("user", "conv", "Quero revisar consistência em sistemas distribuídos.");

        // O teto de contexto agora incide sobre a seleção do juiz.
        assertThat(captured.get().userPrompt()).contains("Está estudando sistemas distribuídos.");
        assertThat(captured.get().userPrompt()).doesNotContain("Gosta de culinária italiana.")
                .doesNotContain("Tem dois filhos pequenos.");
    }

    @Test
    void judgeFailureDegradesToScoreOrder() {
        Memory distributed = memory(MemoryKind.INTEREST, "sistemas-distribuidos",
                "Está estudando sistemas distribuídos.");
        Memory cooking = memory(MemoryKind.PREFERENCE, "culinaria", "Gosta de culinária italiana.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(distributed, cooking));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(distributed.getId(), 0.70),
                new VectorStorePort.Match(cooking.getId(), 0.68)));
        when(judgeReranker.select(anyString(), any())).thenReturn(java.util.Optional.empty());

        RetrievalProperties withJudge = new RetrievalProperties();
        withJudge.setJudgeRerank(true);
        assistant(text -> new float[] {0.1f}, withJudge)
                .chat("user", "conv", "Quero revisar consistência em sistemas distribuídos.");

        assertThat(captured.get().userPrompt()).contains("Está estudando sistemas distribuídos.");
        assertThat(captured.get().userPrompt()).contains("Gosta de culinária italiana.");
    }

    @Test
    void judgeIsNotConsultedWhenDisabled() {
        RetrievalProperties withoutJudge = new RetrievalProperties();
        withoutJudge.setJudgeRerank(false);
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of());
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of());

        assistant(text -> new float[] {0.1f}, withoutJudge).chat("user", "conv", "Oi.");

        verify(judgeReranker, never()).select(anyString(), any());
    }

    @Test
    void narrowModeCutsTheMarginalCandidateBeforeTheJudge() {
        // Caso real do diagnóstico offline 21/10 (gato-abc): a linha de estado
        // atual cai na margem relativa e o juiz nunca a vê.
        Memory renamed = memory(MemoryKind.FACT, "gato-tobi", "Adotou um gato chamado Tobi, que fugiu.");
        Memory current = memory(MemoryKind.FACT, "gata-nina",
                "A gata Mel morreu, e adotou uma gata chamada Nina.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(renamed, current));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(renamed.getId(), 0.70),
                new VectorStorePort.Match(current.getId(), 0.55)));

        assistant(text -> new float[] {0.1f}).chat("user", "conv", "Onde mora o meu parente?");

        assertThat(captured.get().userPrompt()).contains("Adotou um gato chamado Tobi");
        assertThat(captured.get().userPrompt()).doesNotContain("Nina");
        verify(judgeReranker, never()).select(anyString(), any());
    }

    @Test
    void wideCandidatesHandTheMarginalMatchToTheJudge() {
        Memory renamed = memory(MemoryKind.FACT, "gato-tobi", "Adotou um gato chamado Tobi, que fugiu.");
        Memory current = memory(MemoryKind.FACT, "gata-nina",
                "A gata Mel morreu, e adotou uma gata chamada Nina.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(renamed, current));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(renamed.getId(), 0.70),
                new VectorStorePort.Match(current.getId(), 0.55)));
        final var seenCandidates = new AtomicReference<Integer>();
        when(judgeReranker.select(anyString(), any())).thenAnswer(invocation -> {
            seenCandidates.set(((List<?>) invocation.getArgument(1)).size());
            return java.util.Optional.of(List.of(renamed.getId(), current.getId()));
        });

        RetrievalProperties wide = new RetrievalProperties();
        wide.setJudgeWideCandidates(true);
        assistant(text -> new float[] {0.1f}, wide).chat("user", "conv", "Onde mora o meu parente?");

        // A margem não corta a geração de candidatos: o juiz decide sobre as
        // duas linhas do mesmo tópico, e o contexto final recebe o estado atual.
        assertThat(seenCandidates.get()).isEqualTo(2);
        assertThat(captured.get().userPrompt()).contains("Adotou um gato chamado Tobi");
        assertThat(captured.get().userPrompt()).contains("A gata Mel morreu");
    }

    @Test
    void wideCandidatesDegradeWithoutMarginAfterJudgeFailure() {
        Memory renamed = memory(MemoryKind.FACT, "gato-tobi", "Adotou um gato chamado Tobi, que fugiu.");
        Memory current = memory(MemoryKind.FACT, "gata-nina",
                "A gata Mel morreu, e adotou uma gata chamada Nina.");
        when(conversationService.process(eq("user"), eq("conv"), any()))
                .thenReturn(new ChatResponse(UUID.randomUUID(), List.of()));
        when(memoryService.recallActive("user")).thenReturn(List.of(renamed, current));
        when(vectorStore.nearest(eq("user"), any(), anyInt())).thenReturn(List.of(
                new VectorStorePort.Match(renamed.getId(), 0.70),
                new VectorStorePort.Match(current.getId(), 0.55)));
        when(judgeReranker.select(anyString(), any())).thenReturn(java.util.Optional.empty());

        RetrievalProperties wide = new RetrievalProperties();
        wide.setJudgeWideCandidates(true);
        assistant(text -> new float[] {0.1f}, wide).chat("user", "conv", "Onde mora o meu parente?");

        // Degradação honesta do modo largo: ordenação por score, sem margem.
        assertThat(captured.get().userPrompt()).contains("Adotou um gato chamado Tobi");
        assertThat(captured.get().userPrompt()).contains("A gata Mel morreu");
    }
}
