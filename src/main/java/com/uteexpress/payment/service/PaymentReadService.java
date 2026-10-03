package com.uteexpress.payment.service;

import com.uteexpress.payment.dto.PaymentRecordView;
import com.uteexpress.payment.repository.PaymentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal read boundary. The caller must first authorize ownership of the order. */
@Service
public class PaymentReadService {
    private final PaymentRepository payments;

    public PaymentReadService(PaymentRepository payments) { this.payments = payments; }

    @Transactional(readOnly = true)
    public List<PaymentRecordView> recordsForOrder(Long authorizedOrderId) {
        return payments.findByOrderIdOrderByCreatedAtAscIdAsc(authorizedOrderId).stream()
                .map(payment -> new PaymentRecordView(payment.getMethod(), payment.getStatus(),
                        payment.getAmount(), payment.getCreatedAt())).toList();
    }
}
