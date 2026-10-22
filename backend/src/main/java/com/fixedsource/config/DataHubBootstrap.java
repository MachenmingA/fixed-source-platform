package com.fixedsource.config;

import com.fixedsource.service.DataHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Docker 演示环境首次启动时，通过真实 HTTP 数据服务出口发布一组数据包，
 * 让国家数据仓页面一打开就有可追踪的目录、订阅、投递和回执数据。
 */
@Component
@Profile("docker")
public class DataHubBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DataHubBootstrap.class);
    private final DataHubService dataHubService;
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public DataHubBootstrap(DataHubService dataHubService, JdbcTemplate jdbc,
                            @Value("${app.data-hub.bootstrap:true}") boolean enabled) {
        this.dataHubService = dataHubService;
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        if (!enabled) return;
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2500);
                Integer packages = jdbc.queryForObject("SELECT COUNT(*) FROM data_package", Integer.class);
                if (packages != null && packages > 0) return;
                for (String code : List.of("ENTERPRISE_MASTER", "MONITOR_POINT_MASTER", "POLLUTANT_STANDARD",
                        "MONITORING_HOURLY", "MONITORING_DAILY", "KEY_DISCHARGE_UNIT", "STANDARD_LIBRARY")) {
                    try {
                        dataHubService.publish(code, null, "system");
                    } catch (Exception e) {
                        log.warn("Data hub bootstrap publish {} failed: {}", code, e.getMessage());
                    }
                }
                log.info("Data hub bootstrap completed.");
            } catch (Exception e) {
                log.warn("Data hub bootstrap skipped: {}", e.getMessage());
            }
        });
    }
}
