package com.zhiwei.superemployee.app.runtime;

/**
 * 历史 Maven 工具网关入口；实际路由已迁移至 {@link RuntimeProfileToolGateway}。
 * <p>保留类名避免外部文档引用失效；不再注册为 Spring Bean。</p>
 * @author yangdb
 */
@Deprecated
public final class RuntimeMavenToolGateway {
    private RuntimeMavenToolGateway() {
    }
}
