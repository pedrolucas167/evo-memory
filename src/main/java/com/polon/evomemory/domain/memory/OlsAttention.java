package com.polon.evomemory.domain.memory;

import java.util.List;

/**
 * Mecanismo de atenção OLS (Goulet Coulombe, NeurIPS 2025), três vistas equivalentes:
 *
 * <pre>
 *   coeficientes:  ŷ_j = x_j β̂                       com β̂ = (X'X)^{-1} X'y
 *   similaridade:  ŷ_j = Σ_i ⟨F_j, F_i⟩ y_i           com F = X U Λ^{-1/2}, X'X = U Λ U'
 *   atenção:       ŷ_j = g(Q_j K' V)                  g = identidade, Q_j = F_j, K = F, V = y
 * </pre>
 *
 * O embedding ótimo é F = X U Λ^{-1/2}: ele resolve min_Ω ||y - X Ω X' y||² para todo y.
 * Autovalores nulos ou negativos de X'X recebem o piso em {@code ridge}, gerando a
 * codificação ridge-regularizada do embedding ótimo e mantendo β̂ finito para
 * poucas evidências (X'X singular).
 *
 * Java puro: sem Spring, sem banco, sem LLM. Solver simétrico por rotações de Jacobi.
 */
public final class OlsAttention {

    private static final double JACOBI_TOLERANCE = 1e-12;
    private static final int JACOBI_MAX_SWEEPS = 100;

    /** Evidência armazenada: linha de atributos x_i e o valor associado y_i. */
    public record Evidence(double[] features, double value) {}

    /**
     * Resultado da atenção sobre uma consulta j: os pesos de similaridade, a predição
     * (idêntica nas três vistas), os coeficientes OLS e a codificação ótima.
     */
    public record Attention(
            double[] weights,      // w_i = ⟨F_j, F_i⟩
            double prediction,     // ŷ_j = Σ_i w_i y_i = x_j β̂ = g(Q_j K' V)
            double[] coefficients, // β̂
            double[][] encoding) { // F = X U Λ^{-1/2}

        public int evidenceCount() {
            return encoding.length;
        }
    }

    private OlsAttention() {
    }

    public static Attention attend(List<Evidence> evidence, double[] query) {
        return attend(evidence, query, 1e-8);
    }

    public static Attention attend(List<Evidence> evidence, double[] query, double ridge) {
        int n = evidence.size();
        int p = query.length;
        if (p == 0) {
            throw new IllegalArgumentException("Consulta sem atributos");
        }
        for (Evidence row : evidence) {
            if (row.features().length != p) {
                throw new IllegalArgumentException("Evidência com número de atributos diferente da consulta");
            }
        }

        double[][] x = new double[n][p];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = evidence.get(i).features().clone();
            y[i] = evidence.get(i).value();
        }

        // G = X'X = U Λ U' (simétrica).
        double[][] g = new double[p][p];
        for (int i = 0; i < n; i++) {
            for (int a = 0; a < p; a++) {
                for (int b = 0; b < p; b++) {
                    g[a][b] += x[i][a] * x[i][b];
                }
            }
        }
        double[][] u = identity(p);
        double[] lambda = new double[p];
        jacobi(g, u, lambda);

        // Piso de ridge nos autovalores nulos/negativos: F = X U D, D = diag(1/√max(λ, ridge)).
        double[] inverseRoots = new double[p];
        for (int k = 0; k < p; k++) {
            inverseRoots[k] = 1.0 / Math.sqrt(Math.max(lambda[k], ridge));
        }
        double[][] m = scaleColumns(u, inverseRoots);          // M = U Λ^{-1/2}
        double[][] f = new double[n][p];                // F = X M
        for (int i = 0; i < n; i++) {
            f[i] = multiply(x[i], m);
        }
        double[] fj = multiply(query, m);               // F_j

        // Vista de similaridade: w_i = ⟨F_j, F_i⟩; predição ŷ_j = Σ w_i y_i.
        double[] weights = new double[n];
        double prediction = 0.0;
        for (int i = 0; i < n; i++) {
            weights[i] = dot(fj, f[i]);
            prediction += weights[i] * y[i];
        }

        // Vista de coeficientes: β̂ = U Λ^{-1} U' X'y (mesma ridge).
        double[] beta = coefficients(x, y, u, lambda, ridge);

