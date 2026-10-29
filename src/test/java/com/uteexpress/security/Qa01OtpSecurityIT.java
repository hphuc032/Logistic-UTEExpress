package com.uteexpress.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uteexpress.identity.entity.OtpPurpose;
import com.uteexpress.identity.service.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** H8/T03/T04/T07: actual default OTP policy and account-independent HTTP abuse budget. */
@SpringBootTest(properties={"management.health.mail.enabled=false",
        "uteexpress.security.otp-request-limit.max-requests=3",
        "uteexpress.security.otp-request-limit.window=PT1M",
        "uteexpress.security.otp-request-limit.max-clients=64"})
@AutoConfigureMockMvc @Testcontainers @Import(Qa01OtpSecurityIT.Configuration.class)
class Qa01OtpSecurityIT {
    static final Instant BASE=Instant.parse("2026-10-27T10:00:00Z");
    static final String CODE="064271", NEW_PASSWORD="Qa01NewPassword1!";
    static final AtomicInteger IPS=new AtomicInteger();
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.6");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailVerificationService verification;
    @Autowired PasswordResetService resets;
    @Autowired PasswordEncoder passwords;
    @Autowired TestClock clock;
    @MockitoBean JavaMailSender mail;
    @BeforeEach void setup(){clock.now=BASE;reset(mail);}

    @ParameterizedTest @EnumSource(OtpPurpose.class)
    void otpIsValidImmediatelyBeforeFiveMinutesAndInvalidAtTheBoundary(OtpPurpose purpose) {
        var before=account(purpose);
        var at=account(purpose);
        assertThat(issue(purpose,before.email())).isEqualTo(EmailDispatchResult.SENT);
        assertThat(issue(purpose,at.email())).isEqualTo(EmailDispatchResult.SENT);
        var expiry=jdbc.queryForMap("SELECT sent_at,expires_at,code_hash FROM uteexpress.otp_tokens WHERE user_id=?",at.id());
        Instant sent=((java.sql.Timestamp)expiry.get("sent_at")).toInstant();
        Instant expires=((java.sql.Timestamp)expiry.get("expires_at")).toInstant();
        assertThat(Duration.between(sent,expires)).isEqualTo(Duration.ofMinutes(5));
        assertThat(expiry.get("code_hash").toString()).hasSize(64).doesNotContain(CODE);
        clock.now=BASE.plusSeconds(299);
        assertThat(consume(purpose,before.email())).isTrue();
        clock.now=BASE.plusSeconds(300);
        assertThat(consume(purpose,at.email())).isFalse();
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NULL AND attempts=0 FROM uteexpress.otp_tokens WHERE user_id=?",Boolean.class,at.id())).isTrue();
        if(purpose==OtpPurpose.EMAIL_VERIFICATION)
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.users WHERE id=?",String.class,at.id())).isEqualTo("PENDING_VERIFICATION");
        else {
            assertThat(jdbc.queryForObject("SELECT token_version FROM uteexpress.users WHERE id=?",Long.class,at.id())).isZero();
            assertThat(passwords.matches(NEW_PASSWORD,jdbc.queryForObject("SELECT password_hash FROM uteexpress.users WHERE id=?",String.class,at.id()))).isFalse();
        }
    }

    @ParameterizedTest @EnumSource(OtpPurpose.class)
    void previouslyIssuedLongerLivedRowsCannotBypassTheCurrentMaximumLifetime(OtpPurpose purpose) {
        var user=account(purpose);
        assertThat(issue(purpose,user.email())).isEqualTo(EmailDispatchResult.SENT);
        jdbc.update("UPDATE uteexpress.otp_tokens SET expires_at=sent_at+INTERVAL '10 minutes' WHERE user_id=?",user.id());
        clock.now=BASE.plusSeconds(300);
        assertThat(consume(purpose,user.email())).isFalse();
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NULL AND attempts=0 FROM uteexpress.otp_tokens WHERE user_id=?",Boolean.class,user.id())).isTrue();
    }

    @Test void rotatingUnknownIdentitiesAndForwardedHeadersCannotBypassTheIpBudget() throws Exception {
        String ip=ip(); long tokens=countTokens();
        for(int i=0;i<3;i++) submit(post("/forgot-password").param("email","unknown"+i+"@example.test")
                .header("X-Forwarded-For","203.0.113."+(i+1)).header("Forwarded","for=203.0.113."+(i+1)),ip,true)
                .andExpect(status().is3xxRedirection());
        submit(post("/forgot-password").param("email","another@example.test").header("X-Forwarded-For","203.0.113.99"),ip,true)
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        submit(post("/forgot-password").param("email","another@example.test"),ip(),true).andExpect(status().is3xxRedirection());
        assertThat(countTokens()).isEqualTo(tokens);verifyNoInteractions(mail);
    }

    @Test void cyclingOtpPurposesAndRoutesDoesNotResetTheIpBudget() throws Exception {
        String ip=ip(); long tokens=countTokens();
        submit(post("/forgot-password").param("email","unknown@example.test"),ip,true).andExpect(status().is3xxRedirection());
        submit(post("/verify-otp").param("identifier","unknown@example.test").param("code",CODE),ip,true).andExpect(status().isOk());
        submit(post("/reset-password").param("email","unknown@example.test").param("code",CODE)
                .param("newPassword",NEW_PASSWORD).param("confirmPassword",NEW_PASSWORD),ip,true).andExpect(status().isOk());
        submit(post("/verify-otp/resend").param("identifier","unknown@example.test"),ip,true)
                .andExpect(status().isTooManyRequests());
        assertThat(countTokens()).isEqualTo(tokens);verifyNoInteractions(mail);
    }

