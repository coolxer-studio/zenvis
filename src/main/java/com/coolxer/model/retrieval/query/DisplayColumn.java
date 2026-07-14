package com.coolxer.model.retrieval.query;

import com.coolxer.model.retrieval.meta.DataAttribute;
import lombok.Data;

@Data
public class DisplayColumn {

    private String columnName;

    private String columnType;

    private String displayName;

    private String displayType;

    public DisplayColumn fromDisplayColumn(DataAttribute dataAttribute) {
        this.setColumnName(dataAttribute.getColumnName());
        this.setColumnType(dataAttribute.getColumnType());
        // SQL aliases and response keys are always the stable logical attribute name.
        this.setDisplayName(dataAttribute.getName());
        this.setDisplayType(dataAttribute.getDisplayType());
        return this;
    }

}
