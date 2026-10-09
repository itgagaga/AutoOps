package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.db.DbQueryService;
import com.zhiwei.superemployee.app.resource.*;
import com.zhiwei.superemployee.app.runtime.HostConnectivityService;
import com.zhiwei.superemployee.app.runtime.SshLogPathPolicy;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * 资源中心 HTTP 入口：主机、应用、数据库与凭据的 CRUD、启停与连通性探测。
 * <p>
 * 路径前缀 {@code /api/resources}。各子资源权限分别要求：
 * {@code RESOURCE_HOST} / {@code RESOURCE_APPLICATION} / {@code RESOURCE_DATABASE} / {@code RESOURCE_CREDENTIAL}
 * （或管理员）。密文经 {@link CredentialSecretService} 加密存储；写操作记审计。
 * 主机/数据库运行状态（ONLINE/OFFLINE）仅由连通性探测写入，编辑接口不改写。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceRepository repository;
    private final CredentialSecretService secrets;
    private final AuditService audit;
    private final HostConnectivityService connectivity;
    private final DbQueryService dbQueries;

    /**
     * @param repository   资源仓储
     * @param secrets      凭据密文加解密
     * @param audit        审计服务
     * @param connectivity 主机 SSH 连通性
     * @param dbQueries    数据库连通/探测
     */
    public ResourceController(ResourceRepository repository, CredentialSecretService secrets,
                              AuditService audit, HostConnectivityService connectivity,
                              DbQueryService dbQueries) {
        this.repository = repository;
        this.secrets = secrets;
        this.audit = audit;
        this.connectivity = connectivity;
        this.dbQueries = dbQueries;
    }

    /**
     * 列出全部托管主机。
     *
     * @return 主机列表
     */
    @GetMapping("/hosts")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_HOST')")
    public List<ManagedHost> hosts() {
        return repository.findHosts();
    }

    /**
     * 创建主机。编码冲突返回 409；新建运行状态默认 UNKNOWN。
     *
     * @param r 创建请求
     * @return 201 + 主机实体，或错误正文
     */
    @PostMapping("/hosts")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_HOST')")
    public ResponseEntity<?> createHost(@Valid @RequestBody SaveHostRequest r) {
        String code = code(r.getCode());
        if (repository.hostCodeExists(code)) {
            return conflict("Host code already exists");
        }
        try {
            String now = now();
            // 新建默认 UNKNOWN；ONLINE/OFFLINE 仅由连通性心跳探测写入。
            ManagedHost candidate = host(UUID.randomUUID().toString(), code, now, now, "UNKNOWN", null, r, true);
            ManagedHost saved = repository.saveHost(candidate);
            audit.record("CREATE", "HOST", saved.getId(), saved.getCode());
            return ResponseEntity.created(URI.create("/api/resources/hosts/" + saved.getId())).body(saved);
        }
        catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 更新主机配置；不改启用状态与运行状态（由探测/启用心跳维护）。
     *
     * @param id 主机主键
     * @param r  更新请求
     * @return 更新后实体；不存在 404
     */
    @PutMapping("/hosts/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_HOST')")
    public ResponseEntity<?> updateHost(@PathVariable String id, @Valid @RequestBody SaveHostRequest r) {
        ManagedHost old = repository.findHost(id).orElse(null);
        if (old == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            // 编辑不改启用状态与运行状态；状态由探测/启用心跳维护。
            ManagedHost candidate = host(id, old.getCode(), old.getCreatedAt(), now(),
                    old.getStatus(), old.getLastConnectivityAt(), r, old.isEnabled());
            ManagedHost saved = repository.updateHost(candidate);
            audit.record("UPDATE", "HOST", id, saved.getCode());
            return ResponseEntity.ok(saved);
        }
        catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 启用或禁用主机。启用前须 SSH 可达，否则 400。
     *
     * @param id 主机主键
     * @param r  启用状态
     * @return 更新后主机
     */
    @PutMapping("/hosts/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_HOST')")
    public ResponseEntity<?> enableHost(@PathVariable String id, @Valid @RequestBody AgentEnabledRequest r) {
        ManagedHost current = repository.findHost(id).orElse(null);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            if (Boolean.TRUE.equals(r.getEnabled())) {
                ManagedHost probed = connectivity.requireReachable(
                        current.withConnectivity(current.getStatus(), current.getLastConnectivityAt(), true));
                repository.updateHost(probed);
            }
            else {
                repository.setEnabled("managed_host", id, false, now());
            }
            audit.record(Boolean.TRUE.equals(r.getEnabled()) ? "ENABLE" : "DISABLE", "HOST", id, null);
            return ResponseEntity.ok(repository.findHost(id).orElseThrow());
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 对已保存主机执行连通性探测并持久化 ONLINE/OFFLINE 与指纹。
     *
     * @param id 主机主键
     * @return 探测结果与最新主机；失败时 400
     */
    @PostMapping("/hosts/{id}/connectivity-check")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_HOST')")
    public ResponseEntity<?> checkHostConnectivity(@PathVariable String id) {
        if (repository.findHost(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HostConnectivityService.ProbeResult result = connectivity.probeAndPersist(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result.success());
        body.put("message", result.message());
        body.put("fingerprints", result.fingerprints());
        body.put("host", repository.findHost(id).orElseThrow());
        return result.success() ? ResponseEntity.ok(body) : ResponseEntity.badRequest().body(body);
    }

    /**
     * 按 IP/端口自动拉取 SSH 主机密钥指纹（无需已保存主机；凭据仅用于后续登录探测）。
     *
     * @param r 含 ipAddress、sshPort（默认 22）
     * @return fingerprints 列表；失败 400
     */
    @PostMapping("/hosts/fetch-fingerprint")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_HOST')")
    public ResponseEntity<?> fetchHostFingerprint(@Valid @RequestBody FetchHostFingerprintRequest r) {
        try {
            List<String> fingerprints = connectivity.fetchFingerprints(
                    r.getIpAddress(), r.getSshPort() == null ? 22 : r.getSshPort());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("fingerprints", fingerprints);
            body.put("message", "已自动获取 " + fingerprints.size() + " 条主机密钥指纹");
            return ResponseEntity.ok(body);
        }
        catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 列出全部托管应用。
     *
     * @return 应用列表
     */
    @GetMapping("/applications")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_APPLICATION')")
    public List<ManagedApplication> applications() {
        return repository.findApplications();
    }

    /**
     * 创建应用；编码冲突返回 409。
     *
     * @param r 创建请求
     * @return 201 + 应用实体
     */
    @PostMapping("/applications")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_APPLICATION')")
    public ResponseEntity<?> createApplication(@Valid @RequestBody SaveApplicationRequest r) {
        String code = code(r.getCode());
        if (repository.applicationCodeExists(code)) {
            return conflict("Application code already exists");
        }
        String now = now();
        ManagedApplication a = repository.saveApplication(application(UUID.randomUUID().toString(), code, now, now, r, true));
        audit.record("CREATE", "APPLICATION", a.getId(), a.getCode());
        return ResponseEntity.created(URI.create("/api/resources/applications/" + a.getId())).body(a);
    }

    /**
     * 更新应用配置（编码、启用状态沿用原值，启用走独立接口）。
     *
     * @param id 应用主键
     * @param r  更新请求
     * @return 更新后实体；不存在 404
     */
    @PutMapping("/applications/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_APPLICATION')")
    public ResponseEntity<ManagedApplication> updateApplication(@PathVariable String id,
                                                                @Valid @RequestBody SaveApplicationRequest r) {
        ManagedApplication old = repository.findApplication(id).orElse(null);
        if (old == null) {
            return ResponseEntity.notFound().build();
        }
        ManagedApplication a = repository.updateApplication(
                application(id, old.getCode(), old.getCreatedAt(), now(), r, old.isEnabled()));
        audit.record("UPDATE", "APPLICATION", id, a.getCode());
        return ResponseEntity.ok(a);
    }

    /**
     * 启用或禁用应用。
     *
     * @param id 应用主键
     * @param r  启用状态
     * @return 更新后实体
     */
    @PutMapping("/applications/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_APPLICATION')")
    public ResponseEntity<ManagedApplication> enableApplication(@PathVariable String id,
                                                                @Valid @RequestBody AgentEnabledRequest r) {
        if (repository.findApplication(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        repository.setEnabled("managed_application", id, r.getEnabled(), now());
        audit.record(r.getEnabled() ? "ENABLE" : "DISABLE", "APPLICATION", id, null);
        return ResponseEntity.ok(repository.findApplication(id).orElseThrow());
    }

    /**
     * 列出全部托管数据库（密文不向客户端明文回显，由实体视图控制）。
     *
     * @return 数据库列表
     */
    @GetMapping("/databases")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_DATABASE')")
    public List<ManagedDatabase> databases() {
        return repository.findDatabases();
    }

    /**
     * 创建数据库资源；须提供账号与密码；新建状态 UNKNOWN。
     *
     * @param r 创建请求
     * @return 201 + 实体，或缺字段 400 / 编码冲突 409
     */
    @PostMapping("/databases")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_DATABASE')")
    public ResponseEntity<?> createDatabase(@Valid @RequestBody SaveDatabaseRequest r) {
        String code = code(r.getCode());
        if (repository.databaseCodeExists(code)) {
            return conflict("Database code already exists");
        }
        String now = now();
        if (empty(r.getUsername())) {
            return ResponseEntity.badRequest().body(message("请填写数据库登录账号"));
        }
        if (empty(r.getSecret())) {
            return ResponseEntity.badRequest().body(message("请填写数据库登录密码"));
        }
        // 新建默认 UNKNOWN；ONLINE/OFFLINE 仅由 JDBC 心跳探测写入。
        ManagedDatabase d = repository.saveDatabase(
                database(UUID.randomUUID().toString(), code, now, now, "UNKNOWN", null,
                        secrets.encrypt(r.getSecret()), r, true));
        audit.record("CREATE", "DATABASE", d.getId(), d.getCode());
        return ResponseEntity.created(URI.create("/api/resources/databases/" + d.getId())).body(d);
    }

    /**
     * 更新数据库配置；secret 空则保留原密文；不改启用与运行状态。
     *
     * @param id 数据库主键
     * @param r  更新请求
     * @return 更新后实体
     */
    @PutMapping("/databases/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_DATABASE')")
    public ResponseEntity<?> updateDatabase(@PathVariable String id,
                                            @Valid @RequestBody SaveDatabaseRequest r) {
        ManagedDatabase old = repository.findDatabase(id).orElse(null);
        if (old == null) {
            return ResponseEntity.notFound().build();
        }
        if (empty(r.getUsername()) && empty(old.getUsername()) && empty(old.getCredentialId())) {
            return ResponseEntity.badRequest().body(message("请填写数据库登录账号"));
        }
        String secret = empty(r.getSecret()) ? old.getSecretValue() : secrets.encrypt(r.getSecret());
        if ((empty(secret) || secret.isBlank()) && empty(old.getCredentialId()) && empty(r.getCredentialId())) {
            return ResponseEntity.badRequest().body(message("请填写数据库登录密码"));
        }
        // 编辑不改启用状态与运行状态；状态由探测/启用心跳维护。
        ManagedDatabase d = repository.updateDatabase(
                database(id, old.getCode(), old.getCreatedAt(), now(),
                        old.getStatus(), old.getLastConnectivityAt(), secret, r, old.isEnabled()));
        audit.record("UPDATE", "DATABASE", id, d.getCode());
        return ResponseEntity.ok(d);
    }

    /**
     * 启用或禁用数据库。启用前须 JDBC 可达。
     *
     * @param id 数据库主键
     * @param r  启用状态
     * @return 更新后实体
     */
    @PutMapping("/databases/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_DATABASE')")
    public ResponseEntity<?> enableDatabase(@PathVariable String id,
                                            @Valid @RequestBody AgentEnabledRequest r) {
        ManagedDatabase current = repository.findDatabase(id).orElse(null);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            if (Boolean.TRUE.equals(r.getEnabled())) {
                ManagedDatabase probed = dbQueries.requireReachable(
                        current.withConnectivity(current.getStatus(), current.getLastConnectivityAt(), true));
                repository.updateDatabase(probed);
            }
            else {
                repository.setEnabled("managed_database", id, false, now());
            }
            audit.record(Boolean.TRUE.equals(r.getEnabled()) ? "ENABLE" : "DISABLE", "DATABASE", id, null);
            return ResponseEntity.ok(repository.findDatabase(id).orElseThrow());
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 对已保存数据库执行连通性探测并持久化状态。
     *
     * @param id 数据库主键
     * @return 探测结果体；失败时 400
     */
    @PostMapping("/databases/{id}/connectivity-check")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_DATABASE')")
    public ResponseEntity<?> checkDatabaseConnectivity(@PathVariable String id) {
        if (repository.findDatabase(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = dbQueries.pingAndPersist(id);
        boolean success = Boolean.TRUE.equals(body.get("success"));
        return success ? ResponseEntity.ok(body) : ResponseEntity.badRequest().body(body);
    }

    /**
     * 列出全部托管凭据（不含明文 secret）。
     *
     * @return 凭据列表
     */
    @GetMapping("/credentials")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_CREDENTIAL')")
    public List<ManagedCredential> credentials() {
        return repository.findCredentials();
    }

    /**
     * 创建凭据；secret 必填并加密存储；编码冲突 409。
     *
     * @param r 创建请求
     * @return 201 + 凭据实体
     */
    @PostMapping("/credentials")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_CREDENTIAL')")
    public ResponseEntity<?> createCredential(@Valid @RequestBody SaveCredentialRequest r) {
        String code = code(r.getCode());
        if (repository.credentialCodeExists(code)) {
            return conflict("Credential code already exists");
        }
        if (empty(r.getSecret())) {
            return ResponseEntity.badRequest().body(message("Secret is required"));
        }
        String now = now();
        ManagedCredential c = repository.saveCredential(
                credential(UUID.randomUUID().toString(), code, secrets.encrypt(r.getSecret()), now, now, r, true));
        audit.record("CREATE", "CREDENTIAL", c.getId(), c.getCode());
        return ResponseEntity.created(URI.create("/api/resources/credentials/" + c.getId())).body(c);
    }

    /**
     * 更新凭据；secret 空则保留原密文。
     *
     * @param id 凭据主键
     * @param r  更新请求
     * @return 更新后实体
     */
    @PutMapping("/credentials/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_CREDENTIAL')")
    public ResponseEntity<ManagedCredential> updateCredential(@PathVariable String id,
                                                              @Valid @RequestBody SaveCredentialRequest r) {
        ManagedCredential old = repository.findCredential(id).orElse(null);
        if (old == null) {
            return ResponseEntity.notFound().build();
        }
        String secret = empty(r.getSecret()) ? old.getSecretValue() : secrets.encrypt(r.getSecret());
        ManagedCredential c = repository.updateCredential(
                credential(id, old.getCode(), secret, old.getCreatedAt(), now(), r, old.isEnabled()));
        audit.record("UPDATE", "CREDENTIAL", id, c.getCode());
        return ResponseEntity.ok(c);
    }

    /**
     * 启用或禁用凭据。
     *
     * @param id 凭据主键
     * @param r  启用状态
     * @return 更新后实体
     */
    @PutMapping("/credentials/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('RESOURCE_CREDENTIAL')")
    public ResponseEntity<ManagedCredential> enableCredential(@PathVariable String id,
                                                              @Valid @RequestBody AgentEnabledRequest r) {
        if (repository.findCredential(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        repository.setEnabled("managed_credential", id, r.getEnabled(), now());
        audit.record(r.getEnabled() ? "ENABLE" : "DISABLE", "CREDENTIAL", id, null);
        return ResponseEntity.ok(repository.findCredential(id).orElseThrow());
    }

    /**
     * 由请求组装主机实体；规范化日志路径白名单与主机密钥指纹。
     *
     * @param id               主键
     * @param code             编码
     * @param created          创建时间
     * @param updated          更新时间
     * @param status           运行状态
     * @param lastConnectivity 最近探测时间
     * @param r                请求体
     * @param fallbackEnabled  默认启用状态
     * @return 主机实体
     */
    private ManagedHost host(String id, String code, String created, String updated,
                             String status, String lastConnectivity,
                             SaveHostRequest r, boolean fallbackEnabled) {
        String allowed = r.joinedAllowedLogPaths();
        if (allowed != null) {
            // 规范化并回写成标准白名单，非法路径在保存前即失败。
            allowed = String.join("|", SshLogPathPolicy.parseAllowed(allowed));
            if (allowed.isBlank()) {
                allowed = null;
            }
        }
        String runtimeStatus = status == null || status.isBlank() ? "UNKNOWN" : status;
        return new ManagedHost(id, code, r.getName().trim(), r.getIpAddress().trim(),
                r.getSshPort() == null ? 22 : r.getSshPort(), r.getOsType(), r.getEnvironment(),
                r.getOwnerName(), runtimeStatus,
                blank(r.getCredentialId()), r.getDescription(), allowed,
                r.joinedHostKeyFingerprints(), lastConnectivity,
                enabledOrDefault(r.getEnabled(), fallbackEnabled), created, updated);
    }

    /**
     * 由请求组装应用实体。
     *
     * @param id              主键
     * @param code            编码
     * @param created         创建时间
     * @param updated         更新时间
     * @param r               请求体
     * @param fallbackEnabled 默认启用状态
     * @return 应用实体
     */
    private ManagedApplication application(String id, String code, String created, String updated,
                                           SaveApplicationRequest r, boolean fallbackEnabled) {
        return new ManagedApplication(id, code, r.getName().trim(), r.getType(), r.getRepositoryUrl(),
                r.getDeployPath(), blank(r.getHostId()), null, r.getOwnerName(), r.getDescription(),
                enabledOrDefault(r.getEnabled(), fallbackEnabled), created, updated);
    }

    /**
     * 由请求组装数据库实体；端口空则按库类型默认。
     *
     * @param id               主键
     * @param code             编码
     * @param created          创建时间
     * @param updated          更新时间
     * @param status           运行状态
     * @param lastConnectivity 最近探测时间
     * @param encryptedSecret  已加密口令
     * @param r                请求体
     * @param fallbackEnabled  默认启用状态
     * @return 数据库实体
     */
    private ManagedDatabase database(String id, String code, String created, String updated,
                                     String status, String lastConnectivity, String encryptedSecret,
                                     SaveDatabaseRequest r, boolean fallbackEnabled) {
        int port = r.getPort() == null ? defaultPort(r.getType()) : r.getPort();
        String runtimeStatus = status == null || status.isBlank() ? "UNKNOWN" : status;
        String username = r.getUsername() == null ? null : r.getUsername().trim();
        if (username != null && username.isBlank()) {
            username = null;
        }
        return new ManagedDatabase(id, code, r.getName().trim(), r.getType(), r.getHostAddress().trim(), port,
                r.getDatabaseName().trim(), r.getSchemaName(), r.getEnvironment(), r.getOwnerName(),
                blank(r.getCredentialId()), username, encryptedSecret,
                runtimeStatus, r.getDescription(), lastConnectivity,
                enabledOrDefault(r.getEnabled(), fallbackEnabled), created, updated);
    }

    /**
     * 由请求组装凭据实体。
     *
     * @param id              主键
     * @param code            编码
     * @param secret          已加密密文
     * @param created         创建时间
     * @param updated         更新时间
     * @param r               请求体
     * @param fallbackEnabled 默认启用状态
     * @return 凭据实体
     */
    private ManagedCredential credential(String id, String code, String secret, String created, String updated,
                                         SaveCredentialRequest r, boolean fallbackEnabled) {
        return new ManagedCredential(id, code, r.getName().trim(), r.getType(), r.getUsername(), secret,
                r.getDescription(), enabledOrDefault(r.getEnabled(), fallbackEnabled), created, updated);
    }

    /**
     * 取启用状态：请求为空则用 fallback。
     *
     * @param value    请求中的 enabled
     * @param fallback 默认值
     * @return 最终启用标志
     */
    private boolean enabledOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    /**
     * 按数据库类型返回默认端口（DM/MYSQL/ORACLE/其它）。
     *
     * @param type 库类型编码
     * @return 默认端口
     */
    private int defaultPort(String type) {
        if ("DM".equals(type)) return 5236;
        if ("MYSQL".equals(type)) return 3306;
        if ("ORACLE".equals(type)) return 1521;
        return 54321;
    }

    /**
     * 构造 409 冲突响应。
     *
     * @param value 错误文案
     * @return 409 ResponseEntity
     */
    private ResponseEntity<Map<String, String>> conflict(String value) {
        return ResponseEntity.status(409).body(message(value));
    }

    /**
     * 构造统一消息正文。
     *
     * @param value 文案
     * @return message Map
     */
    private Map<String, String> message(String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("message", value);
        return m;
    }

    /**
     * 规范化资源编码（trim + 大写）。
     *
     * @param value 原始编码
     * @return 规范化编码
     */
    private String code(String value) {
        return value.trim().toUpperCase();
    }

    /**
     * 当前 UTC 时间的 ISO-8601 字符串。
     *
     * @return 时间戳字符串
     */
    private String now() {
        return Instant.now().toString();
    }

    /**
     * 判断字符串是否为 null 或空白。
     *
     * @param value 待测字符串
     * @return 是否为空
     */
    private boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 空白转 null，否则 trim。
     *
     * @param value 原始字符串
     * @return trim 后或 null
     */
    private String blank(String value) {
        return empty(value) ? null : value.trim();
    }
}
