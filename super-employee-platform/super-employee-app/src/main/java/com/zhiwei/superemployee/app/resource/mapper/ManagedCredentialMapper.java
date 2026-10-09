package com.zhiwei.superemployee.app.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 受管凭据表 {@code managed_credential} 的 MyBatis Mapper。
 * <p>
 * 继承 {@link BaseMapper} 提供按主键增删改查与条件构造能力；
 * 复杂查询条件在 {@link com.zhiwei.superemployee.app.resource.ResourceRepository} 中通过 Wrapper 组装。
 * 密文字段读写需配合 {@link com.zhiwei.superemployee.app.resource.CredentialSecretService} 加解密。
 * </p>
 * @author yangdb
 */
@Mapper
public interface ManagedCredentialMapper extends BaseMapper<ManagedCredentialEntity> {
}
