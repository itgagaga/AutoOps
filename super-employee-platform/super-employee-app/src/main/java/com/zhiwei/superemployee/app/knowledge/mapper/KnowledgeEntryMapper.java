package com.zhiwei.superemployee.app.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库条目 MyBatis Mapper。
 * <p>
 * 继承 {@link BaseMapper} 提供按主键增删改查与条件构造能力；
 * 复杂查询条件在 {@link com.zhiwei.superemployee.app.knowledge.KnowledgeRepository} 中通过 Wrapper 组装。
 * </p>
 * @author yangdb
 */
@Mapper
public interface KnowledgeEntryMapper extends BaseMapper<KnowledgeEntryEntity> {
}
