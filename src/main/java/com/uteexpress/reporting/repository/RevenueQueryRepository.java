package com.uteexpress.reporting.repository;

import com.uteexpress.reporting.dto.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

/** Shared DASH-01/DASH-02 read projections. Each stream stays at its own grain; no lifecycle writes or locks. */
@Repository
public class RevenueQueryRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    public RevenueQueryRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc) { this.jdbc = jdbc; }

    // Public SQL constants also let PostgreSQL acceptance tests EXPLAIN the exact production query.
    public static final String EVIDENCE_SQL = """
            WITH scoped AS (SELECT * FROM uteexpress.orders WHERE shop_id=:shop),
            payment_counts AS (
                SELECT p.order_id, count(*) FILTER (WHERE p.status='PAID') AS paid_count
                FROM uteexpress.payments p JOIN scoped o ON o.id=p.order_id GROUP BY p.order_id
            ), payment_facts AS (
                -- Count ALL PAID rows before validating their content. More than one
                -- never selects an arbitrary winner, even if only one looks valid.
                SELECT c.order_id,c.paid_count,p.id AS payment_id,p.amount AS paid_amount,p.paid_at,
                    p.method='COD' AND p.expired_at IS NULL AS collected,
                    p.method AS paid_method,p.provider_reference,p.expired_at,p.created_at AS payment_created_at
                FROM payment_counts c LEFT JOIN uteexpress.payments p
                    ON p.order_id=c.order_id AND p.status='PAID' AND c.paid_count=1
            ), cancellations AS (
                SELECT o.id,COALESCE(o.status='CANCELLED' AND o.delivered_at IS NULL
                    AND o.cancelled_at IS NOT NULL AND o.inventory_released_at=o.cancelled_at
                    AND NOT EXISTS(SELECT 1 FROM uteexpress.return_requests t WHERE t.order_id=o.id)
                    AND NOT EXISTS(SELECT 1 FROM uteexpress.order_status_history h
                        WHERE h.order_id=o.id AND h.to_status='DELIVERED')
                    AND EXISTS(SELECT 1 FROM uteexpress.order_status_history h WHERE h.order_id=o.id
                        AND h.from_status IN ('NEW','CONFIRMED','SHIPPING') AND h.to_status='CANCELLED'
                        AND h.actor_id>0 AND h.created_at=o.cancelled_at),false) AS cancellation_ok
                FROM scoped o
            ), facts AS (
                SELECT o.*, o.subtotal-o.discount_total AS goods,
                    p.paid_count, p.payment_id, p.paid_amount, p.paid_at,
                    COALESCE(o.subtotal>=0 AND o.discount_total BETWEEN 0 AND o.subtotal
                        AND o.shipping_fee>=0 AND o.grand_total=o.subtotal-o.discount_total+o.shipping_fee
                        AND o.subtotal=trunc(o.subtotal) AND o.discount_total=trunc(o.discount_total)
                        AND o.shipping_fee=trunc(o.shipping_fee) AND o.grand_total=trunc(o.grand_total)
                        AND o.subtotal NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)
                        AND o.grand_total NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric),false) AS money_ok,
                    COALESCE(o.commission_amount>=0 AND o.commission_amount<=o.subtotal-o.discount_total
                        AND o.commission_amount=trunc(o.commission_amount)
                        AND o.commission_rate_snapshot BETWEEN 0 AND 100
                        AND o.commission_amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric),false) AS commission_ok,
                    COALESCE(p.paid_count=1 AND (p.collected OR
                        p.paid_method='ONLINE' AND p.expired_at IS NULL AND p.provider_reference ~ '[^[:space:]]'
                            AND p.paid_at>=p.payment_created_at
                            AND c.cancellation_ok AND p.paid_at BETWEEN o.created_at AND o.cancelled_at)
                        AND p.paid_at IS NOT NULL
                        AND p.paid_amount=o.grand_total,false) AS payment_ok,
                    c.cancellation_ok,
                    EXISTS(SELECT 1 FROM uteexpress.order_status_history h WHERE h.order_id=o.id
                        AND h.from_status='SHIPPING' AND h.to_status='DELIVERED' AND h.created_at=o.delivered_at) AS delivery_ok,
                    t.id AS receipt_id, t.received_at,
                    COALESCE(t.status='APPROVED' AND t.received_by>0 AND btrim(t.receipt_reference)<>''
                        AND t.received_at IS NOT NULL AND EXISTS(
                            SELECT 1 FROM uteexpress.order_status_history h WHERE h.order_id=o.id
                            AND h.from_status='RETURN_REQUESTED' AND h.to_status='RETURNED'
                            AND h.created_at=t.received_at AND h.actor_id=t.received_by),false) AS receipt_ok
                FROM scoped o LEFT JOIN payment_facts p ON p.order_id=o.id
                JOIN cancellations c ON c.id=o.id
                LEFT JOIN uteexpress.return_requests t ON t.order_id=o.id
            )
            """;
    public static final String METRICS_SQL = EVIDENCE_SQL + """
            , events AS (
                SELECT 'delivery' AS kind, goods AS amount, commission_amount AS commission,
                    money_ok AND payment_ok AND delivery_ok AS valid, commission_ok,
                    delivered_at IS NOT NULL AS dated
                FROM facts WHERE delivered_at>=:start AND delivered_at<:end
                    OR delivered_at IS NULL AND status IN ('DELIVERED','RETURN_REQUESTED','RETURNED','REFUNDED')
                UNION ALL
                SELECT 'collection',goods,commission_amount,money_ok AND payment_ok,commission_ok,paid_at IS NOT NULL
                FROM facts WHERE paid_at>=:start AND paid_at<:end
                    OR paid_at IS NULL AND (paid_count>0 OR delivered_at IS NOT NULL
                        OR status IN ('DELIVERED','RETURN_REQUESTED','RETURNED','REFUNDED'))
                UNION ALL
                SELECT 'refund',r.amount-f.shipping_fee,
                    CASE WHEN r.operation='PAY02_CANCELLED_PAID_REFUND_V1' THEN 0 ELSE f.commission_amount END,
                    f.money_ok AND f.payment_ok AND r.amount=f.grand_total
                    AND r.payment_id=f.payment_id AND r.payment_paid_at=f.paid_at
                    AND r.server_timestamp>=f.paid_at AND (
                        r.operation='PAY02_MANUAL_COD_REFUND_V1' AND f.receipt_ok
                        AND r.return_request_id=f.receipt_id AND r.return_received_at=f.received_at
                        AND r.server_timestamp>=f.received_at
                        OR r.operation='PAY02_CANCELLED_PAID_REFUND_V1' AND f.cancellation_ok
                        AND r.return_request_id IS NULL AND r.return_received_at IS NULL
                        AND r.expected_return_request_version IS NULL AND r.order_cancelled_at=f.cancelled_at
                        AND r.payment_paid_at BETWEEN f.created_at AND f.cancelled_at
                        AND r.server_timestamp>=f.cancelled_at),
                    CASE WHEN r.operation='PAY02_CANCELLED_PAID_REFUND_V1' THEN true ELSE f.commission_ok END,
                    r.server_timestamp IS NOT NULL
                FROM facts f LEFT JOIN uteexpress.refunds r ON r.order_id=f.id
                WHERE r.status='SUCCEEDED' AND (r.server_timestamp>=:start AND r.server_timestamp<:end OR r.server_timestamp IS NULL)
                    OR r.id IS NULL AND f.status='REFUNDED'
            )
            SELECT
                COALESCE(sum(amount) FILTER (WHERE kind='delivery' AND valid AND dated),0) AS delivered,
                count(*) FILTER (WHERE kind='delivery' AND NOT COALESCE(valid AND dated,false)) AS delivered_unknown,
                COALESCE(sum(amount) FILTER (WHERE kind='collection' AND valid AND dated),0) AS collected,
                count(*) FILTER (WHERE kind='collection' AND NOT COALESCE(valid AND dated,false)) AS collected_unknown,
                COALESCE(sum(amount) FILTER (WHERE kind='refund' AND valid AND dated),0) AS refunded,
                count(*) FILTER (WHERE kind='refund' AND NOT COALESCE(valid AND dated,false)) AS refunded_unknown,
                COALESCE(sum(commission) FILTER (WHERE kind='delivery' AND valid AND dated AND commission_ok),0) AS accrued,
                count(*) FILTER (WHERE kind='delivery' AND NOT COALESCE(valid AND dated AND commission_ok,false)) AS accrued_unknown,
                COALESCE(sum(commission) FILTER (WHERE kind='refund' AND valid AND dated AND commission_ok),0) AS reversed,
                count(*) FILTER (WHERE kind='refund' AND NOT COALESCE(valid AND dated AND commission_ok,false)) AS reversed_unknown,
                (SELECT count(*) FROM scoped WHERE created_at>=:start AND created_at<:end) AS total_orders,
                (SELECT count(*) FROM scoped WHERE delivered_at>=:start AND delivered_at<:end) AS delivered_orders,
                (SELECT count(*) FROM scoped WHERE cancelled_at>=:start AND cancelled_at<:end) AS cancelled_orders
            FROM events
            """;
    public static final String TOP_SQL = EVIDENCE_SQL + """
            , quantity_events AS (
                SELECT f.id, f.delivered_at AS at, 1 AS sign, f.delivery_ok AND f.payment_ok AND f.money_ok AS valid
                FROM facts f WHERE delivered_at>=:start AND delivered_at<:end
                    OR delivered_at IS NULL AND status IN ('DELIVERED','RETURN_REQUESTED','RETURNED','REFUNDED')
                UNION ALL
                SELECT f.id,f.received_at,-1,f.receipt_ok FROM facts f
                WHERE received_at>=:start AND received_at<:end
                    OR received_at IS NULL AND status IN ('RETURNED','REFUNDED')
            ), item_events AS (
                SELECT i.product_id,i.product_name_snapshot,i.id AS item_id,i.quantity,q.sign,
                    COALESCE(q.valid AND q.at IS NOT NULL AND i.quantity>0 AND i.product_id>0,false) AS valid
                FROM quantity_events q LEFT JOIN uteexpress.order_items i ON i.order_id=q.id
            ), ranked AS (
                SELECT product_id, (array_agg(product_name_snapshot ORDER BY item_id DESC))[1] AS name,
                    COALESCE(sum(quantity::bigint) FILTER (WHERE sign=1),0) AS delivered,
                    COALESCE(sum(quantity::bigint) FILTER (WHERE sign=-1),0) AS returned,
                    sum(sign::bigint*quantity) AS net
                FROM item_events WHERE valid GROUP BY product_id
                ORDER BY net DESC,product_id ASC LIMIT 10
            ), diagnostics AS (SELECT count(*) FILTER (WHERE NOT valid) AS unknown FROM item_events)
            SELECT r.*,d.unknown FROM diagnostics d LEFT JOIN ranked r ON true ORDER BY r.net DESC,r.product_id ASC
            """;

    public static final String GLOBAL_METRICS_SQL = METRICS_SQL.replace("WHERE shop_id=:shop", "");
    public RevenueMetrics metrics(RevenueScope scope, RevenuePeriod period) {
        String sql = scope.kind() == RevenueScope.Kind.ALL_SHOPS ? GLOBAL_METRICS_SQL : METRICS_SQL;
        return jdbc.getObject().queryForObject(sql, parameters(scope.shopId() == null ? 0 : scope.shopId(), period), (r,n) ->
            new RevenueMetrics(amount(r.getBigDecimal("delivered"),r.getLong("delivered_unknown")),
                amount(r.getBigDecimal("collected"),r.getLong("collected_unknown")),
                amount(r.getBigDecimal("refunded"),r.getLong("refunded_unknown")),
                amount(r.getBigDecimal("accrued"),r.getLong("accrued_unknown")),
                amount(r.getBigDecimal("reversed"),r.getLong("reversed_unknown")),
                r.getLong("total_orders"),r.getLong("delivered_orders"),r.getLong("cancelled_orders")));
    }
    public RevenueSummary.TopProducts topProducts(long shop, RevenuePeriod period) {
        return jdbc.getObject().query(TOP_SQL, parameters(shop, period), r -> {
            var products = new ArrayList<RevenueSummary.Product>(); long unknown=0;
            while(r.next()) {
                unknown=r.getLong("unknown");
                if(r.getObject("product_id")!=null) products.add(new RevenueSummary.Product(r.getLong("product_id"),
                    r.getString("name"),r.getLong("delivered"),r.getLong("returned"),r.getLong("net")));
            }
            return new RevenueSummary.TopProducts(products,unknown);
        });
    }
    private static RevenueAmount amount(BigDecimal known, long unknown) { return new RevenueAmount(known,unknown); }
    public static MapSqlParameterSource parameters(long shop, RevenuePeriod period) {
        return new MapSqlParameterSource("shop",shop).addValue("start",Timestamp.from(period.start()))
            .addValue("end",Timestamp.from(period.end()));
    }
}
