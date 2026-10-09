package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.tool.ManagedTool;
import com.zhiwei.superemployee.app.tool.ManagedToolRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 托管工具（Agent 可调用能力）配置管理接口。
 * <p>
 * 路径前缀 {@code /api/tools}。菜单权限：管理员或 {@code AGENT_TOOL}。
 * 支持列表、创建、更新与启停；写操作记审计。编码创建后不可改。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/tools")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AGENT_TOOL')")
public class ToolController {
    private final ManagedToolRepository repository; private final AuditService audit;
    /**
     * @param repository 工具仓储
     * @param audit      审计服务
     */
    public ToolController(ManagedToolRepository repository,AuditService audit){this.repository=repository;this.audit=audit;}
    /**
     * 列出全部托管工具。
     *
     * @return 工具实体列表
     */
    @GetMapping public List<ManagedTool> list(){return repository.findAll();}
    /**
     * 创建工具。编码冲突返回 409。
     *
     * @param request 创建内容（类型、风险等级、命令模板、超时等）
     * @return 201 + 工具实体，或 409 正文
     */
    @PostMapping public ResponseEntity<?> create(@Valid @RequestBody SaveToolRequest request){String code=request.getCode().trim().toUpperCase();if(repository.findByCode(code).isPresent())return ResponseEntity.status(409).body("Tool code already exists");String now=Instant.now().toString();ManagedTool tool=repository.save(build(UUID.randomUUID().toString(),code,now,now,request,true));audit.record("CREATE","TOOL",tool.getId(),tool.getCode());return ResponseEntity.created(URI.create("/api/tools/"+tool.getId())).body(tool);}
    /**
     * 更新工具配置（编码不可改）；不存在返回 404。
     *
     * @param id      工具主键
     * @param request 更新内容
     * @return 更新后的工具实体
     */
    @PutMapping("/{id}") public ResponseEntity<ManagedTool> update(@PathVariable String id,@Valid @RequestBody SaveToolRequest request){ManagedTool current=repository.findById(id).orElse(null);if(current==null)return ResponseEntity.notFound().build();ManagedTool tool=repository.update(build(id,current.getCode(),current.getCreatedAt(),Instant.now().toString(),request,current.isEnabled()));audit.record("UPDATE","TOOL",id,tool.getCode());return ResponseEntity.ok(tool);}
    /**
     * 启用或禁用工具，并写审计。
     *
     * @param id      工具主键
     * @param request 启用状态
     * @return 更新后的工具实体；不存在时 404
     */
    @PutMapping("/{id}/enabled") public ResponseEntity<ManagedTool> enabled(@PathVariable String id,@Valid @RequestBody AgentEnabledRequest request){if(repository.findById(id).isEmpty())return ResponseEntity.notFound().build();ManagedTool tool=repository.updateEnabled(id,request.getEnabled(),Instant.now().toString());audit.record(request.getEnabled()?"ENABLE":"DISABLE","TOOL",id,tool.getCode());return ResponseEntity.ok(tool);}
    /**
     * 由请求体组装 {@link ManagedTool}；超时默认 300 秒，enabled 空则用 fallback。
     *
     * @param id              主键
     * @param code            工具编码
     * @param created         创建时间
     * @param updated         更新时间
     * @param r               请求体
     * @param fallbackEnabled 请求未指定 enabled 时的默认值
     * @return 工具实体
     */
    private ManagedTool build(String id, String code, String created, String updated, SaveToolRequest r,
                              boolean fallbackEnabled) {
        return new ManagedTool(id, code, r.getName().trim(), r.getType(), r.getDescription(), r.getCommandTemplate(),
                r.getRiskLevel(), r.getTimeoutSeconds() == null ? 300 : r.getTimeoutSeconds(),
                r.getEnabled() == null ? fallbackEnabled : r.getEnabled(), created, updated);
    }
}
