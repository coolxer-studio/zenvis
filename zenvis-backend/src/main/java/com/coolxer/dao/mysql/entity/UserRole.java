package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 用户角色关系表
 */

@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@EntityListeners(AuditingEntityListener.class)
@Table(name = MysqlFinalTableName.T_SYS_USER_ROLE)
public class UserRole extends BaseEntity {


    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 角色ID
     */
    @Column(name = "role_id")
    private Integer roleId;

}
