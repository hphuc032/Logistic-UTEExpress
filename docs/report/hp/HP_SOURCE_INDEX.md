# HP source index

Snapshot `6a26370659a2fa7b95c6a1d00d2598820de6bb34`. Master Plan `18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a`.

## Mapping chức năng và source

### 4.1 Đăng ký tài khoản — AUTH-01
- Routes / entry: GET /register; POST /register
- Controller/entry: `src/main/java/com/uteexpress/identity/controller/RegistrationController.java`
- Service: `src/main/java/com/uteexpress/identity/service/RegistrationService.java`; anchor `public RegistrationOutcome register(`
- Contracts: RegisterForm; RegistrationCommand; RegistrationOutcome; UserEntity; RoleEntity; UserRepository; RoleRepository; UserRoleRepository.
- Test classes: AuthRegistrationIT; RegistrationControllerTest; RegistrationServiceTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 4.2 Đăng nhập bằng email hoặc username — AUTH-02
- Routes / entry: GET /login; POST /login
- Controller/entry: `src/main/java/com/uteexpress/security/controller/LoginController.java`
- Service: `src/main/java/com/uteexpress/security/service/LoginService.java`; anchor `public LoginOutcome login(`
- Contracts: LoginForm/LoginCommand/LoginOutcome; UteExpressPrincipal; IdentityAuthenticationService; AuthAccountSnapshot.
- Test classes: Auth02AuthenticationIT; AuthenticationWebTest; JwtAuthenticationFilterTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 4.3 Xác thực JWT trên từng request — AUTH-02 SEC-01
- Routes / entry: Không có endpoint JWT riêng; JwtAuthenticationFilter áp dụng trên request trong SecurityFilterChain.
- Controller/entry: `src/main/java/com/uteexpress/security/filter/JwtAuthenticationFilter.java`
- Service: `src/main/java/com/uteexpress/security/filter/JwtAuthenticationFilter.java`; anchor `private void authenticate(`
- Contracts: JwtTokenService; JwtConfiguration; IdentityAuthenticationService; UteExpressPrincipalFactory; CurrentAccountIdProvider.
- Test classes: JwtSecurityConfigurationTest; JwtAuthenticationFilterTest; Auth02AuthenticationIT
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 4.4 Đăng xuất và thu hồi JWT — AUTH-02
- Routes / entry: POST /logout (Spring Security LogoutFilter, không phải MVC controller)
- Controller/entry: `src/main/java/com/uteexpress/security/SecurityConfig.java`
- Service: `src/main/java/com/uteexpress/security/web/TokenVersionLogoutHandler.java`; anchor `public void logout(`
- Contracts: IdentityAuthenticationService.invalidateTokens; UserRepository; JwtLogoutSuccessHandler; JwtCookieService.
- Test classes: Auth02AuthenticationIT; AuthenticationWebTest; CsrfCookieWebTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 4.5 Xác minh email và gửi lại OTP — AUTH-03 QA-01
- Routes / entry: GET /verify-otp; POST /verify-otp; POST /verify-otp/resend
- Controller/entry: `src/main/java/com/uteexpress/identity/controller/EmailVerificationController.java`
- Service: `src/main/java/com/uteexpress/identity/service/EmailVerificationService.java`; anchor `public EmailVerificationResult verify(`
- Contracts: VerifyOtpForm; ResendOtpForm; OtpTokenEntity; OtpPurpose; OtpTokenRepository; OtpMailService.
- Test classes: Auth03EmailVerificationIT; Qa01OtpSecurityIT; OtpSecurityTest; EmailVerificationControllerTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 4.6 Yêu cầu khôi phục mật khẩu — AUTH-04
- Routes / entry: GET /forgot-password; POST /forgot-password
- Controller/entry: `src/main/java/com/uteexpress/identity/controller/PasswordResetController.java`
- Service: `src/main/java/com/uteexpress/identity/service/PasswordResetService.java`; anchor `public EmailDispatchResult sendResetCode(`
- Contracts: ForgotPasswordForm; PasswordResetService.issue; UserRepository; OtpPurpose.RESET_PASSWORD; OtpTokenRepository.
- Test classes: PasswordResetControllerTest; PasswordResetServiceTest; Auth04PasswordResetIT
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 4.7 Đặt lại mật khẩu bằng OTP — AUTH-04
- Routes / entry: GET /reset-password; POST /reset-password
- Controller/entry: `src/main/java/com/uteexpress/identity/controller/PasswordResetController.java`
- Service: `src/main/java/com/uteexpress/identity/service/PasswordResetService.java`; anchor `public PasswordResetResult resetPassword(`
- Contracts: ResetPasswordForm; PasswordPolicy; PasswordMatches; UserEntity.resetPassword; OtpTokenEntity.consume.
- Test classes: Auth04PasswordResetIT; PasswordResetServiceTest; PasswordResetControllerTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 4.8 Phân quyền CSRF và ngân sách OTP — SEC-01 SEC-02 QA-01
- Routes / entry: Cross-cutting: public allowlist và /user/**, /vendor/**, /admin/**, /manager/**, /shipper/**; không thêm endpoint nghiệp vụ.
- Controller/entry: `src/main/java/com/uteexpress/security/SecurityConfig.java`
- Service: `src/main/java/com/uteexpress/security/filter/OtpRequestLimitFilter.java`; anchor `@Override protected void doFilterInternal(`
- Contracts: RoleCode; CurrentUserProvider; CurrentAccountIdProvider; SecurityErrorResponseWriter; GlobalExceptionHandler.
- Test classes: SecurityFoundationTest; SecurityHardeningIT; OtpRequestLimitFilterTest; OtpRequestRateLimiterTest; Qa01OtpSecurityIT
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.1 Hồ sơ cá nhân — USER-01
- Routes / entry: GET /user/profile; POST /user/profile
- Controller/entry: `src/main/java/com/uteexpress/account/controller/ProfileController.java`
- Service: `src/main/java/com/uteexpress/account/service/ProfileService.java`; anchor `public ProfileView updateProfile(`
- Contracts: ProfileForm; ProfileView; AccountProfileData; AccountIdentityService; UserRepository/UserEntity trong identity.
- Test classes: User01ProfileIT; ProfileControllerTest; ProfileServiceTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.2 Đổi mật khẩu khi đã đăng nhập — USER-01
- Routes / entry: POST /user/password
- Controller/entry: `src/main/java/com/uteexpress/account/controller/ProfileController.java`
- Service: `src/main/java/com/uteexpress/identity/service/AccountIdentityService.java`; anchor `public void changePassword(`
- Contracts: PasswordChangeForm; CurrentPasswordMismatchException; PasswordPolicy; UserEntity; PasswordEncoder.
- Test classes: User01ProfileIT; ProfileControllerTest; ProfileServiceTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.3 Ảnh đại diện và storage an toàn — USER-01 SEC-02
- Routes / entry: GET /user/avatar; POST /user/avatar
- Controller/entry: `src/main/java/com/uteexpress/account/controller/ProfileController.java`
- Service: `src/main/java/com/uteexpress/account/service/ProfileService.java`; anchor `public void updateAvatar(`
- Contracts: AvatarUpload; UploadContent; ImageStoragePolicy; FileStorageService; LocalFileStorageService; StoredContent.
- Test classes: User01ProfileIT; LocalFileStorageServiceTest; SecurityHardeningIT
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.4 Địa chỉ và địa chỉ mặc định — USER-02
- Routes / entry: GET /user/addresses; POST /user/addresses; POST /user/addresses/{id}/update; POST /user/addresses/{id}/delete; POST /user/addresses/{id}/default
- Controller/entry: `src/main/java/com/uteexpress/account/controller/AddressController.java`
- Service: `src/main/java/com/uteexpress/account/service/AddressService.java`; anchor `public AddressView setDefault(`
- Contracts: Address; AddressRepository; AddressForm/AddressView/AddressData; AddressQueryService; AccountIdentityService.
- Test classes: User02AddressIT; AddressServiceTest; AddressControllerTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.5 Đăng ký và theo dõi cửa hàng — VENDOR-01
- Routes / entry: GET /user/shop; GET /user/shop/register; POST /user/shop/register
- Controller/entry: `src/main/java/com/uteexpress/shop/controller/ShopRegistrationController.java`
- Service: `src/main/java/com/uteexpress/shop/service/ShopRegistrationService.java`; anchor `public ShopView register(`
- Contracts: Shop/ShopStatus; ShopRepository; ShopView; ShopRegistrationForm/Request; VendorRoleGrantService ở identity.
- Test classes: ShopRegistrationIT; ShopRegistrationControllerTest; ShopRegistrationServiceTest; ShopApprovalIT (QĐ boundary)
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.6 Quản lý sản phẩm của Vendor — PROD-00 VENDOR-02
- Routes / entry: GET /vendor/products; GET /vendor/products/new; GET /vendor/products/{id}/edit; POST /vendor/products; POST /vendor/products/{id}/update; POST /vendor/products/{id}/delete
- Controller/entry: `src/main/java/com/uteexpress/catalog/controller/VendorProductController.java`
- Service: `src/main/java/com/uteexpress/catalog/service/ProductService.java`; anchor `public ProductView update(`
- Contracts: Product; ProductRepository; ProductView; ProductCreateRequest/ProductUpdateRequest; VendorShopData; CategoryOption.
- Test classes: VendorProductIT; ProductServiceTest; VendorProductControllerTest; ProductModelTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.7 Ảnh sản phẩm và quyền đọc ảnh — VENDOR-02 PROD-01 SEC-02
- Routes / entry: POST /vendor/products/{id}/images; POST /vendor/products/{id}/images/{imageId}/delete; GET /vendor/products/{id}/images/{imageId}/content; GET /products/{productId}/images/{imageId}/content
- Controller/entry: `src/main/java/com/uteexpress/catalog/controller/VendorProductController.java`
- Service: `src/main/java/com/uteexpress/catalog/service/ProductService.java`; anchor `public ProductImageView addImage(`
- Contracts: ProductImage; ProductImageRepository; ProductImageView; FileStorageService; PublicCatalogService.image.
- Test classes: VendorProductIT; PublicCatalogIT; ProductImagePolicyTest; LocalFileStorageServiceTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.8 Catalog công khai và chi tiết — PROD-01
- Routes / entry: GET /; GET /products; GET /products/{id}; GET /categories/{slug}; GET /shops; GET /shops/{slug}
- Controller/entry: `src/main/java/com/uteexpress/catalog/controller/PublicCatalogController.java`
- Service: `src/main/java/com/uteexpress/catalog/service/PublicCatalogService.java`; anchor `public ProductDetailView product(`
- Contracts: CatalogReadRepository; ProductCard/ProductDetailView/PublicHomeView; Shop/Category service contracts của owner.
- Test classes: PublicCatalogIT; PublicCatalogControllerTest; PublicCatalogServiceTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.9 Tìm kiếm lọc sắp xếp và phân trang — PROD-02
- Routes / entry: GET /products với q, shop, category, minPrice, maxPrice, sort, page, size
- Controller/entry: `src/main/java/com/uteexpress/catalog/controller/PublicCatalogController.java`
- Service: `src/main/java/com/uteexpress/catalog/service/PublicCatalogService.java`; anchor `public ProductSearchPage search(`
- Contracts: ProductSearchCriteria; ProductSearchPage; ProductSort; CatalogReadRepository.countPublicProducts/findPublicProducts.
- Test classes: ProductDiscoveryIT; PublicCatalogControllerTest; PublicCatalogServiceTest
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.10 Inventory contract và tích hợp checkout — PROD-00 INT-01
- Routes / entry: InventoryService là biên service, không có HTTP controller riêng. Checkout/Order của TD gọi trong transaction nghiệp vụ.
- Controller/entry: `src/main/java/com/uteexpress/catalog/service/InventoryService.java`
- Service: `src/main/java/com/uteexpress/catalog/service/DatabaseInventoryService.java`; anchor `public void lockAndCheck(`
- Contracts: InventoryService; StockQuantity; ProductRepository.findAllByIdForUpdate; CatalogQueryService; OrderLifecycleService thuộc TD.
- Test classes: ProductCatalogIT; INT-01 evidence và các Checkout/Order IT của TD
- Runtime behavior và limitations: xem DOCX mục tương ứng.

### 5.11 Shared UI và responsive — UI-01 UI-02
- Routes / entry: Shared templates/static phục vụ các route hiện có; NavbarAuthenticationAdvice bổ sung model, không là business endpoint.
- Controller/entry: `src/main/java/com/uteexpress/security/controller/NavbarAuthenticationAdvice.java`
- Service: `src/main/resources/static/js/app.js`; anchor `document.querySelectorAll(`
- Contracts: layout/base.html; layout/dashboard.html; fragments; app.css/app.js; RoleCode/BuyerAccessService chỉ dùng boundary.
- Test classes: UiLayoutTest; src/test/js/app.test.cjs (7 mới); CI171 có40 Node/705 Java
- Runtime behavior và limitations: xem DOCX mục tương ứng.

## Source excerpt exact ranges và hashes
- `src/main/java/com/uteexpress/identity/service/RegistrationService.java:36–48` SHA256 `03c47719d6124240f1612afe0db6f58a9010c2f362b58ee254cb5ab8f06a636b`; `figures\source\RegistrationService-36.png`
- `src/main/java/com/uteexpress/security/service/LoginService.java:27–38` SHA256 `1ff6076e15065e0cca27faaf33b00fca9040c1f19d3c7a9580da85d1fa1aca2a`; `figures\source\LoginService-27.png`
- `src/main/java/com/uteexpress/security/filter/JwtAuthenticationFilter.java:52–64` SHA256 `6afce6ab375362866292ea3ab1c3acb72413ebbe84adeec492fc2e3b1e9b8e7e`; `figures\source\JwtAuthenticationFilter-52.png`
- `src/main/java/com/uteexpress/security/web/TokenVersionLogoutHandler.java:20–27` SHA256 `7d7105721908a749730c9d921e10b5561fee0d00b0e82cd13dc740d8bc932349`; `figures\source\TokenVersionLogoutHandler-20.png`
- `src/main/java/com/uteexpress/identity/service/EmailVerificationService.java:72–84` SHA256 `fbca0c23bb022be7ba09871ef96f571503a84721cd52774279cdc448de359776`; `figures\source\EmailVerificationService-72.png`
- `src/main/java/com/uteexpress/identity/service/PasswordResetService.java:55–67` SHA256 `ec6811caf5a6aa66957e3ae92a83adfcfbf73bf1a12a91f419ad634ad9fc9c03`; `figures\source\PasswordResetService-55.png`
- `src/main/java/com/uteexpress/identity/service/PasswordResetService.java:76–88` SHA256 `ec6811caf5a6aa66957e3ae92a83adfcfbf73bf1a12a91f419ad634ad9fc9c03`; `figures\source\PasswordResetService-76.png`
- `src/main/java/com/uteexpress/security/filter/OtpRequestLimitFilter.java:33–43` SHA256 `f346de6bce719e0264230225c8692f73d96b7c4fe8bc38cb7bc7e816d0f3b86b`; `figures\source\OtpRequestLimitFilter-33.png`
- `src/main/java/com/uteexpress/account/service/ProfileService.java:45–57` SHA256 `a45050fc490693fa609c49f98f268850161b772013e17e994c31e6018e7c477b`; `figures\source\ProfileService-45.png`
- `src/main/java/com/uteexpress/identity/service/AccountIdentityService.java:50–62` SHA256 `57328c9748ba1335a73fe9d61ee7823ebe0265db87c81d527f41086467f3dc2c`; `figures\source\AccountIdentityService-50.png`
- `src/main/java/com/uteexpress/account/service/ProfileService.java:49–61` SHA256 `a45050fc490693fa609c49f98f268850161b772013e17e994c31e6018e7c477b`; `figures\source\ProfileService-49.png`
- `src/main/java/com/uteexpress/account/service/AddressService.java:86–98` SHA256 `aefe568249291e3255e270f545885f37e71819ddf60e327b7d6226973c6defcf`; `figures\source\AddressService-86.png`
- `src/main/java/com/uteexpress/shop/service/ShopRegistrationService.java:44–56` SHA256 `001a39483091c5fb61741cbe601299c423ccda7d17032a9ba7643f8fc6763375`; `figures\source\ShopRegistrationService-44.png`
- `src/main/java/com/uteexpress/catalog/service/ProductService.java:102–114` SHA256 `f225d7800c84cae840343d7453e3aa17c059cd7f70145ce18efa5613bb253320`; `figures\source\ProductService-102.png`
- `src/main/java/com/uteexpress/catalog/service/ProductService.java:122–134` SHA256 `f225d7800c84cae840343d7453e3aa17c059cd7f70145ce18efa5613bb253320`; `figures\source\ProductService-122.png`
- `src/main/java/com/uteexpress/catalog/service/PublicCatalogService.java:76–88` SHA256 `3559a0a4038e24257d0374463ba72a5a3214129340ddd311f2679f70f789820e`; `figures\source\PublicCatalogService-76.png`
- `src/main/java/com/uteexpress/catalog/service/PublicCatalogService.java:55–67` SHA256 `3559a0a4038e24257d0374463ba72a5a3214129340ddd311f2679f70f789820e`; `figures\source\PublicCatalogService-55.png`
- `src/main/java/com/uteexpress/catalog/service/DatabaseInventoryService.java:38–50` SHA256 `68dadbdf481136791b72f44513aadff41ff9375f170dc2f7191047a3e2cc235a`; `figures\source\DatabaseInventoryService-38.png`
- `src/main/resources/static/js/app.js:5–17` SHA256 `33878f951f4be137e2c592da0003a14a1233c54c0e77dc6190e345dd9b45f16a`; `figures\source\app-5.png`

## Findings theo source không sửa backend
- DOCF01: Shop model có logo/banner nhưng không tìm thấy flow Vendor sửa hồ sơ shop ở snapshot; chỉ registration/status/admin boundary và public view.
- DOCF02: ProductSort hỗ trợ4 lựa chọn, chưa highestRated theo kỳ vọng Plan.
- DOCF03: README/architecture và SEED_MANIFEST chứa phần nền tảng lịch sử; không dùng thì tương lai của chúng thay cho source hiện tại.
- DOCF04: MasterPlan chapter12 là Quản lý nhóm/Git; testing/deployment setup đưa phụ lục, phối hợp TD/QĐ chương10–11.
- DOCF05: Chưa có ảnh UI đúng chức năng cho mọi mục; missing-screenshots.json là checklist, không fabricated UI.
- DOCF06: DOC01 dependencyQA01 overall chưacompleted; scoped remediation accepted không đóng SCA/operational/release.
- DOCF07: Database số bảng thực tế không mặc định30 sau các migration mới. QĐ tổng hợp chương3 bằngschema/runtime đã duyệt.