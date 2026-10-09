package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.workspace.ProjectBackendKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/**
 * @author yangdb
 */

class PatchDeploymentFacadeTest {

    @Test
    void routesNodeToPathReplaceThenClient() {
        JavaPatchDeploymentService java = mock(JavaPatchDeploymentService.class);
        JavaWarDeploymentService war = mock(JavaWarDeploymentService.class);
        PathReplaceDeployer path = mock(PathReplaceDeployer.class);
        DockerDeployer docker = mock(DockerDeployer.class);
        ClientStaticDeployer client = mock(ClientStaticDeployer.class);
        when(path.deploy(any(), eq(ProjectBackendKind.NODE)))
                .thenReturn(new IncidentToolGateway.ToolResult(true, "backend ok"));
        when(client.deployIfConfigured(any()))
                .thenReturn(new IncidentToolGateway.ToolResult(true, "跳过静态部署"));

        PatchDeploymentFacade facade = new PatchDeploymentFacade(java, war, path, docker, client);
        IncidentTask task = new IncidentTask("t", "d", IncidentPriority.P2);
        task.getContext().put("backendKind", "NODE");

        IncidentToolGateway.ToolResult result = facade.deploy(task);
        assertTrue(result.isSuccess());
        verify(path).deploy(any(), eq(ProjectBackendKind.NODE));
        verify(client).deployIfConfigured(any());
    }

    @Test
    void routesMavenToJavaDeployer() {
        JavaPatchDeploymentService java = mock(JavaPatchDeploymentService.class);
        JavaWarDeploymentService war = mock(JavaWarDeploymentService.class);
        PathReplaceDeployer path = mock(PathReplaceDeployer.class);
        DockerDeployer docker = mock(DockerDeployer.class);
        ClientStaticDeployer client = mock(ClientStaticDeployer.class);
        when(java.deploy(any())).thenReturn(new IncidentToolGateway.ToolResult(true, "java ok"));
        when(client.deployIfConfigured(any()))
                .thenReturn(new IncidentToolGateway.ToolResult(true, "未配置 Web 端侧，跳过静态部署"));

        PatchDeploymentFacade facade = new PatchDeploymentFacade(java, war, path, docker, client);
        IncidentTask task = new IncidentTask("t", "d", IncidentPriority.P2);
        task.getContext().put("backendKind", "MAVEN");

        assertTrue(facade.deploy(task).isSuccess());
        verify(java).deploy(any());
    }

    @Test
    void routesJavaWarToWarDeployer() {
        JavaPatchDeploymentService java = mock(JavaPatchDeploymentService.class);
        JavaWarDeploymentService war = mock(JavaWarDeploymentService.class);
        PathReplaceDeployer path = mock(PathReplaceDeployer.class);
        DockerDeployer docker = mock(DockerDeployer.class);
        ClientStaticDeployer client = mock(ClientStaticDeployer.class);
        when(war.deploy(any())).thenReturn(new IncidentToolGateway.ToolResult(true, "war ok"));
        when(client.deployIfConfigured(any()))
                .thenReturn(new IncidentToolGateway.ToolResult(true, "未配置 Web 端侧，跳过静态部署"));

        PatchDeploymentFacade facade = new PatchDeploymentFacade(java, war, path, docker, client);
        IncidentTask task = new IncidentTask("t", "d", IncidentPriority.P2);
        task.getContext().put("backendKind", "JAVA_WAR");

        assertTrue(facade.deploy(task).isSuccess());
        verify(war).deploy(any());
    }
}
