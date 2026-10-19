package com.polon.evomemory.domain.memory;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OlsAttentionTest {

    @Test
    void similarityCoefficientsAndAttentionViewsAgreeWithDirectOls() {
        var evidence = List.of(
                new OlsAttention.Evidence(new double[]{1, 0}, 1),
                new OlsAttention.Evidence(new double[]{0, 1}, 2),
                new OlsAttention.Evidence(new double[]{1, 1}, 2));
        double[] query = {1, 2};

        var attention = OlsAttention.attend(evidence, query);

        // Vista de coeficientes resolvida à mão: beta = (X'X)^{-1} X'y = (2/3, 5/3).
        assertEquals(2.0 / 3.0, attention.coefficients()[0], 1e-6);
        assertEquals(5.0 / 3.0, attention.coefficients()[1], 1e-6);
        // x_j beta = 2/3 * 1 + 5/3 * 2 = 4
        double direct = query[0] * attention.coefficients()[0] + query[1] * attention.coefficients()[1];
        assertEquals(direct, attention.prediction(), 1e-6);
        assertEquals(4.0, attention.prediction(), 1e-4);
        // Vista de similaridade: a predição passa a somar pesos registrados pelos valores.
        double[] values = {1, 2, 2};
        double similarityView = 0.0;
        for (int i = 0; i < 3; i++) {
            similarityView += attention.weights()[i] * values[i];
        }
        assertEquals(attention.prediction(), similarityView, 1e-9);
    }

    @Test
    void closerQueryPaysAttentionToCloserKey() {
        // Duas chaves quase ortogonais: a consulta alinhada com a primeira
        // deve ponderar a primeira com peso grande e a segunda quase nulo.
        var evidence = List.of(
                new OlsAttention.Evidence(new double[]{3, 0}, 1),
                new OlsAttention.Evidence(new double[]{0, 3}, 7));
        var attention = OlsAttention.attend(evidence, new double[]{1, 0}, 1e-10);
        assertTrue(Math.abs(attention.weights()[0]) > 40 * Math.abs(attention.weights()[1]),
                "pesos: w0=" + attention.weights()[0] + " w1=" + attention.weights()[1]);
    }

    @Test
    void ridgeFloorKeepsSingularXtXPredictive() {
        // Toda evidência colapsa em [0]; X'X singular: a codificação de ridge evita explodir.
        var evidence = List.of(
                new OlsAttention.Evidence(new double[]{0, 0}, 1),
                new OlsAttention.Evidence(new double[]{0, 0}, 2));
        var attention = OlsAttention.attend(evidence, new double[]{0, 0}, 1e-8);
        assertFalse(Double.isNaN(attention.prediction()));
        assertFalse(Double.isInfinite(attention.prediction()));
        assertEquals(0.0, attention.prediction(), 1e-6);
    }

    @Test
    void rejectsMismatchedFeatures() {
        var evidence = List.of(new OlsAttention.Evidence(new double[]{0}, 1));
        assertThrows(IllegalArgumentException.class,
                () -> OlsAttention.attend(evidence, new double[]{0, 0}));
        assertThrows(IllegalArgumentException.class,
                () -> OlsAttention.attend(evidence, new double[]{}));
    }
}
