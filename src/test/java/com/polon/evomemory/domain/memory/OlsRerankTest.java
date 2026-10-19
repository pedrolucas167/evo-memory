package com.polon.evomemory.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class OlsRerankTest {

    @Test
    void withNearZeroRidgeOnIndependentEvidenceTheFitReproducesRawScores() {
        double[] raw = {0.9, 0.8, 0.2};
        double[][] gram = {
                {1.0, 0.0, 0.1},
                {0.0, 1.0, 0.0},
                {0.1, 0.0, 1.0},
        };

        double[] fitted = OlsRerank.rerank(raw, gram, 1e-12);

        assertThat(fitted[0]).isCloseTo(raw[0], org.assertj.core.data.Offset.offset(1e-6));
        assertThat(fitted[1]).isCloseTo(raw[1], org.assertj.core.data.Offset.offset(1e-6));
        assertThat(fitted[2]).isCloseTo(raw[2], org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void correlatedClusterPullsTogetherAndIsolatedDistractorShrinks() {
        // Três evidências densas (mutuamente correlatas, scores parecidos) e um
        // distrator isolado com score apenas um pouco abaixo. O re-ranking deve
        // ampliar a separação: as evidências correlatas somam vizinhança e o
        // distrator, sem apoio no Gram, cai.
        double[] raw = {0.60, 0.58, 0.56, 0.55};
        double[][] gram = {
                {1.00, 0.90, 0.90, 0.10},
                {0.90, 1.00, 0.90, 0.12},
                {0.90, 0.90, 1.00, 0.08},
                {0.10, 0.12, 0.08, 1.00},
        };

        double[] fitted = OlsRerank.rerank(raw, gram, 0.1);

        double rawGap = raw[0] - raw[3];
        double fittedGap = fitted[0] - fitted[3];
        assertThat(fittedGap).isGreaterThan(rawGap);
        // Nada regressa a negativo: a atenção não inventa irrelevância.
        for (double value : fitted) {
            assertThat(value).isPositive();
        }
    }

    @Test
    void strongerRidgeShrinksEverythingTowardTheNeighborhoodMean() {
        double[] raw = {0.9, 0.2};
        double[][] gram = {
                {1.0, 0.3},
                {0.3, 1.0},
        };

        double[] mild = OlsRerank.rerank(raw, gram, 0.1);
        double[] strong = OlsRerank.rerank(raw, gram, 50.0);

        assertThat(Math.abs(strong[0] - strong[1]))
                .isLessThan(Math.abs(mild[0] - mild[1]));
    }

    @Test
    void singleEvidenceBlendsItselfWithTheRidge() {
        double[] fitted = OlsRerank.rerank(new double[] {0.8}, new double[][] {{1.0}}, 0.2);
        assertThat(fitted[0]).isCloseTo(0.8 * 1.0 / (1.0 + 0.2), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void emptyInputReturnsEmptyOutput() {
        assertThat(OlsRerank.rerank(new double[0], new double[0][], 0.1)).isEmpty();
    }

    @Test
    void rejectsAsymmetricOrMismatchedGram() {
        assertThatThrownBy(() -> OlsRerank.rerank(new double[] {0.5, 0.4},
                new double[][] {{1.0, 0.2}, {0.9, 1.0}}, 0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OlsRerank.rerank(new double[] {0.5},
                new double[][] {{1.0, 0.2}, {0.2, 1.0}}, 0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OlsRerank.rerank(new double[] {0.5}, new double[][] {{1.0}}, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
