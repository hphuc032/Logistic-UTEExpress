# HP-owned OTP copy correction proposal — NOT APPLIED

Observed source8a96ec0 (Auth files unchanged from develop6a26370):

- `src/main/resources/templates/auth/verify-otp.html:42`: “Mã có hiệu lực trong 10 phút.”
- `src/main/resources/application.yml:77`: `uteexpress.security.otp.ttl: PT5M`.
- `src/main/java/com/uteexpress/identity/service/OtpProperties.java:16-17`: rejects nonpositive or greater-than5minute TTL.
- `src/main/java/com/uteexpress/identity/service/EmailVerificationService.java:126-127`: persisted expiry is `now.plus(properties.ttl())`.

Severity Low: misleading help text; no evidence that server accepts an expired OTP beyond its configured policy. Actual source controls the expiry, not browser text.

Minimal proposed replacement for HP to authorize/apply:

```diff
-                    <div id="code-help" class="form-text">Mã có hiệu lực trong 10 phút.</div>
+                    <div id="code-help" class="form-text">Mã có hiệu lực tối đa 5 phút.</div>
```

“tối đa” matches the constructor's configurable positive TTL up to5minutes. No server TTL/resend/rate-limit/JWT/CSRF policy change. QD has not edited HP Auth/UI source. HP decides correction/disposition and any owner tests.
