package fixtures;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ScriptedChatModel implements ChatModel {

    private final List<Prompt> receivedPrompts = new ArrayList<>();
    private final Deque<AssistantMessage> script = new ArrayDeque<>();

    public static ScriptedChatModel replyingWith(String... messages) {
        ScriptedChatModel scriptedChatModel = new ScriptedChatModel();
        for (String msg : messages) {
            scriptedChatModel.script.add(new AssistantMessage(msg));
        }
        return scriptedChatModel;
    }

    public ScriptedChatModel then(String msg) {
        this.script.add(new AssistantMessage(msg));
        return this;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        this.receivedPrompts.add(prompt);
        AssistantMessage next = this.script.poll();
        if (next == null) {
            throw new IllegalStateException("Invalid scenario. Agent made more calls then expected: (%d)"
                    .formatted(this.receivedPrompts.size())
            );
        }
        return new ChatResponse(List.of(new Generation(next)));
    }

    public List<Prompt> getReceivedPrompts() {
        return List.copyOf(this.receivedPrompts);
    }

    public Prompt lastPrompt() {
        return this.receivedPrompts.getLast();
    }

    public int callCount() {
        return this.receivedPrompts.size();
    }
}
