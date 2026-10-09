package com.zhiwei.superemployee.app.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏器。
 * <p>用于审批参数、工具结果等落库或展示前，遮蔽密码、Token 等常见凭据字段。</p>
 * @author yangdb
 */
@Component
public class SensitiveDataRedactor {
    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "apikey", "api_key", "authorization",
            "credential", "privatekey", "private_key");
    private static final Pattern TEXT_SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|api[-_]?key|authorization)\\s*([:=])\\s*([^,;\\s}]+)");

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper 用于尝试按 JSON 解析并递归脱敏
     */
    public SensitiveDataRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 脱敏 JSON 或纯文本中的常见凭据字段。
     * <p>若可解析为 JSON 则按字段名递归替换；否则用正则替换文本中的键值对。</p>
     *
     * @param value 原始字符串，允许为空
     * @return 脱敏后的字符串；入参为空时原样返回
     */
    public String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            redactNode(root);
            return objectMapper.writeValueAsString(root);
        }
        catch (Exception ignored) {
            return TEXT_SECRET.matcher(value).replaceAll("$1$2" + MASK);
        }
    }

    /**
     * 递归替换 JSON 树中的敏感字段值。
     *
     * @param node JSON 节点
     */
    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode) {
            ObjectNode object = (ObjectNode) node;
            for (Map.Entry<String, JsonNode> field : object.properties()) {
                if (isSensitive(field.getKey())) {
                    object.put(field.getKey(), MASK);
                }
                else {
                    redactNode(field.getValue());
                }
            }
        }
        else if (node instanceof ArrayNode) {
            node.forEach(this::redactNode);
        }
    }

    /**
     * 判断字段名是否属于敏感键。
     *
     * @param key JSON 字段名
     * @return {@code true} 表示应脱敏
     */
    private boolean isSensitive(String key) {
        String normalized = key == null ? "" : key.replace("-", "_").toLowerCase();
        return SENSITIVE_KEYS.contains(normalized) || SENSITIVE_KEYS.contains(normalized.replace("_", ""));
    }
}
