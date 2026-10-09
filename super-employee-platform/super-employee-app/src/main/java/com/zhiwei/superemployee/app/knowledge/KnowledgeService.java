package com.zhiwei.superemployee.app.knowledge;

import com.zhiwei.superemployee.app.security.OpsDataPermissionService;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.domain.AgentStepRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.Collection;

/**
 * 知识库应用服务。
 * <p>
 * 职责拆分：
 * </p>
 * <ul>
 *   <li>管理 CRUD：按创建人隔离，普通用户只能维护自己的条目，管理员可维护全部；</li>
 *   <li>检索 {@link #search}：供 Agent/运维共享只读召回全部启用条目，不按创建人过滤；</li>
 *   <li>故障闭环沉淀 {@link #recordIncidentResolution}：写入时继承故障创建人。</li>
 * </ul>
 * @author yangdb
 */
@Service
public class KnowledgeService {
    /** 知识库仓储，负责领域模型与持久化实体的转换及 CRUD。 */
    private final KnowledgeRepository repository;
    /** 运维数据权限服务，提供当前用户名与管理员判定（本服务复用其认证上下文）。 */
    private final OpsDataPermissionService permission;

    /**
     * 构造知识库应用服务。
     *
     * @param repository 知识仓储
     * @param permission 当前用户身份与管理员判定（复用运维权限服务的认证上下文）
     */
    public KnowledgeService(KnowledgeRepository repository, OpsDataPermissionService permission) {
        this.repository = repository;
        this.permission = permission;
    }

    /**
     * 管理列表：管理员看全部，普通用户只看自己创建的条目。
     *
     * @return 当前主体可见的知识列表
     */
    public List<KnowledgeEntry> list() {
        return permission.isAdmin()
                ? repository.findAll()
                : repository.findAllByCreatedBy(permission.currentUsername());
    }

    /**
     * 新建或更新知识条目。
     * <p>
     * {@code id == null} 表示新建，{@code createdBy} 取当前登录用户；
     * 更新时必须通过 {@link #requireOwned}，且保留原创建人与命中次数。
     * </p>
     *
     * @param id       更新时的主键；新建传 {@code null}
     * @param title    标题，必填
     * @param category 分类，缺省 {@code RUNBOOK}
     * @param tags     标签
     * @param scope    范围，缺省 {@code GLOBAL}
     * @param content  正文，必填
     * @param enabled  是否启用
     * @return 持久化后的条目
     * @throws ResponseStatusException 更新他人条目时 HTTP 404
     * @throws IllegalArgumentException 标题或正文为空
     */
    public KnowledgeEntry save(String id, String title, String category, String tags, String scope,
                               String content, boolean enabled) {
        String now = Instant.now().toString();
        KnowledgeEntry old = id == null ? null : requireOwned(id);
        String owner = old == null ? permission.currentUsername() : old.createdBy();
        KnowledgeEntry entry = new KnowledgeEntry(
                old == null ? UUID.randomUUID().toString() : old.id(),
                required(title, "标题"), value(category, "RUNBOOK"), value(tags, ""), value(scope, "GLOBAL"),
                required(content, "知识内容"), old == null ? null : old.sourceIncidentId(), enabled,
                old == null ? 0 : old.hitCount(), owner, old == null ? now : old.createdAt(), now);
        return old == null ? repository.save(entry) : repository.update(entry);
    }

    /**
     * 删除知识条目；仅所有者或管理员可删，否则 404。
     *
     * @param id 主键
     * @throws ResponseStatusException 不存在或无权时 HTTP 404
     */
    public void delete(String id) {
        requireOwned(id);
        if (!repository.delete(id)) {
            throw notFound();
        }
    }

    /**
     * Agent/页面检索：共享只读，召回全部启用且匹配范围的条目，并累加命中次数。
     *
     * @param query 检索词，按空白/标点切分，长度≤1 的词忽略
     * @param scope 范围过滤；空或 GLOBAL 类条目始终可匹配
     * @param limit 返回条数上限，夹在 1～10
     * @return 按相关度与更新时间排序的结果
     */
    public List<KnowledgeEntry> search(String query, String scope, int limit) {
        List<String> terms = Arrays.stream(value(query, "").toLowerCase(Locale.ROOT).split("[\\s,，;；:/]+"))
                .filter(s -> s.length() > 1).distinct().toList();
        return repository.findAll().stream().filter(KnowledgeEntry::enabled)
                .filter(e -> scope == null || scope.isBlank() || "GLOBAL".equalsIgnoreCase(e.scope())
                        || scope.equalsIgnoreCase(e.scope()))
                .map(e -> new Scored(e, score(e, terms))).filter(x -> terms.isEmpty() || x.score > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(x -> x.entry.updatedAt(), Comparator.reverseOrder()))
                .limit(Math.max(1, Math.min(10, limit))).map(x -> touch(x.entry)).toList();
    }

