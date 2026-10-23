package com.uteexpress.engagement.dto;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;

/** Validated content only; this DTO grants no Order eligibility or authorization. */
public record ReviewContent(int rating, String comment) {
    public ReviewContent {
        comment = comment == null ? null : comment.strip();
        if (rating < 1 || rating > 5 || comment == null || comment.length() < 50
                || comment.length() > 2000 || comment.indexOf('\0') >= 0)
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
    }
}
