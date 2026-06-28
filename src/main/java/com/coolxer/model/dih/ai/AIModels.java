package com.coolxer.model.dih.ai;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class AIModels implements Serializable {

    @Serial
    private static final long serialVersionUID = 2123534567887673L;

    private List<AIModel> models;

    public List<AIModel> getModels() {
        return models;
    }

    public void setModels(List<AIModel> models) {
        this.models = models;
    }

}
