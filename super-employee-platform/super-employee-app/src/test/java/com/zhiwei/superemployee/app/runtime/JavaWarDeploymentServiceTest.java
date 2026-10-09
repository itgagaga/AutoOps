package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
/**
 * @author yangdb
 */

class JavaWarDeploymentServiceTest {

    @TempDir
    Path temp;

    @Test
    void generatesScriptsWithoutAutoDeploy() throws Exception {
        Path module = temp.resolve("app");
        Files.createDirectories(module.resolve("target"));
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        Files.writeString(module.resolve("target").resolve("demo.war"), "war");

        JavaWarDeploymentService service = new JavaWarDeploymentService(mock(ManagedSshConnector.class), 60);
        IncidentTask task = new IncidentTask("t", "d", IncidentPriority.P2);
        task.getContext().put("backendKind", "JAVA_WAR");
        task.getContext().put("workspacePath", module.toString());
        task.getContext().put("backendModulePath", module.toString());
        task.getContext().put("applicationCode", "demo");
        task.getContext().put("autoDeploy", false);
        task.getContext().put("deploymentPath", "/opt/tomcat/webapps");
        task.getContext().put("deploymentJar", "demo.war");

        IncidentToolGateway.ToolResult result = service.deploy(task);
        assertTrue(result.isSuccess(), result.getOutput());
        assertTrue(result.getOutput().contains("WAR"));
        try (Stream<Path> dirs = Files.list(module.resolve(".zhiwei-deploy"))) {
            Path scriptDir = dirs.findFirst().orElseThrow();
            assertTrue(Files.isRegularFile(scriptDir.resolve("deploy.sh")));
            String deploy = Files.readString(scriptDir.resolve("deploy.sh"));
            assertTrue(deploy.contains("webapps") || deploy.contains("REL_WAR"));
        }
    }
}