    @Test void knownAndUnknownAccountsHaveTheSameThrottleAndBlockedRequestsDoNotIssueOtp() throws Exception {
        var known=account(OtpPurpose.RESET_PASSWORD);
        for(String email:new String[]{known.email(),"unknown@example.test"}) {
            String ip=ip();
            for(int i=0;i<3;i++) submit(post("/forgot-password").param("email",email),ip,true)
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reset-password?requested=true"));
            long tokens=countTokens();
            submit(post("/forgot-password").param("email",email),ip,true).andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After","60"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(email))));
            assertThat(countTokens()).isEqualTo(tokens);
        }
    }

    @Test void csrfRejectionDoesNotConsumeTheValidRequestBudget() throws Exception {
        String ip=ip(); long tokens=countTokens();
        for(int i=0;i<5;i++) submit(post("/forgot-password").param("email","unknown@example.test"),ip,false).andExpect(status().isForbidden());
        for(int i=0;i<3;i++) submit(post("/forgot-password").param("email","unknown@example.test"),ip,true).andExpect(status().is3xxRedirection());
        submit(post("/forgot-password").param("email","unknown@example.test"),ip,true).andExpect(status().isTooManyRequests());
        assertThat(countTokens()).isEqualTo(tokens);verifyNoInteractions(mail);
    }

    @Test void encodedRouteSegmentsCannotBypassTheSharedAdmissionBudget() throws Exception {
        String ip=ip(); long tokens=countTokens();
        for(int i=0;i<3;i++) submit(post("/forgot-password").param("email","unknown@example.test"),ip,true)
                .andExpect(status().is3xxRedirection());
        for(String route:new String[]{"/%66orgot-password","/forgot%2Dpassword","/%72egister","/%76erify-otp","/%76erify-otp/resend","/%72eset-password"})
            submit(post(java.net.URI.create(route)).param("email","unknown@example.test"),ip,true)
                    .andExpect(status().isTooManyRequests());
        assertThat(countTokens()).isEqualTo(tokens);verifyNoInteractions(mail);
    }

    @ParameterizedTest @EnumSource(OtpPurpose.class)
    void mailFailureDoesNotExposeOtpOrSensitiveExceptionTextInApplicationLogsOrResponses(OtpPurpose purpose) throws Exception {
        var user=account(purpose); String syntheticSecret="qa01-sensitive-mail-failure-"+CODE;
        doThrow(new MailSendException(syntheticSecret)).when(mail).send(any(SimpleMailMessage.class));
        Logger logger=(Logger)LoggerFactory.getLogger("com.uteexpress.identity.service");
        var logs=new ListAppender<ILoggingEvent>(); logs.start();logger.addAppender(logs);
        try {
            var response=purpose==OtpPurpose.EMAIL_VERIFICATION
                    ? submit(post("/verify-otp/resend").param("identifier",user.email()),ip(),true)
                    : submit(post("/forgot-password").param("email",user.email()),ip(),true);
            assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain(CODE,syntheticSecret,NEW_PASSWORD,"MailSendException");
            assertThat(logs.list).isNotEmpty();
            assertThat(logs.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(CODE,syntheticSecret,NEW_PASSWORD));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.otp_tokens WHERE user_id=? AND code_hash=?",Long.class,user.id(),CODE)).isZero();
        } finally {logger.detachAppender(logs);logs.stop();}
    }

    private ResultActions submit(MockHttpServletRequestBuilder request,String ip,boolean csrf) throws Exception {
        request.with(r->{r.setRemoteAddr(ip);return r;});if(csrf) request.with(csrf());return mvc.perform(request);
    }
    private static String ip(){return "192.0.2."+IPS.incrementAndGet();}
    private long countTokens(){return jdbc.queryForObject("SELECT count(*) FROM uteexpress.otp_tokens",Long.class);}
    private EmailDispatchResult issue(OtpPurpose p,String email){return p==OtpPurpose.EMAIL_VERIFICATION?verification.sendVerificationCode(email):resets.sendResetCode(email);}
    private boolean consume(OtpPurpose p,String email){return p==OtpPurpose.EMAIL_VERIFICATION
            ? verification.verify(email,CODE)==EmailVerificationResult.VERIFIED
            : resets.resetPassword(email,CODE,NEW_PASSWORD)==PasswordResetResult.RESET;}
    private Account account(OtpPurpose p){
        String name="qa01"+UUID.randomUUID().toString().replace("-","").substring(0,20),email=name+"@example.test";
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,?,?) RETURNING id",
                Long.class,email,email,name,name,passwords.encode("Qa01OldPassword1!"),p==OtpPurpose.EMAIL_VERIFICATION?"PENDING_VERIFICATION":"ACTIVE");
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='USER'",id);
        return new Account(id,email);
    }
    record Account(long id,String email){}
    @TestConfiguration(proxyBeanMethods=false) static class Configuration {
        @Bean @Primary TestClock qa01Clock(){return new TestClock();}
        @Bean @Primary OtpCodeGenerator qa01Codes(){return ()->CODE;}
    }
    static class TestClock extends Clock {
        volatile Instant now=BASE;
        @Override public Instant instant(){return now;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
    }
}
