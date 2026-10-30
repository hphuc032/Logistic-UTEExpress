-- HP-approved Phase 2: vendor attestation of physical return receipt. No restocking/refund.
ALTER TABLE uteexpress.return_requests
    ADD COLUMN received_by BIGINT,
    ADD COLUMN receipt_reference VARCHAR(255),
    DROP CONSTRAINT ck_return_requests_no_receipt,
    DROP CONSTRAINT ck_return_requests_times,
    ADD CONSTRAINT fk_return_requests_received_by FOREIGN KEY (received_by) REFERENCES uteexpress.users(id),
    ADD CONSTRAINT ck_return_requests_no_restock CHECK (restockable IS NULL),
    ADD CONSTRAINT ck_return_requests_receipt CHECK (
        (received_at IS NULL AND received_by IS NULL AND receipt_reference IS NULL)
        OR (received_at IS NOT NULL AND received_by IS NOT NULL AND receipt_reference IS NOT NULL
            AND receipt_reference ~ '[^[:space:]]' AND status = 'APPROVED' AND decision_at IS NOT NULL)),
    ADD CONSTRAINT ck_return_requests_times CHECK (
        created_at = requested_at AND updated_at >= created_at
        AND (decision_at IS NULL OR decision_at >= requested_at)
        AND ((received_at IS NULL AND (decision_at IS NULL OR updated_at = decision_at))
            OR (received_at IS NOT NULL AND received_at >= decision_at AND updated_at = received_at)));

-- Existing UNIQUE(order_id) binds a single receipt to its immutable Order/ReturnRequest.
-- Defend successful evidence against overwrite, removal or reassignment, including direct SQL.
CREATE FUNCTION uteexpress.preserve_return_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.received_at IS NOT NULL AND
        (NEW.received_at, NEW.received_by, NEW.receipt_reference, NEW.order_id, NEW.id)
        IS DISTINCT FROM
        (OLD.received_at, OLD.received_by, OLD.receipt_reference, OLD.order_id, OLD.id) THEN
        RAISE EXCEPTION 'Successful return receipt is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_return_requests_preserve_receipt
    BEFORE UPDATE ON uteexpress.return_requests
    FOR EACH ROW EXECUTE FUNCTION uteexpress.preserve_return_receipt();
