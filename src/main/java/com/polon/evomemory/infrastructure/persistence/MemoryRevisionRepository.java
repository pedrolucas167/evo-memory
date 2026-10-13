package com.polon.evomemory.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRevisionRepository extends JpaRepository<MemoryRevision, UUID> {

    List<MemoryRevision> findByMemoryIdOrderByVersionAsc(UUID memoryId);
}
