package com.fixedsource.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SchemaService {

    private final JdbcTemplate jdbc;

    public SchemaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initialize() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS enterprise (
                    id VARCHAR(32) PRIMARY KEY,
                    name VARCHAR(128) NOT NULL,
                    industry VARCHAR(64),
                    region VARCHAR(32),
                    management_level VARCHAR(16),
                    credit_code VARCHAR(32),
                    risk_score INT DEFAULT 0,
                    risk_level VARCHAR(2) DEFAULT 'B',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS monitor_point (
                    id VARCHAR(32) PRIMARY KEY,
                    enterprise_id VARCHAR(32) NOT NULL,
                    outlet_name VARCHAR(64),
                    point_name VARCHAR(64),
                    point_type VARCHAR(16),
                    status VARCHAR(16) DEFAULT 'NORMAL'
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS pollutant (
                    code VARCHAR(16) PRIMARY KEY,
                    name VARCHAR(64),
                    unit VARCHAR(16),
                    limit_value DOUBLE,
                    standard_ref VARCHAR(128)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS monitoring_record (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    enterprise_id VARCHAR(32),
                    point_id VARCHAR(32),
                    pollutant_code VARCHAR(16),
                    monitor_time TIMESTAMP,
                    measured_value DOUBLE,
                    converted_value DOUBLE,
                    flow_value DOUBLE,
                    data_status VARCHAR(16),
                    batch_no VARCHAR(40),
                    UNIQUE(point_id, pollutant_code, monitor_time)
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_monitor_time ON monitoring_record(monitor_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_monitor_point ON monitoring_record(point_id, pollutant_code, monitor_time)");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS collect_batch (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    batch_no VARCHAR(40) UNIQUE,
                    biz_date DATE,
                    source VARCHAR(32),
                    status VARCHAR(20),
                    expected_count INT,
                    received_count INT,
                    success_count INT,
                    failed_count INT,
                    started_at TIMESTAMP,
                    finished_at TIMESTAMP,
                    message VARCHAR(512),
                    original_batch_no VARCHAR(40)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS rule_config (
                    rule_code VARCHAR(16) PRIMARY KEY,
                    name VARCHAR(64),
                    category VARCHAR(32),
                    granularity VARCHAR(32),
                    window_hours INT,
                    threshold_expr VARCHAR(128),
                    enabled BOOLEAN DEFAULT TRUE,
                    version VARCHAR(16),
                    description VARCHAR(512)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS clue (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    clue_no VARCHAR(40) UNIQUE,
                    enterprise_id VARCHAR(32),
                    point_id VARCHAR(32),
                    pollutant_code VARCHAR(16),
                    rule_code VARCHAR(16),
                    rule_name VARCHAR(64),
                    severity VARCHAR(12),
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    trigger_value DOUBLE,
                    threshold_value DOUBLE,
                    evidence_json CLOB,
                    fingerprint VARCHAR(255) UNIQUE,
                    batch_no VARCHAR(40),
                    status VARCHAR(20) DEFAULT 'PENDING',
                    source VARCHAR(20) DEFAULT 'AUTO',
                    event_id BIGINT,
                    credibility INT DEFAULT 60,
                    credibility_json CLOB,
                    ai_status VARCHAR(20) DEFAULT 'NONE',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    confirmed_by VARCHAR(64),
                    confirmed_at TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS abnormal_event (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_no VARCHAR(40) UNIQUE,
                    event_key VARCHAR(255) UNIQUE,
                    enterprise_id VARCHAR(32),
                    point_id VARCHAR(32),
                    pollutant_code VARCHAR(16),
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    title VARCHAR(200),
                    clue_count INT,
                    max_severity VARCHAR(12),
                    priority VARCHAR(4),
                    risk_score INT,
                    credibility INT,
                    status VARCHAR(20) DEFAULT 'PENDING',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_assessment (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    clue_id BIGINT,
                    model VARCHAR(64),
                    prompt_version VARCHAR(32),
                    content_json CLOB,
                    citations_json CLOB,
                    confidence DOUBLE,
                    refusal BOOLEAN,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inspection_task (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_no VARCHAR(40) UNIQUE,
                    clue_id BIGINT,
                    event_id BIGINT,
                    enterprise_id VARCHAR(32),
                    title VARCHAR(200),
                    status VARCHAR(20) DEFAULT 'PENDING_DISPATCH',
                    assignee VARCHAR(64),
                    external_no VARCHAR(64),
                    dispatched_at TIMESTAMP,
                    feedback_at TIMESTAMP,
                    closed_at TIMESTAMP,
                    sla_hours INT DEFAULT 24,
                    due_at TIMESTAMP,
                    urge_count INT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS task_feedback (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    feedback_no VARCHAR(48) UNIQUE,
                    task_id BIGINT,
                    task_no VARCHAR(40),
                    inspector VARCHAR(64),
                    result VARCHAR(20),
                    problem VARCHAR(1000),
                    disposal VARCHAR(1000),
                    attachment_url VARCHAR(512),
                    watermark VARCHAR(512),
                    latitude DOUBLE,
                    longitude DOUBLE,
                    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS dispatch_outbox (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    message_no VARCHAR(48) UNIQUE,
                    task_id BIGINT,
                    task_no VARCHAR(40),
                    target VARCHAR(64),
                    status VARCHAR(20),
                    retry_count INT DEFAULT 0,
                    request_json CLOB,
                    response_json CLOB,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS risk_snapshot (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    enterprise_id VARCHAR(32),
                    score INT,
                    level VARCHAR(2),
                    factors_json CLOB,
                    weight_version VARCHAR(16),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    action VARCHAR(64),
                    target_type VARCHAR(32),
                    target_id VARCHAR(64),
                    operator VARCHAR(64),
                    detail VARCHAR(1000),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_quality_issue (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    batch_no VARCHAR(40),
                    monitor_date DATE,
                    enterprise_id VARCHAR(32),
                    point_id VARCHAR(32),
                    pollutant_code VARCHAR(16),
                    issue_type VARCHAR(32),
                    severity VARCHAR(12),
                    detail VARCHAR(1000),
                    status VARCHAR(16) DEFAULT 'OPEN',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(batch_no, monitor_date, point_id, pollutant_code, issue_type)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_source_config (
                    id VARCHAR(32) PRIMARY KEY,
                    name VARCHAR(128),
                    source_type VARCHAR(32),
                    base_url VARCHAR(256),
                    auth_type VARCHAR(32),
                    enabled BOOLEAN DEFAULT TRUE,
                    version VARCHAR(32),
                    description VARCHAR(512),
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS test_anomaly_label (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    biz_date DATE,
                    enterprise_id VARCHAR(32),
                    point_id VARCHAR(32),
                    pollutant_code VARCHAR(16),
                    rule_code VARCHAR(16),
                    anomaly_type VARCHAR(32),
                    start_hour INT,
                    end_hour INT,
                    expected_severity VARCHAR(12),
                    scenario VARCHAR(32),
                    description VARCHAR(512),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(biz_date, point_id, pollutant_code, rule_code, start_hour)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS backtest_run (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    run_no VARCHAR(48) UNIQUE,
                    start_date DATE,
                    end_date DATE,
                    rule_version VARCHAR(32),
                    total_labels INT,
                    detected_count INT,
                    true_positive INT,
                    false_positive INT,
                    false_negative INT,
                    precision_value DOUBLE,
                    recall_value DOUBLE,
                    f1_value DOUBLE,
                    metrics_json CLOB,
                    created_by VARCHAR(64),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inspection_report (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    report_no VARCHAR(48) UNIQUE,
                    event_id BIGINT,
                    title VARCHAR(256),
                    content_html CLOB,
                    content_json CLOB,
                    status VARCHAR(16) DEFAULT 'DRAFT',
                    created_by VARCHAR(64),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        // 公开参考数据：重点排污单位公开名录、标准与监测指南元数据等。
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public_reference_item (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    dataset_code VARCHAR(48) NOT NULL,
                    record_key VARCHAR(64) NOT NULL,
                    title VARCHAR(160),
                    category VARCHAR(64),
                    region VARCHAR(48),
                    industry VARCHAR(64),
                    credit_code VARCHAR(64),
                    standard_code VARCHAR(64),
                    issuer VARCHAR(128),
                    publish_date DATE,
                    effective_date DATE,
                    value_text VARCHAR(512),
                    scope_text VARCHAR(1024),
                    source_name VARCHAR(128),
                    source_url VARCHAR(512),
                    annual VARCHAR(16),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(dataset_code, record_key)
                )
                """);

        // 国家数据仓 / 数据共享平台。独立于任务移动端 outbox，记录面向外部平台的数据包与投递回执。
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_catalog (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(48) UNIQUE NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    category VARCHAR(32),
                    sensitivity VARCHAR(16) DEFAULT 'INNER',
                    source_api VARCHAR(255),
                    description VARCHAR(512),
                    enabled BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_subscriber (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(32) UNIQUE NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    system_type VARCHAR(32),
                    endpoint VARCHAR(255) NOT NULL,
                    app_key VARCHAR(96) UNIQUE,
                    app_secret VARCHAR(128),
                    datasets VARCHAR(512),
                    enabled BOOLEAN DEFAULT TRUE,
                    last_dispatch_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_package (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    package_no VARCHAR(48) UNIQUE NOT NULL,
                    dataset_code VARCHAR(48) NOT NULL,
                    dataset_name VARCHAR(128),
                    biz_date DATE,
                    record_count INT DEFAULT 0,
                    payload_json CLOB,
                    payload_hash VARCHAR(80),
                    status VARCHAR(24) DEFAULT 'PUBLISHED',
                    created_by VARCHAR(64),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_dispatch (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    dispatch_no VARCHAR(64) UNIQUE NOT NULL,
                    package_id BIGINT NOT NULL,
                    package_no VARCHAR(48) NOT NULL,
                    subscriber_id BIGINT NOT NULL,
                    subscriber_code VARCHAR(32),
                    subscriber_name VARCHAR(128),
                    dataset_code VARCHAR(48),
                    endpoint VARCHAR(255),
                    status VARCHAR(24) NOT NULL,
                    http_status INT,
                    latency_ms INT DEFAULT 0,
                    retry_count INT DEFAULT 0,
                    payload_hash VARCHAR(80),
                    signature VARCHAR(160),
                    response_body VARCHAR(2000),
                    error_message VARCHAR(500),
                    sent_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_receipt (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    receipt_no VARCHAR(64) UNIQUE NOT NULL,
                    package_no VARCHAR(48) NOT NULL,
                    subscriber_code VARCHAR(32) NOT NULL,
                    dataset_code VARCHAR(48),
                    record_count INT DEFAULT 0,
                    payload_hash VARCHAR(80),
                    app_key VARCHAR(96),
                    signature VARCHAR(160),
                    status VARCHAR(16) DEFAULT 'ACCEPTED',
                    summary VARCHAR(512),
                    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(package_no, subscriber_code)
                )
                """);

        // 企业台账扩展字段：兼容已有 H2 文件库，启动时幂等补列。
        for (String ddl : new String[]{
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS address VARCHAR(255)",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS legal_representative VARCHAR(64)",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS contact_name VARCHAR(64)",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(32)",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS longitude DOUBLE",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS latitude DOUBLE",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS production_status VARCHAR(16) DEFAULT 'NORMAL'",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS permit_no VARCHAR(64)",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'ACTIVE'",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS remark VARCHAR(512)",
                "ALTER TABLE enterprise ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                "ALTER TABLE monitor_point ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
        }) {
            jdbc.execute(ddl);
        }
    }
}
