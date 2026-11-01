const fs = require('fs');
const crypto = require('crypto');
const path = require('path');
const {chromium} = require(process.env.DOC01_NODE_MODULES + '/playwright');
const c = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const rows = [];
const assert = (value, message) => { if (!value) throw new Error(message); };
(async () => {
  const browser = await chromium.launch({executablePath:c.chrome,headless:true});
  const context = await browser.newContext({viewport:{width:1280,height:900},locale:'vi-VN'});
  const page = await context.newPage();
  async function go(route) {
    const r = await page.goto(new URL(route,c.base).href, {waitUntil:'networkidle'});
    assert(r.status() === 200, 'GET failed: ' + route + ' ' + r.status());
    return r;
  }
  async function capture(id, role, observation, limits) {
    assert(await page.locator('input[type=password]').evaluateAll(es => es.every(e => !e.value)), 'Password still entered');
    const file = 'doc01-' + id.replace('.','-') + '.png';
    await page.screenshot({path:path.join(c.output,file),fullPage:false});
    rows.push({function:id,path:c.imagePrefix+file,route:new URL(page.url()).pathname+new URL(page.url()).search,
      role,viewport:{width:1280,height:900},source:c.source,jarSha256:c.jarSha256,capturedUtc:new Date().toISOString(),
      browser:browser.version(),observation,limitations:limits,status:'PASS',newCaptureInDoc01:true,
      sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(c.output,file))).digest('hex')});
  }
  async function post(route, data={}, multipart=false) {
    const token=await page.locator('input[name="_csrf"]').first().inputValue();
    const r=await context.request.post(c.base+route,{[multipart?'multipart':'form']:{...data,_csrf:token},maxRedirects:0});
    assert(r.status()===302,'POST failed: '+route+' '+r.status());
    await go(r.headers().location);
    return 302;
  }
  async function login(username) {
    await go('/login');await page.locator('#identifier').fill(username);await page.locator('#password').fill(c.password);
    await Promise.all([page.waitForURL(url=>url.pathname!='/login'),page.locator('form[action="/login"] button[type=submit]').click()]);
    await page.waitForLoadState('networkidle');
  }
  try {
    await login('doc01_buyer');await go('/user/profile');
    await capture('4.3','USER','Protected profile rendered after actual password login and JWT cookie restoration.','No token/cookie values captured; screenshot alone does not prove every JWT negative case.');
    await capture('5.1','USER','Own profile form and display identity rendered.','No profile mutation or foreign-owner acceptance inferred.');
    await page.locator('form[action="/user/password"]').scrollIntoViewIfNeeded();
    await capture('5.2','USER','Password-change form rendered with all password inputs empty.','Password-change transaction not executed in this capture.');
    await go('/user/profile');
    await post('/user/avatar',{avatar:{name:'doc01-fixture.png',mimeType:'image/png',buffer:fs.readFileSync(c.image)}},true);
    const avatar=await context.request.get(c.base+'/user/avatar');assert(avatar.status()===200,'Avatar read failed');
    await page.locator('form[action="/user/avatar"]').scrollIntoViewIfNeeded();
    await capture('5.3','USER','Real fixture avatar upload redirected and own content endpoint returned 200.','Isolated synthetic fixture only; deployed ACL and all hostile uploads not tested.');
    await go('/user/addresses');
    await post('/user/addresses',{receiverName:'DOC01 Test Buyer',phone:'0900000000',provinceCode:'79',district:'Demo district',detail:'Synthetic documentation fixture'});
    await capture('5.4','USER','Address create POST302/GET200; first address shows default state.','DB default and ownership source reviewed; no full address concurrency suite rerun.');
    await go('/user/shop/register');await capture('5.5','USER','Shop application form rendered for current USER without an owned shop.','No shop approval or application submission is claimed.');
    await post('/logout');assert(new URL(page.url()).pathname==='/login','Logout redirect');
    const rejected=await context.request.get(c.base+'/user/profile');assert(rejected.status()===401,'Logout protected request must fail');
    await capture('4.4','anonymous','Real POST logout redirected to login; later protected GET returned 401.','No raw JWT replay was captured; cross-device invalidation is source/test evidence, not screenshot proof.');
    await go('/verify-otp?identifier=doc01_pending');
    const resend = page.waitForResponse(r=>r.url().endsWith('/verify-otp/resend') && r.request().method()==='POST');
    await page.locator('form[action="/verify-otp/resend"] button[type=submit]').click();
    assert((await resend).status()===200,'Resend must render view with HTTP200 per controller contract');
    await page.waitForLoadState('networkidle');
    await capture('4.5','anonymous','Verification/resend UI after real CSRF-protected POST200, matching controller view contract; code field empty.','Loopback Mailpit only; no public SMTP, activation or raw OTP screenshot.');
    await go('/forgot-password');await post('/forgot-password',{email:'not-present@example.invalid'});
    await capture('4.6','anonymous','Unknown-email request received generic redirect/reset requested message.','Single response does not prove timing indistinguishability.');
    await go('/reset-password');await capture('4.7','anonymous','Reset form rendered, OTP and password fields empty.','Successful reset transaction not executed; refer to existing AUTH-04 tests.');
    const denied=await context.request.post(c.base+'/register',{form:{},maxRedirects:0});assert(denied.status()===403,'Missing CSRF must be denied');
    await page.goto(c.base+'/user/profile',{waitUntil:'networkidle'});
    await capture('4.8','anonymous','Protected GET401 visible; separate unsafe request without CSRF returned403.','Single 401/403 example, not all RBAC or OTP rate-limit controls.');
    await login('doc01_vendor');await go('/vendor/products');await capture('5.6','VENDOR','Owned shop product list rendered from isolated PostgreSQL fixture.','No create/update/delete CRUD journey claimed.');
    await go('/vendor/products/'+c.product+'/edit');
    await post('/vendor/products/'+c.product+'/images',{altText:'DOC01 synthetic image',image:{name:'doc01-fixture.png',mimeType:'image/png',buffer:fs.readFileSync(c.image)}},true);
    const img=await page.locator('img[src*="/images/"]').first().getAttribute('src');assert(img,'Image element missing');
    const imageResponse=await context.request.get(c.base+img);assert(imageResponse.status()===200,'Owned image response');
    await page.locator('form[enctype="multipart/form-data"]').scrollIntoViewIfNeeded();
    await capture('5.7','VENDOR','Image upload POST302 and own image GET200; management UI displayed.','Foreign image/visibility matrix not rerun; source contract retained.');
    await go('/products?q=DOC01&sort=priceAsc&page=0&size=6');
    assert(await page.locator('a[href*="/products/"]').count()>0,'Catalog populated result missing');
    await capture('5.9','public as VENDOR','Actual filtered/sorted catalog with page size6 from 13 fixture products.','Screenshot illustrates query/results; full search matrix not rerun.');
  } finally {
    fs.writeFileSync(c.results,JSON.stringify({source:c.source,jarSha256:c.jarSha256,captureOnlyNotFullRegression:true,screenshots:rows},null,2));
    await browser.close();
  }
  console.log(JSON.stringify({captured:rows.length,status:'PASS'}));
})().catch(e=>{console.error(e.message);process.exitCode=1;});
