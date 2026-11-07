package com.harness.hud;

import com.harness.agent.Agent;
import com.harness.agent.event.AgentEvent;
import com.harness.context.TokenEstimator;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.harness.hud.BodyPart.*;
import static com.harness.hud.LanguageUtils.plural;

@Component
public class Hud {

    private final MachineViewPrinter machinePrinter;
    private final Agent agent;

    public Hud(MachineViewPrinter machinePrinter, Agent agent) {
        this.machinePrinter = machinePrinter;
        this.agent = agent;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationStarted(ApplicationStartedEvent event) {
        machinePrinter.showBootLog(collectBootLines(event.getApplicationContext().getBeanDefinitionCount()));
    }

    private List<BootLine> collectBootLines(int beanDefinitionCount) {
        List<BootLine> lines = new ArrayList<>();
        lines.add(BootLine.of(POWER_CELL, BootStatus.ONLINE,
                "спринг контекст " + plural(beanDefinitionCount, "бин", "бина", "бинов")));
        lines.add(BootLine.of(NEUROPROCESSOR, BootStatus.ONLINE, agent.chatModel()));
        lines.add(BootLine.of(MEMORY_BANK, BootStatus.ONLINE, agent.conversation().getClass().getSimpleName()));
        lines.add(BootLine.of(DIAGNOSTIC_BUS, BootStatus.ONLINE, "Agent Events: " + AgentEvent.class.getPermittedSubclasses().length));
        return lines;

    }

    @EventListener
    public void onTurnStarted(AgentEvent.TurnStarted event) {
        String userInput = event.userMessage();
        machinePrinter.showOrder(userInput);
        List<Message> promptMessages = agent.conversation().toPromptMessages();
        machinePrinter.showContext(promptMessages, TokenEstimator.estimate(promptMessages));

    }

    @EventListener
    public void onTurnFinished(AgentEvent.TurnFinished event) {
        machinePrinter.showTurnFinished(event.duration());
    }
}
