package com.polon.evomemory.domain.memory;

import java.time.Instant;

/**
 * Regra de domínio pura: decide evolução de uma memória.
 * Sem Spring, sem banco, sem LLM.
 */
public class MemoryEvolution {

    public record State(String content, double confidence, MemoryStatus status,
                        int evidenceCount, int version, Instant lastConfirmedAt, Instant updatedAt) {}

    public static State evolve(State current, ChangeType change, String content,
                               double confidence, Instant now) {
        return evolve(current, change, content, confidence, now, 0.0);
    }

    /**
     * Evolução com atenção OLS (Goulet Coulombe, arXiv:2504.09663). O parâmetro
     * {@code attention} é a leitura de atenção Ã=ŷ_j = Σ_i ⟨F_j, F_i⟩ y_i sobre
     * as memórias ativas do usuário, com g = identidade no lugar do softmax.
     * Valores maiores indicam evidência corroborada por memórias ativas similares
     * e confiantes; negativos indicam dissimilaridade. É limitado (clamp) a [0, 1]
     * e escala a taxa de reforço da confirmação: 0,1 + 0,1·Ã.
     * Outros tipos de mudança ignoram {@code attention}.
     */
    public static State evolve(State current, ChangeType change, String content,
                               double confidence, Instant now, double attention) {
        return switch (change) {
            case CONFIRMED -> {
                double weight = Math.max(0.0, Math.min(1.0, attention));
                yield new State(current.content(),
                        Math.min(1.0, current.confidence() + (0.1 + 0.1 * weight) * (1 - current.confidence())),
                        current.status(), current.evidenceCount() + 1, current.version() + 1, now, now);
            }
            case COMPLEMENTED, CONTRADICTED -> new State(content, confidence, current.status(),
                    1, current.version() + 1, now, now);
            case CORRECTED -> new State(content, 1.0, MemoryStatus.ACTIVE,
                    current.evidenceCount(), current.version() + 1, now, now);
            case FORGOTTEN -> new State(current.content(), current.confidence(), MemoryStatus.RETRACTED,
                    current.evidenceCount(), current.version() + 1, current.lastConfirmedAt(), now);
            case CREATED -> throw new IllegalArgumentException("Use creation for the first observation");
        };
    }
}
