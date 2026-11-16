package com.harness;

import com.harness.agent.Agent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сценарий текущего урока. Единственный файл, который меняется «до» урока —
 * всё остальное мы дописываем по ходу.
 *
 * <p>Урок 6: спрашиваем то, что агент обязан знать до первого вопроса, —
 * где он находится и что это за проект. В начале урока он пойдёт это искать
 * инструментами и ответит неверно; в конце ответит сразу, не сделав ни одного
 * вызова.
 */
@Component
@Profile("!test")
public class DemoRunner implements CommandLineRunner {

    private final Agent agent;

    public DemoRunner(Agent agent) {
        this.agent = agent;
    }

    @Override
    public void run(String... args) throws IOException {
        editsScene();
//        System.out.println(agent.run("Who are you, give me response in one sentence"));
//        System.out.println(agent.run("Repeat what you have said"));
//        System.out.println(agent.run("What java version this project uses"));
//        System.out.println(agent.run("Tell me what is the purpose of the ToolRegistry"));
//        System.out.println(agent.run("Who is listening the ToolCallFinished event"));
//        System.out.println(agent.run("Are you sure?"));
        System.out.println(agent.run("""
                На какой ветке git мы сейчас работаем и есть ли незакоммиченные изменения?
                И что это вообще за проект? Ответь коротко."""));
    }



    private void editsScene() throws IOException {
        // Новый файл можно писать без предварительного Read, поэтому прошлую копию убираем.
        Files.deleteIfExists(Path.of("target/lesson7-note.txt"));
        // В DEFAULT запись спрашивает, ответь y. В ACCEPT_EDITS запись внутри проекта проходит молча.
        System.out.println("\n=== БОНУС. Запись файла: DEFAULT спрашивает, ACCEPT_EDITS — нет ===");
        System.out.println(agent.run("""
                Создай инструментом Write новый файл target/lesson7-note.txt
                с одной строкой: LESSON7_EDITS
                Другие инструменты не нужны. В конце сообщи, получилось ли записать файл.
                """));
    }
}
