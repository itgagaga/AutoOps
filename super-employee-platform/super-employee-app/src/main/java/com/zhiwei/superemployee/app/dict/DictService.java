package com.zhiwei.superemployee.app.dict;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据字典应用服务。
 * <p>
 * 封装字典类型与字典项的增删改查、启停以及下拉选项组装，
 * 对编码做规范化（去空白并转大写），并将领域对象转换为前端可用的 Map 响应。
 * </p>
 * @author yangdb
 */
@Service
public class DictService {
    /** 字典仓储。 */
    private final DictRepository repository;

    /**
     * 构造字典服务。
     *
     * @param repository 字典仓储
     */
    public DictService(DictRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询全部字典类型，并转换为响应 Map 列表。
     *
     * @return 字典类型响应列表
     */
    public List<Map<String, Object>> listTypes() {
        return repository.findAllTypes().stream().map(this::typeResponse).toList();
    }

    /**
     * 新建字典类型。
     *
     * @param code        类型编码（将规范化为大写）
     * @param name        显示名称
     * @param description 说明，可为空
     * @param sortOrder   排序号；{@code null} 时默认 100
     * @param enabled     是否启用；{@code null} 时默认启用
     * @return 新建后的类型响应 Map
     * @throws IllegalArgumentException 当编码为空或编码已存在时抛出
     */
    public Map<String, Object> createType(String code, String name, String description, Integer sortOrder, Boolean enabled) {
        String normalized = normalizeCode(code);
        if (repository.findTypeByCode(normalized).isPresent()) {
            throw new IllegalArgumentException("字典类型编码已存在：" + normalized);
        }
        String now = Instant.now().toString();
        DictType type = new DictType(UUID.randomUUID().toString(), normalized, name.trim(),
                blankToNull(description), false, enabled == null || enabled,
                sortOrder == null ? 100 : sortOrder, now, now);
        return typeResponse(repository.saveType(type));
    }

    /**
     * 更新字典类型的名称、说明、排序与启用状态（不修改编码与内置标记）。
     *
     * @param id          类型 ID
     * @param name        新显示名称
     * @param description 新说明，可为空
     * @param sortOrder   新排序号；{@code null} 表示不修改
     * @param enabled     新启用状态；{@code null} 表示不修改
     * @return 更新后的类型响应 Map
     * @throws IllegalArgumentException 当类型不存在时抛出
     */
    public Map<String, Object> updateType(String id, String name, String description, Integer sortOrder, Boolean enabled) {
        DictType type = repository.findTypeById(id).orElseThrow(() -> new IllegalArgumentException("字典类型不存在"));
        type.setName(name.trim());
        type.setDescription(blankToNull(description));
        if (sortOrder != null) {
            type.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            type.setEnabled(enabled);
        }
        type.setUpdatedAt(Instant.now().toString());
        return typeResponse(repository.updateType(type));
    }

    /**
     * 删除非内置字典类型（仓储侧会级联删除其下字典项）。
     *
     * @param id 类型 ID
     * @throws IllegalArgumentException 当类型不存在时抛出
     * @throws IllegalStateException    当类型为内置或删除失败时抛出
     */
    public void deleteType(String id) {
        DictType type = repository.findTypeById(id).orElseThrow(() -> new IllegalArgumentException("字典类型不存在"));
        if (type.isBuiltIn()) {
            throw new IllegalStateException("内置字典类型不可删除：" + type.getCode());
        }
        if (!repository.deleteType(id)) {
            throw new IllegalStateException("删除字典类型失败");
        }
    }

    /**
     * 按类型编码列出该类型下全部字典项。
     *
     * @param typeCode 类型编码
     * @return 字典项响应列表
     * @throws IllegalArgumentException 当类型编码无效或类型不存在时抛出
     */
    public List<Map<String, Object>> listItems(String typeCode) {
        DictType type = repository.findTypeByCode(normalizeCode(typeCode))
                .orElseThrow(() -> new IllegalArgumentException("字典类型不存在：" + typeCode));
        return repository.findItemsByTypeId(type.getId()).stream().map(this::itemResponse).toList();
    }

    /**
     * 在指定类型下新建字典项。
     *
     * @param typeCode  类型编码
     * @param code      项编码（将规范化为大写）
     * @param label     展示标签
     * @param value     业务取值；空白时回退为项编码
     * @param cssType   样式类型，可为空
     * @param sortOrder 排序号；{@code null} 时默认 100
     * @param enabled   是否启用；{@code null} 时默认启用
     * @return 新建后的字典项响应 Map
     * @throws IllegalArgumentException 当类型不存在、编码为空或项编码已存在时抛出
     */
    public Map<String, Object> createItem(String typeCode, String code, String label, String value,
                                          String cssType, Integer sortOrder, Boolean enabled) {
        DictType type = repository.findTypeByCode(normalizeCode(typeCode))
                .orElseThrow(() -> new IllegalArgumentException("字典类型不存在：" + typeCode));
        String itemCode = normalizeCode(code);
        if (repository.findItemByTypeAndCode(type.getId(), itemCode).isPresent()) {
            throw new IllegalArgumentException("字典码值已存在：" + itemCode);
        }
        String now = Instant.now().toString();
        String itemValue = blankToNull(value) == null ? itemCode : value.trim();
        DictItem item = new DictItem(UUID.randomUUID().toString(), type.getId(), itemCode, label.trim(),
                itemValue, blankToNull(cssType), sortOrder == null ? 100 : sortOrder,
                enabled == null || enabled, now, now);
        return itemResponse(repository.saveItem(item));
    }

    /**
     * 更新字典项标签、取值、样式、排序与启用状态（不修改类型与项编码）。
     *
     * @param id        字典项 ID
     * @param label     新标签
     * @param value     新业务取值；空白时回退为项编码
     * @param cssType   新样式类型，可为空
     * @param sortOrder 新排序号；{@code null} 表示不修改
     * @param enabled   新启用状态；{@code null} 表示不修改
     * @return 更新后的字典项响应 Map
     * @throws IllegalArgumentException 当字典项不存在时抛出
     */
    public Map<String, Object> updateItem(String id, String label, String value, String cssType,
                                          Integer sortOrder, Boolean enabled) {
        DictItem item = repository.findItemById(id).orElseThrow(() -> new IllegalArgumentException("字典码值不存在"));
        item.setLabel(label.trim());
        item.setValue(blankToNull(value) == null ? item.getCode() : value.trim());
        item.setCssType(blankToNull(cssType));
        if (sortOrder != null) {
            item.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            item.setEnabled(enabled);
        }
        item.setUpdatedAt(Instant.now().toString());
        return itemResponse(repository.updateItem(item));
    }

    /**
     * 单独切换字典项启用状态。
     *
     * @param id      字典项 ID
     * @param enabled 目标启用状态
     * @return 更新后的字典项响应 Map
     * @throws IllegalArgumentException 当字典项不存在时抛出
     */
    public Map<String, Object> setItemEnabled(String id, boolean enabled) {
        DictItem item = repository.findItemById(id).orElseThrow(() -> new IllegalArgumentException("字典码值不存在"));
        item.setEnabled(enabled);
        item.setUpdatedAt(Instant.now().toString());
        return itemResponse(repository.updateItem(item));
    }

    /**
     * 按主键删除字典项。
     *
     * @param id 字典项 ID
     * @throws IllegalArgumentException 当字典项不存在（删除影响行数为 0）时抛出
     */
    public void deleteItem(String id) {
        if (!repository.deleteItem(id)) {
            throw new IllegalArgumentException("字典码值不存在");
        }
    }

    /**
     * 按类型编码获取下拉选项（仅返回已启用项的精简字段）。
     *
     * @param typeCode 类型编码
     * @return 选项列表（含 code、label、value、cssType）
     * @throws IllegalArgumentException 当类型编码为空时抛出
     */
    public List<Map<String, Object>> options(String typeCode) {
        return repository.findEnabledItemsByTypeCode(normalizeCode(typeCode)).stream()
                .map(this::optionResponse).toList();
    }

    /**
     * 批量按类型编码获取下拉选项。
     * <p>空白编码会被跳过；结果 Map 的 key 为规范化后的类型编码。</p>
     *
     * @param typeCodes 类型编码列表，可为 {@code null}
     * @return 类型编码 → 选项列表；入参为 {@code null} 时返回空 Map
     * @throws IllegalArgumentException 当某个非空编码规范化失败时抛出
     */
    public Map<String, List<Map<String, Object>>> optionsBatch(List<String> typeCodes) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (typeCodes == null) {
            return result;
        }
        for (String code : typeCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            String normalized = normalizeCode(code);
            result.put(normalized, options(normalized));
        }
        return result;
    }

