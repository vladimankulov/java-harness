package com.harness.agent.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@ConfigurationProperties("harness.agent")
public record AgentProperty(
        @DefaultValue("20") int maxTurns,
        @DefaultValue(".") String workingDirectory,
        @DefaultValue("true") boolean personalityEnabled
) {

    public AgentProperty {
        if (maxTurns < 1) {
            throw new IllegalArgumentException("harness.agent.max-turns cannot be less than 1");
        }
        if (!StringUtils.hasText(workingDirectory)) {
            throw new IllegalArgumentException("harness.agent.working-directory cannot be null or empty");
        }
    }
}
