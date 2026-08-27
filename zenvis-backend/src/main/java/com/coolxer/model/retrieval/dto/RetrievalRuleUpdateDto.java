package com.coolxer.model.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RetrievalRuleUpdateDto extends RetrievalRuleConfigDto {

    @JsonAlias("rule_id")
    private Integer id;

    private String ruleName;

    private String ruleDescription;

    public RetrievalRequestDto toRetrievalRequestDto() {
        RetrievalRequestDto target = new RetrievalRequestDto();
        copyConfigTo(target);
        target.setId(id);
        target.setRuleName(ruleName);
        target.setRuleDescription(ruleDescription);
        return target;
    }
}
