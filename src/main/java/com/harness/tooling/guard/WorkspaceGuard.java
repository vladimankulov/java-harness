package com.harness.tooling.guard;

import com.harness.agent.property.AgentProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@Component
public class WorkspaceGuard {

    private final Path root;

    public WorkspaceGuard(AgentProperty agentProperty) {
        this.root = Path.of(agentProperty.workingDirectory()).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return this.root;
    }

    public Path resolve(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw new WorkspaceViolationException("path is empty");
        }
        Path candidate = Path.of(rawPath);
        Path resolved = (candidate.isAbsolute() ? candidate : root.resolve(candidate)).normalize();

        if (!resolved.startsWith(root)) {
            throw new WorkspaceViolationException("Requested path: '%s' outside of the working directory: '%s'".formatted(resolved, root));
        }

        try {
            Path realPath = resolved.toRealPath();
            if (!realPath.startsWith(root.toRealPath())) {
                throw new WorkspaceViolationException("Requested path: '%s' outside of the working directory: '%s'".formatted(realPath, root.toRealPath()));
            }
        } catch (NoSuchFileException ignored) {

        } catch (IOException e) {
            throw new WorkspaceViolationException("Fail to resolve path: %s inside working directory: %s, err: %s".formatted(resolved, root, e.getMessage()));
        }
        return resolved;
    }

    public String relativize(Path path) {
        return root.relativize(path).toString().replace("\\", "/");
    }
}
