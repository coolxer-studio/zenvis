package com.coolxer.service.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BusinessServiceCleanupScheduler {

    private final BusinessServiceRegistryService businessServiceRegistryService;

    public BusinessServiceCleanupScheduler(BusinessServiceRegistryService businessServiceRegistryService) {
        this.businessServiceRegistryService = businessServiceRegistryService;
    }

    @Scheduled(fixedDelayString = "${app.business-service.cleanup-delay-ms:3600000}")
    public void cleanup() {
        try {
            businessServiceRegistryService.cleanupExpiredData();
        } catch (Exception e) {
            log.error("业务应用服务历史数据清理失败", e);
        }
    }
}
