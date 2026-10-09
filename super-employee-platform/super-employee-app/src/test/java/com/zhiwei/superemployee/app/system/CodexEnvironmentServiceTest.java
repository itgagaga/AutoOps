package com.zhiwei.superemployee.app.system;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link CodexEnvironmentService} 单测：CLI 安装/登录状态探测、输出脱敏、并发互斥与失败态暴露。
 * @author yangdb
 */
class CodexEnvironmentServiceTest {
    /** 存放伪 codex.cmd 或缺失可执行文件的临时目录。 */
    @TempDir
    Path tempDir;

    private CodexEnvironmentService service;

    /**
     * 用例结束后关闭服务内部异步资源，避免线程泄漏影响后续测试。
     */
    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
    }

    /**
     * 场景（仅 Windows）：伪 CLI 同时响应 {@code --version} 与 {@code login status}。
     * 预期：status 报告已安装、已认证及版本；再次 install 返回「已安装无需重复」。
     */
    @Test
    void shouldReportInstalledAndAuthenticatedFromCliStatus() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path command = tempDir.resolve("codex.cmd");
        Files.writeString(command, """
                @echo off
                if "%1"=="--version" (
                  echo codex-cli 1.2.3
                  exit /b 0
                )
                if "%1"=="login" if "%2"=="status" (
                  echo Logged in using ChatGPT
                  exit /b 0
                )
                exit /b 1
                """);
        service = new CodexEnvironmentService(command.toString(), 60, 60, null);

        Map<String, Object> status = service.status();

        assertThat(status).containsEntry("installed", true)
                .containsEntry("authenticated", true)
                .containsEntry("version", "codex-cli 1.2.3");
        assertThat(status.get("authSummary")).isEqualTo("Logged in using ChatGPT");
        assertThat(service.install()).containsEntry("state", "SUCCEEDED")
                .containsEntry("message", "CodeX CLI 已安装，无需重复安装");
    }

    /**
     * 场景：可执行文件路径不存在。
     * 预期：status 中 installed/authenticated 均为 false。
     */
    @Test
    void shouldReportMissingInstallation() {
        service = new CodexEnvironmentService(
                tempDir.resolve("missing-codex.exe").toString(), 60, 60, null);

        assertThat(service.status()).containsEntry("installed", false)
                .containsEntry("authenticated", false);
    }

    /**
     * 场景：进程输出含 Bearer Token 与 sk- 密钥片段。
     * 预期：sanitizeOutput 隐藏明文凭据，并插入「凭据已隐藏」占位。
     */
    @Test
    void shouldMaskCredentialsInProcessOutput() {
        String sanitized = CodexEnvironmentService.sanitizeOutput(
                "Authorization: Bearer abcdefghijklmnopqrstuvwxyz\nkey=sk-abcdefghijklmnop");

        assertThat(sanitized).doesNotContain("abcdefghijklmnopqrstuvwxyz", "sk-abcdefghijklmnop")
                .contains("[凭据已隐藏]");
    }

    /**
     * 场景（仅 Windows）：长耗时 install 进行中再次调用 install，随后 cancel。
     * 预期：并发 install 抛出「正在进行」；cancel 后操作态保持 CANCELLED。
     */
    @Test
    void shouldRejectConcurrentOperationAndKeepCancelledState() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        service = new CodexEnvironmentService(tempDir.resolve("missing-codex.exe").toString(),
                60, 60, type -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return new CodexEnvironmentService.ExecutionResult(true, "done");
        });

        service.install();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(service::install)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("正在进行");

        Map<String, Object> cancelled = service.cancel();
        release.countDown();
        Thread.sleep(100);

        assertThat(cancelled).containsEntry("state", "CANCELLED");
        assertThat(service.currentOperation()).containsEntry("state", "CANCELLED");
    }

    /**
     * 场景（仅 Windows）：执行器返回失败结果。
     * 预期：异步操作结束后状态为 FAILED，并保留失败消息原文。
     */
    @Test
    void shouldExposeFailedOperationMessage() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
        service = new CodexEnvironmentService(tempDir.resolve("missing-codex.exe").toString(), 60, 60,
                type -> new CodexEnvironmentService.ExecutionResult(false, "操作超时，请稍后重试"));

        service.install();
        Map<String, Object> operation = awaitTerminalOperation();

        assertThat(operation).containsEntry("state", "FAILED")
                .containsEntry("message", "操作超时，请稍后重试");
    }

    /**
     * 轮询当前操作直至离开 RUNNING，或达到重试上限后返回最新快照。
     *
     * @return 终态或超时后的 operation Map
     * @throws InterruptedException 等待被中断时抛出
     */
    private Map<String, Object> awaitTerminalOperation() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Map<String, Object> current = service.currentOperation();
            if (!"RUNNING".equals(current.get("state"))) return current;
            Thread.sleep(20);
        }
        return service.currentOperation();
    }
}
