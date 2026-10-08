package com.uteexpress.promotion;

import com.uteexpress.common.exception.*;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.service.VendorVoucherService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class VendorVoucherIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    private static final String PASSWORD = "TestVendor1!";
    private static final Pattern TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired VendorVoucherService management;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    long vendor, shop, other, otherShop, ownVoucher, foreignVoucher, platformVoucher;
    String username, code, foreignCode, platformCode;

    @BeforeEach void fixture() {
        username = "v" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        vendor = account(username);
        other = account("o" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        shop = shop(vendor);
        otherShop = shop(other);
        code = "OWN_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        foreignCode = "OTHER_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        platformCode = "PLATFORM_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        ownVoucher = voucher(code, "SHOP", shop, vendor);
        foreignVoucher = voucher(foreignCode, "SHOP", otherShop, other);
        platformVoucher = voucher(platformCode, "PLATFORM", null, vendor);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void listIncludesOnlyOwnedShopVoucherAndRendersDisableCsrf() throws Exception {
        mvc.perform(get("/vendor/vouchers").with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(view().name("vendor/vouchers/list"))
                .andExpect(content().string(containsString(code)))
                .andExpect(content().string(not(containsString(foreignCode))))
                .andExpect(content().string(not(containsString(platformCode))))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("/vendor/vouchers/new")));
    }

    @Test void createDerivesShopCreatorScopeAndNormalizesCodeAndTimezone() throws Exception {
        String newCode = "new_" + UUID.randomUUID().toString().replace("-", "");
        mvc.perform(valid(post("/vendor/vouchers"), newCode).with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().is3xxRedirection());
        var row = jdbc.queryForMap("SELECT * FROM uteexpress.vouchers WHERE code=?", newCode.toUpperCase(Locale.ROOT));
        assertThat(row).containsEntry("scope", "SHOP").containsEntry("shop_id", shop).containsEntry("created_by", vendor);
        assertThat(jdbc.queryForObject("SELECT starts_at AT TIME ZONE 'UTC' FROM uteexpress.vouchers WHERE id=?", LocalDateTime.class, row.get("id")))
                .isEqualTo(LocalDateTime.of(2020, 1, 1, 17, 0));
    }

    @ParameterizedTest @CsvSource({"code,bad code", "type,PLATFORM", "type,INVALID", "value,0", "value,100.5",
            "value,garbage", "maxDiscount,-1", "maxDiscount,0.5", "minSubtotal,-1", "minSubtotal,0.5",
            "totalLimit,0", "totalLimit,9223372036854775808", "perUserLimit,0", "endsAt,2019-01-01T00:00",
            "startsAt,invalid", "code,''"})
    void invalidCreateRedisplaysErrorsWithoutPersistence(String field, String value) throws Exception {
        var request = valid(post("/vendor/vouchers"), code + "_NEW");
        override(request, field, value);
        long before = count();
        mvc.perform(request.with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(view().name("vendor/vouchers/form"))
                .andExpect(model().attributeHasErrors("voucherForm"));
        assertThat(count()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"shopId", "ownerId", "vendorId", "createdBy", "scope"})
    void forgedIdentityAndPlatformScopeAreRejectedOnCreate(String field) throws Exception {
        long before = count();
        mvc.perform(valid(post("/vendor/vouchers"), code + "_NEW").param(field, field.equals("scope") ? "PLATFORM" : "" + otherShop)
                        .with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("voucherForm"));
        assertThat(count()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"shopId", "ownerId", "vendorId", "createdBy", "scope"})
    void updateCannotChangeOwnerShopOrScope(String field) throws Exception {
        var before = row(ownVoucher);
        mvc.perform(valid(post(path(ownVoucher, "/update")), code).param("version", "0")
                        .param(field, field.equals("scope") ? "PLATFORM" : "" + otherShop)
                        .with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("voucherForm"));
        assertThat(row(ownVoucher)).isEqualTo(before);
    }

    @Test void updateOwnedUnusedVoucherPersistsDefinitionAndIncrementsVersion() throws Exception {
        var request = valid(post(path(ownVoucher, "/update")), code + "_EDIT").param("version", "0");
        override(request, "type", "FIXED");
        override(request, "value", "5000");
        mvc.perform(request.with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path(ownVoucher, "/edit")));
        assertThat(row(ownVoucher)).containsEntry("code", code + "_EDIT").containsEntry("type", "FIXED")
                .containsEntry("shop_id", shop).containsEntry("scope", "SHOP").containsEntry("created_by", vendor)
                .containsEntry("version", 1L);
        assertThat((BigDecimal) row(ownVoucher).get("value")).isEqualByComparingTo("5000");
    }

    @ParameterizedTest @CsvSource({"foreign,edit", "platform,edit", "foreign,update", "platform,update", "foreign,disable", "platform,disable"})
    void foreignAndPlatformResourcesReturnNotFound(String target, String operation) throws Exception {
        long id = target.equals("foreign") ? foreignVoucher : platformVoucher;
        var before = row(id);
        var request = operation.equals("edit") ? get(path(id, "/edit")) : post(path(id, "/" + operation)).with(csrf());
        if (operation.equals("update")) request = valid(request, "TAMPERED").param("version", "0");
        mvc.perform(request.with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isNotFound()).andExpect(view().name("vendor/vouchers/error"))
                .andExpect(content().string(not(containsString((String) before.get("code")))));
        assertThat(row(id)).isEqualTo(before);
    }

    @Test void disableIsIdempotentAndDoesNotDeleteHistory() throws Exception {
        usage("REDEEMED");
        var history = jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE voucher_id=?", ownVoucher);
        mvc.perform(post(path(ownVoucher, "/disable")).with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().is3xxRedirection());
        var disabled = row(ownVoucher);
        assertThat(disabled).containsEntry("active", false);
        mvc.perform(post(path(ownVoucher, "/disable")).with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().is3xxRedirection());
        assertThat(row(ownVoucher)).isEqualTo(disabled);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE voucher_id=?", ownVoucher)).isEqualTo(history);
    }

    @ParameterizedTest @ValueSource(strings = {"create", "update", "disable"})
    void everyMutationRequiresCsrf(String operation) throws Exception {
        var before = row(ownVoucher);
        var request = operation.equals("create") ? post("/vendor/vouchers") : post(path(ownVoucher, "/" + operation));
        mvc.perform(valid(request, code).param("version", "0").with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isForbidden());
        assertThat(row(ownVoucher)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"ANONYMOUS", "USER", "ADMIN", "MANAGER", "SHIPPER"})
    void allRoutesFollowVendorSecurityContract(String role) throws Exception {
        for (var request : List.of(get("/vendor/vouchers"), get("/vendor/vouchers/new"), get(path(ownVoucher, "/edit")),
                valid(post("/vendor/vouchers"), code), valid(post(path(ownVoucher, "/update")), code).param("version", "0"),
                post(path(ownVoucher, "/disable")))) {
            request.with(csrf());
            if (!role.equals("ANONYMOUS")) request.with(user(principal(vendor, role)));
            mvc.perform(request).andExpect(role.equals("ANONYMOUS") ? status().isUnauthorized() : status().isForbidden());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "REJECTED"})
    void unapprovedShopCannotReadOrMutateVouchers(String shopStatus) throws Exception {
        jdbc.update("UPDATE uteexpress.shops SET status=?, rejection_reason=? WHERE id=?", shopStatus,
                shopStatus.equals("REJECTED") ? "Rejected" : null, shop);
        for (var request : List.of(get("/vendor/vouchers"), get("/vendor/vouchers/new"), get(path(ownVoucher, "/edit")),
                valid(post("/vendor/vouchers"), code + "_NEW"), valid(post(path(ownVoucher, "/update")), code).param("version", "0"),
                post(path(ownVoucher, "/disable")))) {
            mvc.perform(request.with(csrf()).with(user(principal(vendor, "VENDOR")))).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"create", "update"})
    void duplicateCanonicalCodeReturnsFriendlyFormError(String operation) throws Exception {
        var before = row(ownVoucher);
        var request = post(operation.equals("create") ? "/vendor/vouchers" : path(ownVoucher, "/update"));
        mvc.perform(valid(request, foreignCode.toLowerCase(Locale.ROOT)).param("version", "0")
                        .with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("voucherForm"));
        assertThat(row(ownVoucher)).isEqualTo(before);
    }

    @Test void staleVersionReturnsFormConflictWithoutOverwritingNewerData() throws Exception {
        jdbc.update("UPDATE uteexpress.vouchers SET value=15,version=1 WHERE id=?", ownVoucher);
        var before = row(ownVoucher);
        mvc.perform(valid(post(path(ownVoucher, "/update")), code).param("version", "0")
                        .with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("voucherForm"));
        assertThat(row(ownVoucher)).isEqualTo(before);
    }

    @ParameterizedTest @CsvSource({"REDEEMED,code,RENAMED", "RELEASED,code,RENAMED", "REDEEMED,type,FIXED", "RELEASED,type,FIXED",
            "REDEEMED,value,20", "RELEASED,value,20", "REDEEMED,maxDiscount,500.00", "RELEASED,maxDiscount,500.00",
            "REDEEMED,minSubtotal,100.00", "RELEASED,minSubtotal,100.00", "REDEEMED,totalLimit,9", "RELEASED,totalLimit,9",
            "REDEEMED,perUserLimit,1", "RELEASED,perUserLimit,1"})
    void anyHistoryPreventsRedefinitionOrQuotaReduction(String state, String field, String value) throws Exception {
        usage(state);
        var before = row(ownVoucher);
        var request = valid(post(path(ownVoucher, "/update")), code).param("version", "0");
        override(request, field, value);
        mvc.perform(request.with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("voucherForm"))
                .andExpect(result -> {
                    var binding = (org.springframework.validation.BindingResult) result.getModelAndView().getModel()
                            .get(org.springframework.validation.BindingResult.MODEL_KEY_PREFIX + "voucherForm");
                    assertThat(binding.getGlobalErrors()).extracting(error -> error.getCode()).contains("conflict");
                });
        assertThat(row(ownVoucher)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"REDEEMED", "RELEASED"})
    void historyAllowsQuotaIncreaseAndWindowChangesWithoutChangingUsage(String state) throws Exception {
        jdbc.update("UPDATE uteexpress.vouchers SET max_discount=500.00 WHERE id=?", ownVoucher);
        usage(state);
        var before = jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE voucher_id=?", ownVoucher);
        var request = valid(post(path(ownVoucher, "/update")), code).param("version", "0");
        override(request, "minSubtotal", "0.00");
        request.param("maxDiscount", "500.00");
        override(request, "totalLimit", "20");
        override(request, "perUserLimit", "3");
        override(request, "endsAt", "2099-01-01T00:00");
        override(request, "active", "false");
        mvc.perform(request.with(csrf()).with(user(principal(vendor, "VENDOR")))).andExpect(status().is3xxRedirection());
        assertThat(row(ownVoucher)).containsEntry("total_limit", 20L).containsEntry("per_user_limit", 3L).containsEntry("active", false);
        authenticateVendor();
        assertThat(management.get(ownVoucher).used()).isEqualTo(state.equals("REDEEMED") ? 1 : 0);
        assertThat(management.get(ownVoucher).hasUsage()).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE voucher_id=?", ownVoucher)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"0.00", "500.00"})
    void renderedEditFormRoundTripsScaleTwoMoneyWhileUpdatingQuota(String minimum) throws Exception {
        String newCode = code + "_ROUNDTRIP";
        var create = valid(post("/vendor/vouchers"), newCode).param("maxDiscount", "500.00");
        override(create, "minSubtotal", minimum);
        mvc.perform(create.with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().is3xxRedirection());
        long id = jdbc.queryForObject("SELECT id FROM uteexpress.vouchers WHERE code=?", Long.class, newCode);
        var before = row(id);
        String html = mvc.perform(get(path(id, "/edit")).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(view().name("vendor/vouchers/form"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("action=\"" + path(id, "/update") + "\"");
        assertThat(renderedInput(html, "minSubtotal")).isEqualTo(minimum);
        assertThat(renderedInput(html, "maxDiscount")).isEqualTo("500.00");
        var update = post(path(id, "/update"));
        for (String field : List.of("code", "value", "maxDiscount", "minSubtotal", "startsAt", "endsAt",
                "totalLimit", "perUserLimit", "active", "version")) {
            update.param(field, renderedInput(html, field));
        }
        var selectedType = Pattern.compile("<option\\b[^>]*selected=\"selected\"[^>]*>").matcher(html);
        assertThat(selectedType.find()).isTrue();
        update.param("type", renderedAttribute(selectedType.group(), "value"));
        override(update, "totalLimit", "20");
        mvc.perform(update.with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path(id, "/edit")));
        var after = row(id);
        assertThat(after).containsEntry("total_limit", 20L).containsEntry("version", 1L);
        assertThat(after.get("min_subtotal")).isEqualTo(before.get("min_subtotal"));
        assertThat(after.get("max_discount")).isEqualTo(before.get("max_discount"));
    }

    @ParameterizedTest @CsvSource({"create,minSubtotal", "create,maxDiscount", "update,minSubtotal", "update,maxDiscount"})
    void fractionalMoneyIsRejectedOnCreateAndUpdateWithoutPersistence(String operation, String field) throws Exception {
        var before = row(ownVoucher);
        long beforeCount = count();
        var request = valid(post(operation.equals("create") ? "/vendor/vouchers" : path(ownVoucher, "/update")),
                operation.equals("create") ? code + "_FRACTION" : code).param("version", "0");
        override(request, field, "500.50");
        mvc.perform(request.with(csrf()).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(view().name("vendor/vouchers/form"))
                .andExpect(model().attributeHasErrors("voucherForm"));
        assertThat(count()).isEqualTo(beforeCount);
        assertThat(row(ownVoucher)).isEqualTo(before);
    }

    private static String renderedInput(String html, String field) {
        var input = Pattern.compile("<input\\b[^>]*\\bname=\"" + Pattern.quote(field) + "\"[^>]*>").matcher(html);
        assertThat(input.find()).as("rendered input %s", field).isTrue();
        return renderedAttribute(input.group(), "value");
    }
    private static String renderedAttribute(String element, String attribute) {
        var value = Pattern.compile("\\b" + Pattern.quote(attribute) + "=\"([^\"]*)\"").matcher(element);
        assertThat(value.find()).as("rendered attribute %s", attribute).isTrue();
        return HtmlUtils.htmlUnescape(value.group(1));
    }

    @Test void managementWaitsForQuotaLockAndSeesHistoryCommittedWhileWaiting() throws Exception {
        authenticateVendor();
        var form = updateForm();
        form.setValue(new BigDecimal("20"));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = new TransactionTemplate(transactions).execute(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.vouchers WHERE id=? FOR UPDATE", Long.class, ownVoucher);
                var pending = executor.submit(() -> {
                    authenticateVendor();
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(inner -> {
                            jdbc.execute("SET LOCAL lock_timeout='15s'");
                            management.update(ownVoucher, form);
                        });
                        return null;
                    } catch (ApplicationException error) { return error.errorCode(); }
                    finally { SecurityContextHolder.clearContext(); }
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (!waiting && System.nanoTime() < deadline) {
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%vouchers%'", Integer.class) > 0;
                    if (!waiting) {
                        try { Thread.sleep(20); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                    }
                }
                assertThat(waiting).as("management serializes with the checkout Voucher row lock").isTrue();
                usage("REDEEMED");
                return pending;
            });
            assertThat(future.get(20, TimeUnit.SECONDS)).isEqualTo(ErrorCode.CONFLICT);
            assertThat((BigDecimal) row(ownVoucher).get("value")).isEqualByComparingTo("10");
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void realJwtCookieAndRenderedCsrfTokensAuthorizeCreateEditAndDisableForms() throws Exception {
        // csrf() installs a test repository on the shared filter. Isolate this real-cookie proof.
        var loginPage = mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn().getResponse();
        Cookie auth = mvc.perform(post("/login").cookie(loginPage.getCookie("XSRF-TOKEN"))
                        .param("_csrf", token(loginPage.getContentAsString())).param("identifier", username).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
        var createPage = mvc.perform(get("/vendor/vouchers/new").cookie(auth)).andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn().getResponse();
        String newCode = code + "_NEW";
        mvc.perform(valid(post("/vendor/vouchers"), newCode).cookie(auth, createPage.getCookie("XSRF-TOKEN"))
                        .param("_csrf", token(createPage.getContentAsString())))
                .andExpect(status().is3xxRedirection());
        long id = jdbc.queryForObject("SELECT id FROM uteexpress.vouchers WHERE code=?", Long.class, newCode);
        var editPage = mvc.perform(get(path(id, "/edit")).cookie(auth)).andExpect(status().isOk()).andReturn().getResponse();
        mvc.perform(valid(post(path(id, "/update")), newCode).param("version", "0")
                        .cookie(auth, editPage.getCookie("XSRF-TOKEN")).param("_csrf", token(editPage.getContentAsString())))
                .andExpect(status().is3xxRedirection());
        var listPage = mvc.perform(get("/vendor/vouchers").cookie(auth)).andExpect(status().isOk()).andReturn().getResponse();
        mvc.perform(post(path(id, "/disable")).cookie(auth, listPage.getCookie("XSRF-TOKEN"))
                        .param("_csrf", token(listPage.getContentAsString())))
                .andExpect(status().is3xxRedirection());
        assertThat(row(id)).containsEntry("active", false);
    }

    private String token(String html) {
        var matcher = TOKEN.matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
    private static MockHttpServletRequestBuilder valid(MockHttpServletRequestBuilder request, String code) {
        return request.param("code", code).param("type", "PERCENTAGE").param("value", "10")
                .param("minSubtotal", "0").param("startsAt", "2020-01-02T00:00").param("endsAt", "2100-01-01T00:00")
                .param("totalLimit", "10").param("perUserLimit", "2").param("active", "true");
    }
    private static void override(MockHttpServletRequestBuilder request, String field, String value) {
        request.with(http -> { http.setParameter(field, value); return http; });
    }
    private VendorVoucherUpdateForm updateForm() {
        var form = new VendorVoucherUpdateForm();
        form.setCode(code); form.setType(VoucherType.PERCENTAGE); form.setValue(new BigDecimal("10"));
        form.setStartsAt(LocalDateTime.of(2020, 1, 2, 0, 0)); form.setEndsAt(LocalDateTime.of(2100, 1, 1, 0, 0));
        form.setTotalLimit(10L); form.setPerUserLimit(2L); form.setVersion(0L);
        return form;
    }
    private String path(long id, String suffix) { return "/vendor/vouchers/" + id + suffix; }
    private Map<String, Object> row(long id) { return jdbc.queryForMap("SELECT * FROM uteexpress.vouchers WHERE id=?", id); }
    private long count() { return jdbc.queryForObject("SELECT count(*) FROM uteexpress.vouchers", Long.class); }
    private UteExpressPrincipal principal(long id, String role) {
        return new UteExpressPrincipal(id, "vendor" + id, null, 0, List.of(new SimpleGrantedAuthority("ROLE_" + role)), true);
    }
    private void authenticateVendor() {
        var principal = principal(vendor, "VENDOR");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
    private long account(String name) {
        long id = jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id",
                Long.class, name + "@example.test", name + "@example.test", name, name, passwords.encode(PASSWORD));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='VENDOR'", id);
        return id;
    }
    private long shop(long owner) {
        return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, owner, UUID.randomUUID().toString());
    }
    private long voucher(String code, String scope, Long shop, long creator) {
        return jdbc.queryForObject("INSERT INTO uteexpress.vouchers(code,scope,shop_id,type,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,active,created_by) VALUES (?,?,?,'PERCENTAGE',10,0,'2020-01-01','2100-01-01',10,2,true,?) RETURNING id", Long.class, code, scope, shop, creator);
    }
    private void usage(String state) {
        String key = UUID.randomUUID().toString();
        long order = jdbc.queryForObject("""
                INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,
                    receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,
                    grand_total,commission_amount,commission_rate_snapshot,voucher_id,voucher_code,voucher_scope)
                VALUES (?,?,?, ?,?,'NEW','Receiver','0900','VN','District','Detail',10000,1000,0,9000,0,0,?,?,'SHOP') RETURNING id
                """, Long.class, "ORD-" + key, key, "hash-" + key, vendor, shop, ownVoucher, code);
        jdbc.update("INSERT INTO uteexpress.voucher_usages(voucher_id,user_id,order_id,status,discount_amount,released_at) VALUES (?,?,?,?,1000,CASE WHEN ?='RELEASED' THEN CURRENT_TIMESTAMP ELSE NULL END)", ownVoucher, vendor, order, state, state);
    }
}
