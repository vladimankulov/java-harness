package com.harness.model.conversation;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.*;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class Conversation {
    private final String systemPrompt;
    private final List<Message> messages;

    public Conversation(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        this.messages = new ArrayList<>();
    }

    public void addUserMessage(String userMessage) {
        if (StringUtils.hasText(userMessage)) {
            this.messages.add(new UserMessage(userMessage));
        }
    }

    public void addAssistantMessage(@Nullable AssistantMessage assistantMessage) {
        if (assistantMessage != null) {
            this.messages.add(assistantMessage);
        }
    }

    public List<Message> toPromptMessages() {
        List<Message> messages = new ArrayList<>(this.messages.size() + 1);
        messages.add(new SystemMessage(this.systemPrompt));
        messages.addAll(this.messages);
        return messages;
    }

    public void addToolResponses(@Nullable ToolResponseMessage toolResponseMessage) {
        if (toolResponseMessage != null) {
            this.messages.add(toolResponseMessage);
        }
    }

    public void addSystemReminder(String systemReminder) {
        if (StringUtils.hasText(systemReminder)) {
            this.messages.add(new UserMessage("<system-reminder>\n%s\n</system-reminder>".formatted(systemReminder)));
        }
    }
}
