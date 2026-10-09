package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.db.DbMetadataService;
import com.zhiwei.superemployee.app.db.DbQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据库终端 HTTP 入口：连通性探测、SQL 执行与对象浏览器（schema/表/DDL/预览）。
 * <p>
 * 路径前缀 {@code /api/db}。菜单权限：管理员，或 {@code OPS_DB_CLIENT} / {@code RESOURCE_DATABASE}。
 * 操作目标为资源中心已登记的数据库主键。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/db")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('OPS_DB_CLIENT') or hasAuthority('RESOURCE_DATABASE')")
public class DbTerminalController {
    private final DbQueryService queries;
    private final DbMetadataService metadata;

    /**
     * @param queries  SQL 执行与连通探测
     * @param metadata 元数据浏览（schema/表/DDL/预览）
     */
    public DbTerminalController(DbQueryService queries, DbMetadataService metadata) {
        this.queries = queries;
        this.metadata = metadata;
    }

    /**
     * 探测指定数据库的 JDBC 连通性。
     *
     * @param databaseId 资源中心数据库主键
     * @return 探测结果 Map
     */
    @PostMapping("/{databaseId}/ping")
    public Map<String, Object> ping(@PathVariable String databaseId) {
        return queries.ping(databaseId);
    }

    /**
     * 在指定数据库上执行 SQL（受服务层安全策略约束）。
     *
     * @param databaseId 资源中心数据库主键
     * @param request    含 sql 字段，最长 20000
     * @return 执行结果（列/行或错误信息）
     */
    @PostMapping("/{databaseId}/query")
    public Map<String, Object> query(@PathVariable String databaseId,
                                     @Valid @RequestBody DbQueryRequest request) {
        return queries.execute(databaseId, request.getSql());
    }

    /**
     * 列出数据库下的 schema。
     *
     * @param databaseId 资源中心数据库主键
     * @return schema 列表视图
     */
    @GetMapping("/{databaseId}/schemas")
    public Map<String, Object> schemas(@PathVariable String databaseId) {
        return metadata.listSchemas(databaseId);
    }

    /**
     * 列出指定 schema 下的表。
     *
     * @param databaseId 资源中心数据库主键
     * @param schema     schema 名
     * @return 表列表视图
     */
    @GetMapping("/{databaseId}/tables")
    public Map<String, Object> tables(@PathVariable String databaseId,
                                      @RequestParam String schema) {
        return metadata.listTables(databaseId, schema);
    }

    /**
     * 获取表的 DDL 定义。
     *
     * @param databaseId 资源中心数据库主键
     * @param schema     schema 名
     * @param table      表名
     * @return DDL 文本等
     */
    @GetMapping("/{databaseId}/ddl")
    public Map<String, Object> ddl(@PathVariable String databaseId,
                                   @RequestParam String schema,
                                   @RequestParam String table) {
        return metadata.tableDdl(databaseId, schema, table);
    }

    /**
     * 预览表数据（可限制行数，默认由服务层处理，入参 1–500）。
     *
     * @param databaseId 资源中心数据库主键
     * @param schema     schema 名
     * @param table      表名
     * @param limit      可选行数上限
     * @return 预览行列数据
     */
    @GetMapping("/{databaseId}/preview")
    public Map<String, Object> preview(@PathVariable String databaseId,
                                       @RequestParam String schema,
                                       @RequestParam String table,
                                       @RequestParam(required = false) @Min(1) @Max(500) Integer limit) {
        return metadata.previewData(databaseId, schema, table, limit);
    }

    /**
     * 数据库终端 SQL 执行请求体。
     */
    public static class DbQueryRequest {
        @NotBlank
        @Size(max = 20000)
        private String sql;

        /**
         * @return 待执行 SQL
         */
        public String getSql() {
            return sql;
        }

        /**
         * @param sql 待执行 SQL
         */
        public void setSql(String sql) {
            this.sql = sql;
        }
    }
}
