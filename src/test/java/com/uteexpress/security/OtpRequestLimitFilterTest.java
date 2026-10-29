package com.uteexpress.security;

import com.uteexpress.security.filter.OtpRequestLimitFilter;
import com.uteexpress.security.service.OtpRequestRateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class OtpRequestLimitFilterTest {
    @Test void contextRelativeEncodedRoutesShareTheCanonicalBudget() throws Exception {
        var filter=filter();
        assertThat(request(filter,"POST","/ute/forgot-password","/ute")).isEqualTo(204);
        assertThat(request(filter,"POST","/ute/%66orgot-password","/ute")).isEqualTo(429);
        assertThat(request(filter,"POST","/ute/%72egister","/ute")).isEqualTo(429);
        var fresh=filter();
        assertThat(request(fresh,"POST","/ute/%66orgot-password","/ute")).isEqualTo(204);
        assertThat(request(fresh,"POST","/ute/forgot-password","/ute")).isEqualTo(429);
    }

    @Test void unrelatedPathsAndSafeMethodsDoNotConsumeTheOtpBudget() throws Exception {
        var filter=filter();
        for(String route:new String[]{"/forgot-password/extra","/other","/login"})
            assertThat(request(filter,"POST",route,"")).isEqualTo(204);
        assertThat(request(filter,"GET","/forgot-password","")).isEqualTo(204);
        assertThat(request(filter,"POST","/forgot-password","")).isEqualTo(204);
        assertThat(request(filter,"POST","/forgot-password","")).isEqualTo(429);
    }

    private OtpRequestLimitFilter filter() {
        var policy=new OtpRequestLimitProperties(Duration.ofMinutes(1),1,10);
        return new OtpRequestLimitFilter(new OtpRequestRateLimiter(policy,Clock.systemUTC()),
                new SecurityErrorResponseWriter(new ObjectMapper()));
    }

    private int request(OtpRequestLimitFilter filter,String method,String uri,String context) throws Exception {
        var request=new MockHttpServletRequest(method,uri);request.setContextPath(context);
        request.setRemoteAddr("192.0.2.1");
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(204));
        return response.getStatus();
    }
}
