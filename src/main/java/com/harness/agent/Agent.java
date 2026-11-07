package com.harness.agent;

import com.harness.agent.event.AgentEvent;
import com.harness.model.Conversation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class Agent {

    private static final String AGENT_SYSTEM = """
            You are Harness, a coding agent working in a local repository.
            Answer concisely
            """;
    private final ApplicationEventPublisher publisher;

    private Conversation conversation = new Conversation(AGENT_SYSTEM);

    private final ChatModel chatModel;

    public Agent(ChatModel chatModel, ApplicationEventPublisher publisher) {
        this.chatModel = chatModel;
        this.publisher = publisher;
    }

    public String run(String userMessage) {
        Instant start = Instant.now();
        conversation.addUserMessage(userMessage);
        publisher.publishEvent(new AgentEvent.TurnStarted(userMessage));
        AssistantMessage assistantMessage = chatModel.call(new Prompt(conversation.toPromptMessages())).getResult().getOutput();
        conversation.addAssistantMessage(assistantMessage);
        String assistantText = assistantMessage.getText();

        publisher.publishEvent(new AgentEvent.AgentSpoke(assistantText));
        publisher.publishEvent(new AgentEvent.TurnFinished(Duration.between(start, Instant.now())));
        return assistantText;
    }
}
