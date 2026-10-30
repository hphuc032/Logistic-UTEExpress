(() => {
    "use strict";

    document.addEventListener("DOMContentLoaded", () => {
        document.querySelectorAll("[data-current-year]").forEach((element) => {
            element.textContent = String(new Date().getFullYear());
        });

        // Native scrolling keeps every column/action available on narrow screens.
        // Give keyboard users the same access without changing table semantics.
        document.querySelectorAll(".table-responsive").forEach((region) => {
            if (!region.hasAttribute("tabindex")) {
                region.setAttribute("tabindex", "0");
            }
            if (!region.hasAttribute("role")) {
                region.setAttribute("role", "region");
            }
            if (!region.hasAttribute("aria-label") && !region.hasAttribute("aria-labelledby")) {
                const caption = region.querySelector("caption");
                region.setAttribute("aria-label", caption?.textContent.trim()
                    || "Bảng dữ liệu — có thể cuộn ngang");
            }
        });

        const firstInvalidField = document.querySelector(".form-control.is-invalid, .form-select.is-invalid");
        if (firstInvalidField && !document.querySelector("[autofocus]")) {
            firstInvalidField.focus({preventScroll: true});
        }
    });
})();
