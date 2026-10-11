package com.polon.evomemory.llm;

public interface EmbeddingClient {

    float[] embed(String text);
}
