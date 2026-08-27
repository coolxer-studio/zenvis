package com.coolxer.model.base.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 单值对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SingleValueVo implements Serializable {

    /**
     * 单值
     */
    private String value;

}