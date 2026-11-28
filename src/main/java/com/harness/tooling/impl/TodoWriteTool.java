package com.harness.tooling.impl;

import com.harness.agent.event.AgentEvent;
import com.harness.model.tooling.TodoItem;
import com.harness.model.tooling.ToolContext;
import com.harness.model.tooling.ToolResult;
import com.harness.model.tooling.enums.TodoStatus;
import com.harness.tooling.Tool;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * План работы. Единственный инструмент, который ничего не делает с миром:
 * не читает, не пишет, не запускает. Он кладёт список в память разговора.
 */
@Component
public class TodoWriteTool implements Tool<TodoWriteTool.Input> {
    private final ApplicationEventPublisher publisher;

    public TodoWriteTool(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }

    @Override
    public String name() {
        return "TodoWrite";
    }

    @Override
    public String description() {
        return """
                Create and manage a structured task list for the current session.
                Use it to track progress on multi-step work and to keep the plan visible.
                
                ## When to use
                - The task takes three or more distinct steps
                - The user gave several things to do at once (numbered or comma-separated)
                - You are starting a step — mark it IN_PROGRESS BEFORE the work, not after
                - You finished a step — mark it COMPLETED immediately, do not batch
                
                ## When not to use
                - A single straightforward action
                - A purely conversational or informational request
                
                ## Task states
                - PENDING: not started
                - IN_PROGRESS: being worked on right now. EXACTLY ONE at a time
                - COMPLETED: fully finished
                
                Only mark a step COMPLETED when it is really done. If you hit an error,
                leave it IN_PROGRESS and add a task for what blocks you.
                
                ## Rules
                - Send the whole list every time. It replaces the previous one
                - Every item needs both content ("Run the tests") and activeForm ("Running the tests")
                
                ## Example
                User: Add a dark mode toggle. Run the tests when you are done.
                Assistant: *calls TodoWrite before touching anything:*
                  1. [IN_PROGRESS] Add the toggle component — "Adding the toggle component"
                  2. [PENDING] Wire it to the theme store — "Wiring it to the theme store"
                  3. [PENDING] Run the tests — "Running the tests"
                *then does step 1 and calls TodoWrite again: step 1 COMPLETED, step 2 IN_PROGRESS.*""";

    }

    /**
     * Строку вызова не показываем: экран нарисует сам список по событию.
     */
    @Override
    public String renderToolUsage(Input input) {
        return "";
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public ToolResult call(Input input, ToolContext context) {
        String problem = findProblem(input.todos());
        if (problem != null) return ToolResult.error(problem);
        context.todos().replace(allDone(input.todos()) ? List.of() : input.todos());
        publisher.publishEvent(new AgentEvent.TodosUpdated(List.copyOf(input.todos())));
        return ToolResult.ok("""
                Todos have been modified successfully. \
                Ensure that you continue to use the todo list to track your progress. \
                Please proceed with the current tasks if applicable""");
    }

    private boolean allDone(List<TodoItem> todos) {
        return todos.stream().allMatch(item -> item.status() == TodoStatus.COMPLETED);
    }


    private @Nullable String findProblem(@Nullable List<TodoItem> todos) {
        if (todos == null) return "не передан список задач";
        for (TodoItem item : todos) {
            if (item == null || !StringUtils.hasText(item.content())) {
                return "у задачи пустое поле content";
            }
            if (item.status() == null) {
                return "у задачи '%s' не указан status".formatted(item.content());
            }
        }
        return null;
    }


    public record Input(
            @ToolParam(description = "The updated todo list, in full")
            List<TodoItem> todos) {
    }
}
