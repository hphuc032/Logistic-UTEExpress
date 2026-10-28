package com.uteexpress.payment.repository;

import com.uteexpress.payment.entity.RefundLedger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

/** JDBC shares the lifecycle's JpaTransactionManager connection and commit. */
@Repository
public class RefundLedgerRepository {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    public RefundLedgerRepository(ObjectProvider<JdbcTemplate> jdbcProvider) { this.jdbcProvider = jdbcProvider; }
    private JdbcTemplate jdbc() { return jdbcProvider.getObject(); }

    public void lockKey(long id) {
        jdbc().query("SELECT pg_advisory_xact_lock(?)", rs -> { }, id);
    }
    public Optional<RefundLedger> byKey(String operation, String key) {
        return jdbc().query("SELECT * FROM uteexpress.refunds WHERE operation=? AND idempotency_key=?",
                RefundLedgerRepository::row, operation, key).stream().findFirst();
    }
    public boolean lockForOrder(long orderId) {
        return !jdbc().queryForList("SELECT id FROM uteexpress.refunds WHERE order_id=? FOR UPDATE", Long.class, orderId).isEmpty();
    }
    public RefundLedger insert(long orderId, long paymentId, long requestId, long actor,
            String operation, String key, int schema, long orderVersion, long requestVersion,
            byte[] canonical, byte[] digest, BigDecimal amount, String proof, Instant at,
            Instant paidAt, Instant receivedAt) {
        return jdbc().queryForObject("""
                INSERT INTO uteexpress.refunds(order_id,payment_id,return_request_id,processed_by,operation,
                    idempotency_key,schema_version,expected_order_version,expected_return_request_version,
                    canonical_payload,payload_sha256,amount,status,proof_reference,server_timestamp,created_at,
                    result_order_version,payment_paid_at,return_received_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'SUCCEEDED',?,?,?, ?,?,?) RETURNING *
                """, RefundLedgerRepository::row, orderId, paymentId, requestId, actor, operation, key,
                schema, orderVersion, requestVersion, canonical, digest, amount, proof, Timestamp.from(at),
                Timestamp.from(at), orderVersion + 1, Timestamp.from(paidAt), Timestamp.from(receivedAt));
    }
    public RefundLedger insertCancelledPaid(long orderId, long paymentId, long actor, String operation,
            String key, int schema, long orderVersion, byte[] canonical, byte[] digest,
            BigDecimal amount, String proof, Instant at, Instant paidAt, Instant cancelledAt) {
        return jdbc().queryForObject("""
                INSERT INTO uteexpress.refunds(order_id,payment_id,processed_by,operation,idempotency_key,
                    schema_version,expected_order_version,canonical_payload,payload_sha256,amount,status,
                    proof_reference,server_timestamp,created_at,result_order_version,payment_paid_at,order_cancelled_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,'SUCCEEDED',?,?,?,?,?,?) RETURNING *
                """, RefundLedgerRepository::row, orderId,paymentId,actor,operation,key,schema,orderVersion,
                canonical,digest,amount,proof,Timestamp.from(at),Timestamp.from(at),orderVersion,
                Timestamp.from(paidAt),Timestamp.from(cancelledAt));
    }
    private static RefundLedger row(ResultSet rs, int ignored) throws SQLException {
        return new RefundLedger(rs.getLong("id"), rs.getLong("order_id"), rs.getLong("processed_by"),
                rs.getString("operation"), rs.getString("idempotency_key"), rs.getBytes("canonical_payload"),
                rs.getBytes("payload_sha256"), rs.getBigDecimal("amount"), rs.getString("proof_reference"),
                rs.getTimestamp("server_timestamp").toInstant());
    }
}
