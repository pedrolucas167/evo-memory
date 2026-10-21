package com.polon.evomemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memory.retrieval")
public class RetrievalProperties {

    private double minSimilarity = 0.42;
    private double relativeMargin = 0.08;
    private int topK = 8;
    /**
     * Peso da re-classificação pela atenção OLS (0 = comportamento histórico).
     * Score final = (1 - peso) · cru + peso · ŷ, onde ŷ vem de OlsRerank.
     */
    private double attentionRerankWeight = 0.0;
    /** Ridge λ do OlsRerank; K é cosseno (diagonal ~1), então 0.1 suaviza sem apagar sinais. */
    private double attentionRerankRidge = 0.1;
    /**
     * Filtro de relevância pelo modelo juiz antes do corte de contexto
     * (atende o caso profissao-por-situacao, que filtros determinísticos
     * não sustentam). Padrão LIGADO por medição (19/10: 3/3 no caso alvo e
     * zero regressão em 16 casos; custo: uma chamada LLM por consulta). Falha
     * do juiz degrada para a ordenação por score.
     */
    private boolean judgeRerank = true;
    /** Quantos candidatos, no máximo, o juiz avalia (a partir do topo da ordenação por score). */
    private int judgeRerankMaxCandidates = 16;
    /**
     * Geração de candidatos largos para o juiz (frente A.2): quando LIGADA, a
     * margem relativa não corta a lista que vai ao juiz — o corte fica só no
     * {@code minSimilarity}, e a proteção do contexto final passa a ser o
     * filtro conservador do juiz (diagnóstico offline 21/10: a margem
     * eliminava o estado atual por 0,0014 antes da disputa). Falha do juiz
     * degrada para a ordenação por score. Padrão DESLIGADO: sem medição
     * pré-registrada não vira comportamento padrão.
     */
    private boolean judgeWideCandidates = false;

    public boolean isJudgeWideCandidates() {
        return judgeWideCandidates;
    }

    public void setJudgeWideCandidates(boolean judgeWideCandidates) {
        this.judgeWideCandidates = judgeWideCandidates;
    }

    public double getMinSimilarity() {
        return minSimilarity;
    }

    public void setMinSimilarity(double minSimilarity) {
        this.minSimilarity = minSimilarity;
    }

    public double getRelativeMargin() {
        return relativeMargin;
    }

    public void setRelativeMargin(double relativeMargin) {
        this.relativeMargin = relativeMargin;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getAttentionRerankWeight() {
        return attentionRerankWeight;
    }

    public void setAttentionRerankWeight(double attentionRerankWeight) {
        this.attentionRerankWeight = attentionRerankWeight;
    }

    public double getAttentionRerankRidge() {
        return attentionRerankRidge;
    }

    public void setAttentionRerankRidge(double attentionRerankRidge) {
        this.attentionRerankRidge = attentionRerankRidge;
    }

    public boolean isJudgeRerank() {
        return judgeRerank;
    }

    public void setJudgeRerank(boolean judgeRerank) {
        this.judgeRerank = judgeRerank;
    }

    public int getJudgeRerankMaxCandidates() {
        return judgeRerankMaxCandidates;
    }

    public void setJudgeRerankMaxCandidates(int judgeRerankMaxCandidates) {
        this.judgeRerankMaxCandidates = judgeRerankMaxCandidates;
    }
}