    /**
     * 故障处置完成后自动沉淀经验知识；同一故障只沉淀一次。
     * <p>创建人优先取故障 {@code createdBy}，缺失时回退当前登录用户。</p>
     *
     * @param incident 已处置故障
     * @param summary  经验摘要；空则跳过
     */
    public void recordIncidentResolution(IncidentTask incident, String summary) {
        if (repository.findBySourceIncidentId(incident.getId()).isPresent() || !isReusableResolution(incident, summary)) {
            return;
        }
        String now = Instant.now().toString();
        String caseName = canonicalCaseName(incident.getTitle());
        String title = "故障案例：" + caseName;
        String tags = "自动沉淀,已验证," + incident.getPriority().name() + "," + caseName;
        String content = buildResolutionCase(incident, summary);
        String owner = incident.getCreatedBy() == null || incident.getCreatedBy().isBlank()
                ? permission.currentUsername() : incident.getCreatedBy();
        KnowledgeEntry similar = repository.findAll().stream()
                .filter(entry -> entry.sourceIncidentId() != null)
                .filter(entry -> Objects.equals(owner, entry.createdBy()))
                .filter(entry -> canonicalCaseName(entry.title()).equalsIgnoreCase(caseName))
                .findFirst().orElse(null);
        if (similar != null) {
            repository.update(new KnowledgeEntry(
                    similar.id(), title, "INCIDENT", tags, "OPS", content,
                    similar.sourceIncidentId(), true, similar.hitCount(), similar.createdBy(),
                    similar.createdAt(), now));
            return;
        }
        KnowledgeEntry entry = new KnowledgeEntry(
                UUID.randomUUID().toString(), title,
                "INCIDENT", tags, "OPS", content, incident.getId(), true, 0, owner, now, now);
        repository.save(entry);
    }

    /** 只沉淀已经形成处置或验证结论的异常，过滤正常巡检与过程通知。 */
    private boolean isReusableResolution(IncidentTask incident, String summary) {
        if (isProcessMessage(summary)) {
            return false;
        }
        String cleanSummary = cleanText(summary);
        Map<String, Object> context = incident.getContext();
        boolean verified = Boolean.TRUE.equals(context.get("verificationPassed"))
                || Boolean.TRUE.equals(context.get("manualDeploymentVerified"))
                || Boolean.TRUE.equals(context.get("automaticDeploymentSuccessful"));
        boolean repaired = hasValue(context.get("patchSummary"))
                || hasValue(context.get("patchTitle"))
                || context.get("codexChangedFiles") instanceof Collection<?> files && !files.isEmpty();
        // 合并/部署完结摘要往往较短；已验证或已修复时正文可由 context 组装，不再强制 ≥30 字
        if (!verified && !repaired && cleanSummary.length() < 30) {
            return false;
        }
        boolean normalInspection = cleanSummary.matches("(?s).*?(运行正常|未发现异常|未见异常|无异常|无需修复|无需处置|零\\s*ERROR|无\\s*ERROR).*?");
        if (!verified && !repaired && normalInspection) {
            return false;
        }
        boolean diagnosed = hasValue(context.get("rootCause")) || hasValue(context.get("topHypothesis"))
                || cleanSummary.matches("(?s).*?(根因|故障原因|修复|异常|ERROR|EXCEPTION|失败).*?");
        return verified || repaired || diagnosed;
    }

    private String buildResolutionCase(IncidentTask incident, String summary) {
        Map<String, Object> context = incident.getContext();
        String evidence = firstValue(context, "evidenceReference", "logQueryReason");
        if (evidence.isBlank()) {
            evidence = incident.getTimeline().stream()
                    .filter(step -> step.isSuccess() && step.getStep() != null
                            && (step.getStep().contains("日志") || step.getStep().contains("证据")))
                    .reduce((left, right) -> right).map(AgentStepRecord::getSummary).orElse("");
        }
        String rootCause = firstValue(context, "rootCause", "topHypothesis", "patchSummary");
        if (rootCause.isBlank()) rootCause = cleanText(summary);
        String action = joinNonBlank(
                label("修复标题", context.get("patchTitle")),
                label("修复说明", context.get("patchSummary")),
                label("变更文件", context.get("codexChangedFiles")));
        if (action.isBlank()) action = "本次未执行代码变更，按诊断结论完成处置。";
        String validation = validationSummary(context);
        return """
                ## 适用场景
                %s

                ## 故障现象
                %s

                ## 关键证据
                %s

                ## 根因判断
                %s

                ## 处置方案
                %s

                ## 验证结果
                %s

                ## 适用边界
                本案例仅作为相似故障的排查线索；执行修复前必须重新核验当前日志、代码版本和运行环境。
                """.formatted(
                truncate(cleanText(incident.getDescription()), 500),
                truncate(cleanText(summary), 1200),
                evidence.isBlank() ? "未保留可复用的原始证据摘要。" : truncate(cleanText(evidence), 1000),
                truncate(cleanText(rootCause), 1000), truncate(cleanText(action), 1000), validation);
    }

