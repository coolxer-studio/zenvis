package com.coolxer.service.core.impl;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.service.core.ClickhouseSchemeService;
import com.coolxer.service.retrieval.MetaDataService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceContextType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

/**
 * 系统数据初始化
 */
@Slf4j
@Service
public class ClickhouseSchemeServiceImpl implements ClickhouseSchemeService {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private MetaDataService metaDataService;

    /**
     * entityManager实现原生查询，unitName是通过clickHouseEntityManagerFactoryBean注入时候指定的名字
     */
    @PersistenceContext(unitName = "clickhouse", type = PersistenceContextType.TRANSACTION)
    private EntityManager entityManager;

    public void initScheme(String initSqlPath) {
        File sqlFile = new File(initSqlPath);
        // 校验文件是否存在且为普通文件
        if (!sqlFile.exists() || !sqlFile.isFile()) {
            log.warn("SQL初始化文件不存在，路径: {}", initSqlPath);
        }
        // try-with-resources 自动关闭所有流，彻底避免句柄泄漏
        String sqlScript;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sqlFile), StandardCharsets.UTF_8)
        )) {
            // 使用StringJoiner替代reduce字符串拼接，减少GC开销
            StringJoiner joiner = new StringJoiner("\n");
            reader.lines()
                    .map(this::removeComments)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .forEach(joiner::add);
            sqlScript = joiner.toString();
        } catch (IOException e) {
            log.error("读取SQL初始化文件失败，路径: {}", initSqlPath, e);
            throw new RuntimeException("读取SQL脚本文件IO异常", e);
        }

        // 空脚本防护
        if (sqlScript.isBlank()) {
            log.warn("SQL初始化文件内容为空，无需执行");
            return;
        }
        // 执行SQL脚本
        executeSql(sqlScript);
        log.info("ClickHouse 表结构初始化成功，脚本路径: {}", initSqlPath);
    }

    public void loadSchemeFromMetaData(MetaData metaData) {
        if (metaData == null || CollectionUtils.isEmpty(metaData.getEntity())) {
            log.warn("metaData or entity is empty!");
            return;
        }
        // 根据MetaData 转化为sql
        StringBuilder createTableSql = new StringBuilder();
        metaData.getEntity().stream().filter(
                dataEntity -> dataEntity.getAutoCreate() != null &&
                        dataEntity.getAutoCreate().getEngine() != null &&
                        dataEntity.getAutoCreate().getOrderBy() != null
        ).forEach(dataEntity -> {
            createTableSql.append("CREATE TABLE IF NOT EXISTS ")
                    .append(dataEntity.getTableName())
                    .append(" (");
            List<DataAttribute> attributeList = metaDataService.getAllDataAttributeByEntity(dataEntity);
            attributeList.forEach(dataAttribute -> createTableSql
                    .append(columnDefinition(dataAttribute)).append(","));
            createTableSql.deleteCharAt(createTableSql.length() - 1);
            String orderBy = String.format(" ORDER BY ( %s )", String.join(",", dataEntity.getAutoCreate().getOrderBy()));
            String engine = String.format(" ENGINE = %s", dataEntity.getAutoCreate().getEngine());
            String partitionBy = StringUtils.isEmpty(dataEntity.getAutoCreate().getPartitionBy()) ? "" :
                    String.format(" PARTITION BY %s", dataEntity.getAutoCreate().getPartitionBy());
            createTableSql.append(")").append(engine).append(orderBy).append(partitionBy)
                    .append(";");
        });

        executeSql(createTableSql.toString());

        metaData.getEntity().stream()
                .filter(this::isClickHouseEntity)
                .forEach(dataEntity -> {
                    executeSql("ALTER TABLE " + dataEntity.getTableName()
                            + " ADD COLUMN IF NOT EXISTS " + MetaDataConstants.INSERT_TIME_COLUMN
                            + " " + MetaDataConstants.INSERT_TIME_COLUMN_TYPE
                            + " DEFAULT " + MetaDataConstants.INSERT_TIME_DEFAULT_EXPRESSION);
                    migrateRecordIdColumn(dataEntity);
                });
        log.info("clickhouse scheme init successfully.");
    }

    private String columnDefinition(DataAttribute attribute) {
        String definition = attribute.getColumnName() + " " + attribute.getColumnType();
        if (MetaDataConstants.isRecordId(attribute)) {
            return definition + " DEFAULT " + MetaDataConstants.RECORD_ID_DEFAULT_EXPRESSION;
        }
        if (MetaDataConstants.isInsertTime(attribute)) {
            return definition + " DEFAULT " + MetaDataConstants.INSERT_TIME_DEFAULT_EXPRESSION;
        }
        return definition;
    }

    private void migrateRecordIdColumn(DataEntity entity) {
        String tableName = entity.getTableName();
        executeRequiredSql("ALTER TABLE " + tableName
                + " ADD COLUMN IF NOT EXISTS " + MetaDataConstants.RECORD_ID_COLUMN
                + " " + MetaDataConstants.RECORD_ID_COLUMN_TYPE + " DEFAULT NULL");
        executeRequiredSql("ALTER TABLE " + tableName
                + " MATERIALIZE COLUMN " + MetaDataConstants.RECORD_ID_COLUMN
                + " SETTINGS mutations_sync = 1");
        executeRequiredSql("ALTER TABLE " + tableName
                + " MODIFY COLUMN " + MetaDataConstants.RECORD_ID_COLUMN
                + " " + MetaDataConstants.RECORD_ID_COLUMN_TYPE
                + " DEFAULT " + MetaDataConstants.RECORD_ID_DEFAULT_EXPRESSION);
    }

    private boolean isClickHouseEntity(DataEntity entity) {
        return entity != null && (StringUtils.isBlank(entity.getDataSource())
                || "clickhouse".equalsIgnoreCase(entity.getDataSource()));
    }

    @Override
    public void deleteTable(String tableName) {
        executeSql(String.format("DROP TABLE IF EXISTS %s;", tableName));
    }

    private void executeSql(String sqlScript) {
        // 执行SQL语句
        String[] queries = sqlScript.split(";");
        for (String query : queries) {
            if (!query.trim().isEmpty()) {
                try {
                    entityManager.createNativeQuery(query).getResultList();
                } catch (Exception e) {
                    String message = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
                    if (StringUtils.contains(message, "TABLE_ALREADY_EXISTS")) {
                        log.warn(message);
                    } else {
                        log.error("ClickHouse schema statement failed: {}", query, e);
                    }
                }
            }
        }
    }

    private void executeRequiredSql(String sql) {
        try {
            entityManager.createNativeQuery(sql).getResultList();
        } catch (Exception e) {
            log.error("Required ClickHouse schema statement failed: {}", sql, e);
            throw new IllegalStateException("ClickHouse记录ID字段迁移失败: " + sql, e);
        }
    }

    private String removeComments(String line) {
        // 移除SQL注释（包括单行注释和多行注释）
        return line.replaceAll("--.*|/\\*.*?\\*/", "");
    }

}
