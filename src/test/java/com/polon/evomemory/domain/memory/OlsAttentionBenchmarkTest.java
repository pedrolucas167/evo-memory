package com.polon.evomemory.domain.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verificação numerica do mecanismo de atenção OLS em escala, Java puro e
 * determinístico (semente fixa). Mede custo de computação e estabilidade
 * numérica; não mede qualidade de memória. Incluído no `mvn test` do CI.
 */
class OlsAttentionBenchmarkTest {

    private static final int EVIDENCE = 300;
    private static final int FEATURES = 8;

    @Test
    void attentionAtScaleStayedStableCheapAndOrderInvariant() {
        Random random = new Random(42);
        List<OlsAttention.Evidence> evidence = new ArrayList<>(EVIDENCE);
        double[] query = new double[FEATURES];
        for (int b = 0; b < FEATURES; b++) {
            query[b] = 0.5 + random.nextDouble(); // [0.5, 1.5]
        }
        for (int i = 0; i < EVIDENCE; i++) {
            double[] features = new double[FEATURES];
            for (int b = 0; b < FEATURES; b++) {
                features[b] = random.nextDouble() + (b == 0 ? 1e-9 : 0); // desloca a coluna constante
            }
            evidence.add(new OlsAttention.Evidence(features, random.nextInt(100) / 10.0));
        }

        long start = System.nanoTime();
        var attention = OlsAttention.attend(evidence, query, 1e-8);
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        // Estabilidade: predição finita; consistência interna das vistas implícita
        // (a classe lança ArithmeticException se coeficiente e similaridade divergirem).
        assertFalse(Double.isNaN(attention.prediction()));
        assertFalse(Double.isInfinite(attention.prediction()));

        // Invariância à ordem das evidências (OLS não depende da ordem das linhas).
        for (int i = 0; i < EVIDENCE / 2; i++) {
            int swap = EVIDENCE - 1 - i;
            java.util.Collections.swap(evidence, i, swap);
        }
        var shuffled = OlsAttention.attend(evidence, query, 1e-8);
        assertEquals(attention.prediction(), shuffled.prediction(),
                1e-6 * (1 + Math.abs(attention.prediction())));

        // Custo: porta de segurança contra regressão quadrática acidental; folga
        // grande para não instabilizar o CI em hardware variado.
        System.out.printf("OLS attention: %d evidências x %d atributos em %d ms%n",
                EVIDENCE, FEATURES, elapsed);
        assertTrue(elapsed < 10_000, "custo fora da perspectiva esperada: " + elapsed + " ms");
    }

    @Test
    void collinearFeaturesIncludingIdenticalColumnsRemainFinite() {
        Random random = new Random(7);
        List<OlsAttention.Evidence> evidence = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            double v = random.nextDouble();
            // Colunas idênticas tornam X'X singular; o piso de ridge evita explosão.
            evidence.add(new OlsAttention.Evidence(new double[]{v, v, 0.5 * v}, v));
        }
        var attention = OlsAttention.attend(evidence, new double[]{0.3, 0.3, 0.15}, 1e-8);
        assertFalse(Double.isNaN(attention.prediction()));
        assertFalse(Double.isInfinite(attention.prediction()));
        for (double coefficient : attention.coefficients()) {
            assertTrue(!Double.isNaN(coefficient) && Math.abs(coefficient) <= 1e4,
                    "coeficiente fora do intervalo esperado: " + coefficient);
        }
    }

    @Test
    void attentionScansTheWholeConfidenceRangeAcrossTwoHundredStates() {
        var start = new MemoryEvolution.State("old", 0.1, MemoryStatus.ACTIVE, 1, 1,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        var moment = Instant.parse("2026-01-02T00:00:00Z");
        double previous = -1.0;
        for (int i = 0; i <= 200; i++) {
            double attention = i / 200.0;
            var next = MemoryEvolution.evolve(start, ChangeType.CONFIRMED, null, 0, moment, attention);
            assertTrue(next.confidence() >= previous,
                    "confiança deve ser monótona na leitura de atenção");
            assertTrue(next.confidence() <= 1.0);
            previous = next.confidence();
        }
        // Atenção máxima ao teto de reforço: 0,1 + 0,2 -> confiança 0,1 + 0,2*0,9 = 0,28.
        assertEquals(0.28,
                MemoryEvolution.evolve(start, ChangeType.CONFIRMED, null, 0, moment, 2.0).confidence(), 1e-9);
    }
}
