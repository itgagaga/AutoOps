package com.zhiwei.superemployee.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
/**
 * @author yangdb
 */

class BackendWorkspacePathsTest {

    @TempDir
    Path temp;

    @Test
    void findsGradleModule() throws Exception {
        Path module = temp.resolve("svc");
        Files.createDirectories(module);
        Files.writeString(module.resolve("build.gradle"), "plugins {}");
        assertEquals(module.toAbsolutePath().normalize(),
                BackendWorkspacePaths.requireBackend(ProjectBackendKind.GRADLE, temp.toString()));
    }

    @Test
    void findsGoModule() throws Exception {
        Path module = temp.resolve("api");
        Files.createDirectories(module);
        Files.writeString(module.resolve("go.mod"), "module example.com/api");
        assertNotNull(BackendWorkspacePaths.findBackendModule(ProjectBackendKind.GO, temp));
    }

    @Test
    void findsDockerCompose() throws Exception {
        Files.writeString(temp.resolve("docker-compose.yml"), "services: {}");
        assertEquals(temp.toAbsolutePath().normalize(),
                BackendWorkspacePaths.requireBackend(ProjectBackendKind.DOCKER, temp.toString()));
    }

    @Test
    void rejectsMissingMarker() {
        assertThrows(IllegalArgumentException.class,
                () -> BackendWorkspacePaths.requireBackend(ProjectBackendKind.PYTHON, temp.toString()));
    }
}
