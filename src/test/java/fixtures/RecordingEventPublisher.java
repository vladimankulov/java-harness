package fixtures;

import com.harness.agent.event.AgentEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

public class RecordingEventPublisher implements ApplicationEventPublisher {

    private final List<AgentEvent> events = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        if (event instanceof AgentEvent agentEvent) {
            events.add(agentEvent);
        }
    }

    public List<AgentEvent> events() {
        return List.copyOf(events);
    }
}
