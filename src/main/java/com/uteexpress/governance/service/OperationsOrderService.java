package com.uteexpress.governance.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.OpsOrderDetail;
import com.uteexpress.governance.dto.OpsOrderPage;
import com.uteexpress.governance.repository.OpsOrderQueryRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), "
        + "T(com.uteexpress.security.RoleCode).MANAGER.authority())")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OperationsOrderService {
    public static final List<String> STATUSES = List.of("NEW", "CONFIRMED", "PICKED_UP", "SHIPPING",
            "DELIVERED", "CANCELLED", "RETURN_REQUESTED", "RETURNED", "REFUNDED");
    private final OpsOrderQueryRepository orders;

    public OperationsOrderService(OpsOrderQueryRepository orders) { this.orders = orders; }

    public OpsOrderPage search(String query, String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || (query != null && query.length() > 80)
                || (status != null && !status.isBlank() && !STATUSES.contains(status))) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        String code = query == null || query.isBlank() ? null : query.trim();
        String selectedStatus = status == null || status.isBlank() ? null : status;
        long total = orders.count(code, selectedStatus);
        var content = orders.search(code, selectedStatus, size, (long) page * size);
        return new OpsOrderPage(content, page, size, total, page > 0,
                ((long) page + 1) * size < total);
    }

    public OpsOrderDetail detail(Long id) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        var result = orders.detail(id);
        if (result == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        return result;
    }
}
