CREATE TABLE IF NOT EXISTS t_asset_rule (
    id INT NOT NULL AUTO_INCREMENT,
    create_time TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by INT NULL,
    update_by INT NULL,
    is_delete INT NOT NULL DEFAULT 0,
    name VARCHAR(255) NULL,
    description VARCHAR(255) NULL,
    asset VARCHAR(64) NULL,
    conditions TEXT NULL,
    action VARCHAR(64) NULL,
    status VARCHAR(64) NULL,
    result VARCHAR(255) NULL,
    PRIMARY KEY (id),
    INDEX idx_asset_rule_filter (asset, action, status),
    INDEX idx_asset_rule_update_time (update_time)
);
