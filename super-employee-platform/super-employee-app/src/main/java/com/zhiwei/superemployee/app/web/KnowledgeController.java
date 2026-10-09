package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.knowledge.KnowledgeDraftGenerationService;
import com.zhiwei.superemployee.app.knowledge.KnowledgeDraftGenerationService.KnowledgeGenerateResult;
import com.zhiwei.superemployee.app.knowledge.KnowledgeEntry;
import com.zhiwei.superemployee.app.knowledge.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 运维知识库 HTTP 接口。
 * <p>
 * 需管理员或具备 {@code OPS_KNOWLEDGE} 菜单权限。列表/改删在服务层按创建人隔离；
 * {@link #search} 仍可读全部启用条目，供 Agent 与运维检索共享。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/knowledge")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('OPS_KNOWLEDGE')")
public class KnowledgeController {
    private final KnowledgeService service;
    private final KnowledgeDraftGenerationService draftGenerationService;

    /**
     * @param service                 知识库应用服务
     * @param draftGenerationService  文档/描述预生成服务
     */
    public KnowledgeController(KnowledgeService service, KnowledgeDraftGenerationService draftGenerationService) {
        this.service = service;
        this.draftGenerationService = draftGenerationService;
    }

    /**
     * 管理列表：返回当前用户可见的知识条目。
     *
     * @return 知识列表
     */
    @GetMapping
    public List<KnowledgeEntry> list() {
        return service.list();
    }

    /**
     * 关键词检索启用中的知识条目（不按创建人隔离）。
     *
     * @param q     检索词
     * @param scope 可选范围过滤
     * @param limit 返回条数，默认 5
     * @return 匹配结果
     */
    @GetMapping("/search")
    public List<KnowledgeEntry> search(@RequestParam String q,
                                       @RequestParam(required = false) String scope,
                                       @RequestParam(defaultValue = "5") int limit) {
        return service.search(q, scope, limit);
    }

    /**
     * 根据上传文档与/或描述预生成知识草稿（不落库），供前端填入表单后人工确认保存。
     *
     * @param file        可选文档（txt/md/docx/log/json 等）
     * @param description 可选描述
     * @param category    偏好分类
     * @param scope       偏好范围
     * @return 草稿预览
     */
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeGenerateResponse generate(@RequestPart(value = "file", required = false) MultipartFile file,
                                              @RequestParam(value = "description", required = false) String description,
                                              @RequestParam(value = "category", required = false) String category,
                                              @RequestParam(value = "scope", required = false) String scope) {
        KnowledgeGenerateResult result = draftGenerationService.generate(file, description, category, scope);
        return KnowledgeGenerateResponse.from(result);
    }

    /**
     * 新建知识条目，创建人取当前登录用户。
     *
     * @param r 保存请求体
     * @return 新建后的条目
     */
    @PostMapping
    public KnowledgeEntry create(@Valid @RequestBody SaveKnowledgeRequest r) {
        return save(null, r);
    }

    /**
     * 更新指定知识条目；非所有者且非管理员将得到 404。
     *
     * @param id 条目主键
     * @param r  保存请求体
     * @return 更新后的条目
     */
    @PutMapping("/{id}")
    public KnowledgeEntry update(@PathVariable String id, @Valid @RequestBody SaveKnowledgeRequest r) {
        return save(id, r);
    }

    /**
     * 删除指定知识条目；非所有者且非管理员将得到 404。
     *
     * @param id 条目主键
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    /**
     * 将请求 DTO 映射到服务层保存逻辑。
     *
     * @param id 新建为 {@code null}，更新为已有主键
     * @param r  请求体
     * @return 持久化结果
     */
    private KnowledgeEntry save(String id, SaveKnowledgeRequest r) {
        return service.save(id, r.getTitle(), r.getCategory(), r.getTags(), r.getScope(), r.getContent(), r.isEnabled());
    }
}
