package com.coolxer.model.system.dto;

import com.coolxer.commons.enums.BusinessServiceReportedStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
public class BusinessServiceHeartbeatDto {

    @NotBlank(message = "服务标识不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$", message = "服务标识格式不正确")
    private String serviceCode;

    @NotBlank(message = "服务名称不能为空")
    @Size(max = 128, message = "服务名称不能超过128个字符")
    private String serviceName;

    @NotBlank(message = "实例标识不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$", message = "实例标识格式不正确")
    private String instanceId;

    @NotNull(message = "服务状态不能为空")
    private BusinessServiceReportedStatus status;

    @Size(max = 512, message = "状态说明不能超过512个字符")
    private String statusMessage;

    @Size(max = 64, message = "版本不能超过64个字符")
    private String version;

    @Size(max = 64, message = "环境不能超过64个字符")
    private String environment;

    @Size(max = 255, message = "主机地址不能超过255个字符")
    private String host;

    @Min(value = 1, message = "端口必须大于0")
    @Max(value = 65535, message = "端口不能超过65535")
    private Integer port;

    @Size(max = 512, message = "管理地址不能超过512个字符")
    private String managementUrl;

    private Date heartbeatTime;

    private Map<String, Object> metadata;
}
