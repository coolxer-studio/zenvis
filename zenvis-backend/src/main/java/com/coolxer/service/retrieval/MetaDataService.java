package com.coolxer.service.retrieval;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import com.coolxer.model.retrieval.meta.MetaData;

import java.util.List;
import java.nio.file.Path;

public interface MetaDataService {

    MetaData loadMetaData();

    /**
     * 重新加载 Meta；任何解析、冲突或合并错误都直接抛出，不沿用旧快照。
     */
    default MetaData reloadMetaDataOrThrow() {
        MetaData metaData = loadMetaData();
        if (metaData == null) {
            throw new IllegalStateException("元数据加载失败");
        }
        return metaData;
    }

    /**
     * 校验并合并指定 Meta 文件，但不替换当前运行快照。
     */
    default MetaData validateMetaDataFiles(List<Path> metaFiles) {
        throw new UnsupportedOperationException("当前 MetaDataService 不支持候选文件校验");
    }

    DataEntity getDataEntityById(Integer entityId);

    DataEntity getDataEntityByName(String name);

    DataAttribute getDataAttributeById(Integer attributeId);

    DataAttribute getDataAttributeByName(String entity, String attribute);

    List<DataEntity> getAllDataEntity();

    List<DataAttribute> getAllDataAttribute();

    List<DataAttribute> getAllDataAttributeByEntity(DataEntity dataEntity);

    DataOperator getDataOperatorByName(String name);
}
