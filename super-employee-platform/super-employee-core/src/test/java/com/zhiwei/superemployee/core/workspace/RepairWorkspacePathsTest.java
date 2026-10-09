package com.zhiwei.superemployee.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
/**
 * @author yangdb
 */

class RepairWorkspacePathsTest {

    @TempDir
    Path temp;

    @Test
    void siblingProjectsShareParent() throws Exception {
        Path backend = temp.resolve("backend");
        Path frontend = temp.resolve("frontend");
        Files.createDirectories(backend);
        Files.createDirectories(frontend);
        Files.writeString(backend.resolve("pom.xml"), "<project/>");
        Files.writeString(frontend.resolve("package.json"), "{}");

        Path root = RepairWorkspacePaths.resolveRepairRoot(backend, frontend);
        assertEquals(temp.toAbsolutePath().normalize(), root);
    }

    @Test
    void frontendUnderBackendUsesBackend() throws Exception {
        Path backend = temp.resolve("app");
        Path frontend = backend.resolve("web");
        Files.createDirectories(frontend);
        Files.writeString(backend.resolve("pom.xml"), "<project/>");
        Files.writeString(frontend.resolve("package.json"), "{}");

        Path root = RepairWorkspacePaths.resolveRepairRoot(backend, frontend);
        assertEquals(backend.toAbsolutePath().normalize(), root);
    }

    @Test
    void backendOnlyReturnsBackend() throws Exception {
        Path backend = temp.resolve("svc");
        Files.createDirectories(backend);
        Files.writeString(backend.resolve("pom.xml"), "<project/>");
        assertEquals(backend.toAbsolutePath().normalize(),
                RepairWorkspacePaths.resolveRepairRoot(backend, null));
    }

    @Test
    void tooDeepAncestorRejected() {
        Path backend = Path.of("C:/a/b/c/d/e/backend");
        Path frontend = Path.of("C:/a/other/frontend");
        assertThrows(IllegalArgumentException.class,
                () -> RepairWorkspacePaths.resolveRepairRoot(backend, frontend));
    }
}
