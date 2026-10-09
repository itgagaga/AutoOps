package com.zhiwei.superemployee.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MavenWorkspacePaths} 的单元测试：覆盖根 pom、子模块 pom、带引号路径与无 pom 拒绝场景。
 * @author yangdb
 */
class MavenWorkspacePathsTest {

    /** JUnit 临时目录，每个测试用例独立。 */
    @TempDir
    Path tempDir;

    /**
     * 工作区根目录直接含 pom.xml 时应通过校验，且模块解析指向根目录。
     *
     * @throws Exception 写入临时 pom 失败时
     */
    @Test
    void shouldAcceptRootPom() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

        assertThat(MavenWorkspacePaths.requireContainsMaven(tempDir.toString())).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(MavenWorkspacePaths.resolveMavenModule(tempDir)).isEqualTo(tempDir.toAbsolutePath().normalize());
    }

    /**
     * 父目录无 pom、子目录有 pom 时，工作区可接受父路径，模块解析应指向子模块。
     *
     * @throws Exception 创建目录或写入 pom 失败时
     */
    @Test
    void shouldAcceptParentWhenChildHasPom() throws Exception {
        Path backend = Files.createDirectories(tempDir.resolve("服务后端"));
        Files.createDirectories(tempDir.resolve("互联网前端"));
        Files.writeString(backend.resolve("pom.xml"), "<project/>");

        Path workspace = MavenWorkspacePaths.requireContainsMaven(tempDir.toString());
        assertThat(workspace).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(MavenWorkspacePaths.resolveMavenModule(tempDir)).isEqualTo(backend.toAbsolutePath().normalize());
        assertThat(MavenWorkspacePaths.containsMaven(tempDir)).isTrue();
    }

    /**
     * 路径被英文双引号包裹时应仍能正确解析。
     *
     * @throws Exception 写入临时 pom 失败时
     */
    @Test
    void shouldAcceptQuotedUnicodeWorkspacePath() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

        Path workspace = MavenWorkspacePaths.requireContainsMaven(
                "\"" + tempDir + "\"");

        assertThat(workspace).isEqualTo(tempDir.toAbsolutePath().normalize());
    }

    /**
     * 目录树中无 pom.xml 时应抛出 {@link IllegalArgumentException}。
     */
    @Test
    void shouldRejectDirectoryWithoutPom() {
        assertThatThrownBy(() -> MavenWorkspacePaths.requireContainsMaven(tempDir.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pom.xml");
    }
}
