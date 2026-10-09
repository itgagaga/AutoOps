package com.zhiwei.superemployee.core.gateway;

import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.workspace.MavenWorkspacePaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 基于本地进程命令的工具网关：在含 {@code pom.xml} 的 Maven 模块目录中执行验证/部署命令。
 * <p>
 * 预览仅返回 diff 文本不落盘；验证与部署受允许前缀列表、Maven 目标白名单与超时约束保护，
 * 工作目录经 {@link MavenWorkspacePaths#resolveMavenModule} 校验。与 {@link IncidentModelGateway} 产出的
 * {@link IncidentModelGateway.PatchPlan} 配合，供真实本地验证场景使用。
 * @author yangdb
 */
public class CommandIncidentToolGateway implements IncidentToolGateway {

    /** 验证阶段执行的命令行（如 {@code mvn test}）。 */
    private final String verificationCommand;
    /** 部署阶段执行的命令行。 */
    private final String deploymentCommand;
    /** 已解析为含 pom.xml 的 Maven 模块工作目录。 */
    private final Path workingDirectory;
    /** 允许执行的命令前缀白名单。 */
    private final List<String> allowedCommands;
    /** 单次进程等待超时（秒）。 */
    private final long timeoutSeconds;
    /** 为 true 时强制解析 Maven 模块；否则仅要求目录存在。 */
    private final boolean requireMavenModule;

    /**
     * 构造命令工具网关；工作目录必须能解析到 Maven 模块。
     */
    public CommandIncidentToolGateway(String verificationCommand,
                                      String deploymentCommand,
                                      Path workingDirectory,
                                      List<String> allowedCommands,
                                      long timeoutSeconds) {
        this(verificationCommand, deploymentCommand, workingDirectory, allowedCommands, timeoutSeconds, true);
    }

    /**
     * 构造命令工具网关。
     *
     * @param requireMavenModule true 时解析 pom.xml 模块；false 时仅校验目录存在（多栈验证）
     */
    public CommandIncidentToolGateway(String verificationCommand,
                                      String deploymentCommand,
                                      Path workingDirectory,
                                      List<String> allowedCommands,
                                      long timeoutSeconds,
                                      boolean requireMavenModule) {
        this.verificationCommand = verificationCommand;
        this.deploymentCommand = deploymentCommand;
        this.requireMavenModule = requireMavenModule;
        this.workingDirectory = validateWorkspace(workingDirectory);
        this.allowedCommands = allowedCommands == null ? Collections.<String>emptyList() : allowedCommands;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 返回补丁内容的预览结果，不执行实际修改。
     *
     * @param task 故障任务（本实现未使用）
     * @param plan 含 diff 预览的补丁方案
     * @return 始终成功、输出为预览说明的结果
     */
    @Override
    public ToolResult previewPatch(IncidentTask task, IncidentModelGateway.PatchPlan plan) {
        return new ToolResult(true, "patch preview ready: " + plan.getDiffPreview());
    }

    /**
     * 执行配置的验证命令。
     *
     * @param task 故障任务（本实现未使用）
     * @param plan 补丁方案（本实现未使用命令参数）
     * @return 进程退出码为 0 则成功，否则失败并附带输出
     */
    @Override
    public ToolResult runVerification(IncidentTask task, IncidentModelGateway.PatchPlan plan) {
        return execute("verification", verificationCommand);
    }

    /**
     * 执行配置的部署命令。
     *
     * @param task 故障任务（本实现未使用）
     * @param plan 补丁方案（本实现未使用命令参数）
     * @return 进程退出码为 0 则成功，否则失败并附带输出
     */
    @Override
    public ToolResult deploy(IncidentTask task, IncidentModelGateway.PatchPlan plan) {
        return execute("deployment", deploymentCommand);
    }

    /**
     * 校验命令白名单与 Maven 安全性后启动进程，并收集受限长度的输出。
     *
     * @param operation 操作名（用于错误信息前缀）
     * @param command   待执行命令行
     * @return 成功或失败的工具结果
     */
    private ToolResult execute(String operation, String command) {
        if (command == null || command.trim().isEmpty()) {
            return new ToolResult(false, operation + " command is not configured");
        }
        if (!isAllowed(command) || !isSafeBuildCommand(command)) {
            return new ToolResult(false, operation + " command is blocked by allowlist: " + command);
        }

        Process process = null;
        try {
            process = new ProcessBuilder(command.trim().split("\\s+"))
                    .directory(workingDirectory == null ? null : workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = readOutput(process);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolResult(false, operation + " timed out after " + timeoutSeconds + " seconds\n" + output);
            }
            return new ToolResult(process.exitValue() == 0, output);
        }
        catch (IOException ex) {
            return new ToolResult(false, operation + " failed to start: " + ex.getMessage());
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new ToolResult(false, operation + " interrupted");
        }
        finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 读取进程标准输出并限制最大内容长度（约 16KB）。
     *
     * @param process 已启动进程
     * @return 截断后的合并输出
     * @throws IOException 读取输入流失败时
     */
    private String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 16_000) {
                    break;
                }
                output.append(line).append(System.lineSeparator());
            }
        }
        return output.toString().trim();
    }

    /**
     * 判断命令是否匹配配置的允许前缀。
     *
     * @param command 原始命令行
     * @return 匹配任一允许前缀时为 {@code true}
     */
    private boolean isAllowed(String command) {
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        for (String allowed : allowedCommands) {
            String prefix = allowed == null ? "" : allowed.trim().toLowerCase(Locale.ROOT);
            if (!prefix.isEmpty() && (normalized.equals(prefix) || normalized.startsWith(prefix + " "))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 禁止 shell 元字符；Maven 仍限制为 test/verify；其它栈允许安全参数字符集。
     */
    private boolean isSafeBuildCommand(String command) {
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches(".*[&|;<>`$].*")) {
            return false;
        }
        if (normalized.startsWith("mvn") || normalized.startsWith("mvn.cmd")) {
            if (!normalized.matches("mvn(\\.cmd)?(\\s+[-a-z0-9_.=]+)*")) {
                return false;
            }
            return normalized.endsWith(" test") || normalized.endsWith(" verify");
        }
        return normalized.matches("[\\w./\\\\-]+(\\s+[-\\w./:=*@+]+)*");
    }

    private Path validateWorkspace(Path value) {
        if (value == null) {
            throw new IllegalArgumentException("本地验证必须配置工作目录");
        }
        Path normalized = value.toAbsolutePath().normalize();
        if (requireMavenModule) {
            return MavenWorkspacePaths.resolveMavenModule(normalized);
        }
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("工作目录不存在：" + normalized);
        }
        return normalized;
    }

    /**
     * 将逗号分隔的允许命令配置解析为列表。
     *
     * @param value 如 {@code mvn,mvn.cmd}；空或空白返回空列表
     * @return 去空白后的前缀列表
     */
    public static List<String> parseAllowlist(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .forEach(result::add);
        return result;
    }
}
