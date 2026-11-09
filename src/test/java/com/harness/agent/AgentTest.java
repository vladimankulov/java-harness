package com.harness.agent;

import fixtures.RecordingEventPublisher;
import fixtures.ScriptedChatModel;
import fixtures.TestAgents;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class AgentTest {

    private final RecordingEventPublisher publisher = new RecordingEventPublisher();

    @Test
    void mustCallOnceAndReturnResponseFromModel() {
        String expectedResponseFromModel = "I'm Harness";

        Agent agent = TestAgents.agent(ScriptedChatModel.replyingWith(expectedResponseFromModel), publisher);

        assertThat(agent.run("Who are you")).isEqualTo(expectedResponseFromModel);
    }

    @Test
    void mustReceiveSystemMessageAsFirstMessageInModel() {
        String expectedResponseFromModel = "I'm Harness";
        ScriptedChatModel model = ScriptedChatModel.replyingWith(expectedResponseFromModel);
        Agent agent = TestAgents.agent(model, publisher);

        assertThat(agent.run("Who are you")).isEqualTo(expectedResponseFromModel);

        List<Message> messageSent = model.lastPrompt().getInstructions();
        assertThat(messageSent.getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
    }

    @Test
    void mustKeepConversationHistory() {
        String firstResponse = "I'm Harness";
        String secondResponse = "You're human";
        ScriptedChatModel model = ScriptedChatModel.replyingWith(firstResponse, secondResponse);
        Agent agent = TestAgents.agent(model, publisher);

        String firstUserQuestion = "Who are you";
        String secondUserQuestion = "Who am I";

        assertThat(agent.run(firstUserQuestion)).isEqualTo(firstResponse);
        assertThat(agent.run(secondUserQuestion)).isEqualTo(secondResponse);

        List<@Nullable String> secondCall = model.lastPrompt().getInstructions().stream().map(Message::getText).toList();
        assertThat(secondCall)
                //second call is the prompt without model last response
                .containsExactly(secondCall.getFirst(),
                        firstUserQuestion,
                        firstResponse,
                        secondUserQuestion);
    }
}
