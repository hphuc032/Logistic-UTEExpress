(() => {
    "use strict";
    const form = document.querySelector("[data-shipment-assignment]");
    if (!form) return;
    const shipper = form.querySelector("[name=shipperId]");
    const button = form.querySelector("[data-assignment-submit]");
    const status = form.querySelector("[data-assignment-status]");
    const refresh = form.querySelector("[data-assignment-refresh]");
    if (!shipper || !button || !status || !refresh || typeof window.fetch !== "function") return;
    const version = form.dataset.version;
    const field = form.dataset.versionField;
    const header = form.dataset.csrfHeader;
    const token = form.dataset.csrfToken;
    const decimal = (value, zero) => typeof value === "string"
        && (zero ? /^(0|[1-9][0-9]{0,18})$/ : /^[1-9][0-9]{0,18}$/).test(value)
        && (value.length < 19 || value <= "9223372036854775807");
    let url;
    try { url = new URL(form.dataset.action, window.location.href); } catch (_) { return; }
    if (url.origin !== window.location.origin || url.search || url.hash
            || !/\/(admin|manager)\/orders\/[1-9][0-9]*\/(assign|reassign)$/.test(url.pathname)
            || !decimal(version, true)
            || (url.pathname.endsWith("/reassign") ? field !== "expectedShipmentVersion" : field !== "expectedOrderVersion")
            || typeof header !== "string" || !header || /[\r\n\0:]/.test(header)
            || typeof token !== "string" || !token || /[\r\n\0]/.test(token)) return;
    let pending = false;
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (pending || !decimal(shipper.value, false)) return;
        pending = true;
        button.disabled = true;
        form.setAttribute("aria-busy", "true");
        status.textContent = "Đang gửi yêu cầu phân công…";
        try {
            const response = await window.fetch(url.href, {
                method: "POST", credentials: "same-origin", redirect: "error",
                headers: {"Content-Type": "application/json", "Accept": "application/json", [header]: token},
                // Preserve persisted Long digits. The server remains the authorization/version authority.
                body: '{"shipperId":' + shipper.value + ',"' + field + '":' + version + '}'
            });
            if (response.status === 200 || response.status === 201) {
                status.textContent = "Đã phân công. Đang tải lại trạng thái…";
                window.location.reload();
                return;
            }
            status.textContent = ({401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                403: "Không đủ quyền hoặc Shipper không còn hợp lệ. Hãy tải lại trang.",
                404: "Không tìm thấy đơn hàng trong phạm vi được phép.",
                409: "Đơn hàng hoặc chuyến giao đã thay đổi. Hãy tải lại trang trước khi thao tác lại.",
                400: "Dữ liệu không hợp lệ. Hãy tải lại trang và chọn Shipper phù hợp."})[response.status]
                || "Chưa xác nhận được kết quả. Hãy tải lại trạng thái trước khi thử lại.";
        } catch (_) {
            status.textContent = "Chưa xác nhận được kết quả. Hãy tải lại trạng thái trước khi thử lại.";
        } finally {
            form.removeAttribute("aria-busy");
            refresh.hidden = false;
        }
    });
    button.disabled = false;
})();
