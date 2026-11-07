package com.harness.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ConversationTest {

    @Test
    void mustAddUserMessageAfterSystemPrompt() {
        String systemPrompt = "SYSTEM";
        Conversation conversation = new Conversation(systemPrompt);
        conversation.addUserMessage("User message");

        assertThat(conversation.toPromptMessages().size()).isEqualTo(2);
    }

    @Test
    void mustAddMessageAndPreserveOrder() {
        String systemPrompt = "SYSTEM";
        String userPrompt = "USER";
        String assistantPrompt = "ASSISTANT";

        Conversation conversation = new Conversation(systemPrompt);
        conversation.addUserMessage(userPrompt);
        conversation.addAssistantMessage(new AssistantMessage(assistantPrompt));

        assertThat(conversation.toPromptMessages().size()).isEqualTo(3);

        assertThat(conversation.toPromptMessages().stream()
                .map(Message::getMessageType))
                .containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT);
    }
}
