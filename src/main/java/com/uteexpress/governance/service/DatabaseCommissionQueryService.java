package com.uteexpress.governance.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.CommissionPolicySnapshot;
import com.uteexpress.governance.repository.CommissionPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Checkout boundary: a missing or disabled latest policy blocks order creation. */
@Service
public class DatabaseCommissionQueryService implements CommissionQueryService {
    private final CommissionPolicyRepository policies;

    public DatabaseCommissionQueryService(CommissionPolicyRepository policies) {
        this.policies = policies;
    }

    @Override
    @Transactional(readOnly = true)
    public CommissionPolicySnapshot requireEffectivePolicy(Instant checkoutAt) {
        if (checkoutAt == null) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        // PostgreSQL stores microseconds; truncation must not round a pre-boundary
        // nanosecond up into a future policy's effective timestamp.
        List<CommissionPolicyRepository.PolicyRow> candidates = policies.latestAt(
                checkoutAt.truncatedTo(ChronoUnit.MICROS));
        if (candidates.isEmpty() || !candidates.getFirst().active()
                || (candidates.size() > 1 && candidates.getFirst().effectiveFrom()
                        .equals(candidates.get(1).effectiveFrom()))) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        var current = candidates.getFirst();
        return new CommissionPolicySnapshot(current.id(), current.ratePercent(), current.effectiveFrom());
    }
}
