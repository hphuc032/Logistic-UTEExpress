-- Preserve every existing returned-COD result; add a distinct no-return cancellation refund.
ALTER TABLE uteexpress.refunds
    ALTER COLUMN return_request_id DROP NOT NULL,
    ALTER COLUMN expected_return_request_version DROP NOT NULL,
    ALTER COLUMN return_received_at DROP NOT NULL,
    ADD COLUMN order_cancelled_at TIMESTAMPTZ;

ALTER TABLE uteexpress.refunds DROP CONSTRAINT refunds_operation_check;
-- PostgreSQL names this original cross-column CHECK refunds_check.
ALTER TABLE uteexpress.refunds DROP CONSTRAINT refunds_check;
ALTER TABLE uteexpress.refunds ADD CONSTRAINT ck_refunds_operation CHECK
    (operation IN ('PAY02_MANUAL_COD_REFUND_V1','PAY02_CANCELLED_PAID_REFUND_V1'));
ALTER TABLE uteexpress.refunds ADD CONSTRAINT ck_refunds_operation_evidence CHECK (
    (operation='PAY02_MANUAL_COD_REFUND_V1'
        AND return_request_id IS NOT NULL AND expected_return_request_version IS NOT NULL
        AND return_received_at IS NOT NULL AND order_cancelled_at IS NULL
        AND result_order_version=expected_order_version+1)
    OR
    (operation='PAY02_CANCELLED_PAID_REFUND_V1'
        AND return_request_id IS NULL AND expected_return_request_version IS NULL
        AND return_received_at IS NULL AND order_cancelled_at IS NOT NULL
        AND payment_paid_at<=order_cancelled_at AND order_cancelled_at<=server_timestamp
        AND result_order_version=expected_order_version)
);
-- Existing unique Order/Payment/Return/key, composite FKs and immutability triggers remain.
