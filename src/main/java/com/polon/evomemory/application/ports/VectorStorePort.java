package com.polon.evomemory.application.ports;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface VectorStorePort {
    void store(UUID memoryId, float[] embedding, String model);
    List<Match> nearest(String userId, float[] query, int limit);

    /**
     * Vetores brutos das memórias informadas, quando armazenados. Ausências
     * simplesmente não aparecem no mapa.
     */
    Map<UUID, float[]> vectors(List<UUID> memoryIds);

    record Match(UUID memoryId, double similarity) {}
}
