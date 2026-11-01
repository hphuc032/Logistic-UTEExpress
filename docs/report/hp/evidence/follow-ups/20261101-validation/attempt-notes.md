# Browser capture attempts

All attempts used accepted source a83c4aa03fb3806bd5aba47f0f9fa5ccf0f0b473 and JAR7392d65248ea6f62585a1e21d1a36cad3bb0d4178fbb18daea56dd687100a66b.

1. Attempt1 captured3 screenshots, then the harness incorrectly concatenated an absolute redirect Location with the base URL. Result BLOCKED; actual captures and cleanup retained.
2. Attempt2 captured7 screenshots, then expected302 from OTP resend. Source EmailVerificationController correctly renders the view with200. Result BLOCKED; no application defect or weakened assertion. The corrected runner submits the real browser form and explicitly asserts200.
3. Attempt3 captured all14 required function illustrations. Assertions confirm real login, protected401 after logout, missing-CSRF403, avatar upload/read, first address create and product image upload/read. Remaining captures show forms/list/query states only. No full functional regression or human acceptance is inferred.

Each run owned a separate PostgreSQL17.6 and Mailpit instance. All six exact owned containers and three JVMs were stopped/removed and checked absent. Orders/Payments remain0. Fixture address/avatar/image/OTP/tokenVersion mutations were expected; no claim that every DB fingerprint stayed unchanged is made. Production/Render was not accessed.
