package com.zhiwei.superemployee.app.dict;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.dict.mapper.DictItemEntity;
import com.zhiwei.superemployee.app.dict.mapper.DictItemMapper;
import com.zhiwei.superemployee.app.dict.mapper.DictTypeEntity;
import com.zhiwei.superemployee.app.dict.mapper.DictTypeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据字典仓储。
 * <p>维护字典类型（{@code dict_type}）与字典项（{@code dict_item}），
 * 供下拉选项、状态标签等前端字典消费。</p>
 * @author yangdb
 */
@Repository
public class DictRepository {
    /** 字典类型表 Mapper。 */
    private final DictTypeMapper typeMapper;
    /** 字典项表 Mapper。 */
    private final DictItemMapper itemMapper;

    /**
     * 注入字典类型与字典项 Mapper。
     *
     * @param typeMapper 字典类型 Mapper
     * @param itemMapper 字典项 Mapper
     */
    public DictRepository(DictTypeMapper typeMapper, DictItemMapper itemMapper) {
        this.typeMapper = typeMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 查询全部字典类型，按排序号、类型编码升序。
     *
     * @return 字典类型列表
     */
    public List<DictType> findAllTypes() {
        return typeMapper.selectList(Wrappers.<DictTypeEntity>lambdaQuery()
                        .orderByAsc(DictTypeEntity::getSortOrder, DictTypeEntity::getTypeCode))
                .stream().map(this::toType).toList();
    }

    /**
     * 按主键查询字典类型。
     *
     * @param id 类型 ID
     * @return 类型；不存在则为 empty
     */
    public Optional<DictType> findTypeById(String id) {
        return Optional.ofNullable(typeMapper.selectById(id)).map(this::toType);
    }

    /**
     * 按类型编码查询（如 PRIORITY、INCIDENT_STATUS）。
     *
     * @param code 类型编码
     * @return 类型；不存在则为 empty
     */
    public Optional<DictType> findTypeByCode(String code) {
        return Optional.ofNullable(typeMapper.selectOne(Wrappers.<DictTypeEntity>lambdaQuery()
                        .eq(DictTypeEntity::getTypeCode, code)))
                .map(this::toType);
    }

    /**
     * 新增字典类型。
     *
     * @param type 待插入类型（含已分配 ID）
     * @return 原样返回入参
     */
    public DictType saveType(DictType type) {
        typeMapper.insert(toTypeEntity(type));
        return type;
    }

    /**
     * 更新字典类型展示与启用字段；不修改 type_code、built_in。
     *
     * @param type 含最新配置的类型对象
     * @return 原样返回入参
     */
    public DictType updateType(DictType type) {
        typeMapper.update(null, Wrappers.<DictTypeEntity>lambdaUpdate()
                .set(DictTypeEntity::getTypeName, type.getName())
                .set(DictTypeEntity::getDescription, type.getDescription())
                .set(DictTypeEntity::getEnabled, type.isEnabled() ? 1 : 0)
                .set(DictTypeEntity::getSortOrder, type.getSortOrder())
                .set(DictTypeEntity::getUpdatedAt, type.getUpdatedAt())
                .eq(DictTypeEntity::getId, type.getId()));
        return type;
    }

    /**
     * 删除非内置字典类型，并级联删除其下全部字典项。
     *
     * @param id 类型 ID
     * @return true 表示类型删除成功（内置类型不会被删）
     */
    public boolean deleteType(String id) {
        itemMapper.delete(Wrappers.<DictItemEntity>lambdaQuery().eq(DictItemEntity::getTypeId, id));
        return typeMapper.delete(Wrappers.<DictTypeEntity>lambdaQuery()
                .eq(DictTypeEntity::getId, id)
                .eq(DictTypeEntity::getBuiltIn, 0)) > 0;
    }

    /**
     * 按类型 ID 查询全部字典项，按排序号、项编码升序。
     *
     * @param typeId 类型 ID
     * @return 字典项列表
     */
    public List<DictItem> findItemsByTypeId(String typeId) {
        return itemMapper.selectList(Wrappers.<DictItemEntity>lambdaQuery()
                        .eq(DictItemEntity::getTypeId, typeId)
                        .orderByAsc(DictItemEntity::getSortOrder, DictItemEntity::getItemCode))
                .stream().map(this::toItem).toList();
    }

    /**
     * 按类型编码查询已启用字典项（JOIN 类型表，供前端按码表拉选项）。
     *
     * @param typeCode 类型编码
     * @return 已启用字典项列表
     */
    public List<DictItem> findEnabledItemsByTypeCode(String typeCode) {
        return itemMapper.selectEnabledByTypeCode(typeCode).stream().map(this::toItem).toList();
    }

    /**
     * 按主键查询字典项。
     *
     * @param id 字典项 ID
     * @return 字典项；不存在则为 empty
     */
    public Optional<DictItem> findItemById(String id) {
        return Optional.ofNullable(itemMapper.selectById(id)).map(this::toItem);
    }

    /**
     * 按类型 ID + 项编码查询（同一类型下项编码唯一）。
     *
     * @param typeId 类型 ID
     * @param code   项编码
     * @return 字典项；不存在则为 empty
     */
    public Optional<DictItem> findItemByTypeAndCode(String typeId, String code) {
        return Optional.ofNullable(itemMapper.selectOne(Wrappers.<DictItemEntity>lambdaQuery()
                        .eq(DictItemEntity::getTypeId, typeId)
                        .eq(DictItemEntity::getItemCode, code)))
                .map(this::toItem);
    }

    /**
     * 新增字典项。
     *
     * @param item 待插入项（含已分配 ID）
     * @return 原样返回入参
     */
    public DictItem saveItem(DictItem item) {
        itemMapper.insert(toItemEntity(item));
        return item;
    }

    /**
     * 更新字典项标签、值、样式、排序与启用状态；不修改 type_id、item_code。
     *
     * @param item 含最新配置的项对象
     * @return 原样返回入参
     */
    public DictItem updateItem(DictItem item) {
        itemMapper.update(null, Wrappers.<DictItemEntity>lambdaUpdate()
                .set(DictItemEntity::getItemLabel, item.getLabel())
                .set(DictItemEntity::getItemValue, item.getValue())
                .set(DictItemEntity::getCssType, item.getCssType())
                .set(DictItemEntity::getSortOrder, item.getSortOrder())
                .set(DictItemEntity::getEnabled, item.isEnabled() ? 1 : 0)
                .set(DictItemEntity::getUpdatedAt, item.getUpdatedAt())
                .eq(DictItemEntity::getId, item.getId()));
        return item;
    }

    /**
     * 按主键删除字典项。
     *
     * @param id 字典项 ID
     * @return true 表示删除成功
     */
    public boolean deleteItem(String id) {
        return itemMapper.deleteById(id) > 0;
    }

    /** 字典类型领域对象转表实体。 */
    private DictTypeEntity toTypeEntity(DictType type) {
        DictTypeEntity entity = new DictTypeEntity();
        entity.setId(type.getId());
        entity.setTypeCode(type.getCode());
        entity.setTypeName(type.getName());
        entity.setDescription(type.getDescription());
        entity.setBuiltIn(type.isBuiltIn() ? 1 : 0);
        entity.setEnabled(type.isEnabled() ? 1 : 0);
        entity.setSortOrder(type.getSortOrder());
        entity.setCreatedAt(type.getCreatedAt());
        entity.setUpdatedAt(type.getUpdatedAt());
        return entity;
    }

    /** 字典项领域对象转表实体。 */
    private DictItemEntity toItemEntity(DictItem item) {
        DictItemEntity entity = new DictItemEntity();
        entity.setId(item.getId());
        entity.setTypeId(item.getTypeId());
        entity.setItemCode(item.getCode());
        entity.setItemLabel(item.getLabel());
        entity.setItemValue(item.getValue());
        entity.setCssType(item.getCssType());
        entity.setSortOrder(item.getSortOrder());
        entity.setEnabled(item.isEnabled() ? 1 : 0);
        entity.setCreatedAt(item.getCreatedAt());
        entity.setUpdatedAt(item.getUpdatedAt());
        return entity;
    }

    /** 字典类型表实体转领域对象。 */
    private DictType toType(DictTypeEntity e) {
        return new DictType(e.getId(), e.getTypeCode(), e.getTypeName(), e.getDescription(),
                e.getBuiltIn() != null && e.getBuiltIn() == 1,
                e.getEnabled() != null && e.getEnabled() == 1,
                e.getSortOrder() == null ? 0 : e.getSortOrder(), e.getCreatedAt(), e.getUpdatedAt());
    }

    /** 字典项表实体转领域对象。 */
    private DictItem toItem(DictItemEntity e) {
        return new DictItem(e.getId(), e.getTypeId(), e.getItemCode(), e.getItemLabel(), e.getItemValue(),
                e.getCssType(), e.getSortOrder() == null ? 0 : e.getSortOrder(),
                e.getEnabled() != null && e.getEnabled() == 1, e.getCreatedAt(), e.getUpdatedAt());
    }
}
