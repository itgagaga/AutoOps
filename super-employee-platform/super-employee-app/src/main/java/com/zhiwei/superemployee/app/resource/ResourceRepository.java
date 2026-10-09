package com.zhiwei.superemployee.app.resource;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.resource.mapper.ManagedApplicationEntity;
import com.zhiwei.superemployee.app.resource.mapper.ManagedApplicationMapper;
import com.zhiwei.superemployee.app.resource.mapper.ManagedCredentialEntity;
import com.zhiwei.superemployee.app.resource.mapper.ManagedCredentialMapper;
import com.zhiwei.superemployee.app.resource.mapper.ManagedDatabaseEntity;
import com.zhiwei.superemployee.app.resource.mapper.ManagedDatabaseMapper;
import com.zhiwei.superemployee.app.resource.mapper.ManagedHostEntity;
import com.zhiwei.superemployee.app.resource.mapper.ManagedHostMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 资源中心仓储。
 * <p>
 * 统一维护受管主机、应用、数据库实例与凭据四类资源的 CRUD，
 * 以及按表名切换启用状态；应用查询会 JOIN 主机名便于列表展示。
 * 持久化实体与领域对象在本类私有转换方法中互转。
 * </p>
 * @author yangdb
 */
@Repository
public class ResourceRepository {
    /** 允许切换启用状态的物理表名白名单。 */
    private static final Set<String> ENABLED_TABLES = Set.of(
            "managed_host", "managed_application", "managed_database", "managed_credential");

    /** 受管主机 Mapper。 */
    private final ManagedHostMapper hosts;
    /** 受管应用 Mapper。 */
    private final ManagedApplicationMapper applications;
    /** 受管数据库 Mapper。 */
    private final ManagedDatabaseMapper databases;
    /** 受管凭据 Mapper。 */
    private final ManagedCredentialMapper credentials;

    /**
     * 注入四类资源 Mapper 并组装仓储。
     *
     * @param hosts         主机 Mapper
     * @param applications  应用 Mapper
     * @param databases     数据库 Mapper
     * @param credentials   凭据 Mapper
     */
    public ResourceRepository(ManagedHostMapper hosts, ManagedApplicationMapper applications,
                              ManagedDatabaseMapper databases, ManagedCredentialMapper credentials) {
        this.hosts = hosts;
        this.applications = applications;
        this.databases = databases;
        this.credentials = credentials;
    }

    /**
     * 查询全部主机，按主机名、编码升序。
     *
     * @return 主机列表
     */
    public List<ManagedHost> findHosts() {
        return hosts.selectList(Wrappers.<ManagedHostEntity>lambdaQuery()
                        .orderByAsc(ManagedHostEntity::getHostName, ManagedHostEntity::getHostCode))
                .stream().map(this::toHost).toList();
    }

    /**
     * 按主键查询主机。
     *
     * @param id 主机 ID
     * @return 主机；不存在则为 empty
     */
    public Optional<ManagedHost> findHost(String id) {
        return Optional.ofNullable(hosts.selectById(id)).map(this::toHost);
    }

    /**
     * 判断主机编码是否已存在。
     *
     * @param code 主机编码
     * @return true 表示已存在
     */
    public boolean hostCodeExists(String code) {
        return hosts.selectCount(Wrappers.<ManagedHostEntity>lambdaQuery()
                .eq(ManagedHostEntity::getHostCode, code)) > 0;
    }

    /**
     * 新增主机。
     *
     * @param h 待插入主机（含已分配 ID）
     * @return 原样返回入参
     */
    public ManagedHost saveHost(ManagedHost h) {
        hosts.insert(toHostEntity(h));
        return h;
    }

    /**
     * 按主键全量更新主机后重新查询返回。
     *
     * @param h 最新主机快照
     * @return 库中最新主机
     */
    public ManagedHost updateHost(ManagedHost h) {
        hosts.updateById(toHostEntity(h));
        return findHost(h.getId()).orElseThrow();
    }

    /**
     * 查询全部应用（含关联主机名）。
     *
     * @return 应用列表
     */
    public List<ManagedApplication> findApplications() {
        return applications.selectAllWithHostName().stream().map(this::toApp).toList();
    }

    /**
     * 按主键查询应用（含关联主机名）。
     *
     * @param id 应用 ID
     * @return 应用；不存在则为 empty
     */
    public Optional<ManagedApplication> findApplication(String id) {
        return Optional.ofNullable(applications.selectByIdWithHostName(id)).map(this::toApp);
    }

