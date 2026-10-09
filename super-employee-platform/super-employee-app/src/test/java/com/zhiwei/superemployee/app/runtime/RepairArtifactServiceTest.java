package com.zhiwei.superemployee.app.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * @author yangdb
 */

class RepairArtifactServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDecodeUtf8MavenOutput() {
        byte[] bytes = "BUILD SUCCESS 构建成功".getBytes(StandardCharsets.UTF_8);
        assertThat(RepairArtifactService.decodeProcessOutput(bytes)).contains("BUILD SUCCESS", "构建成功");
    }

    @Test
    void shouldDecodeGbkMavenOutputWithoutThrowing() {
        byte[] bytes = "BUILD SUCCESS 构建成功".getBytes(Charset.forName("GBK"));
        String text = RepairArtifactService.decodeProcessOutput(bytes);
        assertThat(text).contains("BUILD SUCCESS");
        assertThat(text).contains("构建成功");
    }

    @Test
    void shouldTolerateMixedOrBrokenBytes() {
        byte[] broken = new byte[] {(byte) 0xC0, 0x20, 0x41};
        assertThat(RepairArtifactService.decodeProcessOutput(broken)).isNotBlank();
    }

    @Test
    void shouldParseMajorJavaVersions() {
        assertThat(RepairArtifactService.parseMajorVersion("17.0.12")).isEqualTo(17);
        assertThat(RepairArtifactService.parseMajorVersion("1.8.0_471")).isEqualTo(8);
        assertThat(RepairArtifactService.parseMajorVersion("jdk-21.0.2")).isEqualTo(21);
    }

    @Test
    void shouldPreferConfiguredJdkAndInjectJavaHome() throws Exception {
        Path jdk8 = createFakeJdk(tempDir.resolve("jdk-1.8"), "1.8.0_471");
        Path jdk17 = createFakeJdk(tempDir.resolve("jdk-17"), "17.0.12");
        RepairArtifactService service = new RepairArtifactService(
                null, 900, jdk17.toString(), 17);

        assertThat(service.resolveBuildJavaHome()).isEqualTo(jdk17.toAbsolutePath().normalize());
        assertThat(RepairArtifactService.isUsableJdk(jdk8, 17)).isFalse();
        assertThat(RepairArtifactService.isUsableJdk(jdk17, 17)).isTrue();

        Map<String, String> env = new LinkedHashMap<>();
        env.put("Path", "C:\\Windows\\System32");
        RepairArtifactService.configureBuildEnvironment(env, jdk17);
        assertThat(env.get("JAVA_HOME")).isEqualTo(jdk17.toAbsolutePath().normalize().toString());
        assertThat(env.get("Path")).startsWith(jdk17.resolve("bin").toAbsolutePath().normalize().toString());
    }

    private static Path createFakeJdk(Path home, String version) throws Exception {
        Files.createDirectories(home.resolve("bin"));
        String javac = System.getProperty("os.name", "").toLowerCase().contains("win") ? "javac.exe" : "javac";
        Files.writeString(home.resolve("bin").resolve(javac), "");
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n");
        return home;
    }
}
