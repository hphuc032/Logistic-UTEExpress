const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');
function region(attributes = {}, caption = null) {
    const attrs = new Map(Object.entries(attributes));
    return {
        attrs,
        hasAttribute: name => attrs.has(name),
        setAttribute: (name, value) => attrs.set(name, value),
        querySelector: selector => selector === 'caption' && caption !== null ? {textContent: caption} : null
    };
}
function boot({regions = [], years = [], invalid = null, autofocus = false} = {}) {
    const document = {
        addEventListener: (event, callback) => { assert.equal(event, 'DOMContentLoaded'); callback(); },
        querySelectorAll: selector => selector === '.table-responsive' ? regions : years,
        querySelector: selector => selector === '[autofocus]' ? autofocus : invalid
    };
    vm.runInNewContext(source, {document, Date});
}

test('overflow regions are reachable by keyboard without replacing the table', () => {
    const scroll = region(); boot({regions: [scroll]});
    assert.equal(scroll.attrs.get('tabindex'), '0');
    assert.equal(scroll.attrs.get('role'), 'region');
    assert.equal(scroll.attrs.get('aria-label'), 'Bảng dữ liệu — có thể cuộn ngang');
});
test('the visible caption supplies a meaningful scroll-region name as plain text', () => {
    const scroll = region({}, '  Sản phẩm và giá đã lưu  '); boot({regions: [scroll]});
    assert.equal(scroll.attrs.get('aria-label'), 'Sản phẩm và giá đã lưu');
});
test('existing names, role and keyboard policy are preserved', () => {
    const scroll = region({'aria-label': 'Đơn hàng', role: 'group', tabindex: '-1'});
    boot({regions: [scroll]});
    assert.deepEqual(Object.fromEntries(scroll.attrs), {'aria-label': 'Đơn hàng', role: 'group', tabindex: '-1'});
});
test('an existing labelledby association is not overridden', () => {
    const scroll = region({'aria-labelledby': 'order-title'}); boot({regions: [scroll]});
    assert.equal(scroll.attrs.has('aria-label'), false);
});
test('empty captions use a nonempty accessible fallback', () => {
    const scroll = region({}, '  '); boot({regions: [scroll]});
    assert.equal(scroll.attrs.get('aria-label'), 'Bảng dữ liệu — có thể cuộn ngang');
});
test('existing invalid-field focus and explicit autofocus behavior remain intact', () => {
    let focused = 0;
    const invalid = {focus: options => {assert.equal(options.preventScroll, true); focused++;}};
    boot({invalid}); boot({invalid, autofocus: true});
    assert.equal(focused, 1);
});
test('pages without tables still update the footer and initialize safely', () => {
    const year = {}; boot({years: [year]});
    assert.equal(year.textContent, String(new Date().getFullYear()));
});
