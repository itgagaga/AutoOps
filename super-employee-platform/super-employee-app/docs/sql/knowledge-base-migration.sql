CREATE TABLE ops_knowledge_entry (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    category VARCHAR(32) NOT NULL,
    tags VARCHAR(1000),
    scope VARCHAR(64) NOT NULL,
    content CLOB NOT NULL,
    source_incident_id VARCHAR(64),
    enabled INTEGER NOT NULL,
    hit_count INTEGER NOT NULL,
    created_by VARCHAR(128),
    created_at VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL
);

COMMENT ON TABLE ops_knowledge_entry IS '运维知识库条目表，存储人工维护及故障处置自动沉淀的知识';
COMMENT ON COLUMN ops_knowledge_entry.id IS '知识条目主键UUID';
COMMENT ON COLUMN ops_knowledge_entry.title IS '知识标题';
COMMENT ON COLUMN ops_knowledge_entry.category IS '知识分类：RUNBOOK运行手册、INCIDENT故障经验、CONFIG配置规范';
COMMENT ON COLUMN ops_knowledge_entry.tags IS '检索标签，多个标签使用逗号分隔';
COMMENT ON COLUMN ops_knowledge_entry.scope IS '适用范围：OPS运维场景、GLOBAL全局场景';
COMMENT ON COLUMN ops_knowledge_entry.content IS '知识正文，包含现象、判断依据、处置步骤、验证方式及回滚方案';
COMMENT ON COLUMN ops_knowledge_entry.source_incident_id IS '来源故障事件ID；人工维护时为空，自动沉淀时关联事件';
COMMENT ON COLUMN ops_knowledge_entry.enabled IS '启用状态：1启用、0停用';
COMMENT ON COLUMN ops_knowledge_entry.hit_count IS '被运维Agent检索召回的累计次数';
COMMENT ON COLUMN ops_knowledge_entry.created_by IS '创建知识条目的登录用户名';
COMMENT ON COLUMN ops_knowledge_entry.created_at IS '创建时间，ISO-8601格式';
COMMENT ON COLUMN ops_knowledge_entry.updated_at IS '最后更新时间，ISO-8601格式';

CREATE UNIQUE INDEX uk_ops_knowledge_source
    ON ops_knowledge_entry(source_incident_id);
CREATE INDEX idx_ops_knowledge_created_by ON ops_knowledge_entry(created_by);

INSERT INTO app_menu
    (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
SELECT '00000000-0000-0000-0000-000000000350',
       '00000000-0000-0000-0000-000000000300',
       'OPS_KNOWLEDGE','知识库','/knowledge','Notebook',25,1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_code='OPS_KNOWLEDGE');

INSERT INTO app_role_menu(role_id,menu_id)
SELECT '00000000-0000-0000-0000-000000000010',
       '00000000-0000-0000-0000-000000000350'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_menu
    WHERE role_id='00000000-0000-0000-0000-000000000010'
      AND menu_id='00000000-0000-0000-0000-000000000350'
);

COMMIT;