    /**
     * 判断应用编码是否已存在。
     *
     * @param code 应用编码
     * @return true 表示已存在
     */
    public boolean applicationCodeExists(String code) {
        return applications.selectCount(Wrappers.<ManagedApplicationEntity>lambdaQuery()
                .eq(ManagedApplicationEntity::getAppCode, code)) > 0;
    }

    /**
     * 新增应用后按主键（含主机名）回查。
     *
     * @param a 待插入应用
     * @return 含主机名的最新快照
     */
    public ManagedApplication saveApplication(ManagedApplication a) {
        applications.insert(toAppEntity(a));
        return findApplication(a.getId()).orElseThrow();
    }

    /**
     * 更新应用后按主键（含主机名）回查。
     *
     * @param a 最新应用快照
     * @return 含主机名的最新快照
     */
    public ManagedApplication updateApplication(ManagedApplication a) {
        applications.updateById(toAppEntity(a));
        return findApplication(a.getId()).orElseThrow();
    }

    /**
     * 查询全部数据库实例，按实例名、编码升序。
     *
     * @return 数据库实例列表
     */
    public List<ManagedDatabase> findDatabases() {
        return databases.selectList(Wrappers.<ManagedDatabaseEntity>lambdaQuery()
                        .orderByAsc(ManagedDatabaseEntity::getInstanceName, ManagedDatabaseEntity::getInstanceCode))
                .stream().map(this::toDb).toList();
    }

    /**
     * 按主键查询数据库实例。
     *
     * @param id 实例 ID
     * @return 实例；不存在则为 empty
     */
    public Optional<ManagedDatabase> findDatabase(String id) {
        return Optional.ofNullable(databases.selectById(id)).map(this::toDb);
    }

    /**
     * 判断数据库实例编码是否已存在。
     *
     * @param code 实例编码
     * @return true 表示已存在
     */
    public boolean databaseCodeExists(String code) {
        return databases.selectCount(Wrappers.<ManagedDatabaseEntity>lambdaQuery()
                .eq(ManagedDatabaseEntity::getInstanceCode, code)) > 0;
    }

    /**
     * 新增数据库实例。
     *
     * @param d 待插入实例
     * @return 原样返回入参
     */
    public ManagedDatabase saveDatabase(ManagedDatabase d) {
        databases.insert(toDbEntity(d));
        return d;
    }

    /**
     * 更新数据库实例后回查。
     *
     * @param d 最新实例快照
     * @return 库中最新实例
     */
    public ManagedDatabase updateDatabase(ManagedDatabase d) {
        databases.updateById(toDbEntity(d));
        return findDatabase(d.getId()).orElseThrow();
    }

    /**
     * 查询全部凭据，按名称、编码升序。
     *
     * @return 凭据列表（含密文，仅供受控服务使用）
     */
    public List<ManagedCredential> findCredentials() {
        return credentials.selectList(Wrappers.<ManagedCredentialEntity>lambdaQuery()
                        .orderByAsc(ManagedCredentialEntity::getCredentialName, ManagedCredentialEntity::getCredentialCode))
                .stream().map(this::toCred).toList();
    }

    /**
     * 按主键查询凭据。
     *
     * @param id 凭据 ID
     * @return 凭据；不存在则为 empty
     */
    public Optional<ManagedCredential> findCredential(String id) {
        return Optional.ofNullable(credentials.selectById(id)).map(this::toCred);
    }

    /**
     * 判断凭据编码是否已存在。
     *
     * @param code 凭据编码
     * @return true 表示已存在
     */
    public boolean credentialCodeExists(String code) {
        return credentials.selectCount(Wrappers.<ManagedCredentialEntity>lambdaQuery()
                .eq(ManagedCredentialEntity::getCredentialCode, code)) > 0;
    }

    /**
     * 新增凭据。
     *
     * @param c 待插入凭据
     * @return 原样返回入参
     */
    public ManagedCredential saveCredential(ManagedCredential c) {
        credentials.insert(toCredEntity(c));
        return c;
    }

    /**
     * 更新凭据后回查。
     *
     * @param c 最新凭据快照
     * @return 库中最新凭据
     */
    public ManagedCredential updateCredential(ManagedCredential c) {
        credentials.updateById(toCredEntity(c));
        return findCredential(c.getId()).orElseThrow();
    }

