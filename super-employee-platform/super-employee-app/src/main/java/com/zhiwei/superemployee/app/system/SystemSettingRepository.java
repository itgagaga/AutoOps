package com.zhiwei.superemployee.app.system;

import com.zhiwei.superemployee.app.system.mapper.SystemSettingEntity;
import com.zhiwei.superemployee.app.system.mapper.SystemSettingMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 系统键值配置仓储。
 * <p>访问 {@code app_system_setting}，用于工作区路径等全局配置的读写。</p>
 * @author yangdb
 */
@Repository
public class SystemSettingRepository {
    /** 系统配置表 Mapper。 */
    private final SystemSettingMapper mapper;

    /**
     * 注入系统配置 Mapper。
     *
     * @param mapper {@code app_system_setting} 表 Mapper
     */
    public SystemSettingRepository(SystemSettingMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按配置键读取配置值。
     *
     * @param key 配置键（主键）
     * @return 配置值；键不存在则为 empty
     */
    public Optional<String> find(String key) {
        return Optional.ofNullable(mapper.selectById(key)).map(SystemSettingEntity::getSettingValue);
    }

    /**
     * 按键更新配置；若键不存在则插入新行（upsert）。
     *
     * @param key       配置键
     * @param value     配置值
     * @param updatedAt 更新时间（ISO-8601 字符串）
     */
    public void upsert(String key, String value, String updatedAt) {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setSettingKey(key);
        entity.setSettingValue(value);
        entity.setUpdatedAt(updatedAt);
        if (mapper.updateById(entity) == 0) {
            mapper.insert(entity);
        }
    }
}
