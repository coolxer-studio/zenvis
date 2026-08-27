package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class BuiltinMcpServiceVo implements Serializable {

    private String code;

    private String name;

    private String description;

    private String sseEndpoint;

    private String messageEndpoint;

    private Integer toolCount;
}
