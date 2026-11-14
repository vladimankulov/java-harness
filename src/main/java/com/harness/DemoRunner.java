package com.harness;

import com.harness.agent.Agent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Сценарий текущего урока. Единственный файл, который меняется «до» урока —
 * всё остальное мы дописываем по ходу.
 *
 * <p>Урок 1: хотим просто поговорить с агентом. Пока не с чем: класса Agent нет.
 */
@Component
@Profile("!test")
public class DemoRunner implements CommandLineRunner {

    private final Agent agent;

    public DemoRunner(Agent agent) {
        this.agent = agent;
    }

    @Override
    public void run(String... args) {
//        System.out.println(agent.run("Who are you, give me response in one sentence"));
//        System.out.println(agent.run("Repeat what you have said"));
//        System.out.println(agent.run("What java version this project uses"));
//        System.out.println(agent.run("Tell me what is the purpose of the ToolRegistry"));
//        System.out.println(agent.run("Who is listening the ToolCallFinished event"));
//        System.out.println(agent.run("Are you sure?"));
    }
}
