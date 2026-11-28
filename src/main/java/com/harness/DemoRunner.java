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
 * <p>Урок 8: план из шести шагов. Первый шаг — прочитать три файла, и они
 * вместе не помещаются в окно модели.
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
        // Прошлый прогон оставил ответы: убираем, чтобы видеть, что записал именно этот.
        for (String result : new String[] {"number", "count", "done"}) {
            Files.deleteIfExists(Path.of("target", result + ".txt"));
        }
        System.out.println(agent.run("""
                В папке lesson-08 лежат три файла с заметками. Вот план, выполни его по шагам:
                1. Прочитай все три файла: lesson-08/zametki-1.txt, lesson-08/zametki-2.txt, lesson-08/zametki-3.txt.
                2. Запиши в файл target/number.txt контрольное число из третьего файла.
                3. Запиши в файл target/count.txt, сколько файлов прочитано.
                4. Запиши в файл target/done.txt слово ГОТОВО.
                5. Прочитай target/done.txt и убедись, что слово на месте.
                6. Доложи одной строкой: ПЛАН ВЫПОЛНЕН."""));
    }
}
