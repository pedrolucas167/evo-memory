package com.polon.evomemory.domain.memory;

import java.time.Instant;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Experimento A/B offline e sintético do reforço de confirmação pela atenção
 * OLS (regra de MemoryEvolution com e sem leitura). Determinístico, Java puro:
 * mede se a taxa condicionada discrimina evidência coerente de ruído, enquanto
 * a taxa legada trata ambos de forma idêntica. Verificação de regra de domínio,
 * não qualidade pontual de memória.
 */
class AttentionAbExperimentTest {

    private static final int STEPS = 10;
    private static final Instant FIRST = Instant.parse("2026-01-01T00:00:00Z");

    /** Confiança final sob os padrões coerente (leitura 0,9) e ruído (leitura 0,12). */
    private static double[] run(DoubleUnaryOperator policy) {
        double coherent = 0.5;
        double noise = 0.5;
        for (int i = 1; i <= STEPS; i++) {
            Instant moment = FIRST.plusSeconds(i * 60L);
            var coherentState = state(coherent);
            var noiseState = state(noise);
            coherent = MemoryEvolution.evolve(
                    coherentState, ChangeType.CONFIRMED, null, 0, moment, policy.applyAsDouble(0.9)).confidence();
            noise = MemoryEvolution.evolve(
                    noiseState, ChangeType.CONFIRMED, null, 0, moment, policy.applyAsDouble(0.12)).confidence();
        }
        return new double[]{coherent, noise};
    }

    private static MemoryEvolution.State state(double confidence) {
        return new MemoryEvolution.State("fato", confidence, MemoryStatus.ACTIVE, 1, 1, FIRST, FIRST);
    }

    @Test
    void attentionRateSeparatesCoherentEvidenceFromNoiseAndLegacyDoesNot() {
        double[] legacy = run(reading -> 0.0);
        double[] attended = run(reading -> reading);

        System.out.printf("A/B legada     : coerente=%.4f ruido=%.4f separacao=%.4f%n",
                legacy[0], legacy[1], Math.abs(legacy[0] - legacy[1]));
        System.out.printf("A/B atencao OLS: coerente=%.4f ruido=%.4f separacao=%.4f%n",
                attended[0], attended[1], Math.abs(attended[0] - attended[1]));

        // A política legada não discrimina: trilhas idênticas para coerente e ruído.
        assertEquals(legacy[0], legacy[1], 1e-12,
                "taxa fixa empurra coerente e ruido na mesma direcao");
        // A leitura de atenção separa os padrões; taxas 0,19 vs 0,112 compostas em
        // 10 passos partem de 0,5 para 1 - 0,5*0,81^10 ≈ 0,9392 e 1 - 0,5*0,888^10 ≈ 0,8476.
        assertTrue(attended[0] > attended[1] + 0.08,
                "atencao deve separar confianca coerente de ruido: "
                        + attended[0] + " vs " + attended[1]);
        assertTrue(Math.abs(attended[1] - legacy[1]) < 0.06,
                "ruido deve permanecer proximo do reforco conservador legado");
        assertTrue(attended[0] <= 1.0 && attended[1] <= 1.0);
    }
}
