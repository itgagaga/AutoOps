package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.workspace.ClientWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ProjectClientKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 端侧工程验证：按 clientKind 路由 npm 构建或小程序弱结构校验。
 * @author yangdb
 */
@Service
public class FrontendBuildService {
    private final String webBuildCommand;
    private final String uniAppBuildCommand;
    private final long timeoutSeconds;

    public FrontendBuildService(
            @Value("${super-employee.frontend.build-command:npm run build}") String webBuildCommand,
            @Value("${super-employee.frontend.uniapp-build-command:npm run build:mp-weixin}") String uniAppBuildCommand,
            @Value("${super-employee.frontend.timeout-seconds:600}") long timeoutSeconds) {
        this.webBuildCommand = blankDefault(webBuildCommand, "npm run build");
        this.uniAppBuildCommand = blankDefault(uniAppBuildCommand, "npm run build:mp-weixin");
        this.timeoutSeconds = timeoutSeconds <= 0 ? 600 : timeoutSeconds;
    }

    /**
     * 按故障上下文中的端侧画像执行验证；无端侧时跳过。
     */
    public IncidentToolGateway.ToolResult verifyIfConfigured(IncidentTask task) {
        ProjectClientKind kind = resolveClientKind(task);
        if (kind == ProjectClientKind.NONE) {
            return new IncidentToolGateway.ToolResult(true, "未配置端侧工程，跳过端侧验证");
        }
        Object raw = task.getContext().get("frontendWorkspacePath");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return new IncidentToolGateway.ToolResult(false,
                    "端侧类型为 " + kind.displayName() + "，但未配置端侧工程目录");
        }
        Path module;
        try {
            module = ClientWorkspacePaths.requireClient(kind, String.valueOf(raw));
        }
        catch (RuntimeException ex) {
            return new IncidentToolGateway.ToolResult(false, "端侧工程无效：" + ex.getMessage());
        }
        return switch (kind) {
            case WEB_NPM, UNI_APP -> runNodeBuild(module, resolveCommand(task, kind));
            case MP_WEIXIN_NATIVE -> weakMiniProgramCheck(module);
            case NONE -> new IncidentToolGateway.ToolResult(true, "跳过端侧验证");
        };
    }

    private ProjectClientKind resolveClientKind(IncidentTask task) {
        Object raw = task.getContext().get("clientKind");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return ProjectClientKind.from(String.valueOf(raw));
        }
        Object path = task.getContext().get("frontendWorkspacePath");
        if (path != null && !String.valueOf(path).isBlank()) {
            return ProjectClientKind.WEB_NPM;
        }
        return ProjectClientKind.NONE;
    }

    private String resolveCommand(IncidentTask task, ProjectClientKind kind) {
        Object override = task.getContext().get("clientVerifyCommand");
        if (override != null && !String.valueOf(override).isBlank()) {
            return String.valueOf(override).trim();
        }
        if (kind == ProjectClientKind.UNI_APP) {
            return uniAppBuildCommand;
        }
        return webBuildCommand;
    }

    private IncidentToolGateway.ToolResult weakMiniProgramCheck(Path module) {
        boolean hasProject = Files.isRegularFile(module.resolve("project.config.json"))
                || Files.isRegularFile(module.resolve("project.private.config.json"));
        boolean hasApp = Files.isRegularFile(module.resolve("app.json"))
                || Files.isRegularFile(module.resolve("app.js"))
                || Files.isRegularFile(module.resolve("app.ts"));
        if (!hasProject && !hasApp) {
            return new IncidentToolGateway.ToolResult(false,
                    "微信原生小程序弱校验失败：未找到 project.config.json / app.json\n工作区：" + module);
        }
        return new IncidentToolGateway.ToolResult(true,
                "微信原生小程序弱校验通过（结构检查，未执行开发者工具上传）\n工作区：" + module
                        + "\n说明：完整构建/上传需对接微信开发者工具 CLI，本阶段仅做工程结构门禁。");
    }

    private IncidentToolGateway.ToolResult runNodeBuild(Path module, String buildCommand) {
        if (!Files.isRegularFile(module.resolve("package.json"))) {
            return new IncidentToolGateway.ToolResult(false, "端侧目录缺少 package.json：" + module);
        }
        List<String> command = splitCommand(buildCommand);
        if (command.isEmpty() || !isSafeNpmCommand(command)) {
            return new IncidentToolGateway.ToolResult(false, "端侧构建命令不被允许：" + buildCommand);
        }
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(module.toFile())
                    .redirectErrorStream(true);
            process = builder.start();
            String output = readOutput(process);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new IncidentToolGateway.ToolResult(false,
                        "端侧构建超时（" + timeoutSeconds + "s）\n工作区：" + module + "\n" + truncate(output));
            }
            if (process.exitValue() != 0) {
                return new IncidentToolGateway.ToolResult(false,
                        "端侧构建失败（exitCode=" + process.exitValue() + "）\n工作区：" + module
                                + "\n命令：" + buildCommand + "\n" + truncate(output));
            }
            return new IncidentToolGateway.ToolResult(true,
                    "端侧构建成功\n工作区：" + module + "\n命令：" + buildCommand + "\n" + truncate(output));
        }
        catch (Exception ex) {
            return new IncidentToolGateway.ToolResult(false,
                    "无法启动端侧构建：" + ex.getMessage() + "（工作区：" + module + "）");
        }
        finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static boolean isSafeNpmCommand(List<String> command) {
        if (command.isEmpty()) {
            return false;
        }
        String exe = command.get(0).toLowerCase(Locale.ROOT);
        if (!(exe.equals("npm") || exe.equals("npm.cmd") || exe.equals("pnpm")
                || exe.equals("pnpm.cmd") || exe.equals("yarn") || exe.equals("yarn.cmd"))) {
            return false;
        }
        String joined = String.join(" ", command).toLowerCase(Locale.ROOT);
        return !joined.contains("&&") && !joined.contains(";") && !joined.contains("|")
                && !joined.contains(">") && !joined.contains("<");
    }

    private static List<String> splitCommand(String command) {
        String[] parts = command.trim().split("\\s+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                result.add(part);
            }
        }
        return result;
    }

    private static String readOutput(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        Charset charset = Charset.defaultCharset();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
                if (sb.length() > 80_000) {
                    sb.append("\n...[已截断]");
                    break;
                }
            }
        }
        return sb.toString();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 12_000 ? value : value.substring(0, 12_000) + "\n...[已截断]";
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
