package com.zhiwei.superemployee.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
/**
 * @author yangdb
 */

class ClientWorkspacePathsTest {

    @TempDir
    Path temp;

    @Test
    void noneReturnsNull() {
        assertNull(ClientWorkspacePaths.requireClient(ProjectClientKind.NONE, null));
        assertNull(ClientWorkspacePaths.requireClient(ProjectClientKind.NONE, temp.toString()));
    }

    @Test
    void findsPackageJsonForWebNpm() throws Exception {
        Path module = temp.resolve("web");
        Files.createDirectories(module);
        Files.writeString(module.resolve("package.json"), "{}");
        Path found = ClientWorkspacePaths.requireClient(ProjectClientKind.WEB_NPM, temp.toString());
        assertEquals(module.toAbsolutePath().normalize(), found);
    }

    @Test
    void findsMiniProgramMarkers() throws Exception {
        Path module = temp.resolve("mp");
        Files.createDirectories(module);
        Files.writeString(module.resolve("project.config.json"), "{}");
        Path found = ClientWorkspacePaths.requireClient(ProjectClientKind.MP_WEIXIN_NATIVE, temp.toString());
        assertEquals(module.toAbsolutePath().normalize(), found);
    }

    @Test
    void requiresPathWhenClientConfigured() {
        assertThrows(IllegalArgumentException.class,
                () -> ClientWorkspacePaths.requireClient(ProjectClientKind.UNI_APP, " "));
    }
}
