package com.polon.evomemory.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, UUID> {

    List<Episode> findByUserIdOrderByOccurredAtDesc(String userId);
}