        // Consistência das vistas: x_j β̂ ≡ Σ ⟨F_j, F_i⟩ y_i (até a precisão numérica).
        double coefficientsView = 0.0;
        for (int b = 0; b < p; b++) {
            coefficientsView += query[b] * beta[b];
        }
        if (Math.abs(coefficientsView - prediction)
                > 1e-6 * (1 + Math.abs(prediction))) {
            throw new ArithmeticException("Vistas de coeficientes e similaridade divergem");
        }

        return new Attention(weights, prediction, beta, f);
    }

    private static double[] coefficients(double[][] x, double[] y, double[][] u,
                                         double[] lambda, double ridge) {
        int n = x.length;
        int p = lambda.length;
        // Xt y
        double[] xty = new double[p];
        for (int i = 0; i < n; i++) {
            for (int a = 0; a < p; a++) {
                xty[a] += x[i][a] * y[i];
            }
        }
        // β̂ = U diag(1/max(λ, ridge)) U' xty
        double[] tmp = new double[p];
        for (int k = 0; k < p; k++) {
            double column = 0.0;
            for (int a = 0; a < p; a++) {
                column += u[a][k] * xty[a];
            }
            tmp[k] = column / Math.max(lambda[k], ridge);
        }
        double[] beta = new double[p];
        for (int b = 0; b < p; b++) {
            double value = 0.0;
            for (int k = 0; k < p; k++) {
                value += u[b][k] * tmp[k];
            }
            beta[b] = value;
        }
        return beta;
    }

    /** Diagonaliza a matriz simétrica {@code a} acumulando autovetores em {@code u} (Jacobi). */
    private static void jacobi(double[][] a, double[][] u, double[] lambda) {
        int p = a.length;
        for (int sweep = 0; sweep < JACOBI_MAX_SWEEPS; sweep++) {
            double off = 0.0;
            for (int r = 0; r < p; r++) {
                for (int c = r + 1; c < p; c++) {
                    off += a[r][c] * a[r][c];
                }
            }
            if (off < JACOBI_TOLERANCE) {
                break;
            }
            for (int r = 0; r < p - 1; r++) {
                for (int c = r + 1; c < p; c++) {
                    if (Math.abs(a[r][c]) <= 1e-15) {
                        continue;
                    }
                    double theta = (a[c][c] - a[r][r]) / (2 * a[r][c]);
                    double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                    if (theta == 0) {
                        t = 1.0;
                    }
                    double cos = 1.0 / Math.sqrt(t * t + 1);
                    double sin = t * cos;
                    rotate(a, u, r, c, cos, sin);
                }
            }
        }
        for (int r = 0; r < p; r++) {
            lambda[r] = a[r][r];
        }
    }

    private static void rotate(double[][] a, double[][] u, int r, int c, double cos, double sin) {
        int p = a.length;
        // Lado esquerdo: A ← J' A (linhas r e c).
        for (int k = 0; k < p; k++) {
            double ark = a[r][k];
            double ack = a[c][k];
            a[r][k] = cos * ark - sin * ack;
            a[c][k] = sin * ark + cos * ack;
        }
        // Lado direito: A ← A J (colunas r e c). J' A J mantém a simetria.
        for (int k = 0; k < p; k++) {
            double akr = a[k][r];
            double akc = a[k][c];
            a[k][r] = cos * akr - sin * akc;
            a[k][c] = sin * akr + cos * akc;
        }
        // Autovetores acumulam U ← U J (colunas de U representam X'X na base própria).
        for (int k = 0; k < p; k++) {
            double ukr = u[k][r];
            double ukc = u[k][c];
            u[k][r] = cos * ukr - sin * ukc;
            u[k][c] = sin * ukr + cos * ukc;
        }
    }

    private static double[][] identity(int p) {
        double[][] u = new double[p][p];
        for (int k = 0; k < p; k++) {
            u[k][k] = 1.0;
        }
        return u;
    }

    private static double[][] scaleColumns(double[][] matrix, double[] scale) {
        int p = matrix.length;
        double[][] result = new double[p][p];
        for (int r = 0; r < p; r++) {
            for (int c = 0; c < p; c++) {
                result[r][c] = matrix[r][c] * scale[c];
            }
        }
        return result;
    }

    private static double[] multiply(double[] row, double[][] matrix) {
        int p = row.length;
        double[] result = new double[p];
        for (int c = 0; c < p; c++) {
            for (int b = 0; b < p; b++) {
                result[c] += row[b] * matrix[b][c];
            }
        }
        return result;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
