package com.coolxer.service.dih.rag;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.RagRetrievalLog;
import com.coolxer.dao.mysql.repository.RagRetrievalLogRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.RagRetrievalLogSearchDto;
import com.coolxer.model.dih.vo.RagRetrievalLogVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG检索审计日志服务
 * <p>记录每次检索的请求与结果；保存操作以 best-effort 方式执行，失败仅记录警告，不影响主检索流程。</p>
 */
@Slf4j
@Service
public class RagRetrievalLogService {

    private static final ObjectMapper OBJECT_MAPPER = JacksonConfig.OBJECT_MAPPER.copy();

    private static final int MAX_TEXT_TRUNCATE = 500;

    @Autowired
    private RagRetrievalLogRepository ragRetrievalLogRepository;

    /**
     * 保存检索日志（best-effort，异常仅记录不抛出）
     */
    public void saveLog(RagRetrievalLog entity) {
        try {
            if (entity == null) {
                return;
            }
            ragRetrievalLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("保存RAG检索日志失败: query={}", truncate(entity.getQueryText()), e);
        }
    }

    /**
     * 分页查询检索日志
     */
    public PageRowsVo<RagRetrievalLogVo> getPageList(RagRetrievalLogSearchDto searchDto) {
        Pageable pageable = PageRequest.of(searchDto.getPage() - 1, searchDto.getPerPage());
        Page<RagRetrievalLog> page = ragRetrievalLogRepository.findByPage(pageable,
                searchDto.getKnowledgeBaseId(), searchDto.getRequestType(),
                searchDto.getStatus(), searchDto.getKeyword());
        List<RagRetrievalLogVo> rows = page.getContent().stream()
                .map(RagRetrievalLogVo::new)
                .collect(Collectors.toList());
        return new PageRowsVo<>(rows, page.getTotalElements());
    }

    /**
     * 将命中文档摘要序列化为 JSON 字符串（内容截断，避免大字段膨胀）
     */
    public String toResultJson(List<Object> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(summaries);
        } catch (Exception e) {
            log.warn("序列化检索结果摘要失败", e);
            return "[]";
        }
    }

    /**
     * 构建命中文档摘要
     */
    public static DocumentSummary summary(String id, String source, String knowledgeBaseName,
                                          String datasetName, String fileName, String text) {
        return new DocumentSummary(id, source, knowledgeBaseName, datasetName, fileName, truncate(text));
    }

    /**
     * 从命中文档的 metadata 中提取出现次数最多的 knowledge_base_id，
     * 用于全局检索（请求未指定知识库）时确定日志的知识库归属，便于按知识库查看审计记录。
     *
     * @param metadatas 命中文档的 metadata 列表
     * @return 命中数最多的知识库ID；无法确定时返回 null
     */
    public static Integer dominantKnowledgeBaseId(List<Map<String, Object>> metadatas) {
        if (metadatas == null || metadatas.isEmpty()) {
            return null;
        }
        Map<Integer, Long> counts = new HashMap<>();
        for (Map<String, Object> metadata : metadatas) {
            if (metadata == null) {
                continue;
            }
            Object value = metadata.get("knowledge_base_id");
            if (value == null) {
                continue;
            }
            try {
                counts.merge(Integer.valueOf(String.valueOf(value)), 1L, Long::sum);
            } catch (NumberFormatException ignored) {
                // 忽略非法的 knowledge_base_id 值
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_TEXT_TRUNCATE ? value.substring(0, MAX_TEXT_TRUNCATE) + "..." : value;
    }

    /**
     * metadata 值转字符串
     */
    public static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 命中文档摘要
     */
    public record DocumentSummary(String id, String source, String knowledgeBaseName,
                                  String datasetName, String fileName, String text) {
    }
}