    /**
     * 将字典类型转为 API 响应 Map。
     *
     * @param type 字典类型
     * @return 含 id、code、name 等字段的有序 Map
     */
    private Map<String, Object> typeResponse(DictType type) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", type.getId());
        map.put("code", type.getCode());
        map.put("name", type.getName());
        map.put("description", type.getDescription());
        map.put("builtIn", type.isBuiltIn());
        map.put("enabled", type.isEnabled());
        map.put("sortOrder", type.getSortOrder());
        map.put("createdAt", type.getCreatedAt());
        map.put("updatedAt", type.getUpdatedAt());
        return map;
    }

    /**
     * 将字典项转为完整 API 响应 Map。
     *
     * @param item 字典项
     * @return 含 id、typeId、code、label 等字段的有序 Map
     */
    private Map<String, Object> itemResponse(DictItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("typeId", item.getTypeId());
        map.put("code", item.getCode());
        map.put("label", item.getLabel());
        map.put("value", item.getValue() == null || item.getValue().isBlank() ? item.getCode() : item.getValue());
        map.put("cssType", item.getCssType());
        map.put("sortOrder", item.getSortOrder());
        map.put("enabled", item.isEnabled());
        map.put("createdAt", item.getCreatedAt());
        map.put("updatedAt", item.getUpdatedAt());
        return map;
    }

    /**
     * 将字典项转为下拉选项精简 Map。
     *
     * @param item 字典项
     * @return 含 code、label、value、cssType 的有序 Map
     */
    private Map<String, Object> optionResponse(DictItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", item.getCode());
        map.put("label", item.getLabel());
        map.put("value", item.getValue() == null || item.getValue().isBlank() ? item.getCode() : item.getValue());
        map.put("cssType", item.getCssType());
        return map;
    }

    /**
     * 规范化编码：去首尾空白并转大写。
     *
     * @param code 原始编码
     * @return 规范化后的编码
     * @throws IllegalArgumentException 当编码为 {@code null} 或空白时抛出
     */
    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("编码不能为空");
        }
        return code.trim().toUpperCase();
    }

    /**
     * 将空白字符串转为 {@code null}，非空白则 trim。
     *
     * @param value 原始字符串
     * @return trim 后的字符串，或 {@code null}
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
