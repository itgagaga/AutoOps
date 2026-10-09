package com.zhiwei.superemployee.app.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统键值配置 MyBatis Mapper。
 * <p>映射表 {@code app_system_setting}，继承 {@link BaseMapper} 提供标准 CRUD，无额外自定义 SQL。</p>
 * @author yangdb
 */
@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSettingEntity> {
}
