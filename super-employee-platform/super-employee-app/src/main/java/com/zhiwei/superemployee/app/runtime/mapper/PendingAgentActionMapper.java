package com.zhiwei.superemployee.app.runtime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待审批 Agent 动作表 Mapper。
 * @author yangdb
 */
@Mapper
public interface PendingAgentActionMapper extends BaseMapper<PendingAgentActionEntity> {
}
