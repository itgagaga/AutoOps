package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.auth.AppMenu;
import com.zhiwei.superemployee.app.auth.AppMenuRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 系统菜单管理接口。
 * <p>
 * 路径前缀 {@code /api/menus}。菜单权限：管理员或 {@code SYS_MENU}。
 * 支持树形父子关系；编码唯一；不可将自身设为父菜单。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/menus")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_MENU')")
public class MenuController {
    private final AppMenuRepository repository;

    /**
     * @param repository 菜单仓储
     */
    public MenuController(AppMenuRepository repository) {
        this.repository = repository;
    }

    /**
     * 列出全部系统菜单（含禁用项，供管理端编辑）。
     *
     * @return 菜单实体列表
     */
    @GetMapping
    public List<AppMenu> list() {
        return repository.findAll();
    }

    /**
     * 创建菜单。编码冲突返回 409；父菜单不存在返回 400。
     *
     * @param request 创建内容（编码、名称、路径、图标、排序、父级等）
     * @return 201 + 菜单实体，或错误正文
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody SaveMenuRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.findByCode(code).isPresent()) {
            return ResponseEntity.status(409).body(message("Menu code already exists"));
        }
        String parentId = normalizeParent(request.getParentId());
        if (parentId != null && !repository.findById(parentId).isPresent()) {
            return ResponseEntity.badRequest().body(message("Parent menu does not exist"));
        }
        AppMenu menu = repository.save(build(UUID.randomUUID().toString(), parentId, code, request, true));
        return ResponseEntity.created(URI.create("/api/menus/" + menu.getId())).body(menu);
    }

    /**
     * 更新菜单配置（编码不可改）。自身为父、父级不存在返回 400；不存在返回 404。
     *
     * @param id      菜单主键
     * @param request 更新内容
     * @return 更新后的菜单实体
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") String id, @Valid @RequestBody SaveMenuRequest request) {
        AppMenu current = repository.findById(id).orElse(null);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        String parentId = normalizeParent(request.getParentId());
        if (id.equals(parentId)) {
            return ResponseEntity.badRequest().body(message("Menu cannot be its own parent"));
        }
        if (parentId != null && !repository.findById(parentId).isPresent()) {
            return ResponseEntity.badRequest().body(message("Parent menu does not exist"));
        }
        return ResponseEntity.ok(repository.update(build(id, parentId, current.getCode(), request, current.isEnabled())));
    }

    /**
     * 由请求体组装 {@link AppMenu}；enabled 为空时沿用 fallback。
     *
     * @param id              主键
     * @param parentId        父菜单主键，可为 null
     * @param code            菜单编码（已规范化）
     * @param request         请求体
     * @param fallbackEnabled 请求未指定 enabled 时的默认值
     * @return 菜单实体
     */
    private AppMenu build(String id, String parentId, String code, SaveMenuRequest request, boolean fallbackEnabled) {
        boolean enabled = request.getEnabled() == null ? fallbackEnabled : request.getEnabled();
        return new AppMenu(id, parentId, code, request.getName().trim(), request.getPath(), request.getIcon(),
                request.getSortOrder() == null ? 0 : request.getSortOrder(), enabled);
    }

    /**
     * 将空白父级 ID 规范为 null（表示顶级菜单）。
     *
     * @param parentId 原始父级 ID
     * @return 规范化后的父级 ID 或 null
     */
    private String normalizeParent(String parentId) {
        return parentId == null || parentId.trim().isEmpty() ? null : parentId;
    }

    /**
     * 构造统一错误正文 {@code { "message": ... }}。
     *
     * @param value 错误文案
     * @return 消息 Map
     */
    private Map<String, String> message(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", value);
        return result;
    }
}
