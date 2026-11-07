package com.harness.hud;

import com.harness.agent.event.AgentEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class Hud {

    @EventListener
    public void receive(AgentEvent event) {

    }
}
