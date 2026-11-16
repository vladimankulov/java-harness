package com.harness.permission;

import com.harness.agent.Agent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@Component
@Profile("!test")
public class ConsolePermissionPrompt implements PermissionPrompt {



    private final BufferedReader input;
    private final PrintStream output;


    public ConsolePermissionPrompt() {
        this(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out);
    }

    public ConsolePermissionPrompt(BufferedReader input, PrintStream output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public PermissionAnswer ask(String toolName, String target, String reason) {
        return null;
    }
}
