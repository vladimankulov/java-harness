package com.harness.agent;

import org.springframework.ai.chat.messages.*;

import java.util.ArrayList;
import java.util.List;

public class Conversation {
    private final String systemPrompt;
    private final List<Message> messages = new ArrayList<>();

    public Conversation(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public void addUserMessage(String userMessage) {
        messages.add(new UserMessage(userMessage));
    }

    public void addAssistantMessage(AssistantMessage assistantMessage) {
        messages.add(assistantMessage);
    }

    public List<Message> toPromptMessages() {
        ArrayList<Message> prompt = new ArrayList<>(messages.size() + 1);
        prompt.add(new SystemMessage(systemPrompt));
        prompt.addAll(messages);
        return prompt;
    }

    public void addToolResponses(ToolResponseMessage toolResponseMessage) {
        messages.add(toolResponseMessage);
    }
}