    /**
     * 按资源表名切换启用状态（白名单表：host / application / database / credential）。
     *
     * @param table     物理表名，须在允许集合内
     * @param id        资源主键
     * @param enabled   true 启用，false 停用
     * @param updatedAt 更新时间字符串
     * @throws IllegalArgumentException 表名不在白名单时
     */
    public void setEnabled(String table, String id, boolean enabled, String updatedAt) {
        if (!ENABLED_TABLES.contains(table)) {
            throw new IllegalArgumentException("Unsupported table");
        }
        int flag = enabled ? 1 : 0;
        switch (table) {
            case "managed_host" -> hosts.update(null, Wrappers.<ManagedHostEntity>lambdaUpdate()
                    .set(ManagedHostEntity::getEnabled, flag).set(ManagedHostEntity::getUpdatedAt, updatedAt)
                    .eq(ManagedHostEntity::getId, id));
            case "managed_application" -> applications.update(null, Wrappers.<ManagedApplicationEntity>lambdaUpdate()
                    .set(ManagedApplicationEntity::getEnabled, flag).set(ManagedApplicationEntity::getUpdatedAt, updatedAt)
                    .eq(ManagedApplicationEntity::getId, id));
            case "managed_database" -> databases.update(null, Wrappers.<ManagedDatabaseEntity>lambdaUpdate()
                    .set(ManagedDatabaseEntity::getEnabled, flag).set(ManagedDatabaseEntity::getUpdatedAt, updatedAt)
                    .eq(ManagedDatabaseEntity::getId, id));
            case "managed_credential" -> credentials.update(null, Wrappers.<ManagedCredentialEntity>lambdaUpdate()
                    .set(ManagedCredentialEntity::getEnabled, flag).set(ManagedCredentialEntity::getUpdatedAt, updatedAt)
                    .eq(ManagedCredentialEntity::getId, id));
            default -> throw new IllegalArgumentException("Unsupported table");
        }
    }

    /**
     * 主机领域对象转表实体。
     *
     * @param h 主机领域对象
     * @return 可插入/更新的表实体
     */
    private ManagedHostEntity toHostEntity(ManagedHost h) {
        ManagedHostEntity e = new ManagedHostEntity();
        e.setId(h.getId());
        e.setHostCode(h.getCode());
        e.setHostName(h.getName());
        e.setIpAddress(h.getIpAddress());
        e.setSshPort(h.getSshPort());
        e.setOsType(h.getOsType());
        e.setEnvironment(h.getEnvironment());
        e.setOwnerName(h.getOwnerName());
        e.setRuntimeStatus(h.getStatus());
        e.setCredentialId(h.getCredentialId());
        e.setDescription(h.getDescription());
        e.setAllowedLogPaths(h.getAllowedLogPaths());
        e.setHostKeyFingerprint(h.getHostKeyFingerprint());
        e.setLastConnectivityAt(h.getLastConnectivityAt());
        e.setEnabled(h.isEnabled() ? 1 : 0);
        e.setCreatedAt(h.getCreatedAt());
        e.setUpdatedAt(h.getUpdatedAt());
        return e;
    }

    /**
     * 主机表实体转领域对象；SSH 端口为空时默认 22。
     *
     * @param e 主机表实体
     * @return 主机领域对象
     */
    private ManagedHost toHost(ManagedHostEntity e) {
        return new ManagedHost(e.getId(), e.getHostCode(), e.getHostName(), e.getIpAddress(),
                e.getSshPort() == null ? 22 : e.getSshPort(), e.getOsType(), e.getEnvironment(), e.getOwnerName(),
                e.getRuntimeStatus(), e.getCredentialId(), e.getDescription(), e.getAllowedLogPaths(),
                e.getHostKeyFingerprint(), e.getLastConnectivityAt(),
                e.getEnabled() != null && e.getEnabled() == 1, e.getCreatedAt(), e.getUpdatedAt());
    }

