package com.polon.evomemory.domain.memory;

/**
 * Re-ranking de recuperação pela vista de atenção do OLS (Goulet Coulombe,
 * NeurIPS 2025), forma dual para N evidências:
 *
 * <pre>
 *   atenção:   ŷ = g(Q K' V)   com g = identidade, K = Gram das evidências, V = y
 *   equivalente: ŷ = K (K + λI)^{-1} y = H_λ y
 *   autovalores: U diag(λ_k / (λ_k + ridge)) U' y
 * </pre>
 *
 * Diferente do softmax, g identidade não normaliza pesos: candidatos correlatos
 * entre si compartilham relevância (a matriz H_λ interpola cada score cru na
 * média ponderada da sua vizinhança no Gram), e candidatos isolados — os
 * distratores com vizinhança fraca — encolhem em direção à média. O ridge λ
 * garante a inversão (K é PSD, logo K + λI é SPD) e controla quanto do score
 * original sobrevive.
 *
 * A aplicação informa o Gram <em>cósseo</em> das evidências e os scores crus
 * (similaridades da consulta) e usa {@code fitted} no lugar dos scores crus
 * quando o peso de re-ranking está configurado.
 *
 * Java puro: sem Spring, sem banco, sem LLM. Inversão por Cholesky
 * (K + λI é simétrica positiva definida).
 */
public final class OlsRerank {

    private static final double SYMMETRY_TOLERANCE = 1e-9;

    private OlsRerank() {
    }

    /**
     * @param rawScores y_i, os scores crus das evidências contra a consulta
     * @param gram      matriz N×N simétrica, gram[i][j] = ⟨F_i, F_j⟩ (cosseno)
     * @param ridge     λ > 0, piso de regularização na inversão
     * @return ŷ = K (K + λI)^{-1} y, o score re-classificado
     */
    public static double[] rerank(double[] rawScores, double[][] gram, double ridge) {
        int n = rawScores.length;
        if (ridge <= 0) {
            throw new IllegalArgumentException("Ridge precisa ser positivo");
        }
        if (n == 0) {
            return new double[0];
        }
        if (gram.length != n) {
            throw new IllegalArgumentException("Gram com dimensão diferente dos scores");
        }
        for (int i = 0; i < n; i++) {
            if (gram[i].length != n) {
                throw new IllegalArgumentException("Gram não é quadrada");
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(gram[i][j] - gram[j][i]) > SYMMETRY_TOLERANCE) {
                    throw new IllegalArgumentException("Gram não é simétrica");
                }
            }
        }

        // A = K + λI é simétrica positiva definida: fatoração de Cholesky A = L L'.
        double[][] a = new double[n][n];
        for (int i = 0; i < n; i++) {
            a[i] = gram[i].clone();
            a[i][i] += ridge;
        }
        double[][] l = cholesky(a);

        // Resolve A α = y (substituição direta + retrosubstituição).
        double[] alpha = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = rawScores[i];
            for (int k = 0; k < i; k++) {
                sum -= l[i][k] * alpha[k];
            }
            alpha[i] = sum / l[i][i];
        }
        for (int i = n - 1; i >= 0; i--) {
            double sum = alpha[i];
            for (int k = i + 1; k < n; k++) {
                sum -= l[k][i] * alpha[k];
            }
            alpha[i] = sum / l[i][i];
        }

        // ŷ = K α. Com Gram diagonal e λ pequeno, ŷ ≈ y: o ajuste reproduz os scores.
        double[] fitted = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                sum += gram[i][j] * alpha[j];
            }
            fitted[i] = sum;
        }
        return fitted;
    }

    private static double[][] cholesky(double[][] a) {
        int n = a.length;
        double[][] l = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = a[i][j];
                for (int k = 0; k < j; k++) {
                    sum -= l[i][k] * l[j][k];
                }
                if (i == j) {
                    if (sum <= 0) {
                        throw new ArithmeticException("K + λI não é positiva definida");
                    }
                    l[i][i] = Math.sqrt(sum);
                } else {
                    l[i][j] = sum / l[j][j];
                }
            }
        }
        return l;
    }
}
