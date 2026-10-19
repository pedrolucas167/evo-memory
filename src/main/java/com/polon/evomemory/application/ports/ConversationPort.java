package com.polon.evomemory.application.ports;
import java.util.List;
import com.polon.evomemory.application.model.response.ChatResponse;

public interface ConversationPort {
    ChatResponse process(String userId, String conversationId, List<String> messages);
}
