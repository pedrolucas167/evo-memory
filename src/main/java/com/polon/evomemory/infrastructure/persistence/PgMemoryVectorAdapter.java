package com.polon.evomemory.infrastructure.persistence;
import com.polon.evomemory.domain.memory.MemoryStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PgMemoryVectorAdapter implements com.polon.evomemory.application.ports.VectorStorePort {

    private final JdbcTemplate jdbc;

    public PgMemoryVectorAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void store(UUID memoryId, float[] embedding, String model) {
        jdbc.update("update semantic_memory set embedding = cast(? as vector), embedding_model = ? where id = ?",
                toLiteral(embedding), model, memoryId);
    }

    public List<Match> nearest(String userId, float[] query, int limit) {
        String literal = toLiteral(query);
        return jdbc.query("""
                        select id, 1 - (embedding <=> cast(? as vector)) as similarity
                        from semantic_memory
                        where user_id = ? and status = ? and embedding is not null
                        order by embedding <=> cast(? as vector)
                        limit ?""",
                (rs, row) -> new Match(rs.getObject("id", UUID.class), rs.getDouble("similarity")),
                literal, userId, MemoryStatus.ACTIVE.name(), literal, limit);
    }

    static String toLiteral(float[] vector) {
        return "[" + java.util.stream.IntStream.range(0, vector.length)
                .mapToObj(i -> Float.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }

    @Override
    public Map<UUID, float[]> vectors(List<UUID> memoryIds) {
        if (memoryIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = memoryIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));
        Object[] ids = memoryIds.toArray();
        return jdbc.query("""
                        select id, embedding::text as embedding_text
                        from semantic_memory
                        where id in (%s) and embedding is not null""".formatted(placeholders),
                rs -> {
                    Map<UUID, float[]> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getObject("id", UUID.class), parseLiteral(rs.getString("embedding_text")));
                    }
                    return result;
                }, ids);
    }

    static float[] parseLiteral(String literal) {
        String inner = literal.substring(1, literal.length() - 1);
        if (inner.isBlank()) {
            return new float[0];
        }
        String[] parts = inner.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

}
