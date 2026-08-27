package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillOptionVo implements Serializable {
    private String label;
    private String value;
    private String description;
    private List<String> agentTypes = new ArrayList<>();
}
