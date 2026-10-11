package com.polon.evomemory.memory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, UUID> {

    List<Episode> findByUserIdOrderByOccurredAtDesc(String userId);
}
