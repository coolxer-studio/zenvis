package com.coolxer.dao.mysql.entity;


import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 检索规则表
 */
@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_RETRIEVAL_RULE)
public class RetrievalRule extends BaseEntity {

    @Column
    private String name;

    @Column
    private String description;

    @Lob
    @Column(name = "rule_string", columnDefinition = "LONGTEXT")
    private String ruleString;

}
