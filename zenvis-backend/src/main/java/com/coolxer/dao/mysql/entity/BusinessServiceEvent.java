package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.BusinessServiceEventSeverity;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 业务应用服务上报的事件记录。
 */
@Data
@Entity
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_SYS_BUSINESS_SERVICE_EVENT,
        uniqueConstraints = @UniqueConstraint(name = "uk_business_service_event_id", columnNames = "event_id"),
        indexes = {
                @Index(name = "idx_business_event_instance", columnList = "service_code,instance_id"),
                @Index(name = "idx_business_event_occurred", columnList = "occurred_time"),
                @Index(name = "idx_business_event_created", columnList = "create_time"),
                @Index(name = "idx_business_event_severity", columnList = "severity")
        })
public class BusinessServiceEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "service_instance_id", nullable = false)
    private Integer serviceInstanceId;

    @Column(name = "service_code", nullable = false, length = 64)
    private String serviceCode;

    @Column(name = "instance_id", nullable = false, length = 128)
    private String instanceId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private BusinessServiceEventSeverity severity;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "occurred_time", nullable = false)
    private Date occurredTime;

    @Column(name = "event_data", columnDefinition = "MEDIUMTEXT")
    private String data;

    @Column(name = "remote_address", length = 64)
    private String remoteAddress;
}
