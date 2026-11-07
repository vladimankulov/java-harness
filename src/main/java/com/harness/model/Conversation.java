package com.harness.model;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
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

    public void addAssistantMessage(AssistantMessage assistantMessage) {
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

    public void addToolResponses(ToolResponseMessage toolResponseMessage) {
        if (toolResponseMessage != null) {
            this.messages.add(toolResponseMessage);
        }
    }
}
