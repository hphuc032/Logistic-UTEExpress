package com.uteexpress.payment.service;

import com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.payment.dto.PaymentStatus;
import com.uteexpress.payment.dto.VnpayIpnResponse;
import com.uteexpress.payment.dto.VnpayReturnView;
import com.uteexpress.payment.entity.Payment;
import com.uteexpress.payment.repository.PaymentRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "uteexpress.payment.vnpay.enabled", havingValue = "true")
public class VnpayPaymentService {
    private final PaymentRepository payments;
    private final VnpayGateway gateway;
    private final CurrentAccountIdProvider accounts;
    private final EntityManager entityManager;
    private final Clock clock;

    public VnpayPaymentService(PaymentRepository payments, VnpayGateway gateway,
            CurrentAccountIdProvider accounts, EntityManager entityManager, Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.accounts = accounts;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Trusted checkout only. The NEW order and its one ONLINE attempt commit atomically. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initializeForNewOrder(Long orderId) {
        var order = payments.lockOrderFacts(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"NEW".equals(order.getStatus()) || !payments.lockRecordsForOrder(orderId).isEmpty()) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        try { VnpayGateway.encodedAmount(order.getGrandTotal()); }
        catch (IllegalArgumentException | ArithmeticException ex) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        payments.saveAndFlush(new Payment(orderId, PaymentMethod.ONLINE,
                new OrderTotals(order.getSubtotal(), order.getDiscountTotal(), order.getShippingFee(), order.getGrandTotal()),
                "VP" + UUID.randomUUID().toString().replace("-", ""), now()));
    }

    /** Builds a URL for the existing attempt only; repeated GETs do not create or renew attempts. */
    @Transactional
    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
            + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    public String paymentUrl(Long orderId, String remoteAddress) {
        Long buyer = accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        if (orderId == null || !payments.buyerOwnsOrder(orderId, buyer)) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        var order = payments.lockOrderFacts(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        var records = payments.lockRecordsForOrder(orderId);
        records.forEach(payment -> entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE));
        if (!"NEW".equals(order.getStatus()) || records.size() != 1) throw new ApplicationException(ErrorCode.CONFLICT);
        var payment = records.getFirst();
        if (!isVnpay(payment) || payment.getStatus() != PaymentStatus.UNPAID || payment.getExpiredAt() != null
                || payment.getAmount().compareTo(order.getGrandTotal()) != 0
                || !now().isBefore(payment.getCreatedAt().plus(VnpayGateway.WINDOW))) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        // Use the socket address; never trust arbitrary X-Forwarded-For headers from clients.
        return gateway.paymentUrl(payment.getAttemptKey(), orderId, payment.getAmount(), payment.getCreatedAt(), remoteAddress);
    }

    /** Anonymous server-to-server boundary. No mutation happens before all signed facts are checked. */
    @Transactional
    public VnpayIpnResponse acceptIpn(Map<String, String> fields) {
        if (!gateway.validSignature(fields)) return reply("97", "Invalid signature");
        if (!gateway.validMerchant(fields) || !validCodes(fields)) return reply("99", "Invalid transaction data");
        String reference = fields.get("vnp_TxnRef");
        if (reference == null || !reference.matches("VP[a-f0-9]{32}")) return reply("01", "Order not found");
        var orderId = payments.findOrderIdByAttemptKey(reference);
        if (orderId.isEmpty()) return reply("01", "Order not found");
        // Parent lock serializes IPN with cancellation and all other payment operations.
        var order = payments.lockOrderFacts(orderId.get());
        if (order.isEmpty()) return reply("01", "Order not found");
        var records = payments.lockRecordsForOrder(orderId.get());
        records.forEach(payment -> entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE));
        if (records.size() != 1 || !isVnpay(records.getFirst())
                || !reference.equals(records.getFirst().getAttemptKey())) return reply("01", "Order not found");
        var payment = records.getFirst();
        if (!matchesAmount(fields, payment) || payment.getAmount().compareTo(order.get().getGrandTotal()) != 0) {
            return reply("04", "Invalid amount");
        }
        boolean success = "00".equals(fields.get("vnp_ResponseCode")) && "00".equals(fields.get("vnp_TransactionStatus"));
        String transaction = fields.get("vnp_TransactionNo");
        if (transaction == null || !transaction.matches("[0-9]{1,19}")
                || (success && transaction.chars().allMatch(c -> c == '0'))) return reply("99", "Invalid transaction number");
        String providerReference = "VNPAY:" + transaction;
        if (payment.getStatus() == PaymentStatus.PAID) {
            return success && providerReference.equals(payment.getProviderReference())
                    ? reply("02", "Order already confirmed") : reply("99", "Conflicting transaction");
        }
        if (payment.getExpiredAt() != null) return reply("02", "Order already confirmed");
        if (!"NEW".equals(order.get().getStatus())) return reply("99", "Order cannot accept payment");
        if (success) {
            if (!validPayDate(fields.get("vnp_PayDate"), payment)) return reply("99", "Invalid payment date");
            if (payments.existsByProviderReference(providerReference)) return reply("99", "Conflicting transaction");
            payment.confirmVnpay(order.get().getGrandTotal(), providerReference, now());
        } else if (!"01".equals(fields.get("vnp_TransactionStatus"))) {
            // UNPAID remains the existing status vocabulary; expiredAt marks a closed failed attempt.
            payment.closeVnpayAttempt(now());
        }
        payments.flush();
        return reply("00", "Confirm Success");
    }

