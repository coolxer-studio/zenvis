package com.coolxer.model.system.dto;

import com.coolxer.commons.enums.BusinessServiceEventSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
public class BusinessServiceEventDto {

    @NotBlank(message = "事件标识不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$", message = "事件标识格式不正确")
    private String eventId;

    @NotBlank(message = "服务标识不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$", message = "服务标识格式不正确")
    private String serviceCode;

    @NotBlank(message = "实例标识不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$", message = "实例标识格式不正确")
    private String instanceId;

    @NotBlank(message = "事件类型不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$", message = "事件类型格式不正确")
    private String eventType;

    @NotNull(message = "事件级别不能为空")
    private BusinessServiceEventSeverity severity;

    @NotBlank(message = "事件标题不能为空")
    @Size(max = 255, message = "事件标题不能超过255个字符")
    private String title;

    @Size(max = 4000, message = "事件内容不能超过4000个字符")
    private String message;

    private Date occurredAt;

    @Size(max = 128, message = "Trace ID不能超过128个字符")
    private String traceId;

    private Map<String, Object> data;
}
