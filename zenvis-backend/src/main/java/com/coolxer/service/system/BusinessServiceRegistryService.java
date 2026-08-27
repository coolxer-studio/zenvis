package com.coolxer.service.system;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.BusinessServiceEventDto;
import com.coolxer.model.system.dto.BusinessServiceEventSearchDto;
import com.coolxer.model.system.dto.BusinessServiceHeartbeatDto;
import com.coolxer.model.system.dto.BusinessServiceInstanceSearchDto;
import com.coolxer.model.system.vo.BusinessServiceEventAckVo;
import com.coolxer.model.system.vo.BusinessServiceEventVo;
import com.coolxer.model.system.vo.BusinessServiceHeartbeatAckVo;
import com.coolxer.model.system.vo.BusinessServiceInstanceVo;
import com.coolxer.model.system.vo.BusinessServiceSummaryVo;

public interface BusinessServiceRegistryService {

    BusinessServiceHeartbeatAckVo reportHeartbeat(BusinessServiceHeartbeatDto dto, String remoteAddress);

    BusinessServiceEventAckVo reportEvent(BusinessServiceEventDto dto, String remoteAddress);

    BusinessServiceSummaryVo summary();

    PageRowsVo<BusinessServiceInstanceVo> getInstancePage(BusinessServiceInstanceSearchDto search);

    BusinessServiceInstanceVo getInstance(Integer id);

    PageRowsVo<BusinessServiceEventVo> getEventPage(BusinessServiceEventSearchDto search);

    void cleanupExpiredData();
}
