package com.coolxer.dao.mysql.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 实体通用属性
 */
@Data
@Accessors(chain = true)
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @CreatedDate
    @Column(name = "create_time", updatable = false, columnDefinition = "timestamp default current_timestamp")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time", columnDefinition = "timestamp default current_timestamp")
    private Date updateTime;

    @CreatedBy
    @Column(name = "create_by")
    private Integer createBy;

    @LastModifiedBy
    @Column(name = "update_by")
    private Integer updateBy;

    /**
     * 是否删除(仅逻辑删除时需要)
     * 1:删除
     * 0:默认
     */
    @Column(name = "is_delete", columnDefinition = "int default 0")
    private Integer isDelete = 0;
}
