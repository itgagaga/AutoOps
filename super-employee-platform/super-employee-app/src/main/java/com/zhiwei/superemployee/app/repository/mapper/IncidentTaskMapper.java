package com.zhiwei.superemployee.app.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 故障任务 MyBatis Mapper。
 * <p>映射表 {@code incident_task}，继承 {@link BaseMapper} 提供标准 CRUD；
 * 复杂查询条件在 {@link com.zhiwei.superemployee.app.repository.MybatisIncidentRepository} 中通过 Wrapper 组装。</p>
 * @author yangdb
 */
@Mapper
public interface IncidentTaskMapper extends BaseMapper<IncidentTaskEntity> {
    /** 在事务内锁定任务行，串行化并发 Agent 的整行 JSON 更新。 */
    @Select("SELECT * FROM incident_task WHERE id = #{id} FOR UPDATE")
    IncidentTaskEntity selectByIdForUpdate(@Param("id") String id);
}
