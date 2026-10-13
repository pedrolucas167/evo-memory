package com.polon.evomemory.infrastructure.llm;
import com.polon.evomemory.application.ports.ChangeClassifier;
import com.polon.evomemory.domain.memory.Memory;
import com.polon.evomemory.domain.memory.CandidateMemory;
import com.polon.evomemory.domain.memory.ChangeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polon.evomemory.config.LlmProperties;
import com.polon.evomemory.application.ports.LlmClient;
import com.polon.evomemory.application.ports.LlmRequest;

@Component
@Primary
class LlmChangeClassifier implements ChangeClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmChangeClassifier.class);

    private static final String SYSTEM_PROMPT = """
            Você decide como uma nova informação sobre o usuário altera uma memória já registrada sobre o mesmo assunto.

            Escolha uma decisão:
            - CONFIRMED: a nova informação repete o que a memória já diz.
            - COMPLEMENTED: a nova informação acrescenta detalhes sem contrariar a memória. O conteúdo deve combinar os dois.
            - CONTRADICTED: a nova informação substitui ou contraria a memória (mudança de fato, de opinião ou de decisão). O conteúdo deve refletir apenas o estado mais recente.

            Responda apenas com JSON neste formato:
            {"decision": "CONFIRMED|COMPLEMENTED|CONTRADICTED", "content": "frase curta em português", "reason": "motivo em uma frase"}""";

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final ObjectMapper mapper;
    private final SimpleChangeClassifier fallback;

    LlmChangeClassifier(LlmClient llmClient, LlmProperties properties, ObjectMapper mapper,
                        SimpleChangeClassifier fallback) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.mapper = mapper;
        this.fallback = fallback;
    }

    @Override
    public Decision classify(Memory current, CandidateMemory candidate) {
        String userPrompt = "Memória atual: \"" + current.getContent() + "\"\n"
                + "Nova informação: \"" + candidate.content() + "\"";
        try {
            String raw = llmClient.complete(new LlmRequest(
                    properties.getJudgeModel(), SYSTEM_PROMPT, userPrompt, 0.0));
            return parse(raw, current, candidate);
        } catch (RuntimeException error) {
            log.warn("Juiz de mudancas falhou, usando comparacao textual");
            return fallback.classify(current, candidate);
        }
    }

    private Decision parse(String raw, Memory current, CandidateMemory candidate) {
        String cleaned = raw.trim().replaceAll("(?s)^```(?:json)?\\s*|\\s*```$", "");
        JsonNode node;
        try {
            node = mapper.readTree(cleaned);
        } catch (Exception error) {
            throw new IllegalArgumentException("Resposta do juiz nao era JSON valido");
        }

        String decisionText = node.path("decision").asText("");
        ChangeType type = switch (decisionText) {
            case "CONFIRMED" -> ChangeType.CONFIRMED;
            case "COMPLEMENTED" -> ChangeType.COMPLEMENTED;
            case "CONTRADICTED" -> ChangeType.CONTRADICTED;
            default -> throw new IllegalArgumentException("Decisao desconhecida do juiz");
        };

        String content = node.path("content").asText("").trim();
        if (type == ChangeType.CONFIRMED) {
            content = current.getContent();
        } else if (content.isEmpty()) {
            content = candidate.content();
        }

        return new Decision(type, content, "juiz: " + node.path("reason").asText("sem motivo informado"));
    }
}
