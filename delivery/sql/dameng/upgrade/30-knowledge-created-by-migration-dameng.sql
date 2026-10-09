-- 知识库条目增加创建人，用于管理侧行级隔离；Agent 检索仍可读全部启用条目。
ALTER TABLE ops_knowledge_entry ADD created_by VARCHAR(128);
COMMENT ON COLUMN ops_knowledge_entry.created_by IS '创建知识条目的登录用户名';
CREATE INDEX idx_ops_knowledge_created_by ON ops_knowledge_entry(created_by);
COMMIT;
