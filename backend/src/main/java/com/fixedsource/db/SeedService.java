package com.fixedsource.db;

import com.fixedsource.service.AiService;
import com.fixedsource.service.BacktestService;
import com.fixedsource.service.DataQualityService;
import com.fixedsource.service.EventRiskService;
import com.fixedsource.service.LabelService;
import com.fixedsource.service.MockSourceService;
import com.fixedsource.service.RuleEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private final JdbcTemplate jdbc;
    private final MockSourceService mockSource;
    private final RuleEngineService ruleEngine;
    private final AiService aiService;
    private final EventRiskService eventRiskService;
    private final LabelService labelService;
    private final DataQualityService dataQualityService;
    private final BacktestService backtestService;

    private record EnterpriseExtra(String address, String legalRepresentative, String contactName,
                                   String contactPhone, double longitude, double latitude,
                                   String productionStatus, String permitNo) {}

    private static final Map<String, EnterpriseExtra> ENTERPRISE_EXTRA = buildEnterpriseExtra();

    private static Map<String, EnterpriseExtra> buildEnterpriseExtra() {
        Map<String, EnterpriseExtra> map = new LinkedHashMap<>();
        map.put("E001", new EnterpriseExtra("北京市海淀区永丰路12号", "张建国", "李强", "010-68761188",
                116.2869, 40.0416, "NORMAL", "91110108MA01HC001001P"));
        map.put("E002", new EnterpriseExtra("北京市朝阳区金盏北路8号", "王海峰", "赵敏", "010-85762233",
                116.4953, 39.9490, "NORMAL", "91110105MA01BC002001P"));
        map.put("E003", new EnterpriseExtra("北京市昌平区生命科学园路21号", "陈立", "刘莹", "010-80726655",
                116.2479, 40.2201, "NORMAL", "91110114MA01JB003001P"));
        map.put("E004", new EnterpriseExtra("北京市通州区宋庄镇工业区6号", "侯志强", "孙倩", "010-89586677",
                116.6589, 39.9090, "LIMITED", "91110112MA01TD004001P"));
        map.put("E005", new EnterpriseExtra("北京市丰台区南苑路36号", "周文", "杨帆", "010-67991234",
                116.2870, 39.8580, "NORMAL", "91110106MA01LY005001P"));
        map.put("E006", new EnterpriseExtra("北京市大兴区亦庄经济开发区兴业街9号", "马俊", "高蕾", "010-67893322",
                116.3410, 39.7260, "NORMAL", "91110115MA01HX006001P"));
        map.put("E007", new EnterpriseExtra("北京市房山区燕山新材料基地纬三路5号", "冯磊", "韩梅", "010-69345566",
                116.1430, 39.7490, "LIMITED", "91110111MA01YS007001P"));
        map.put("E008", new EnterpriseExtra("北京市顺义区空港工业区B区天纬四街7号", "朱明", "董然", "010-80497788",
                116.6540, 40.1300, "NORMAL", "91110113MA01QY008001P"));
        map.put("E009", new EnterpriseExtra("北京市海淀区永丰产业基地丰贤中路15号", "刘志远", "沈悦", "010-62887766",
                116.3100, 40.0300, "NORMAL", "91110108MA01RK009001P"));
        map.put("E010", new EnterpriseExtra("北京市石景山区八大处路27号", "韩立", "崔杰", "010-68873344",
                116.2230, 39.9060, "NORMAL", "91110107MA01JQ010001P"));
        return map;
    }

    public SeedService(JdbcTemplate jdbc, MockSourceService mockSource, RuleEngineService ruleEngine,
                       AiService aiService, EventRiskService eventRiskService, LabelService labelService,
                       DataQualityService dataQualityService, BacktestService backtestService) {
        this.jdbc = jdbc;
        this.mockSource = mockSource;
        this.ruleEngine = ruleEngine;
        this.aiService = aiService;
        this.eventRiskService = eventRiskService;
        this.labelService = labelService;
        this.dataQualityService = dataQualityService;
        this.backtestService = backtestService;
    }

    @Transactional
    public void seedIfNeeded() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM enterprise", Integer.class);
        if (count != null && count > 0) {
            log.info("Seed data already exists, skip.");
            return;
        }
        log.info("Seeding fixed-source demo data...");
        insertBaseData();
        String batchNo = "SEED-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int inserted = insertMonitoring(batchNo, LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));
        jdbc.update("""
                INSERT INTO collect_batch(batch_no,biz_date,source,status,expected_count,received_count,success_count,failed_count,
                    started_at,finished_at,message)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,
                batchNo, Date.valueOf(LocalDate.now().minusDays(1)), "MOCK-SEED", "SUCCESS",
                mockSource.totalExpectedPerDay() * 30, inserted, inserted, 0,
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(2)), Timestamp.valueOf(LocalDateTime.now()),
                "初始化 30 天模拟监测数据，含最近 7 天异常剧本");
        for (int offset = 7; offset >= 1; offset--) {
            LocalDate date = LocalDate.now().minusDays(offset);
            ruleEngine.runForDate(date, batchNo);
            aiService.assessNewClues(date);
            eventRiskService.rebuildForDate(date);
        }
        eventRiskService.rebuildRiskForAll();
        log.info("Seed complete: {} monitoring rows, clues={}", inserted, clueCount());
    }

    public void ensureSupplementalData() {
        LocalDate today = LocalDate.now();
        ensureEnterpriseProfileData();
        ensurePublicReferenceData();
        ensureDataHubData();
        Integer sources = jdbc.queryForObject("SELECT COUNT(*) FROM data_source_config", Integer.class);
        if (sources == null || sources == 0) {
            jdbc.update("INSERT INTO data_source_config(id,name,source_type,base_url,auth_type,enabled,version,description) VALUES (?,?,?,?,?,?,?,?)",
                    "MOCK-SOURCE", "北京市固定污染源模拟数据源", "MOCK", "http://127.0.0.1:8080/mock-source/v1",
                    "AppKey/HMAC", true, "v1", "独立模拟源，后续可切换真实监测平台接口");
        }
        boolean todaySeeded = ensureTodayData();
        int labels = labelService.syncRange(today.minusDays(7), today);
        int dq = dataQualityService.backfill(today.minusDays(7), today);
        Integer runs = jdbc.queryForObject("SELECT COUNT(*) FROM backtest_run", Integer.class);
        if (todaySeeded || runs == null || runs == 0) {
            backtestService.run(today.minusDays(7), today, "system");
        }
        log.info("Supplemental data ready: todaySeeded={}, labels={}, dataQualityIssues={}", todaySeeded, labels, dq);
    }

    private void ensureDataHubData() {
        // 数据发送平台只负责基础数据。若检测到上一版包含事件/风险/任务等加工结果的旧目录，
        // 清空国家数据仓的数据包与投递历史，重新按基础数据域生成演示数据。
        Integer legacy = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_catalog
                WHERE code IN ('ABNORMAL_EVENT','RISK_PROFILE','TASK_CLOSURE')
                """, Integer.class);
        Integer publicCatalog = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_catalog WHERE code='KEY_DISCHARGE_UNIT'
                """, Integer.class);
        boolean needReset = (legacy != null && legacy > 0) || (publicCatalog == null || publicCatalog == 0);
        if (needReset) {
            jdbc.update("DELETE FROM data_receipt");
            jdbc.update("DELETE FROM data_dispatch");
            jdbc.update("DELETE FROM data_package");
            jdbc.update("DELETE FROM data_catalog");
        }
        String[][] catalogs = {
                {"ENTERPRISE_MASTER", "企业基础信息", "基础数据", "CONTROLLED",
                        "/api/data-service/v1/export/ENTERPRISE_MASTER",
                        "企业主体、生产状态、排污许可、地址、联络信息与地图坐标"},
                {"MONITOR_POINT_MASTER", "排口与监测点", "基础数据", "CONTROLLED",
                        "/api/data-service/v1/export/MONITOR_POINT_MASTER",
                        "企业排口、监测点类型、运行状态与关联污染物"},
                {"POLLUTANT_STANDARD", "污染物与排放限值", "标准数据", "PUBLIC",
                        "/api/data-service/v1/export/POLLUTANT_STANDARD",
                        "污染物编码、计量单位、排放限值与标准依据"},
                {"MONITORING_HOURLY", "小时监测明细", "监测数据", "CONTROLLED",
                        "/api/data-service/v1/export/MONITORING_HOURLY",
                        "排口/监测点小时实测值、折算值、流量与数据状态"},
                {"MONITORING_DAILY", "日监测指标", "监测数据", "CONTROLLED",
                        "/api/data-service/v1/export/MONITORING_DAILY",
                        "按企业、点位、污染物聚合的日样本数、最小/最大/均值与异常数"},
                {"KEY_DISCHARGE_UNIT", "重点排污单位公开名录", "公开数据", "PUBLIC",
                        "/api/data-service/v1/export/KEY_DISCHARGE_UNIT",
                        "来源：北京市生态环境局、朝阳区生态环境局历年公开名录；含企业名称、区域、信用代码和名录类别"},
                {"STANDARD_LIBRARY", "公开标准与依据", "公开数据", "PUBLIC",
                        "/api/data-service/v1/export/STANDARD_LIBRARY",
                        "来源：国家标准全文公开系统、生态环境部；含标准号、发布/实施日期、适用范围和数据平台入口"}
        };
        for (String[] item : catalogs) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM data_catalog WHERE code=?", Integer.class, item[0]);
            if (exists == null || exists == 0) {
                jdbc.update("""
                        INSERT INTO data_catalog(code,name,category,sensitivity,source_api,description,enabled)
                        VALUES (?,?,?,?,?,?,TRUE)
                        """, item[0], item[1], item[2], item[3], item[4], item[5]);
            }
            jdbc.update("UPDATE data_catalog SET source_api=? WHERE code=? AND source_api<>?", item[4], item[0], item[4]);
        }
        String[][] subscribers = {
                {"NPE", "国家排污许可信息平台", "国家级", "/mock-consumer/v1/npe/ingest",
                        "npe-open-key", "npe-demo-secret-2026",
                        "ENTERPRISE_MASTER,MONITOR_POINT_MASTER,MONITORING_DAILY,KEY_DISCHARGE_UNIT,STANDARD_LIBRARY"},
                {"ENF", "北京市生态环境执法平台", "省级", "/mock-consumer/v1/enf/ingest",
                        "enf-open-key", "enf-demo-secret-2026",
                        "ENTERPRISE_MASTER,MONITOR_POINT_MASTER,MONITORING_HOURLY,POLLUTANT_STANDARD,KEY_DISCHARGE_UNIT,STANDARD_LIBRARY"},
                {"CITY", "城市运行管理平台", "市级", "/mock-consumer/v1/city/ingest",
                        "city-open-key", "city-demo-secret-2026",
                        "MONITORING_HOURLY,MONITORING_DAILY,POLLUTANT_STANDARD,STANDARD_LIBRARY"},
                {"OPEN", "国家公共数据开放平台", "国家级", "/mock-consumer/v1/open/ingest",
                        "open-open-key", "open-demo-secret-2026",
                        "ENTERPRISE_MASTER,MONITOR_POINT_MASTER,POLLUTANT_STANDARD,MONITORING_DAILY,KEY_DISCHARGE_UNIT,STANDARD_LIBRARY"},
                {"EDGE", "海淀区生态环境分局边缘节点", "区级", "/mock-consumer/v1/edge/ingest",
                        "edge-open-key", "edge-demo-secret-2026",
                        "MONITORING_HOURLY,MONITORING_DAILY", "FALSE"}
        };
        for (String[] item : subscribers) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM data_subscriber WHERE code=?", Integer.class, item[0]);
            if (exists == null || exists == 0) {
                jdbc.update("""
                        INSERT INTO data_subscriber(code,name,system_type,endpoint,app_key,app_secret,datasets,enabled)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, item[0], item[1], item[2], item[3], item[4], item[5], item[6],
                        item.length < 8 || Boolean.parseBoolean(item[7]));
            } else {
                // 订阅关系按基础数据目录收敛；接收地址、密钥和启停状态保留人工配置。
                jdbc.update("UPDATE data_subscriber SET datasets=? WHERE code=?", item[6], item[0]);
            }
        }
    }

    private void ensurePublicReferenceData() {
        Integer units = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public_reference_item WHERE dataset_code='KEY_DISCHARGE_UNIT'
                """, Integer.class);
        if (units == null || units == 0) {
            for (PublicDataSeed.KeyUnit item : PublicDataSeed.KEY_UNITS) {
                jdbc.update("""
                        INSERT INTO public_reference_item(dataset_code,record_key,title,category,region,
                            credit_code,source_name,source_url,annual)
                        VALUES ('KEY_DISCHARGE_UNIT',?,?,?,?,?,?,?,?)
                        """, item.recordKey(), item.title(), item.category(), item.region(),
                        item.creditCode(), item.sourceName(), item.sourceUrl(), item.annual());
            }
        }
        Integer standards = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public_reference_item WHERE dataset_code='STANDARD_LIBRARY'
                """, Integer.class);
        if (standards == null || standards == 0) {
            for (PublicDataSeed.Standard item : PublicDataSeed.STANDARDS) {
                jdbc.update("""
                        INSERT INTO public_reference_item(dataset_code,record_key,title,category,standard_code,
                            issuer,publish_date,effective_date,scope_text,source_name,source_url)
                        VALUES ('STANDARD_LIBRARY',?,?,?,?,?,?,?,?,?,?)
                        """, item.recordKey(), item.title(), item.category(), item.standardCode(),
                        item.issuer(), java.sql.Date.valueOf(item.publishDate()),
                        java.sql.Date.valueOf(item.effectiveDate()), item.scope(),
                        item.sourceName(), item.sourceUrl());
            }
        }
    }

    private void ensureEnterpriseProfileData() {
        for (MockSourceService.EnterpriseDef enterprise : MockSourceService.ENTERPRISES) {
            EnterpriseExtra extra = ENTERPRISE_EXTRA.get(enterprise.id());
            if (extra == null) continue;
            jdbc.update("""
                    UPDATE enterprise SET
                        address=COALESCE(address, ?),
                        legal_representative=COALESCE(legal_representative, ?),
                        contact_name=COALESCE(contact_name, ?),
                        contact_phone=COALESCE(contact_phone, ?),
                        longitude=COALESCE(longitude, ?),
                        latitude=COALESCE(latitude, ?),
                        production_status=COALESCE(production_status, ?),
                        permit_no=COALESCE(permit_no, ?),
                        status=COALESCE(status, 'ACTIVE'),
                        updated_at=COALESCE(updated_at, CURRENT_TIMESTAMP)
                    WHERE id=?
                    """, extra.address(), extra.legalRepresentative(), extra.contactName(), extra.contactPhone(),
                    extra.longitude(), extra.latitude(), extra.productionStatus(), extra.permitNo(), enterprise.id());
        }
    }

    private boolean ensureTodayData() {
        LocalDate today = LocalDate.now();
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM monitoring_record WHERE monitor_time >= ? AND monitor_time < ?
                """, Integer.class, Timestamp.valueOf(today.atStartOfDay()), Timestamp.valueOf(today.plusDays(1).atStartOfDay()));
        if (count != null && count > 0) return false;
        String batchNo = "SEED-TODAY-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int inserted = insertMonitoring(batchNo, today, today);
        jdbc.update("""
                INSERT INTO collect_batch(batch_no,biz_date,source,status,expected_count,received_count,success_count,failed_count,
                    started_at,finished_at,message)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, batchNo, Date.valueOf(today), "MOCK-SEED-TODAY", "SUCCESS",
                mockSource.totalExpectedPerDay(), inserted, inserted, 0,
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), Timestamp.valueOf(LocalDateTime.now()),
                "初始化当日模拟数据，补齐今日测试标签与线索");
        ruleEngine.runForDate(today, batchNo);
        aiService.assessNewClues(today);
        eventRiskService.rebuildForDate(today);
        eventRiskService.rebuildRiskForAll();
        log.info("Today's demo data seeded: {} rows", inserted);
        return true;
    }

    private void insertBaseData() {
        for (MockSourceService.EnterpriseDef e : MockSourceService.ENTERPRISES) {
            jdbc.update("INSERT INTO enterprise(id,name,industry,region,management_level,credit_code,risk_score,risk_level) VALUES (?,?,?,?,?,?,?,?)",
                    e.id(), e.name(), e.industry(), e.region(), e.level(), e.creditCode(), 0, "B");
        }
        for (MockSourceService.PointDef p : MockSourceService.POINTS) {
            jdbc.update("INSERT INTO monitor_point(id,enterprise_id,outlet_name,point_name,point_type,status) VALUES (?,?,?,?,?,?)",
                    p.id(), p.enterpriseId(), p.outletName(), p.pointName(), p.type(), "NORMAL");
        }
        for (MockSourceService.PollutantDef p : MockSourceService.POLLUTANTS) {
            jdbc.update("INSERT INTO pollutant(code,name,unit,limit_value,standard_ref) VALUES (?,?,?,?,?)",
                    p.code(), p.name(), p.unit(), p.limit(), p.standardRef());
        }
        String[][] rules = {
                {"R001", "浓度超限", "超限类", "1 小时", "1", "value > limit", "true", "v1.0", "实测浓度超过适用限值，按污染物和排放口径配置"},
                {"R002", "数据缺失", "数据质量类", "24 小时", "24", "missing >= 3", "true", "v1.0", "窗口内有效小时数不足，连续缺失达到 3 小时"},
                {"R003", "连续恒值", "数据质量类", "24 小时", "24", "constant >= 6h", "true", "v1.0", "连续 6 小时及以上数值完全相同且非零"},
                {"R004", "异常突变", "数据质量类", "24 小时", "24", "abs(change) >= 100%", "true", "v1.0", "相邻小时变化率达到 100%"},
                {"R005", "状态不一致", "状态一致性类", "1 小时", "1", "status in (FAULT,STOP) and value > 0", "true", "v1.0", "停运、故障、校准等状态与排放数值矛盾"},
                {"R006", "多参数联动", "关联规则类", "24 小时", "24", "joint change", "false", "v0.9", "相邻窗口内多参数联动变化，首期不启用"}
        };
        for (String[] r : rules) {
            jdbc.update("INSERT INTO rule_config(rule_code,name,category,granularity,window_hours,threshold_expr,enabled,version,description) VALUES (?,?,?,?,?,?,?,?,?)",
                    r[0], r[1], r[2], r[3], Integer.parseInt(r[4]), r[5], Boolean.parseBoolean(r[6]), r[7], r[8]);
        }
    }

    private int insertMonitoring(String batchNo, LocalDate start, LocalDate end) {
        String sql = """
                MERGE INTO monitoring_record(enterprise_id,point_id,pollutant_code,monitor_time,measured_value,
                    converted_value,flow_value,data_status,batch_no)
                KEY(point_id,pollutant_code,monitor_time)
                VALUES (?,?,?,?,?,?,?,?,?)
                """;
        List<Object[]> args = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            for (MockSourceService.MonitoringRow row : mockSource.generateDay(d)) {
                args.add(new Object[]{row.enterpriseId(), row.pointId(), row.pollutantCode(), Timestamp.valueOf(row.time()),
                        row.value(), row.converted(), row.flow(), row.status(), batchNo});
            }
            d = d.plusDays(1);
        }
        jdbc.batchUpdate(sql, args);
        return args.size();
    }

    private int clueCount() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM clue", Integer.class);
        return n == null ? 0 : n;
    }
}
