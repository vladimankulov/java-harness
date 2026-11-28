package com.harness.agent.event;

import com.harness.tool.TodoItem;

import java.time.Duration;
import java.util.List;

public sealed interface AgentEvent {

    record TurnStarted(String userMessage) implements AgentEvent {
    }

    record AgentSpoke(String assistantMessage) implements AgentEvent {
    }

    record TurnFinished(Duration duration, int iteration) implements AgentEvent {
    }

     record ToolCallStarted(String toolName, String description) implements AgentEvent { }
     record ToolCallFinished(String toolName, boolean error, String preview, Duration duration) implements AgentEvent { }
     record TurnLimitReached(int maxTurns) implements AgentEvent { }


    record PermissionRequested(String toolName, String target, String reason) implements AgentEvent { }

    record PermissionResolved(String toolName, boolean granted) implements AgentEvent { }

    /** Агент переписал план. Событие публикует сам инструмент: только он знает, что список изменился. */
    record TodosUpdated(List<TodoItem> todos) implements AgentEvent { }
}
