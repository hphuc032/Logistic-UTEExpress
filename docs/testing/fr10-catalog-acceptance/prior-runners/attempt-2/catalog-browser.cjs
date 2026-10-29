'use strict';
// FR-10 Catalog browser checks. Tool-driven execution is never human sign-off.
const {chromium}=require(process.env.FR10_PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const config=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
const base=config.baseUrl,fixture=config.fixture,out=config.outputDirectory;
fs.mkdirSync(path.join(out,'screenshots'),{recursive:true});
const results={mode:'AUTOMATED_BROWSER; HUMAN PENDING',source:config.source,jarSha256:config.jarSha256,startedUtc:new Date().toISOString(),browser:null,cases:[]};
const negativeMarkers=['Bưu kiện mẫu ẩn','Bưu kiện mẫu kiểm duyệt','FR10 suspended product','FR10 disabled-category'];
let browser;
async function main(){
 browser=await chromium.launch({executablePath:config.chrome,headless:true});results.browser=browser.version();
 for(const [width,height] of [[375,812],[768,1024],[1440,900]]){
  const context=await browser.newContext({viewport:{width,height},locale:'vi-VN',timezoneId:'UTC'});
  const page=await context.newPage();page.setDefaultTimeout(12000);
  let events=[];
  page.on('pageerror',e=>events.push({kind:'pageerror',message:String(e.message).slice(0,250)}));
  page.on('console',m=>{if(m.type()==='error')events.push({kind:'console-error',message:m.text().slice(0,250)})});
  page.on('requestfailed',r=>events.push({kind:'requestfailed',path:publicUrl(r.url()),error:r.failure()?.errorText}));
  page.on('dialog',async d=>{events.push({kind:'unexpected-dialog',type:d.type()});await d.dismiss()});
  async function nav(route,status=200){const response=await page.goto(base+route,{waitUntil:'networkidle'});assert.equal(response.status(),status,route);return response;}
  async function contain(){
   await page.evaluate(()=>document.fonts.ready);
   const x=await page.evaluate(()=>({viewport:innerWidth,scroll:document.documentElement.scrollWidth}));
   assert(x.scroll<=x.viewport+1,JSON.stringify(x));
   return x;
  }
  async function text(){return await page.locator('body').innerText()}
  function cleanErrors(data){
   assert(!/org\.springframework|java\.lang\.|PSQLException|password_hash|token_version|stackTrace|"trace"\s*:/.test(data),'Internal diagnostics exposed');
  }
  async function task(id,title,category,fn){
   if(config.caseFilter && config.caseFilter!==id)return;
   events=[];const row={id:`${id}-${width}`,title,category,role:'ANONYMOUS',viewport:{width,height},startedUtc:new Date().toISOString(),humanResult:null};
   try{row.observations=await fn();row.status='PASS';}
   catch(e){row.status='FAIL';row.failure=String(e.message).slice(0,900)}
   row.route=new URL(page.url()).pathname;row.events=[...events];
   const unexpected=events.filter(e=>e.kind!=='console-error'||!/server responded with a status of (400|401|403|404)/.test(e.message));
   if(unexpected.length&&row.status==='PASS'){row.status=unexpected.some(e=>e.kind==='requestfailed'&&/jsdelivr/.test(e.path))?'BLOCKED':'FAIL';row.failure='Unexpected browser diagnostics';}
   const shot=path.join('screenshots',row.id+'.png');try{await page.screenshot({path:path.join(out,shot),fullPage:true});row.screenshot=shot.replaceAll('\\','/');row.screenshotSha256=crypto.createHash('sha256').update(fs.readFileSync(path.join(out,shot))).digest('hex');}catch(e){row.screenshotError=String(e.message).slice(0,150);if(row.status==='PASS')row.status='BLOCKED'}
   row.finishedUtc=new Date().toISOString();results.cases.push(row);save();console.log(row.id+' '+row.status);
  }
  await task('CAT-HOME','Home renders eligible multi-shop products','browser-ui',async()=>{
   await nav('/');await page.getByRole('heading',{name:'Khám phá sản phẩm mới',exact:true}).waitFor();
   assert.equal(await page.locator('article.app-catalog-card').count(),12);
   const data=await text();for(const m of negativeMarkers)assert(!data.includes(m));
   assert(await page.locator('a[href="/categories/demo-category"]').count()>0);assert(await page.locator('a[href="/shops/demo-shop"]').count()>0);
   return {cards:12,geometry:await contain()};
  });
  await task('CAT-CATEGORY','Active category lists eligible products','browser-ui',async()=>{
   await nav('/categories/demo-category');assert.equal(await page.getByRole('heading',{level:1}).innerText(),'FR10 Catalog category');
   assert.equal(await page.locator('article.app-catalog-card').count(),12);for(const m of negativeMarkers)assert(!(await text()).includes(m));return {cards:12,geometry:await contain()};
  });
  await task('CAT-PRODUCT','Public detail displays server product and image','browser-ui',async()=>{
   await nav('/products/'+fixture.product);assert.equal(await page.getByRole('heading',{level:1}).innerText(),'Bưu kiện mẫu tiêu chuẩn');
   assert((await text()).includes('125.000'));assert((await text()).includes('Còn 12 sản phẩm'));
   const image=page.getByRole('img',{name:'FR10 fixture product image',exact:true});await image.waitFor();await image.evaluate(e=>e.decode());assert(await image.evaluate(e=>e.naturalWidth>0));
   assert.equal(await page.getByRole('link',{name:'Đăng nhập để mua hàng',exact:true}).count(),1);assert.equal(await page.getByRole('button',{name:'Thêm vào giỏ',exact:true}).count(),0);
   return {imageDecoded:true,price:'125000 VND',geometry:await contain()};
  });
  await task('CAT-SHOPS','Shop directory shows approved shops only','browser-ui',async()=>{
   await nav('/shops');assert.equal(await page.locator('a[href="/shops/demo-shop"]').count()>0,true);
   for(const slug of ['fr10-suspended','fr10-pending','fr10-rejected'])assert.equal(await page.locator(`a[href="/shops/${slug}"]`).count(),0);
   return {geometry:await contain()};
  });
  await task('CAT-SHOP','Approved shop detail preserves shop-scoped products','browser-ui',async()=>{
   await nav('/shops/demo-shop');assert.equal(await page.getByRole('heading',{level:1}).innerText(),'FR10 Demo shop');assert.equal(await page.locator('article.app-catalog-card').count(),7);
   assert(!(await text()).includes('Tem vận chuyển chống nước'));return {cards:7,geometry:await contain()};
  });
  await task('CAT-EMPTY-CATEGORY','Active empty category has usable empty state','browser-ui',async()=>{
   await nav('/categories/fr10-empty');assert((await text()).includes('Chưa có sản phẩm'));assert.equal(await page.locator('article.app-catalog-card').count(),0);return {geometry:await contain()};
  });
  await task('CAT-EMPTY-SHOP','Approved empty shop has usable empty state','browser-ui',async()=>{
   await nav('/shops/fr10-empty-shop');assert((await text()).includes('Chưa có sản phẩm'));return {geometry:await contain()};
  });
  await task('CAT-ZERO-STOCK','Zero stock remains visible with honest availability','browser-ui',async()=>{
   await nav('/products/'+fixture.zeroStock);assert((await text()).includes('Hết hàng'));return {geometry:await contain(),stock:0};
  });
  await task('CAT-NONPUBLIC-PRODUCT','Hidden moderated suspended inactive-category products fail closed','security-negative',async()=>{
   const statuses=[];for(const id of fixture.nonpublicProducts){await nav('/products/'+id,404);cleanErrors(await text());statuses.push(404)}return {statuses};
  });
  await task('CAT-NONPUBLIC-SHOP','Non-approved or unknown shops return safe 404','security-negative',async()=>{
   const statuses=[];for(const slug of ['fr10-suspended','fr10-pending','fr10-rejected','fr10-unknown']){await nav('/shops/'+slug,404);cleanErrors(await text());statuses.push(404)}return {statuses};
  });
  await task('CAT-NONPUBLIC-CATEGORY','Inactive and unknown category fail closed','security-negative',async()=>{
   for(const slug of ['fr10-disabled','fr10-unknown']){await nav('/categories/'+slug,404);cleanErrors(await text())}return {statuses:[404,404]};
  });
  await task('CAT-MEDIA','Public image bytes and response headers match fixture','http-browser-context',async()=>{
   await nav('/products/'+fixture.product);const r=await context.request.get(base+`/products/${fixture.product}/images/${fixture.image}/content`);assert.equal(r.status(),200);
   assert.equal(r.headers()['content-type'],'image/png');assert.equal(r.headers()['x-content-type-options'],'nosniff');assert(r.headers()['cache-control'].includes('no-store'));
   const actual=crypto.createHash('sha256').update(await r.body()).digest('hex');assert.equal(actual,fixture.imageSha256);return {imageSha256:actual,status:200};
  });
  await task('CAT-MEDIA-FOREIGN','Cross-product and nonpublic image access denied','security-negative',async()=>{
   await nav('/products/'+fixture.product);for(const pair of [[fixture.zeroStock,fixture.image],[fixture.hidden,fixture.hiddenImage]]){const r=await context.request.get(base+`/products/${pair[0]}/images/${pair[1]}/content`);assert.equal(r.status(),404);cleanErrors(await r.text())}return {statuses:[404,404]};
  });
  await task('CAT-ESCAPING','Vendor-controlled text is escaped rather than executed','security-negative',async()=>{
   await nav('/products/'+fixture.xss);assert.equal(await page.getByRole('heading',{level:1}).innerText(),fixture.xssName);assert.equal(await page.evaluate(()=>window.__fr10Xss),undefined);return {escaped:true,geometry:await contain()};
  });
  await task('CAT-NAVIGATION','Home product category shop links complete browsing journey','browser-ui',async()=>{
   await nav('/');await page.getByRole('link',{name:'Xem Bưu kiện mẫu tiêu chuẩn',exact:true}).click();await page.waitForURL('**/products/'+fixture.product);
   await page.locator('.app-product-meta').getByRole('link',{name:'FR10 Catalog category',exact:true}).click();await page.waitForURL('**/categories/demo-category');
   await page.getByRole('heading',{name:'Bưu kiện mẫu tiêu chuẩn',exact:true}).getByRole('link').click();await page.waitForURL('**/products/'+fixture.product);
   await page.locator('.app-product-meta').getByRole('link',{name:'FR10 Demo shop',exact:true}).click();await page.waitForURL('**/shops/demo-shop');return {manualUrlEditing:false,geometry:await contain()};
  });
  await task('CAT-KEYBOARD-MENU','Keyboard focus and responsive public navbar are operable','browser-ui',async()=>{
   await nav('/');await page.keyboard.press('Tab');const focus=await page.evaluate(()=>({tag:document.activeElement.tagName,text:document.activeElement.textContent.trim(),outline:getComputedStyle(document.activeElement).outlineStyle,shadow:getComputedStyle(document.activeElement).boxShadow}));assert.equal(focus.tag,'A');assert(focus.text.length>0);
   const toggle=page.getByRole('button',{name:'Mở menu điều hướng',exact:true});
   let stateWhenLinkVisible=null;
   if(width<992){await toggle.click();assert.equal(await toggle.getAttribute('aria-expanded'),'true');await page.locator('#primaryNavbar').getByRole('link',{name:'Sản phẩm',exact:true}).waitFor();stateWhenLinkVisible=await page.locator('#primaryNavbar').getAttribute('class');await page.locator('#primaryNavbar.collapse.show').waitFor({state:'visible'});await toggle.click();assert.equal(await toggle.getAttribute('aria-expanded'),'false');await page.locator('#primaryNavbar.collapse:not(.show)').waitFor({state:'hidden'});}
   else assert(await page.getByRole('navigation',{name:'Điều hướng chính',exact:true}).getByRole('link',{name:'Sản phẩm',exact:true}).isVisible());
   return {focus,stateWhenLinkVisible,geometry:await contain()};
  });
  await task('CAT-SAFE-ERROR','Missing and malformed product IDs do not leak internals','security-negative',async()=>{
   await nav('/products/9223372036854775807',404);cleanErrors(await text());await nav('/products/0',404);cleanErrors(await text());return {statuses:[404,404]};
  });
  await task('CAT-SECURITY-SMOKE','Catalog is public while protected actions retain authentication and CSRF','security-negative',async()=>{
   await nav('/products/'+fixture.product);const a=await context.request.get(base+'/vendor/products');assert.equal(a.status(),401);const c=await context.request.post(base+'/user/cart/view/items',{form:{productId:String(fixture.product),quantity:'1'}});assert.equal(c.status(),403);return {protectedGet:401,unsafeWithoutCsrf:403};
  });
  await context.close();
 }
}
function publicUrl(s){try{const u=new URL(s);return u.origin+u.pathname}catch{return 'unparsed'}}
function save(){results.finishedUtc=new Date().toISOString();results.summary={};for(const s of ['PASS','FAIL','BLOCKED','NOT RUN'])results.summary[s]=results.cases.filter(x=>x.status===s).length;fs.writeFileSync(path.join(out,'browser-results.json'),JSON.stringify(results,null,2)+'\n');}
main().catch(e=>{results.infrastructureFailure=String(e.message).slice(0,800);process.exitCode=2}).finally(async()=>{if(browser)await browser.close();save();if(results.cases.some(x=>x.status!=='PASS'))process.exitCode=1;});
