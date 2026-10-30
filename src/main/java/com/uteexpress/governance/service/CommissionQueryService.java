package com.uteexpress.governance.service;

import com.uteexpress.governance.dto.CommissionPolicySnapshot;
import java.time.Instant;

/** QD-owned effective global commission policy boundary; no persistence or fallback policy. */
public interface CommissionQueryService {
    /** Effective at server checkout time. Missing/ambiguous policy fails closed with CONFLICT. */
    CommissionPolicySnapshot requireEffectivePolicy(Instant checkoutAt);
}
