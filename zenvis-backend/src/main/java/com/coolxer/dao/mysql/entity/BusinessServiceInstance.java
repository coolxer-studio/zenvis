package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.BusinessServiceReportedStatus;
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
 * 业务应用服务实例的最新注册状态。
 */
@Data
@Entity
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_SYS_BUSINESS_SERVICE_INSTANCE,
        uniqueConstraints = @UniqueConstraint(name = "uk_business_service_instance",
                columnNames = {"service_code", "instance_id"}),
        indexes = {
                @Index(name = "idx_business_service_code", columnList = "service_code"),
                @Index(name = "idx_business_service_heartbeat", columnList = "last_heartbeat_time"),
                @Index(name = "idx_business_service_status", columnList = "reported_status")
        })
public class BusinessServiceInstance extends BaseEntity {

    @Column(name = "service_code", nullable = false, length = 64)
    private String serviceCode;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(name = "instance_id", nullable = false, length = 128)
    private String instanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reported_status", nullable = false, length = 16)
    private BusinessServiceReportedStatus reportedStatus;

    @Column(name = "status_message", length = 512)
    private String statusMessage;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "environment", length = 64)
    private String environment;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "management_url", length = 512)
    private String managementUrl;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "remote_address", length = 64)
    private String remoteAddress;

    @Column(name = "first_heartbeat_time", nullable = false)
    private Date firstHeartbeatTime;

    @Column(name = "last_heartbeat_time", nullable = false)
    private Date lastHeartbeatTime;

    @Column(name = "reported_heartbeat_time")
    private Date reportedHeartbeatTime;

    @Column(name = "last_event_time")
    private Date lastEventTime;
}
