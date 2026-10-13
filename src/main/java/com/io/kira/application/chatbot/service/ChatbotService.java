package com.io.kira.application.chatbot.service;

import com.io.kira.application.chatbot.command.ChatbotCommand;
import com.io.kira.application.chatbot.error.ChatbotCompletionError;
import com.io.kira.application.chatbot.error.ChatbotError;
import com.io.kira.application.chatbot.port.in.ChatbotUseCase;
import com.io.kira.application.chatbot.port.out.ChatbotCompletionPort;
import com.io.kira.application.chatbot.result.ChatbotReplyData;
import com.io.kira.common.result.Result;
import org.springframework.stereotype.Service;

@Service
public class ChatbotService implements ChatbotUseCase {

    private final ChatbotAccessService accessService;
    private final ChatbotContextService contextService;
    private final ChatbotCompletionPort completionPort;

    public ChatbotService(
            ChatbotAccessService accessService,
            ChatbotContextService contextService,
            ChatbotCompletionPort completionPort
    ) {
        this.accessService = accessService;
        this.contextService = contextService;
        this.completionPort = completionPort;
    }

    @Override
    public Result<ChatbotReplyData, ChatbotError> execute(ChatbotCommand command) {
        String accessLevel;
        String verifiedContext;

        if (command.classroomId() == null) {
            accessLevel = "DASHBOARD";
            verifiedContext = contextService.buildDashboardContext(command.userId(), command.message());
        } else {
            ChatbotAccessService.ClassroomAccess access = accessService.getAccess(
                    command.userId(),
                    command.classroomId()
            );

            if (access == ChatbotAccessService.ClassroomAccess.NONE) {
                return Result.fail(ChatbotError.ACCESS_DENIED);
            }

            accessLevel = access.name();
            verifiedContext = contextService.buildClassroomContext(
                    command.userId(),
                    command.classroomId(),
                    access
            );
        }

        if (command.timezone() != null) {
            verifiedContext += "\nUser Time Zone: " + command.timezone()
                    + "\nCurrent User Local Date: " + java.time.LocalDate.now(java.time.ZoneId.of(command.timezone()))
                    + "\nUse this time zone for today/tomorrow and local deadline displays; source timestamps are UTC.\n";
        }

        Result<String, ChatbotCompletionError> completion = command.history().isEmpty() ? completionPort.generateResponse(
                command.message(),
                accessLevel,
                verifiedContext
        ) : completionPort.generateResponse(
                command.message(), accessLevel, verifiedContext, command.history()
        );

        if (!completion.success()) {
            return Result.fail(mapCompletionError(completion.error()));
        }

        return Result.ok(new ChatbotReplyData(completion.data()));
    }

    private ChatbotError mapCompletionError(ChatbotCompletionError error) {
        if (error == ChatbotCompletionError.NOT_CONFIGURED) {
            return ChatbotError.PROVIDER_NOT_CONFIGURED;
        }
        return ChatbotError.PROVIDER_UNAVAILABLE;
    }
}
