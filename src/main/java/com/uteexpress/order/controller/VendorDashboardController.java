package com.uteexpress.order.controller;

import com.uteexpress.common.exception.*;
import com.uteexpress.order.dto.RevenueSummary;
import com.uteexpress.order.service.RevenueService;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/vendor/dashboard")
@PreAuthorize("hasAuthority('ROLE_VENDOR')")
public class VendorDashboardController {
    private final RevenueService revenue;
    public VendorDashboardController(RevenueService revenue) { this.revenue=revenue; }
    @GetMapping(produces=MediaType.APPLICATION_JSON_VALUE) @ResponseBody
    public RevenueSummary data(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toExclusive,
            @RequestParam Map<String,String> fields) {
        if(!Set.of("from","toExclusive").containsAll(fields.keySet())) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return revenue.dashboard(from,toExclusive);
    }
    @GetMapping(produces=MediaType.TEXT_HTML_VALUE)
    public String page(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toExclusive,
            @RequestParam Map<String,String> fields,Model model) {
        model.addAttribute("revenue",data(from,toExclusive,fields)); return "vendor/dashboard";
    }
    @ExceptionHandler(value=ApplicationException.class,produces=MediaType.TEXT_HTML_VALUE)
    ModelAndView invalid(ApplicationException e) { return error(e.errorCode()); }
    @ExceptionHandler(value=MethodArgumentTypeMismatchException.class,produces=MediaType.TEXT_HTML_VALUE)
    ModelAndView binding(MethodArgumentTypeMismatchException e) { return error(ErrorCode.INVALID_REQUEST); }
    @ExceptionHandler(value=AccessDeniedException.class,produces=MediaType.TEXT_HTML_VALUE)
    ModelAndView denied(AccessDeniedException e) { return error(ErrorCode.ACCESS_DENIED); }
    private ModelAndView error(ErrorCode code) {
        return new ModelAndView("order/error",Map.of("errorCode",code.name(),"publicMessage",code.message()),code.status());
    }
}
