package com.coolxer.model.config.vo;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 文件遍历对象
 */
@Data
@Accessors(chain = true)
public class ConfigVo implements Serializable {

    private Long id;

    private Long parentId;

    /**
     * 文件名(文件夹名)
     */
    private String fileName;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 文件路径
     */
    private String path;

    /**
     * 文件级数
     */
    private Integer depth;

    /**
     * 是否是文件夹
     */
    private Boolean isDir;

    /**
     * 是否可修改(文件夹默认不可修改)
     */
    private Boolean modifiable;


    /**
     * 文件夹下的文件列表
     */
    private List<ConfigVo> nodes;

    public boolean isEmpty() {
        return Objects.isNull(this.id)
                && Objects.isNull(this.parentId)
                && StringUtils.isBlank(this.fileName)
                && (Objects.isNull(this.size) || this.size == 0)
                && StringUtils.isBlank(this.path)
                && (Objects.isNull(this.depth) || this.depth == 0)
                && Objects.isNull(this.isDir)
                && Objects.isNull(this.modifiable)
                && CollectionUtils.isEmpty(this.nodes);
    }

}
