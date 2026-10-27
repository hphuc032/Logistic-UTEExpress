package com.uteexpress.governance.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

final class OpsReportParameters {
    private OpsReportParameters() {}
    static void require(HttpServletRequest request, Set<String> allowed) {
        request.getParameterMap().forEach((name, values) -> {
            if (!allowed.contains(name) || values.length != 1) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        });
    }
}
