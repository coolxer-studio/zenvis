package com.coolxer.model.dih.ai;

import java.io.Serial;
import java.io.Serializable;

public class AIModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 2123534567887673L;

    private String name;
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
