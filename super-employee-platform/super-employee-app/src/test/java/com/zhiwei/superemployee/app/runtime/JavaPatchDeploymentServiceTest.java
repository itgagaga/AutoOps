package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.ops.DeploymentPathDiscoveryService;
import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import com.zhiwei.superemployee.app.system.WorkspaceSettingsService;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link JavaPatchDeploymentService} 单测：关闭自动部署时生成脚本，以及路径识别失败时阻断上传。
 * @author yangdb
 */
class JavaPatchDeploymentServiceTest {
    /** 临时 Maven 工作区根目录，用例内写入 pom.xml 与 jar。 */
    @TempDir
    Path tempDir;

    /**
     * 场景：Java 应用关闭 autoDeploy，仅要求生成本地部署脚本。
     * 预期：deploy 成功；输出含 jar 与 backup/start/deploy 脚本；脚本内容含备份、nohup 启动与编排调用。
     */
    @Test
    void shouldGenerateBackupStartAndDeployScriptsWhenAutoDeployIsDisabled() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path target = Files.createDirectories(tempDir.resolve("target"));
        Path jar = Files.writeString(target.resolve("demo-service-1.0.jar"), "jar");
        WorkspaceSettingsService workspaces = mock(WorkspaceSettingsService.class);
        when(workspaces.resolveMavenModule(tempDir.toString())).thenReturn(tempDir);
        JavaPatchDeploymentService service = new JavaPatchDeploymentService(
                workspaces, mock(ManagedSshConnector.class), mock(DeploymentPathDiscoveryService.class), 60);
        IncidentTask task = new IncidentTask("test", "test", IncidentPriority.P2);
        task.getContext().put("workspacePath", tempDir.toString());
        task.getContext().put("applicationCode", "demo-service");
        task.getContext().put("applicationType", "JAVA");
        task.getContext().put("autoDeploy", false);
        task.getContext().put("deploymentPath", "/opt/apps/demo-service");

        var result = service.deploy(task);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains(jar.toString(), "backup.sh", "start.sh", "deploy.sh");
        Path scripts = Path.of(String.valueOf(task.getContext().get("deploymentScripts")));
        assertThat(Files.readString(scripts.resolve("backup.sh"))).contains("backups", "cp -p", "REL_JAR");
        assertThat(Files.readString(scripts.resolve("start.sh"))).contains("nohup java", "PID_FILE", "REL_JAR");
        assertThat(Files.readString(scripts.resolve("deploy.sh"))).contains("backup.sh", "start.sh", "REL_JAR");
    }

    @Test
    void shouldMatchMultipleModuleArtifactsForBoundTargets() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path moduleA = Files.createDirectories(tempDir.resolve("mod-a"));
        Path moduleB = Files.createDirectories(tempDir.resolve("mod-b"));
        Files.writeString(moduleA.resolve("pom.xml"), "<project/>");
        Files.writeString(moduleB.resolve("pom.xml"), "<project/>");
        Path jarA = Files.writeString(Files.createDirectories(moduleA.resolve("target")).resolve("insiis-rcpx-hlw.jar"), "a");
        Path jarB = Files.writeString(Files.createDirectories(moduleB.resolve("target")).resolve("insiis-rcpx-ywjg.jar"), "b");
        WorkspaceSettingsService workspaces = mock(WorkspaceSettingsService.class);
        when(workspaces.resolveMavenModule(tempDir.toString())).thenReturn(tempDir);
        JavaPatchDeploymentService service = new JavaPatchDeploymentService(
                workspaces, mock(ManagedSshConnector.class), mock(DeploymentPathDiscoveryService.class), 60);
        IncidentTask task = new IncidentTask("test", "test", IncidentPriority.P2);
        task.getContext().put("workspacePath", tempDir.toString());
        task.getContext().put("applicationCode", "rcpx");
        task.getContext().put("applicationType", "JAVA");
        task.getContext().put("autoDeploy", false);
        task.getContext().put("deploymentTargets", List.of(
                Map.of("path", "/data/rcpx/insiis-rcpx-hlw", "jar", "insiis-rcpx-hlw.jar"),
                Map.of("path", "/data/rcpx/insiis-rcpx-ywjg", "jar", "insiis-rcpx-ywjg.jar")));

        var result = service.deploy(task);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains(jarA.toString(), jarB.toString());
        @SuppressWarnings("unchecked")
        List<String> artifacts = (List<String>) task.getContext().get("deploymentArtifacts");
        assertThat(artifacts).containsExactly(jarA.toString(), jarB.toString());
    }

    /**
     * 场景：开启自动部署但远程路径探测返回多候选歧义结果。
     * 预期：deploy 失败且提示路径识别失败；不写入 deploymentScripts；SSH 连接器无交互。
     */
    @Test
    void shouldStopBeforeUploadWhenDeploymentPathCannotBeUniquelyDetected() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Files.writeString(Files.createDirectories(tempDir.resolve("target")).resolve("demo-service.jar"), "jar");
        WorkspaceSettingsService workspaces = mock(WorkspaceSettingsService.class);
        when(workspaces.resolveMavenModule(tempDir.toString())).thenReturn(tempDir);
        ManagedSshConnector connector = mock(ManagedSshConnector.class);
        DeploymentPathDiscoveryService discovery = mock(DeploymentPathDiscoveryService.class);
        when(discovery.detect("host-1", "demo-service", List.of("/opt/a", "/opt/b")))
                .thenReturn(new DeploymentPathDiscoveryService.DetectionResult(
                        false, null, "存在多个同等可信的部署目录", List.of()));
        JavaPatchDeploymentService service =
                new JavaPatchDeploymentService(workspaces, connector, discovery, 60);
        IncidentTask task = new IncidentTask("test", "test", IncidentPriority.P2);
        task.getContext().put("workspacePath", tempDir.toString());
        task.getContext().put("applicationCode", "demo-service");
        task.getContext().put("applicationType", "JAVA");
        task.getContext().put("autoDeploy", true);
        task.getContext().put("preferredHostId", "host-1");
        task.getContext().put("deploymentPath", "/opt/a");
        task.getContext().put("deploymentPaths", List.of("/opt/a", "/opt/b"));

        var result = service.deploy(task);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOutput()).contains("部署前路径识别失败", "多个同等可信");
        assertThat(task.getContext()).doesNotContainKey("deploymentScripts");
        verifyNoInteractions(connector);
    }
}
