CREATE TABLE card_product (
    id UUID PRIMARY KEY,
    product_code VARCHAR(40) NOT NULL UNIQUE,
    tier VARCHAR(40) NOT NULL,
    apr_min NUMERIC(5,2) NOT NULL,
    apr_max NUMERIC(5,2) NOT NULL,
    annual_fee NUMERIC(10,2) NOT NULL DEFAULT 0,
    rewards_description VARCHAR(500) NOT NULL,
    credit_limit_min NUMERIC(12,2) NOT NULL,
    credit_limit_max NUMERIC(12,2) NOT NULL,
    minimum_fico INTEGER NOT NULL,
    maximum_dti NUMERIC(5,4) NOT NULL,
    policy_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT card_product_apr_range CHECK (apr_min >= 0 AND apr_max >= apr_min),
    CONSTRAINT card_product_limit_range CHECK (credit_limit_min > 0 AND credit_limit_max >= credit_limit_min),
    CONSTRAINT card_product_dti_range CHECK (maximum_dti BETWEEN 0 AND 1)
);

CREATE TABLE application (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES card_product(id),
    applicant_email VARCHAR(254) NOT NULL,
    applicant_first_name VARCHAR(100) NOT NULL,
    applicant_last_name VARCHAR(100) NOT NULL,
    ssn_hash VARCHAR(64) NOT NULL,
    ssn_last4 VARCHAR(4) NOT NULL,
    date_of_birth_encrypted BYTEA,
    annual_income NUMERIC(14,2) NOT NULL,
    monthly_housing_payment NUMERIC(12,2) NOT NULL,
    monthly_debt_payment NUMERIC(12,2) NOT NULL,
    employment_status VARCHAR(40) NOT NULL,
    address_line_encrypted BYTEA,
    address_city VARCHAR(100),
    address_state VARCHAR(2),
    address_postal_code VARCHAR(20),
    bureau_score INTEGER,
    bureau_file_type VARCHAR(32),
    bureau_fraud_alert BOOLEAN NOT NULL DEFAULT FALSE,
    address_validation_status VARCHAR(32),
    status VARCHAR(48) NOT NULL,
    customer_message VARCHAR(500),
    correlation_id VARCHAR(100) NOT NULL,
    mock_persona VARCHAR(48),
    submitted_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    embossing_exported_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_application_status_submitted ON application(status, submitted_at);
CREATE INDEX idx_application_expiration ON application(expires_at) WHERE status IN ('SUBMITTED', 'UNDERWRITING', 'MANUAL_REVIEW');
CREATE INDEX idx_application_product_submitted ON application(product_id, submitted_at);

CREATE TABLE decision (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application(id),
    outcome VARCHAR(32) NOT NULL,
    internal_reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    fico_score INTEGER,
    dti_ratio NUMERIC(7,6),
    approved_limit NUMERIC(12,2),
    assigned_apr NUMERIC(5,2),
    policy_version VARCHAR(80) NOT NULL,
    decided_by VARCHAR(120) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_decision_application ON decision(application_id, decided_at DESC);

CREATE TABLE sensitive_validation (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application(id),
    validation_category VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(200),
    encrypted_evidence BYTEA,
    access_policy VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_sensitive_validation_application ON sensitive_validation(application_id, created_at);

CREATE TABLE underwriter_assignment (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application(id),
    assigned_to VARCHAR(120) NOT NULL,
    queue_status VARCHAR(32) NOT NULL,
    notes_restricted TEXT,
    proposed_outcome VARCHAR(32),
    proposed_reason_codes JSONB,
    proposed_by VARCHAR(120),
    override_pending BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_underwriter_queue ON underwriter_assignment(queue_status, assigned_to, assigned_at);

CREATE TABLE application_event (
    event_id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application(id),
    event_type VARCHAR(100) NOT NULL,
    schema_version INTEGER NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    payload_redacted JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_application_event_history ON application_event(application_id, created_at);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_event(created_at) WHERE published_at IS NULL;

CREATE TABLE processed_event (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE idempotency_record (
    principal_id VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body_redacted JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (principal_id, idempotency_key)
);
CREATE INDEX idx_idempotency_expiry ON idempotency_record(expires_at);

CREATE TABLE customer_notice (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application(id),
    notice_type VARCHAR(48) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    template_version VARCHAR(80) NOT NULL,
    notice_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    generated_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ
);
CREATE INDEX idx_customer_notice_status ON customer_notice(delivery_status, generated_at);

CREATE TABLE audit_record (
    id UUID PRIMARY KEY,
    actor_id VARCHAR(160) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    change_summary_redacted JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_entity ON audit_record(entity_type, entity_id, created_at);
CREATE INDEX idx_audit_actor ON audit_record(actor_id, created_at);

CREATE TABLE batch_execution (
    id UUID PRIMARY KEY,
    job_name VARCHAR(120) NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    output_manifest VARCHAR(1000),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (job_name, business_date)
);

CREATE TABLE batch_output_item (
    id UUID PRIMARY KEY,
    batch_execution_id UUID NOT NULL REFERENCES batch_execution(id),
    application_id UUID NOT NULL REFERENCES application(id),
    output_type VARCHAR(48) NOT NULL,
    output_key VARCHAR(200) NOT NULL UNIQUE
);
CREATE INDEX idx_batch_output_execution ON batch_output_item(batch_execution_id, output_type);

CREATE TABLE batch_job_request (
    principal_id VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    job_name VARCHAR(120) NOT NULL,
    business_date DATE NOT NULL,
    execution_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (principal_id, idempotency_key)
);

CREATE TABLE eligibility_rule (
    rule_code VARCHAR(80) PRIMARY KEY,
    product_code VARCHAR(40) NOT NULL REFERENCES card_product(product_code),
    threshold JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE mock_bureau_persona (
    persona_code VARCHAR(80) PRIMARY KEY,
    score INTEGER,
    file_type VARCHAR(32) NOT NULL,
    fraud_alert BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO origination_svc_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO origination_svc_user;
REVOKE UPDATE, DELETE ON audit_record FROM origination_svc_user;
REVOKE UPDATE, DELETE ON application_event FROM origination_svc_user;

CREATE SEQUENCE batch_job_seq;
CREATE SEQUENCE batch_job_execution_seq;
CREATE SEQUENCE batch_step_execution_seq;

CREATE TABLE batch_job_instance (
    job_instance_id BIGINT PRIMARY KEY,
    version BIGINT,
    job_name VARCHAR(100) NOT NULL,
    job_key VARCHAR(32) NOT NULL,
    CONSTRAINT job_inst_un UNIQUE (job_name, job_key)
);
CREATE TABLE batch_job_execution (
    job_execution_id BIGINT PRIMARY KEY,
    version BIGINT,
    job_instance_id BIGINT NOT NULL REFERENCES batch_job_instance(job_instance_id),
    create_time TIMESTAMP NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(10),
    exit_code VARCHAR(2500),
    exit_message VARCHAR(2500),
    last_updated TIMESTAMP
);
CREATE TABLE batch_job_execution_params (
    job_execution_id BIGINT NOT NULL REFERENCES batch_job_execution(job_execution_id),
    parameter_name VARCHAR(100) NOT NULL,
    parameter_type VARCHAR(100) NOT NULL,
    parameter_value VARCHAR(2500),
    identifying CHAR(1) NOT NULL
);
CREATE TABLE batch_step_execution (
    step_execution_id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    step_name VARCHAR(100) NOT NULL,
    job_execution_id BIGINT NOT NULL REFERENCES batch_job_execution(job_execution_id),
    create_time TIMESTAMP NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(10),
    commit_count BIGINT,
    read_count BIGINT,
    filter_count BIGINT,
    write_count BIGINT,
    read_skip_count BIGINT,
    write_skip_count BIGINT,
    process_skip_count BIGINT,
    rollback_count BIGINT,
    exit_code VARCHAR(2500),
    exit_message VARCHAR(2500),
    last_updated TIMESTAMP
);
CREATE TABLE batch_job_execution_context (
    job_execution_id BIGINT PRIMARY KEY REFERENCES batch_job_execution(job_execution_id),
    short_context VARCHAR(2500) NOT NULL,
    serialized_context TEXT
);
CREATE TABLE batch_step_execution_context (
    step_execution_id BIGINT PRIMARY KEY REFERENCES batch_step_execution(step_execution_id),
    short_context VARCHAR(2500) NOT NULL,
    serialized_context TEXT
);
CREATE INDEX idx_batch_job_execution_instance ON batch_job_execution(job_instance_id);
CREATE INDEX idx_batch_step_execution_job ON batch_step_execution(job_execution_id);
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO origination_svc_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO origination_svc_user;
