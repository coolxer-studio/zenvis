package com.coolxer.service.core.impl;

import com.coolxer.service.core.MysqlSchemeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceContextType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

@Slf4j
@Service
public class MysqlSchemeServiceImpl implements MysqlSchemeService {

    @PersistenceContext(unitName = "mysql", type = PersistenceContextType.TRANSACTION)
    private EntityManager entityManager;

    @Override
    public void initScheme(String initSqlPath) {
        File sqlFile = new File(initSqlPath);
        if (!sqlFile.exists() || !sqlFile.isFile()) {
            log.warn("SQL初始化文件不存在，路径: {}", initSqlPath);
            return;
        }

        String sqlScript;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sqlFile), StandardCharsets.UTF_8)
        )) {
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

        if (sqlScript.isBlank()) {
            log.warn("SQL初始化文件内容为空，无需执行");
            return;
        }

        executeSql(sqlScript);
        log.info("MySQL 表结构初始化成功，脚本路径: {}", initSqlPath);
    }

    private void executeSql(String sqlScript) {
        String[] queries = sqlScript.split(";");
        for (String query : queries) {
            if (!query.trim().isEmpty()) {
                try {
                    entityManager.createNativeQuery(query).executeUpdate();
                } catch (Exception e) {
                    log.error("执行SQL失败: {}", query, e);
                    throw new RuntimeException("执行SQL失败: " + query, e);
                }
            }
        }
    }

    private String removeComments(String line) {
        return line.replaceAll("--.*|/\\*.*?\\*/", "");
    }
}