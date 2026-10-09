package com.zhiwei.superemployee.app.runtime.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AgentScope 检查点持久化 Mapper。
 * @author yangdb
 */
@Mapper
public interface AgentscopeCheckpointMapper {
    int deleteByKey(@Param("userId") String userId, @Param("sessionId") String sessionId,
                    @Param("stateKey") String stateKey);

    int deleteBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int insert(@Param("userId") String userId, @Param("sessionId") String sessionId,
               @Param("stateKey") String stateKey, @Param("itemIndex") int itemIndex,
               @Param("stateData") String stateData, @Param("updatedAt") String updatedAt);

    String selectFirstData(@Param("userId") String userId, @Param("sessionId") String sessionId,
                           @Param("stateKey") String stateKey);

    List<String> selectDataList(@Param("userId") String userId, @Param("sessionId") String sessionId,
                                @Param("stateKey") String stateKey);

    int countBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    List<String> selectDistinctSessionIds(@Param("userId") String userId);
}