    /**
     * 应用领域对象转表实体（不含 hostName，该字段仅查询 JOIN）。
     *
     * @param a 应用领域对象
     * @return 可插入/更新的表实体
     */
    private ManagedApplicationEntity toAppEntity(ManagedApplication a) {
        ManagedApplicationEntity e = new ManagedApplicationEntity();
        e.setId(a.getId());
        e.setAppCode(a.getCode());
        e.setAppName(a.getName());
        e.setAppType(a.getType());
        e.setRepositoryUrl(a.getRepositoryUrl());
        e.setDeployPath(a.getDeployPath());
        e.setHostId(a.getHostId());
        e.setOwnerName(a.getOwnerName());
        e.setDescription(a.getDescription());
        e.setEnabled(a.isEnabled() ? 1 : 0);
        e.setCreatedAt(a.getCreatedAt());
        e.setUpdatedAt(a.getUpdatedAt());
        return e;
    }

    /**
     * 应用表实体转领域对象（含 JOIN 出的 hostName）。
     *
     * @param e 应用表实体
     * @return 应用领域对象
     */
    private ManagedApplication toApp(ManagedApplicationEntity e) {
        return new ManagedApplication(e.getId(), e.getAppCode(), e.getAppName(), e.getAppType(),
                e.getRepositoryUrl(), e.getDeployPath(), e.getHostId(), e.getHostName(), e.getOwnerName(),
                e.getDescription(), e.getEnabled() != null && e.getEnabled() == 1,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    /**
     * 数据库实例领域对象转表实体。
     *
     * @param d 数据库领域对象
     * @return 可插入/更新的表实体
     */
    private ManagedDatabaseEntity toDbEntity(ManagedDatabase d) {
        ManagedDatabaseEntity e = new ManagedDatabaseEntity();
        e.setId(d.getId());
        e.setInstanceCode(d.getCode());
        e.setInstanceName(d.getName());
        e.setDatabaseType(d.getType());
        e.setHostAddress(d.getHostAddress());
        e.setPortNumber(d.getPort());
        e.setDatabaseName(d.getDatabaseName());
        e.setSchemaName(d.getSchemaName());
        e.setEnvironment(d.getEnvironment());
        e.setOwnerName(d.getOwnerName());
        e.setCredentialId(d.getCredentialId());
        e.setLoginUsername(d.getUsername());
        e.setSecretValue(d.getSecretValue());
        e.setRuntimeStatus(d.getStatus());
        e.setDescription(d.getDescription());
        e.setLastConnectivityAt(d.getLastConnectivityAt());
        e.setEnabled(d.isEnabled() ? 1 : 0);
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }

    /**
     * 数据库实例表实体转领域对象；端口为空时默认 0。
     *
     * @param e 数据库表实体
     * @return 数据库领域对象
     */
    private ManagedDatabase toDb(ManagedDatabaseEntity e) {
        return new ManagedDatabase(e.getId(), e.getInstanceCode(), e.getInstanceName(), e.getDatabaseType(),
                e.getHostAddress(), e.getPortNumber() == null ? 0 : e.getPortNumber(), e.getDatabaseName(),
                e.getSchemaName(), e.getEnvironment(), e.getOwnerName(), e.getCredentialId(),
                e.getLoginUsername(), e.getSecretValue(),
                e.getRuntimeStatus(), e.getDescription(), e.getLastConnectivityAt(),
                e.getEnabled() != null && e.getEnabled() == 1, e.getCreatedAt(), e.getUpdatedAt());
    }

    /**
     * 凭据领域对象转表实体。
     *
     * @param c 凭据领域对象
     * @return 可插入/更新的表实体
     */
    private ManagedCredentialEntity toCredEntity(ManagedCredential c) {
        ManagedCredentialEntity e = new ManagedCredentialEntity();
        e.setId(c.getId());
        e.setCredentialCode(c.getCode());
        e.setCredentialName(c.getName());
        e.setCredentialType(c.getType());
        e.setLoginUsername(c.getUsername());
        e.setSecretValue(c.getSecretValue());
        e.setDescription(c.getDescription());
        e.setEnabled(c.isEnabled() ? 1 : 0);
        e.setCreatedAt(c.getCreatedAt());
        e.setUpdatedAt(c.getUpdatedAt());
        return e;
    }

    /**
     * 凭据表实体转领域对象。
     *
     * @param e 凭据表实体
     * @return 凭据领域对象
     */
    private ManagedCredential toCred(ManagedCredentialEntity e) {
        return new ManagedCredential(e.getId(), e.getCredentialCode(), e.getCredentialName(),
                e.getCredentialType(), e.getLoginUsername(), e.getSecretValue(), e.getDescription(),
                e.getEnabled() != null && e.getEnabled() == 1, e.getCreatedAt(), e.getUpdatedAt());
    }
}
