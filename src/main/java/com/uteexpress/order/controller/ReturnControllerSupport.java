package com.uteexpress.order.controller;

import com.uteexpress.common.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

/** Shared representation/error handling for the dedicated return pages. */
abstract class ReturnControllerSupport {
    protected static void parameters(HttpServletRequest request, String... allowed) {
        var keys = new HashSet<>(Arrays.asList(allowed));
        keys.add("_csrf");
        if (request.getParameterMap().keySet().stream().anyMatch(key -> !keys.contains(key)))
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        if (request.getParameterMap().values().stream().anyMatch(values -> values.length != 1))
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(value = ApplicationException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView pageError(ApplicationException error) { return errorPage(error.errorCode()); }

    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView invalidSelection(MethodArgumentTypeMismatchException error) { return errorPage(ErrorCode.INVALID_REQUEST); }

    @ExceptionHandler(value = org.springframework.web.bind.MethodArgumentNotValidException.class,
            produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView invalidForm(org.springframework.web.bind.MethodArgumentNotValidException error) {
        return errorPage(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(value = AccessDeniedException.class, produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView denied(AccessDeniedException error) { return errorPage(ErrorCode.ACCESS_DENIED); }

    private static ModelAndView errorPage(ErrorCode code) {
        return new ModelAndView("order/error", Map.of("errorCode", code.name(),
                "publicMessage", code.message()), code.status());
    }
}
