package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.dict.DictService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据字典管理与下拉选项接口。
 * <p>
 * 路径前缀 {@code /api/dicts}。类型/字典项 CRUD：管理员或 {@code SYS_DICT}；
 * {@code /options/**} 仅需已登录，供表单下拉使用。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/dicts")
public class DictController {
    private final DictService service;

    /**
     * @param service 字典应用服务
     */
    public DictController(DictService service) {
        this.service = service;
    }

    /**
     * 列出全部字典类型。
     *
     * @return 类型视图列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_DICT')")
    public List<Map<String, Object>> listTypes() {
        return service.listTypes();
    }

    /**
     * 创建字典类型；参数非法返回 400。
     *
     * @param request 类型编码、名称等
     * @return 201 + 创建结果
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_DICT')")
    public ResponseEntity<?> createType(@Valid @RequestBody SaveDictTypeRequest request) {
        try {
            Map<String, Object> created = service.createType(request.getCode(), request.getName(),
                    request.getDescription(), request.getSortOrder(), request.getEnabled());
            return ResponseEntity.created(URI.create("/api/dicts/" + created.get("id"))).body(created);
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 更新字典类型（编码一般不可改，由服务层约束）。
     *
     * @param id      类型主键
     * @param request 更新内容
     * @return 更新后视图
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_DICT')")
    public ResponseEntity<?> updateType(@PathVariable String id, @Valid @RequestBody SaveDictTypeRequest request) {
        try {
            return ResponseEntity.ok(service.updateType(id, request.getName(), request.getDescription(),
                    request.getSortOrder(), request.getEnabled()));
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 删除字典类型。不存在 404；仍有子项等冲突返回 400。
     *
     * @param id 类型主键
     * @return 204 或错误正文
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_DICT')")
    public ResponseEntity<?> deleteType(@PathVariable String id) {
        try {
            service.deleteType(id);
            return ResponseEntity.noContent().build();
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 按类型编码列出字典项（管理端，含禁用项）。
     *
     * @param typeCode 类型编码
     * @return 字典项列表
     */
    @GetMapping("/{typeCode}/items")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_DICT')")
    public ResponseEntity<?> listItems(@PathVariable String typeCode) {
        try {
            return ResponseEntity.ok(service.listItems(typeCode));
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 在指定类型下创建字典项。
     *
     * @param typeCode 类型编码
     * @param request  项编码、标签、值等
     * @return 201 + 创建结果
     */
    @PostMapping("/{typeCode}/items")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_DICT')")
    public ResponseEntity<?> createItem(@PathVariable String typeCode, @Valid @RequestBody SaveDictItemRequest request) {
        try {
            Map<String, Object> created = service.createItem(typeCode, request.getCode(), request.getLabel(),
                    request.getValue(), request.getCssType(), request.getSortOrder(), request.getEnabled());
            return ResponseEntity.created(URI.create("/api/dicts/items/" + created.get("id"))).body(created);
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 更新字典项展示字段与排序/启用状态。
     *
     * @param id      字典项主键
     * @param request 更新内容
     * @return 更新后视图
     */
    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_DICT')")
    public ResponseEntity<?> updateItem(@PathVariable String id, @Valid @RequestBody SaveDictItemRequest request) {
        try {
            return ResponseEntity.ok(service.updateItem(id, request.getLabel(), request.getValue(),
                    request.getCssType(), request.getSortOrder(), request.getEnabled()));
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 启用或禁用单个字典项。
     *
     * @param id      字典项主键
     * @param request 启用状态
     * @return 更新后视图；不存在时 404
     */
    @PutMapping("/items/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_DICT')")
    public ResponseEntity<?> setItemEnabled(@PathVariable String id, @Valid @RequestBody AgentEnabledRequest request) {
        try {
            return ResponseEntity.ok(service.setItemEnabled(id, Boolean.TRUE.equals(request.getEnabled())));
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除字典项；不存在返回 404。
     *
     * @param id 字典项主键
     * @return 204
     */
    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_DICT')")
    public ResponseEntity<?> deleteItem(@PathVariable String id) {
        try {
            service.deleteItem(id);
            return ResponseEntity.noContent().build();
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 下拉选项（仅启用项），登录用户可用。
     *
     * @param typeCode 类型编码
     * @return 选项列表
     */
    @GetMapping("/options/{typeCode}")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> options(@PathVariable String typeCode) {
        return service.options(typeCode);
    }

    /**
     * 批量拉取多个类型的下拉选项（逗号分隔 type 编码）。
     *
     * @param types 逗号分隔的类型编码
     * @return typeCode → 选项列表
     */
    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public Map<String, List<Map<String, Object>>> optionsBatch(@RequestParam("types") String types) {
        List<String> codes = Arrays.stream(types.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return service.optionsBatch(codes);
    }

    /**
     * 构造统一错误正文 {@code { "message": ... }}。
     *
     * @param value 错误文案
     * @return 消息 Map
     */
    private Map<String, String> message(String value) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", value);
        return body;
    }
}
