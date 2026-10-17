package com.coolxer.model.dih.vo;

import com.coolxer.dao.mysql.entity.RagDataset;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG数据集视图对象
 */
@Data
@NoArgsConstructor
public class RagDatasetVo implements Serializable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private int id;
    private String name;
    private String source;
    private List<String> tags;
    private String tagsText;
    private String description;
    private String directory;
    private int fileCount;
    private Date updateTime;

    public RagDatasetVo(RagDataset dataset) {
        this.id = dataset.getId();
        this.name = dataset.getName();
        this.source = dataset.getSource();
        this.description = dataset.getDescription();
        this.directory = dataset.getDirectory();
        this.fileCount = dataset.getFileCount() == null ? 0 : dataset.getFileCount();
        this.updateTime = dataset.getUpdateTime();
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(dataset.getTags(), new TypeReference<List<String>>() {
            });
            // 兼容历史数据：单个元素内可能逗号拼接了多个标签，拆分为独立标签
            this.tags = parsed == null ? List.of() : parsed.stream()
                    .flatMap(t -> Arrays.stream(t.split(",")))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            this.tagsText = String.join(", ", this.tags);
        } catch (Exception e) {
            this.tags = List.of();
            this.tagsText = "";
        }
    }
}
