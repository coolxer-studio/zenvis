package com.coolxer.model.retrieval.meta;

/**
 * 平台内置元数据字段定义。
 */
public final class MetaDataConstants {

    public static final String RECORD_ID_ATTRIBUTE = "zenvis_id";
    public static final String RECORD_ID_COLUMN = "zenvis_id";
    public static final String RECORD_ID_COLUMN_TYPE = "Nullable(UUID)";
    public static final String RECORD_ID_DEFAULT_EXPRESSION = "generateUUIDv4()";

    public static final String INSERT_TIME_ATTRIBUTE = "zenvis_insert_time";
    public static final String INSERT_TIME_COLUMN = "zenvis_insert_time";
    public static final String INSERT_TIME_COLUMN_TYPE = "DateTime64(3)";
    public static final String INSERT_TIME_DEFAULT_EXPRESSION = "now64(3)";

    private MetaDataConstants() {
    }

    public static boolean isInsertTime(DataAttribute attribute) {
        return attribute != null
                && INSERT_TIME_ATTRIBUTE.equals(attribute.getName())
                && INSERT_TIME_COLUMN.equals(attribute.getColumnName());
    }

    public static boolean isRecordId(DataAttribute attribute) {
        return attribute != null
                && RECORD_ID_ATTRIBUTE.equals(attribute.getName())
                && RECORD_ID_COLUMN.equals(attribute.getColumnName());
    }

    public static boolean isSystemMaintained(DataAttribute attribute) {
        return isRecordId(attribute) || isInsertTime(attribute);
    }
}
