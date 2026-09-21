(() => {
    "use strict";

    document.addEventListener("DOMContentLoaded", () => {
        document.querySelectorAll("[data-current-year]").forEach((element) => {
            element.textContent = String(new Date().getFullYear());
        });

        const firstInvalidField = document.querySelector(".form-control.is-invalid, .form-select.is-invalid");
        if (firstInvalidField && !document.querySelector("[autofocus]")) {
            firstInvalidField.focus({preventScroll: true});
        }
    });
})();
