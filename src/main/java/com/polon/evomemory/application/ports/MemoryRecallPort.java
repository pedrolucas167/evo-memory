package com.polon.evomemory.application.ports;
import java.util.List;
import com.polon.evomemory.domain.memory.Memory;

public interface MemoryRecallPort {
    List<Memory> recallActive(String userId);
}
