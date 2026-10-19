package com.polon.evomemory.application.assistant;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.config.RetrievalProperties;
import com.polon.evomemory.application.ports.EmbeddingPort;
import com.polon.evomemory.application.ports.LlmPort;
import com.polon.evomemory.application.ports.LlmRequest;
import com.polon.evomemory.application.ports.ConversationPort;
import com.polon.evomemory.domain.memory.MemoryKind;
import com.polon.evomemory.domain.memory.OlsRerank;
import com.polon.evomemory.application.ports.MemoryRecallPort;
import com.polon.evomemory.application.ports.VectorStorePort;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.application.model.AssistantReply;
import com.polon.evomemory.application.model.ChatResponse;
import com.polon.evomemory.application.model.MemoryView;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final int FALLBACK_CONTEXT = 3;
    private static final double LEXICAL_WEIGHT = 0.1;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private static final String SYSTEM_PROMPT = """
            Você é um assistente que conhece o usuário por meio de informações registradas em conversas anteriores.

            Regras:
            - Use o contexto do usuário quando isso tornar a resposta mais útil, fazendo a referência de forma natural (ex.: "como você vem estudando...").
            - Não liste o contexto nem fale sobre "memórias" a menos que o usuário pergunte.
            - Se uma informação do contexto parecer desatualizada ou conflitar com a mensagem atual, dê prioridade à mensagem atual.
            - Não invente fatos sobre o usuário que não estejam no contexto ou na mensagem.
            - Não pergunte novamente fatos que já estão no contexto. Use a informação naturalmente, sem listar as memórias.
            - Informações de saúde (marcadas como [HEALTH]) só devem aparecer quando forem relevantes para a segurança ou a adequação da resposta, por exemplo ao sugerir uma receita. Nunca as liste.""";

    private static final int PLAN_STALE_DAYS = 90;

    private final MemoryRecallPort memoryService;
    private final ConversationPort conversationService;
    private final LlmPort llmPort;
    private final LlmProperties properties;
    private final EmbeddingPort embeddingClient;
    private final VectorStorePort vectorStore;
    private final RetrievalProperties retrieval;
    private final Clock clock;

    public AssistantService(MemoryRecallPort memoryService, ConversationPort conversationService,
                            LlmPort llmPort, LlmProperties properties, EmbeddingPort embeddingClient,
                            VectorStorePort vectorStore, RetrievalProperties retrieval, Clock clock) {
        this.memoryService = memoryService;
        this.conversationService = conversationService;
        this.llmPort = llmPort;
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.retrieval = retrieval;
        this.clock = clock;
    }

    public AssistantReply chat(String userId, String conversationId, String message) {
        ChatResponse learned = conversationService.process(userId, conversationId, List.of(message));

        // A mensagem atual já vai no prompt: memórias criadas a partir dela só repetiriam a pergunta.
        // As atualizadas (versão > 1) continuam no contexto, porque guardam informação anterior à
        // mensagem que a LLM acabou de acrescentar a elas.
        Set<UUID> justLearned = learned.memories().stream()
                .filter(memory -> memory.version() == 1)
                .map(MemoryView::id)
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now(clock);
        List<Memory> active = memoryService.recallActive(userId).stream()
                .filter(memory -> !justLearned.contains(memory.getId()))
                .filter(memory -> !isStalePlan(memory, today))
                .toList();

        // Se o usuário pediu com wing explícito, restringe; senão usa "general" como padrão.
        String wingFilter = message.contains("wing:") ? extractWing(message) : null;
        List<Memory> eligible = active.stream()
                .filter(memory -> wingFilter == null || memory.getWing().equals(wingFilter))
                .toList();

        List<Memory> context = select(userId, message, eligible);
        String answer = llmPort.generate(new LlmRequest(
                properties.getChatModel(), SYSTEM_PROMPT, buildUserPrompt(message, context), 0.7));

        return new AssistantReply(
                answer,
                learned.memories(),
                context.stream().map(MemoryView::from).toList());
    }

    private List<Memory> select(String userId, String message, List<Memory> active) {
        Set<UUID> eligible = active.stream().map(Memory::getId).collect(Collectors.toSet());
        Map<UUID, Double> semantic = semanticMatches(userId, message, eligible);
        Set<String> messageWords = words(message);

        List<Memory> relevant = active.stream()
                .map(memory -> new Scored(memory,
                        semantic.getOrDefault(memory.getId(), 0.0),
                        overlap(messageWords, words(memory.getContent() + " " + memory.getSubjectKey()))))
                .filter(scored -> scored.semantic() > 0 || scored.lexical() > 0)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(retrieval.getTopK())
                .map(Scored::memory)
                .toList();

        if (!relevant.isEmpty()) {
            return relevant;
        }
        return active.stream().limit(FALLBACK_CONTEXT).toList();
    }

    private Map<UUID, Double> semanticMatches(String userId, String message, Set<UUID> eligible) {
        try {
            float[] query = embeddingClient.embed(message);
            List<VectorStorePort.Match> matches = vectorStore.nearest(userId, query, retrieval.getTopK()).stream()
                    .filter(match -> eligible.contains(match.memoryId()))
                    .toList();
            if (matches.isEmpty()) {
                return Map.of();
            }
            matches = attenuateByAttention(matches);
            double best = matches.get(0).similarity();
            return matches.stream()
                    .filter(match -> match.similarity() >= retrieval.getMinSimilarity())
                    .filter(match -> match.similarity() >= best - retrieval.getRelativeMargin())
                    .collect(Collectors.toMap(VectorStorePort.Match::memoryId, VectorStorePort.Match::similarity));
        } catch (RuntimeException error) {
            log.warn("Busca semantica indisponivel, usando apenas palavras");
            return Map.of();
        }
    }

    /**
     * Re-classifica os matches pela atenção OLS quando configurado (peso > 0):
     * candidatos com vizinhança forte no Gram ganham relevância compartilhada e
     * distratores isolados encolhem. Manutenção da ordem de {@code nearest};
     * falha na leitura dos vetores degrada para os scores crus.
     */
    private List<VectorStorePort.Match> attenuateByAttention(List<VectorStorePort.Match> matches) {
        double weight = Math.min(1.0, Math.max(0.0, retrieval.getAttentionRerankWeight()));
        if (weight <= 0 || matches.size() < 2) {
            return matches;
        }
        try {
            Map<UUID, float[]> vectors = vectorStore.vectors(
                    matches.stream().map(VectorStorePort.Match::memoryId).toList());
            int n = matches.size();
            if (vectors.size() < n) {
                log.warn("Re-ranking OLS ignorado: vetores ausentes para as evidências");
                return matches;
            }
            double[] raw = new double[n];
            double[][] gram = new double[n][n];
            for (int i = 0; i < n; i++) {
                raw[i] = matches.get(i).similarity();
                for (int j = 0; j < n; j++) {
                    gram[i][j] = i == j ? 1.0 : cosine(vectors.get(matches.get(i).memoryId()),
                            vectors.get(matches.get(j).memoryId()));
                }
            }
            double[] fitted = OlsRerank.rerank(raw, gram, retrieval.getAttentionRerankRidge());
            List<VectorStorePort.Match> reRanked = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                double blended = (1 - weight) * raw[i] + weight * fitted[i];
                reRanked.add(new VectorStorePort.Match(matches.get(i).memoryId(), blended));
            }
            return reRanked;
        } catch (RuntimeException error) {
            log.warn("Re-ranking OLS indisponivel, mantendo scores crus");
            return matches;
        }
    }

    private static double cosine(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    static boolean isStalePlan(Memory memory, LocalDate today) {
        if (memory.getKind() != MemoryKind.PLAN) {
            return false;
        }
        if (memory.getValidUntil() != null) {
            return memory.getValidUntil().isBefore(today);
        }
        LocalDate lastConfirmed = memory.getLastConfirmedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return lastConfirmed.isBefore(today.minusDays(PLAN_STALE_DAYS));
    }

    private static String extractWing(String message) {
        int idx = message.indexOf("wing:");
        if (idx < 0) return null;
        String rest = message.substring(idx + 5).trim();
        int space = rest.indexOf(' ');
        return space > 0 ? rest.substring(0, space) : rest;
    }

    private static String buildUserPrompt(String message, List<Memory> context) {
        StringBuilder prompt = new StringBuilder("Contexto do usuário (a data indica a última confirmação):\n");
        if (context.isEmpty()) {
            prompt.append("(nada registrado ainda)\n");
        }
        for (Memory memory : context) {
            prompt.append("- [").append(memory.getKind()).append("] ")
                    .append(memory.getContent())
                    .append(" (").append(DATE.format(memory.getLastConfirmedAt())).append(")\n");
        }
        prompt.append("\nMensagem atual do usuário:\n").append(message);
        return prompt.toString();
    }

    // Palavras funcionais: casar "para" ou "quando" entre uma pergunta e uma memória não diz
    // nada sobre o assunto, e já trouxe memórias irrelevantes para o contexto.
    private static final Set<String> STOPWORDS = Set.of(
            "para", "porque", "quando", "onde", "durante", "atraves", "mediante", "conforme",
            "enquanto", "embora", "apesar", "sobre", "entre", "desde", "dentro", "apenas",
            "talvez", "tambem", "sempre", "ainda", "agora");

    private static Set<String> words(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(normalized.split("[^\\p{L}]+"))
                .filter(word -> word.length() >= 4)
                .filter(word -> !STOPWORDS.contains(word))
                .collect(Collectors.toSet());
    }

    private static int overlap(Set<String> messageWords, Set<String> memoryWords) {
        Set<String> msgExpanded = SynonymExpander.expand(messageWords);
        Set<String> memExpanded = SynonymExpander.expand(memoryWords);
        return (int) msgExpanded.stream().filter(memExpanded::contains).count();
    }

    private record Scored(Memory memory, double semantic, int lexical) {

        double score() {
            return semantic + LEXICAL_WEIGHT * lexical;
        }
    }
}
