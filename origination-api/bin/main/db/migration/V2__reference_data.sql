INSERT INTO card_product (
    id, product_code, tier, apr_min, apr_max, annual_fee, rewards_description,
    credit_limit_min, credit_limit_max, minimum_fico, maximum_dti, policy_config
) VALUES
    ('10000000-0000-4000-8000-000000000001', 'SECURED', 'SECURED', 24.99, 24.99, 0,
     'No rewards; refundable security deposit required', 200, 2000, 550, 0.5000,
     '{"incomeMultiplier": 0.20, "thinFileManualReview": true}'),
    ('10000000-0000-4000-8000-000000000002', 'CASHBACK', 'CASHBACK', 19.99, 29.99, 0,
     '1.5% eligible net purchases', 500, 15000, 660, 0.4300,
     '{"incomeMultiplier": 0.30, "thinFileManualReview": true}'),
    ('10000000-0000-4000-8000-000000000003', 'TRAVEL_REWARDS', 'TRAVEL_REWARDS', 17.99, 27.99, 95,
     '2 points per eligible travel dollar; 1 point otherwise', 2000, 25000, 720, 0.3600,
     '{"incomeMultiplier": 0.35, "thinFileManualReview": true}');

INSERT INTO eligibility_rule (rule_code, product_code, threshold) VALUES
    ('SECURED_MIN_FICO', 'SECURED', '{"minimumFico": 550, "maximumDti": 0.50}'),
    ('CASHBACK_MIN_FICO', 'CASHBACK', '{"minimumFico": 660, "maximumDti": 0.43}'),
    ('TRAVEL_MIN_FICO', 'TRAVEL_REWARDS', '{"minimumFico": 720, "maximumDti": 0.36}');

INSERT INTO mock_bureau_persona (persona_code, score, file_type, fraud_alert) VALUES
    ('PRIME', 780, 'THICK', FALSE),
    ('NEAR_PRIME', 690, 'THICK', FALSE),
    ('THIN_FILE', NULL, 'THIN', FALSE),
    ('FRAUD_ALERT', 640, 'THICK', TRUE),
    ('LOW_SCORE', 520, 'THICK', FALSE);
