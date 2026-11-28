package com.harness.agent;

import com.harness.tool.impl.BashTool;
import org.springframework.context.annotation.Import;
import fixtures.TestPermissionConfig;
import com.harness.agent.event.AgentEvent;
import com.harness.tooling.Tool;
import fixtures.RecordingEventPublisher;
import fixtures.TestAgents;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Регрессия урока 6 на живой Ollama: готовые сведения не надо искать заново.
 * Обычная сборка этот тест пропускает из-за группы {@code ollama}.
 * Каждый повтор начинает новый разговор, сохраняя все настоящие инструменты.
 *
 * <p>С уроком 7 у модели появился Bash, и за веткой она идёт в git, хотя ветка уже в промпте.
 * Такой вызов допустим, если Bash его отклонил. Свежий git status тоже допустим: снимок в промпте
 * сделан на начало разговора. Любой другой вызов валит тест до исполнения.
 */
@Tag("ollama")
@SpringBootTest
@Import(TestPermissionConfig.class)
@ActiveProfiles("test")
class ContextUsageOllamaTest {

    /** Один git status с ключами, без цепочек: он только читает. */
    private static final Pattern FRESH_STATUS = Pattern.compile("(?i)^\\s*git\\s+status(?:\\s+-[-\\w]+)*\\s*$");

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private List<Tool<?>> tools;

    @RepeatedTest(value = 5, name = "готовый контекст: прогон {currentRepetition} из {totalRepetitions}")
    void отвечаетИзГотовогоКонтекстаБезИнструментов() {
        RecordingEventPublisher events = new RecordingEventPublisher() {
            private String command = "";

            @Override
            public void publishEvent(Object event) {
                super.publishEvent(event);
                if (event instanceof AgentEvent.ToolCallStarted call) {
                    command = call.description();
                    boolean allowed = call.toolName().equals("Bash")
                            && (BashTool.asksForBranch(command) || FRESH_STATUS.matcher(command).matches());
                    // Событие приходит до исполнения: провал виден сразу,
                    // а модель не получает возможности изменить проект.
                    if (!allowed) {
                        fail("Ответ уже есть в контексте, но агент вызвал %s: %s", call.toolName(), command);
                    }
                }
                if (event instanceof AgentEvent.ToolCallFinished done && !done.error() && BashTool.asksForBranch(command)) {
                    fail("Bash должен был отклонить вопрос о ветке, а выполнил: %s", command);
                }
            }
        };
        Agent agent = TestAgents.agent(chatModel, events, tools.toArray(Tool<?>[]::new));

        String answer = agent.run("""
                На какой ветке git мы сейчас работаем и есть ли незакоммиченные изменения?
                И что это вообще за проект? Ответь коротко.""");

        assertThat(answer).as("агент должен ответить, а не просто промолчать").isNotBlank();
    }
}
