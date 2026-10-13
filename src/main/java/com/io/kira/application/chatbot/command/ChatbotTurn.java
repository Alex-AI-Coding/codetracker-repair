package com.io.kira.application.chatbot.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Untrusted conversational reference, never an authorization source. */
public record ChatbotTurn(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = 2000) String text
) { }
