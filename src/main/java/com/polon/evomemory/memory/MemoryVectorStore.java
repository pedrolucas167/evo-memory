package com.polon.evomemory.memory;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MemoryVectorStore {

    private final JdbcTemplate jdbc;

    public MemoryVectorStore(JdbcTemplate jdbc) {
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

    public record Match(UUID memoryId, double similarity) {
    }
}
