package com.harness.tooling.guard;

import com.harness.agent.property.AgentProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

@Component
public class WorkspaceGuard {

    private final Path root;

    public WorkspaceGuard(AgentProperty agentProperty) {
        // Корень храним по реальному пути: иначе реальные пути файлов (/private/var/...) не сравнить с корнем (/var/...).
        this.root = realPath(Path.of(agentProperty.workingDirectory()));
    }

    public Path getRoot() {
        return this.root;
    }

    public Path resolve(Path rawPath) {
        return this.resolve(rawPath.toString());
    }
    public Path resolve(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw new WorkspaceViolationException("path is empty");
        }

        if (rawPath.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(rawPath.substring(2));
        }
        Path candidate = Path.of(rawPath);

        return (candidate.isAbsolute() ? candidate : root.resolve(candidate)).normalize();
    }

    //resolves symlink resulting the actual link
    //for example: given /app/link/file.txt which is symlink, this method should return /app/var/file.txt which is real path
    public Path realPath(Path path) {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path realPath = absolutePath;
        while (realPath != null && !Files.exists(realPath, LinkOption.NOFOLLOW_LINKS)) {
            realPath = realPath.getParent();
        }
        if (realPath == null) {
            throw new WorkspaceViolationException("Failed to check path: " + path);
        }
        try {
            return realPath.toRealPath().resolve(realPath.relativize(absolutePath)).normalize();
        } catch (IOException e) {
            throw new WorkspaceViolationException("Failed to check path %s, err: %s".formatted(path, e.getMessage()));
        }
    }

    public List<Path> pathsForCheck(Path path) {
        Path real = realPath(path);
        return real.equals(path) ? List.of(path) : List.of(path, real);
    }

    public String relativize(Path path) {
        //roots of two paths are different if they are locating at two different disks
        if (!root.getRoot().equals(path.getRoot())) {
            return path.toString().replace("\\", "/");
        }
        return root.relativize(path).toString().replace("\\", "/");
    }
}
