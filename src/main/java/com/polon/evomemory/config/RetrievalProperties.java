package com.polon.evomemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memory.retrieval")
public class RetrievalProperties {

    private double minSimilarity = 0.42;
    private double relativeMargin = 0.08;
    private int topK = 8;

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
}
