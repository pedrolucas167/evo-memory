package com.polon.evomemory.application.ports;

public interface EmbeddingClient extends EmbeddingPort {

    float[] embed(String text);
}
