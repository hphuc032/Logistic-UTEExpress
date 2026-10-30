(() => {
    "use strict";
    const panel = document.querySelector("[data-buyer-cancellation]");
    if (!panel) return;
    const button = panel.querySelector("[data-cancel-button]");
    const status = panel.querySelector("[data-cancel-status]");
    const refresh = panel.querySelector("[data-cancel-refresh]");
    if (!button || !status || !refresh) return;

    const version = panel.dataset.cancelVersion;
    const header = panel.dataset.csrfHeader;
    const token = panel.dataset.csrfToken;
    let url;
    try { url = new URL(panel.dataset.cancelUrl, window.location.href); } catch (_) { return; }
    if (url.origin !== window.location.origin || url.search || url.hash
            || !/\/buyer\/orders\/[1-9][0-9]*\/cancel$/.test(url.pathname)
            || !/^(0|[1-9][0-9]{0,18})$/.test(version)
            || (version.length === 19 && version > "9223372036854775807")
            || typeof header !== "string" || !header || /[\r\n\0:]/.test(header)
            || typeof token !== "string" || !token || /[\r\n\0]/.test(token)
            || typeof window.fetch !== "function" || typeof window.confirm !== "function") return;

    let submitted = false;
    button.addEventListener("click", async () => {
        if (submitted || !window.confirm("Bạn có chắc muốn hủy đơn hàng này?")) return;
        submitted = true;
        button.disabled = true;
        panel.setAttribute("aria-busy", "true");
        status.textContent = "Đang hủy đơn hàng…";
        try {
            const response = await window.fetch(url.href, {
                method: "POST", credentials: "same-origin", redirect: "error",
                headers: {"Content-Type": "application/json", "Accept": "application/json", [header]: token},
                // Preserve all persisted Long digits; converting through Number could round the version.
                body: '{"expectedOrderVersion":' + version + '}'
            });
            if (response.status === 200 && (await response.json()).result === "OK") {
                status.textContent = "Đã hủy đơn hàng. Đang tải lại trạng thái…";
                window.location.reload();
                return;
            }
            const messages = {
                401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                403: "Bạn không có quyền hủy đơn hoặc phiên bảo mật đã hết hạn. Hãy tải lại trang hoặc đăng nhập lại.",
                404: "Không tìm thấy đơn hàng hoặc bạn không có quyền truy cập. Vui lòng quay lại danh sách đơn hàng.",
                409: "Đơn hàng đã thay đổi hoặc không còn có thể hủy. Hãy tải lại trang để xem trạng thái mới nhất."
            };
            status.textContent = messages[response.status]
                || "Chưa xác nhận được kết quả hủy. Hãy tải lại trang để kiểm tra trước khi thử lại.";
        } catch (_) {
            status.textContent = "Chưa xác nhận được kết quả hủy. Hãy tải lại trang để kiểm tra trước khi thử lại.";
        } finally {
            panel.removeAttribute("aria-busy");
            refresh.hidden = false;
            // Even a lost response may follow a committed cancellation. Reload before another attempt.
        }
    });
    button.disabled = false;
})();
