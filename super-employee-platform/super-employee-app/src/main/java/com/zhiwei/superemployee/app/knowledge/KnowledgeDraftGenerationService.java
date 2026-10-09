package com.zhiwei.superemployee.app.knowledge;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 根据上传文档与/或用户描述，预生成知识库草稿（不落库）。
 * <p>优先调用 AgentScope/DashScope 结构化生成；模型不可用或失败时回退为本地模板整理。</p>
 * @author yangdb
 */
@Service
public class KnowledgeDraftGenerationService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDraftGenerationService.class);
    private static final Set<String> CATEGORIES = Set.of("RUNBOOK", "INCIDENT", "CONFIG");
    private static final Set<String> SCOPES = Set.of("OPS", "GLOBAL");

    private final KnowledgeDocumentTextExtractor extractor;
    private final ObjectProvider<Model> modelProvider;

    public KnowledgeDraftGenerationService(KnowledgeDocumentTextExtractor extractor,
                                           ObjectProvider<Model> modelProvider) {
        this.extractor = extractor;
        this.modelProvider = modelProvider;
    }

    /**
     * 生成知识草稿。
     *
     * @param file        可选文档
     * @param description 可选描述/补充说明
     * @param category    期望分类
     * @param scope       期望范围
     * @return 预览草稿（未保存）
     */
    public KnowledgeGenerateResult generate(MultipartFile file, String description, String category, String scope) {
        String desc = description == null ? "" : description.trim();
        KnowledgeDocumentTextExtractor.ExtractedText doc = extractor.extract(file);
        if (!doc.hasText() && desc.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传文档或填写描述后再预生成");
        }
        String preferredCategory = normalizeCategory(category);
        String preferredScope = normalizeScope(scope);

        Model model = modelProvider.getIfAvailable();
        if (model != null) {
            try {
                KnowledgeDraftOutput output = callModel(model, doc, desc, preferredCategory, preferredScope);
                return toResult(output, preferredCategory, preferredScope, doc, "AI");
            } catch (Exception ex) {
                log.warn("知识库 AI 预生成失败，回退本地整理：{}", ex.getMessage());
            }
        }
        return heuristic(doc, desc, preferredCategory, preferredScope);
    }

    private KnowledgeDraftOutput callModel(Model model,
                                           KnowledgeDocumentTextExtractor.ExtractedText doc,
                                           String description,
                                           String category,
                                           String scope) {
        ReActAgent agent = ReActAgent.builder()
                .name("knowledge-draft-generator")
                .sysPrompt("你是运维知识库编辑助手。请把材料整理成可检索的运维知识条目，只返回结构化结果，不要编造未给出的主机/密钥/生产口令。")
                .model(model)
                .build();
        String prompt = """
                请根据下列材料生成一条运维知识库草稿。
                要求：
                1. title：简洁中文标题，含系统或故障关键词；
                2. category：RUNBOOK / INCIDENT / CONFIG 之一，优先使用偏好分类 %s；
                3. tags：3-8 个中文或英文关键词，逗号分隔；
                4. scope：OPS 或 GLOBAL，优先使用偏好范围 %s；
                5. content：使用 Markdown，按「现象 / 判断依据 / 处置步骤 / 验证方式 / 回滚方案」组织，可保留原文关键命令与路径。
                
                【用户描述】
                %s
                
                【文档文件名】
                %s
                
                【文档正文】
                %s
                """.formatted(
                category,
                scope,
                description.isBlank() ? "（无）" : description,
                doc.fileName().isBlank() ? "（无）" : doc.fileName(),
                doc.hasText() ? doc.text() : "（无）"
        );
        RuntimeContext context = RuntimeContext.builder()
                .sessionId("knowledge-generate-" + UUID.randomUUID())
                .build();
        Msg response = agent.call(List.of(new UserMessage(prompt)), KnowledgeDraftOutput.class, context).block();
        if (response == null) {
            throw new IllegalStateException("模型未返回响应");
        }
        KnowledgeDraftOutput output = response.getStructuredData(KnowledgeDraftOutput.class);
        if (output == null) {
            throw new IllegalStateException("模型未返回结构化数据");
        }
        return output;
    }

    private KnowledgeGenerateResult heuristic(KnowledgeDocumentTextExtractor.ExtractedText doc,
                                              String description,
                                              String category,
                                              String scope) {
        String title = firstNonBlank(
                firstLine(description),
                firstLine(doc.text()),
                stripExtension(doc.fileName()),
                "未命名知识条目"
        );
        if (title.length() > 80) {
            title = title.substring(0, 80);
        }
        StringBuilder content = new StringBuilder();
        content.append("## 现象\n");
        content.append(description.isBlank() ? "（请补充现象说明）\n\n" : description + "\n\n");
        content.append("## 判断依据\n");
        content.append("结合日志关键词、报错栈与变更窗口判断是否命中本知识。\n\n");
        content.append("## 处置步骤\n");
        if (doc.hasText()) {
            content.append("以下内容来自导入文档").append(doc.fileName().isBlank() ? "" : "（" + doc.fileName() + "）")
                    .append("，请核对后精简：\n\n");
            content.append(doc.text()).append("\n\n");
        } else {
            content.append("1. 确认影响范围\n2. 按描述执行处置\n3. 记录操作结果\n\n");
        }
        content.append("## 验证方式\n");
        content.append("- 相关接口/页面恢复正常\n- 错误日志不再持续增长\n\n");
        content.append("## 回滚方案\n");
        content.append("- 若变更未生效则回退配置/版本并复检\n");
        if (doc.truncated()) {
            content.append("\n> 注意：源文档较长，正文已截断，请对照原文件补全关键段落。\n");
        }
        KnowledgeDraftOutput output = new KnowledgeDraftOutput();
        output.title = title;
        output.category = category;
        output.tags = guessTags(title + " " + description + " " + doc.text());
        output.scope = scope;
        output.content = content.toString().trim();
        return toResult(output, category, scope, doc, "HEURISTIC");
    }

    private KnowledgeGenerateResult toResult(KnowledgeDraftOutput output,
                                             String preferredCategory,
                                             String preferredScope,
                                             KnowledgeDocumentTextExtractor.ExtractedText doc,
                                             String mode) {
        String title = firstNonBlank(output.title, "未命名知识条目");
        String content = firstNonBlank(output.content, "（模型未返回正文，请手工补充）");
        String category = normalizeCategory(firstNonBlank(output.category, preferredCategory));
        String scope = normalizeScope(firstNonBlank(output.scope, preferredScope));
        String tags = firstNonBlank(output.tags, guessTags(title + " " + content));
        return new KnowledgeGenerateResult(
                title.trim(),
                category,
                tags.trim(),
                scope,
                content.trim(),
                true,
                doc.fileName(),
                doc.truncated(),
                mode
        );
    }

    private static String normalizeCategory(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(v) ? v : "RUNBOOK";
    }

    private static String normalizeScope(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return SCOPES.contains(v) ? v : "OPS";
    }

    private static String guessTags(String source) {
        String text = source == null ? "" : source.toLowerCase(Locale.ROOT);
        StringBuilder tags = new StringBuilder();
        String[] candidates = {"redis", "nginx", "mysql", "tomcat", "jvm", "cpu", "内存", "超时", "连接池",
                "验证码", "登录", "部署", "日志", "ssh", "docker", "maven"};
        for (String c : candidates) {
            if (text.contains(c.toLowerCase(Locale.ROOT))) {
                if (!tags.isEmpty()) {
                    tags.append(',');
                }
                tags.append(c);
            }
            if (tags.length() > 40) {
                break;
            }
        }
        return tags.isEmpty() ? "运维,知识库" : tags.toString();
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String line = text.lines().map(String::trim).filter(s -> !s.isBlank()).findFirst().orElse("");
        return line.replaceAll("^#+\\s*", "").replaceAll("^[-*]\\s*", "");
    }

    private static String stripExtension(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /** 模型结构化输出。 */
    public static final class KnowledgeDraftOutput {
        public String title;
        public String category;
        public String tags;
        public String scope;
        public String content;
    }

    /**
     * 预生成结果（未落库）。
     */
    public record KnowledgeGenerateResult(
            String title,
            String category,
            String tags,
            String scope,
            String content,
            boolean enabled,
            String sourceFileName,
            boolean truncated,
            String generationMode
    ) {
    }
}