    private String validationSummary(Map<String, Object> context) {
        if (Boolean.TRUE.equals(context.get("automaticDeploymentSuccessful"))) return "自动部署及启动验证通过。";
        if (Boolean.TRUE.equals(context.get("manualDeploymentVerified"))) return "人工部署验证通过。";
        if (Boolean.TRUE.equals(context.get("verificationPassed"))) return "自动化验证通过。";
        return "已形成诊断或处置结论，尚无自动化验证记录；复用时需补充验证。";
    }

    private String canonicalCaseName(String title) {
        String value = cleanText(title)
                .replaceFirst("^(事件经验|故障案例)[:：]\\s*", "")
                .replaceAll("[【\\[]?定时巡检[】\\]]?", "")
                .replaceAll("\\s+", " ").trim();
        return value.isBlank() ? "未命名故障" : value;
    }

    private boolean isProcessMessage(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return text.contains("<system-notification>") || text.contains("background subagent task")
                || text.contains("task_id:") || text.contains("status: accepted");
    }

    private String cleanText(Object value) {
        if (value == null) return "";
        return String.valueOf(value)
                .replaceAll("(?is)<system-notification>.*?</system-notification>", "")
                .replaceAll("(?im)^\\s*(status|agent|task_id|objective)\\s*[:=].*$", "")
                .replaceAll("\\*\\*", "").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String firstValue(Map<String, Object> context, String... keys) {
        for (String key : keys) if (hasValue(context.get(key))) return String.valueOf(context.get(key));
        return "";
    }

    private boolean hasValue(Object value) { return value != null && !String.valueOf(value).isBlank(); }
    private String label(String name, Object value) { return hasValue(value) ? name + "：" + value : ""; }
    private String joinNonBlank(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).reduce((a, b) -> a + "\n" + b).orElse("");
    }
    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }

    /**
     * 加载条目并校验所有权：管理员或创建人本人可通过，否则伪装为不存在。
     *
     * @param id 主键
     * @return 有权操作的条目
     * @throws ResponseStatusException HTTP 404
     */
    private KnowledgeEntry requireOwned(String id) {
        KnowledgeEntry entry = repository.findById(id).orElseThrow(this::notFound);
        if (!permission.isAdmin() && !Objects.equals(permission.currentUsername(), entry.createdBy())) {
            throw notFound();
        }
        return entry;
    }

    /**
     * 检索命中后累加 hitCount 并刷新 updatedAt。
     *
     * @param e 原条目
     * @return 更新后的条目
     */
    private KnowledgeEntry touch(KnowledgeEntry e) {
        KnowledgeEntry x = new KnowledgeEntry(
                e.id(), e.title(), e.category(), e.tags(), e.scope(), e.content(),
                e.sourceIncidentId(), e.enabled(), e.hitCount() + 1, e.createdBy(), e.createdAt(),
                Instant.now().toString());
        return repository.update(x);
    }

    /**
     * 计算检索相关度：标题权重 5，标签 3，正文 1。
     *
     * @param e     候选条目
     * @param terms 检索词列表
     * @return 分值，无匹配为 0
     */
    private int score(KnowledgeEntry e, List<String> terms) {
        String title = value(e.title(), "").toLowerCase(Locale.ROOT);
        String tags = value(e.tags(), "").toLowerCase(Locale.ROOT);
        String content = value(e.content(), "").toLowerCase(Locale.ROOT);
        int score = 0;
        for (String t : terms) {
            if (title.contains(t)) {
                score += 5;
            }
            if (tags.contains(t)) {
                score += 3;
            }
            if (content.contains(t)) {
                score++;
            }
        }
        return score;
    }

    /**
     * @return 统一的“数据不存在”404
     */
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "数据不存在");
    }

    /**
     * 校验必填字符串。
     *
     * @param v    原始值
     * @param name 字段中文名，用于错误提示
     * @return trim 后的值
     */
    private String required(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return v.trim();
    }

    /**
     * 空值回退为默认值，非空则 trim。
     *
     * @param v        原始值
     * @param fallback 缺省值
     * @return 处理后的字符串
     */
    private String value(String v, String fallback) {
        return v == null ? fallback : v.trim();
    }

    /**
     * 检索排序用的中间结构：条目与相关度分。
     *
     * @param entry 知识条目
     * @param score 相关度分
     */
    private record Scored(KnowledgeEntry entry, int score) {
    }
}
