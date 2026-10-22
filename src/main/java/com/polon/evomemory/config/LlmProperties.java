package com.polon.evomemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memory.llm")
public class LlmProperties {

    private String baseUrl;
    private String apiKey;
    private String extractionModel;
    private String judgeModel;
    private String chatModel;
    private String embeddingModel;
    private long retryBackoffMillis = 2000;
    /**
     * Fechamento de assunto (frente 1, pré-registro 22/10): quando LIGADO, o juiz
     * ganha o veredito CLOSED (fase encerrada pela nova informação) e a extração
     * passa a descrever só o estado novo, sem narrar a transição. Na evolução,
     * CLOSED retrai a memória de referência (conteúdo e histórico preservados)
     * e o estado novo nasce como memória própria. Padrão DESLIGADO: sem
     * medição pré-registrada não vira comportamento padrão.
     */
    private boolean subjectClosing = false;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getExtractionModel() {
        return extractionModel;
    }

    public void setExtractionModel(String extractionModel) {
        this.extractionModel = extractionModel;
    }

    public String getJudgeModel() {
        return judgeModel;
    }

    public void setJudgeModel(String judgeModel) {
        this.judgeModel = judgeModel;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public long getRetryBackoffMillis() {
        return retryBackoffMillis;
    }

    public void setRetryBackoffMillis(long retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
    }

    public boolean isSubjectClosing() {
        return subjectClosing;
    }

    public void setSubjectClosing(boolean subjectClosing) {
        this.subjectClosing = subjectClosing;
    }
}