    /** Return is read-only, even when it contains a correctly signed success response. */
    @Transactional(readOnly = true)
    public VnpayReturnView returnView(Map<String, String> fields) {
        if (!gateway.validSignature(fields) || !gateway.validMerchant(fields) || !validCodes(fields)) return invalidReturn();
        String reference = fields.get("vnp_TxnRef");
        if (reference == null || !reference.matches("VP[a-f0-9]{32}")) return invalidReturn();
        var id = payments.findOrderIdByAttemptKey(reference);
        if (id.isEmpty()) return invalidReturn();
        var records = payments.findByOrderIdOrderByCreatedAtAscIdAsc(id.get());
        if (records.size() != 1) return invalidReturn();
        var payment = records.getFirst();
        if (!isVnpay(payment) || !reference.equals(payment.getAttemptKey()) || !matchesAmount(fields, payment)) return invalidReturn();
        String state = payment.getStatus() == PaymentStatus.PAID ? "PAID"
                : payment.getExpiredAt() != null ? "CLOSED" : "WAITING";
        return new VnpayReturnView(true, id.get(), state);
    }

    private boolean validPayDate(String text, Payment payment) {
        if (text == null || !text.matches("[0-9]{14}")) return false;
        try {
            Instant paid = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
                    .withResolverStyle(ResolverStyle.STRICT)).toInstant(ZoneOffset.ofHours(7));
            return !paid.isBefore(payment.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
                    && !paid.isAfter(payment.getCreatedAt().plus(VnpayGateway.WINDOW))
                    && !paid.isAfter(now().plusSeconds(60));
        } catch (java.time.DateTimeException ex) { return false; }
    }

    private static boolean matchesAmount(Map<String, String> fields, Payment payment) {
        String amount = fields.get("vnp_Amount");
        return amount != null && amount.matches("[0-9]{1,12}")
                && VnpayGateway.encodedAmount(payment.getAmount()).equals(amount);
    }
    private static boolean validCodes(Map<String, String> fields) {
        return fields.getOrDefault("vnp_ResponseCode", "").matches("[0-9]{2}")
                && fields.getOrDefault("vnp_TransactionStatus", "").matches("[0-9]{2}");
    }
    private static boolean isVnpay(Payment payment) {
        return payment.getMethod() == PaymentMethod.ONLINE && payment.getAttemptKey().matches("VP[a-f0-9]{32}");
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static VnpayIpnResponse reply(String code, String message) { return VnpayIpnResponse.of(code, message); }
    private static VnpayReturnView invalidReturn() { return new VnpayReturnView(false, null, "INVALID"); }
}
