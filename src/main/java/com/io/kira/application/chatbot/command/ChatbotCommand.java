package com.io.kira.application.chatbot.command;

import java.util.UUID;
import java.util.List;

public record ChatbotCommand(
        UUID userId,
        String message,
        UUID classroomId,
        List<ChatbotTurn> history,
        String timezone
) {
    public ChatbotCommand(UUID userId, String message, UUID classroomId) {
        this(userId, message, classroomId, List.of(), null);
    }

    public ChatbotCommand(UUID userId, String message, UUID classroomId, List<ChatbotTurn> history) {
        this(userId, message, classroomId, history, null);
    }

    public ChatbotCommand {
        history = history == null ? List.of() : List.copyOf(history);
        if (timezone != null) {
            try { timezone = java.time.ZoneId.of(timezone).getId(); }
            catch (java.time.DateTimeException exception) { timezone = "UTC"; }
        }
    }
}
