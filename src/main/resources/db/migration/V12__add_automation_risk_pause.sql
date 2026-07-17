-- Keep an automatic risk pause separate from the user's manual account enable/disable state.
-- This way realtime connection can remain available while outbound automation is safely stopped.
ALTER TABLE xianyu_account
    ADD COLUMN automation_risk_paused TINYINT NOT NULL DEFAULT 0 AFTER auto_connect_on_startup,
    ADD COLUMN automation_risk_pause_reason VARCHAR(500) NULL AFTER automation_risk_paused,
    ADD COLUMN automation_risk_paused_at DATETIME(3) NULL AFTER automation_risk_pause_reason,
    ADD KEY idx_account_automation_risk_paused (automation_risk_paused);
