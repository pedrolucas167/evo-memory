package com.polon.evomemory.application.ports;

import java.util.List;
import java.util.UUID;

public interface VectorStorePort {
    void store(UUID memoryId, float[] embedding, String model);
    List<Match> nearest(String userId, float[] query, int limit);
    record Match(UUID memoryId, double similarity) {}
}
