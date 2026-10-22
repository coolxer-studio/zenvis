CREATE TABLE IF NOT EXISTS t_lab_note (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    content TEXT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'draft',
    owner VARCHAR(80) NOT NULL DEFAULT 'demo',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_lab_note_status_update (status, update_time)
);

INSERT INTO t_lab_note (title, content, status, owner)
SELECT '先看能力地图', '从应用总览进入，依次查看访问事件、风险线索、IP 调查和笔记管理。', 'published', 'demo'
WHERE NOT EXISTS (SELECT 1 FROM t_lab_note WHERE title = '先看能力地图');

INSERT INTO t_lab_note (title, content, status, owner)
SELECT '再核对数据契约', '对照 00_doc 与 01_meta，确认字段、字典、数组和 JSON 的映射。', 'published', 'demo'
WHERE NOT EXISTS (SELECT 1 FROM t_lab_note WHERE title = '再核对数据契约');

INSERT INTO t_lab_note (title, content, status, owner)
SELECT '最后验证 Agent 能力', '在 MCP 管理页发现两个只读工具，在 DIH 中选择插件能力实验室导览 Skill。', 'draft', 'demo'
WHERE NOT EXISTS (SELECT 1 FROM t_lab_note WHERE title = '最后验证 Agent 能力');
