package com.zhiwei.superemployee.app.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>仅扫描带 {@link Mapper} 注解的接口，避免把 {@code OpsTaskRepository} 等业务接口
 * 误注册成 Mapper Bean。</p>
 * @author yangdb
 */
@Configuration
@MapperScan(basePackages = "com.zhiwei.superemployee.app", annotationClass = Mapper.class)
public class MybatisPlusConfig {
}
