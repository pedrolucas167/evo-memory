package com.polon.evomemory.application.memory;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.MemoryKind;
import com.polon.evomemory.domain.memory.MemoryStatus;
import com.polon.evomemory.domain.memory.CandidateMemory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.application.ports.LlmClient;
import com.polon.evomemory.application.ports.LlmRequest;

@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    /**
     * Extrator do pacote de fechamento de assunto (pré-registro 24/10, frente
     * 1.1): além do estado novo sem narrar a transição, devolve um candidato
     * explícito de fechamento com o MESMO kind/subject_key da fase a fechar —
     * o fechamento deixa de depender de o candidato citar a fase antiga
     * (rodada 1: cargo fechou porque reusou a chave; gato nunca fechou porque
     * trocou de chave sem menção).
     */
    private static final String SYSTEM_PROMPT_WITH_CLOSING = """
            Você extrai memórias sobre o usuário a partir das mensagens dele.

            Tipos:
            - INTEREST: assunto que o usuário estuda ou acompanha (ex.: "está estudando sistemas distribuídos").
            - PREFERENCE: gosto, escolha ou forma de trabalhar que o usuário prefere (ex.: "prefere consistência eventual").
            - PROJECT: algo que o usuário está construindo ou desenvolvendo.
            - DECISION: escolha já tomada e assumida pelo usuário (ex.: "decidiu usar PostgreSQL").
            - FACT: dado pessoal ou circunstância objetiva (ex.: "mora em Florianópolis").
            - HEALTH: alergia, restrição alimentar ou condição de saúde que o próprio usuário declara sobre si (ex.: "sou alérgico a amendoim").
            - PLAN: intenção declarada de fazer algo (ex.: "vou viajar para o Japão em abril"). Se a mensagem trouxer uma data, preencha valid_until com AAAA-MM-DD; caso contrário, null.

            Regras:
            - Registre apenas afirmações feitas pelo usuário como fatos sobre si.
            - Não registre hipóteses ("e se...", "talvez", "seria legal"), perguntas, ironia ou brincadeiras.
            - Não registre planos de outras pessoas.
            - Não registre documentos (CPF, RG), senhas, números de cartão ou endereços completos.
            - Se a mensagem disser que um plano foi cancelado ou concluído, use o mesmo subject_key do plano, com o conteúdo atualizado (ex.: "Cancelou a viagem para o Japão.") e kind PLAN.
            - Se a mensagem encerra a fase de uma memória existente (algo ou alguém morreu, partiu, foi vendido, terminou, foi cancelado ou concluído), devolva também um candidato de FECHAMENTO para essa memória: mesmo kind e MESMO subject_key da memória existente, com conteúdo curto afirmando a informação que encerra a fase (ex.: para a memória "Tem uma gata chamada Mel.", o candidato "A gata Mel morreu.").
            - Registre um estado novo APENAS quando a mensagem introduzir um estado novo (adotou, comprou, contratou, mudou-se): use um subject_key NOVO e content que não narra a transição nem cita a fase encerrada (em vez de "A gata Mel morreu e adotou Tobi", escreva "Adotou um gato chamado Tobi.").
            - Se nada for relevante, retorne uma lista vazia.

            Responda apenas com JSON neste formato:
            {"memories": [{"kind": "INTEREST|PREFERENCE|PROJECT|DECISION|FACT|HEALTH|PLAN", "subject_key": "slug-curto", "content": "frase curta em português", "confidence": 0.0, "valid_until": null}]}""";

    private static final String SYSTEM_PROMPT = """
            Você extrai memórias sobre o usuário a partir das mensagens dele.

            Tipos:
            - INTEREST: assunto que o usuário estuda ou acompanha (ex.: "está estudando sistemas distribuídos").
            - PREFERENCE: gosto, escolha ou forma de trabalhar que o usuário prefere (ex.: "prefere consistência eventual").
            - PROJECT: algo que o usuário está construindo ou desenvolvendo.
            - DECISION: escolha já tomada e assumida pelo usuário (ex.: "decidiu usar PostgreSQL").
            - FACT: dado pessoal ou circunstância objetiva (ex.: "mora em Florianópolis").
            - HEALTH: alergia, restrição alimentar ou condição de saúde que o próprio usuário declara sobre si (ex.: "sou alérgico a amendoim").
            - PLAN: intenção declarada de fazer algo (ex.: "vou viajar para o Japão em abril"). Se a mensagem trouxer uma data, preencha valid_until com AAAA-MM-DD; caso contrário, null.

            Regras:
            - Registre apenas afirmações feitas pelo usuário como fatos sobre si.
            - Não registre hipóteses ("e se...", "talvez", "seria legal"), perguntas, ironia ou brincadeiras.
            - Não registre planos de outras pessoas.
            - Não registre documentos (CPF, RG), senhas, números de cartão ou endereços completos.
            - Se a mensagem disser que um plano foi cancelado ou concluído, use o mesmo subject_key do plano, com o conteúdo atualizado (ex.: "Cancelou a viagem para o Japão.") e kind PLAN.
            - Se uma mensagem atualiza uma memória existente, use o mesmo subject_key com o conteúdo atualizado.
            - Se nada for relevante, retorne uma lista vazia.

            Responda apenas com JSON neste formato:
            {"memories": [{"kind": "INTEREST|PREFERENCE|PROJECT|DECISION|FACT|HEALTH|PLAN", "subject_key": "slug-curto", "content": "frase curta em português", "confidence": 0.0, "valid_until": null}]}""";

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final ObjectMapper mapper;

    public ExtractionService(LlmClient llmClient, LlmProperties properties, ObjectMapper mapper) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    public List<CandidateMemory> extract(List<String> messages, List<Memory> existing) {
        String userPrompt = "Memórias existentes:\n"
                + toJson(existing)
                + "\n\nMensagens do usuário:\n"
                + String.join("\n", messages.stream().map("- "::concat).toList());

        String raw = llmClient.complete(new LlmRequest(
                properties.getExtractionModel(),
                properties.isSubjectClosing() ? SYSTEM_PROMPT_WITH_CLOSING : SYSTEM_PROMPT,
                userPrompt, 0.0));
        return parse(raw);
    }

    private String toJson(List<Memory> existing) {
        List<ExistingMemory> memories = existing.stream()
                .filter(memory -> memory.getStatus() == MemoryStatus.ACTIVE)
                .map(memory -> new ExistingMemory(
                        memory.getKind().name(),
                        memory.getSubjectKey(),
                        memory.getContent()))
                .toList();
        try {
            return mapper.writeValueAsString(memories);
        } catch (Exception error) {
            return "[]";
        }
    }

    private List<CandidateMemory> parse(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```(?:json)?\\s*|\\s*```$", "");
        }
        try {
            JsonNode root = mapper.readTree(cleaned);
            JsonNode memories = root.path("memories");
            if (!memories.isArray()) {
                throw new IllegalArgumentException("campo memories não é uma lista");
            }
            List<CandidateMemory> result = new ArrayList<>();
            for (JsonNode node : memories) {
                CandidateMemory candidate = toCandidate(node);
                if (candidate != null) {
                    result.add(candidate);
                }
            }
            return result;
        } catch (Exception error) {
            throw new IllegalArgumentException("Resposta da LLM nao era JSON valido");
        }
    }

    private CandidateMemory toCandidate(JsonNode node) {
        String subjectKey = node.path("subject_key").asText("").trim();
        String content = node.path("content").asText("").trim();
        if (subjectKey.isEmpty() || content.isEmpty()) {
            log.warn("Memoria ignorada por falta de subject_key ou content");
            return null;
        }
        String kindText = node.path("kind").asText("").trim().toUpperCase(Locale.ROOT);
        MemoryKind kind;
        try {
            kind = MemoryKind.valueOf(kindText);
        } catch (IllegalArgumentException error) {
            log.warn("Memoria ignorada por tipo desconhecido");
            return null;
        }
        double confidence = node.path("confidence").asDouble(0.5);
        return new CandidateMemory(kind, subjectKey.toLowerCase(Locale.ROOT), content, confidence,
                parseDate(node.path("valid_until").asText("")));
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank() || text.equals("null")) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception error) {
            log.warn("Data de validade ignorada por formato invalido");
            return null;
        }
    }

    private record ExistingMemory(String kind, String subjectKey, String content) {
    }
}
