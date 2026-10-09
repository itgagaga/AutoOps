package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.agent.ManagedAgent;
import com.zhiwei.superemployee.app.agent.ManagedAgentRepository;
import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.permission.*;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import com.zhiwei.superemployee.app.tool.ManagedTool;
import com.zhiwei.superemployee.app.tool.ManagedToolRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Agent 授权配置：工具权限、资源权限与自动审批策略。
 * <p>
 * 路径前缀 {@code /api/agent-permissions}。菜单权限：管理员或 {@code AGENT_AUTHORIZATION}。
 * 保存时整体替换该 Agent 的授权集合；工具/资源须存在且不可重复授予。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/agent-permissions")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AGENT_AUTHORIZATION')")
public class AgentPermissionController {
    private final AgentPermissionRepository permissions; private final ManagedAgentRepository agents; private final ManagedToolRepository tools; private final ResourceRepository resources; private final AuditService audit;
    /**
     * @param permissions 授权仓储
     * @param agents      Agent 仓储
     * @param tools       工具仓储
     * @param resources   资源仓储（校验 HOST/APPLICATION/DATABASE）
     * @param audit       审计服务
     */
    public AgentPermissionController(AgentPermissionRepository permissions,ManagedAgentRepository agents,ManagedToolRepository tools,ResourceRepository resources,AuditService audit){this.permissions=permissions;this.agents=agents;this.tools=tools;this.resources=resources;this.audit=audit;}
    /**
     * 查询指定 Agent 的工具授权、资源授权与审批策略。
     *
     * @param agentId Agent 主键
     * @return 授权视图；Agent 不存在时 404
     */
    @GetMapping("/{agentId}") public ResponseEntity<Map<String,Object>> get(@PathVariable String agentId){if(!agents.findById(agentId).isPresent())return ResponseEntity.notFound().build();return ResponseEntity.ok(result(agentId));}
    /**
     * 整体替换 Agent 授权：工具列表、资源列表与审批策略。
     * 工具/资源重复或不存在返回 400；成功写审计。
     *
     * @param agentId Agent 主键
     * @param request 工具授予、资源授予与策略
     * @return 最新授权视图
     */
    @PutMapping("/{agentId}") public ResponseEntity<?> save(@PathVariable String agentId,@Valid @RequestBody SaveAgentPermissionsRequest request){ManagedAgent agent=agents.findById(agentId).orElse(null);if(agent==null)return ResponseEntity.notFound().build();List<AgentToolPermission> toolGrants=new ArrayList<>();Set<String> toolIds=new HashSet<>();for(SaveAgentPermissionsRequest.ToolGrant grant:request.getTools()){if(!toolIds.add(grant.getToolId()))return bad("Duplicate tool permission");ManagedTool tool=tools.findById(grant.getToolId()).orElse(null);if(tool==null)return bad("Tool not found: "+grant.getToolId());toolGrants.add(new AgentToolPermission(UUID.randomUUID().toString(),agentId,tool.getId(),tool.getCode(),tool.getName(),tool.getRiskLevel(),grant.getTimeoutSeconds()==null?tool.getTimeoutSeconds():grant.getTimeoutSeconds(),grant.getDailyLimit()==null?100:grant.getDailyLimit(),!Boolean.FALSE.equals(grant.getEnabled())));}
        List<AgentResourcePermission> resourceGrants=new ArrayList<>();Set<String> resourceKeys=new HashSet<>();for(SaveAgentPermissionsRequest.ResourceGrant grant:request.getResources()){String key=grant.getResourceType()+":"+grant.getResourceId();if(!resourceKeys.add(key))return bad("Duplicate resource permission");if(!resourceExists(grant.getResourceType(),grant.getResourceId()))return bad("Resource not found: "+key);resourceGrants.add(new AgentResourcePermission(UUID.randomUUID().toString(),agentId,grant.getResourceType(),grant.getResourceId(),null,null,grant.getAccessLevel(),!Boolean.FALSE.equals(grant.getEnabled())));}
        SaveAgentPermissionsRequest.Policy p=request.getPolicy();AgentApprovalPolicy policy=new AgentApprovalPolicy(null,agentId,p.getAutoApproveMaxRisk(),!Boolean.FALSE.equals(p.getProductionRequiresApproval()),true,Boolean.TRUE.equals(p.getCredentialAccessEnabled()));permissions.replace(agentId,toolGrants,resourceGrants,policy);audit.record("UPDATE","AGENT_PERMISSION",agentId,agent.getCode());return ResponseEntity.ok(result(agentId));}
    /**
     * 组装授权查询结果：tools / resources / policy。
     *
     * @param agentId Agent 主键
     * @return 授权视图 Map
     */
    private Map<String,Object> result(String agentId){Map<String,Object> result=new LinkedHashMap<>();result.put("agentId",agentId);result.put("tools",permissions.findTools(agentId));result.put("resources",permissions.findResources(agentId));result.put("policy",permissions.findPolicy(agentId));return result;}
    /**
     * 校验资源是否存在于资源中心（HOST / APPLICATION / 其它视为 DATABASE）。
     *
     * @param type 资源类型
     * @param id   资源主键
     * @return 是否存在
     */
    private boolean resourceExists(String type,String id){if("HOST".equals(type))return resources.findHost(id).isPresent();if("APPLICATION".equals(type))return resources.findApplication(id).isPresent();return resources.findDatabase(id).isPresent();}
    /**
     * 构造 400 错误正文。
     *
     * @param value 错误文案
     * @return bad request ResponseEntity
     */
    private ResponseEntity<Map<String,String>> bad(String value){Map<String,String> body=new LinkedHashMap<>();body.put("message",value);return ResponseEntity.badRequest().body(body);}
}
