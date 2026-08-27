package com.coolxer.model.retrieval.meta;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DataEntity {

    private int id;

    private String name;

    private String description;

    private String label;

    private String tableName;

    private String dataSource;

    private String sortColumn;

    private DbCreate autoCreate;

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public class DbCreate {
        private String engine;
        private List<String> orderBy;
        private String partitionBy;
        private Ttl ttl;
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Ttl {
        private String column;
        private long expireAfter;
        private TtlUnit unit;

        @JsonAnySetter
        public void rejectUnknownProperty(String propertyName, JsonNode ignoredValue) {
            throw new IllegalArgumentException("auto_create.ttl不支持字段: " + propertyName);
        }
    }

    public enum TtlUnit {
        HOUR,
        DAY,
        WEEK,
        MONTH,
        YEAR
    }

}
