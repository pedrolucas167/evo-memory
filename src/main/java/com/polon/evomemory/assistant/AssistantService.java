package com.polon.evomemory.assistant;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
import com.polon.evomemory.llm.EmbeddingClient;
import com.polon.evomemory.llm.LlmClient;
import com.polon.evomemory.llm.LlmRequest;
import com.polon.evomemory.memory.ConversationService;
import com.polon.evomemory.memory.MemoryKind;
import com.polon.evomemory.memory.MemoryService;
import com.polon.evomemory.memory.MemoryVectorStore;
import com.polon.evomemory.memory.SemanticMemory;
import com.polon.evomemory.web.AssistantReply;
import com.polon.evomemory.web.ChatResponse;
import com.polon.evomemory.web.MemoryView;

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
            - Informações de saúde (marcadas como [HEALTH]) só devem aparecer quando forem relevantes para a segurança ou a adequação da resposta, por exemplo ao sugerir uma receita. Nunca as liste.""";

    private static final int PLAN_STALE_DAYS = 90;

    private final MemoryService memoryService;
    private final ConversationService conversationService;
    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final EmbeddingClient embeddingClient;
    private final MemoryVectorStore vectorStore;
    private final RetrievalProperties retrieval;
    private final Clock clock;

    public AssistantService(MemoryService memoryService, ConversationService conversationService,
                            LlmClient llmClient, LlmProperties properties, EmbeddingClient embeddingClient,
                            MemoryVectorStore vectorStore, RetrievalProperties retrieval, Clock clock) {
        this.memoryService = memoryService;
        this.conversationService = conversationService;
        this.llmClient = llmClient;
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
        List<SemanticMemory> active = memoryService.recallActive(userId).stream()
                .filter(memory -> !justLearned.contains(memory.getId()))
                .filter(memory -> !isStalePlan(memory, today))
                .toList();

        List<SemanticMemory> context = select(userId, message, active);
        String answer = llmClient.complete(new LlmRequest(
                properties.getChatModel(), SYSTEM_PROMPT, buildUserPrompt(message, context), 0.7));

        return new AssistantReply(
                answer,
                learned.memories(),
                context.stream().map(MemoryView::from).toList());
    }

    private List<SemanticMemory> select(String userId, String message, List<SemanticMemory> active) {
        Set<UUID> eligible = active.stream().map(SemanticMemory::getId).collect(Collectors.toSet());
        Map<UUID, Double> semantic = semanticMatches(userId, message, eligible);
        Set<String> messageWords = words(message);

        List<SemanticMemory> relevant = active.stream()
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
            List<MemoryVectorStore.Match> matches = vectorStore.nearest(userId, query, retrieval.getTopK()).stream()
                    .filter(match -> eligible.contains(match.memoryId()))
                    .toList();
            if (matches.isEmpty()) {
                return Map.of();
            }
            double best = matches.get(0).similarity();
            return matches.stream()
                    .filter(match -> match.similarity() >= retrieval.getMinSimilarity())
                    .filter(match -> match.similarity() >= best - retrieval.getRelativeMargin())
                    .collect(Collectors.toMap(MemoryVectorStore.Match::memoryId, MemoryVectorStore.Match::similarity));
        } catch (RuntimeException error) {
            log.warn("Busca semântica indisponível, usando apenas palavras: {}", error.getMessage());
            return Map.of();
        }
    }

    static boolean isStalePlan(SemanticMemory memory, LocalDate today) {
        if (memory.getKind() != MemoryKind.PLAN) {
            return false;
        }
        if (memory.getValidUntil() != null) {
            return memory.getValidUntil().isBefore(today);
        }
        LocalDate lastConfirmed = memory.getLastConfirmedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return lastConfirmed.isBefore(today.minusDays(PLAN_STALE_DAYS));
    }

    private static String buildUserPrompt(String message, List<SemanticMemory> context) {
        StringBuilder prompt = new StringBuilder("Contexto do usuário (a data indica a última confirmação):\n");
        if (context.isEmpty()) {
            prompt.append("(nada registrado ainda)\n");
        }
        for (SemanticMemory memory : context) {
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
        return (int) messageWords.stream().filter(memoryWords::contains).count();
    }

    private record Scored(SemanticMemory memory, double semantic, int lexical) {

        double score() {
            return semantic + LEXICAL_WEIGHT * lexical;
        }
    }
}
