/* Disposable, real Chromium acceptance harness. No credentials or raw traces are persisted. */
const fs = require('fs'), path = require('path'), crypto = require('crypto'), cp = require('child_process'), net = require('net');
const assert = require('assert/strict');
const { chromium } = require('C:/GestureHome-Web-HTN/node_modules/playwright');
const root = path.resolve(__dirname, '../..'), evidence = path.join(__dirname, process.argv[2]==='B-KEYBOARD'?'evidence-keyboard':'evidence-final3');
const stamp = () => new Date().toISOString(), sleep = ms => new Promise(r => setTimeout(r, ms));
const uuid = crypto.randomUUID(), container = 'qa02-pr67-browser-' + uuid.slice(0, 8);
const password = crypto.randomBytes(24).toString('base64url');
const results = {sha: cp.execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(), started:stamp(), cases:[], environment:{}, events:[]};
assert.equal(results.sha,'c5901c326ff92fee0fde0f8a4c66633a003cfc81');
let browser, active = 'SETUP', apps = [], created = false;
const exec = (file, args, input, env) => cp.execFileSync(file, args, {encoding:'utf8',input,env,windowsHide:true,stdio:['pipe','pipe','pipe'],maxBuffer:10*1024*1024}).trim();
const db = sql => exec('docker',['exec','-i',container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','qa67','-d','qa67'],sql);
const jsonDb = sql => JSON.parse(db(sql));
const q = s => "'"+String(s).replaceAll("'","''")+"'";
const event = e => {const item={time:stamp(),test:active,...e};results.events.push(item);fs.appendFileSync(path.join(evidence,'browser-network.jsonl'),JSON.stringify(item)+'\n');};
const save = () => fs.writeFileSync(path.join(evidence,'results.json'),JSON.stringify(results,null,2));
async function port(){return await new Promise(r=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>r(p));});});}
async function startApp(extra=[]){
  const p=await port(), base='http://127.0.0.1:'+p;
  const env={...process.env};
  for(const key of Object.keys(env)) if(/^(DB_|JWT_|OTP_|MAIL_|SPRING_|SERVER_|UPLOAD_|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS)/i.test(key))delete env[key];
  Object.assign(env,{DB_URL:`jdbc:postgresql://127.0.0.1:${results.environment.databasePort}/qa67`,DB_USERNAME:'qa67',DB_PASSWORD:password,MAIL_HOST:'127.0.0.1',MAIL_PORT:'9',MAIL_SMTP_AUTH:'false',MAIL_STARTTLS:'false',MAIL_FROM:'qa67@example.test',JWT_SECRET_BASE64:crypto.randomBytes(48).toString('base64'),OTP_PEPPER_BASE64:crypto.randomBytes(48).toString('base64'),UPLOAD_DIR:path.join(__dirname,'uploads'),SERVER_PORT:String(p)});
  const log=path.join(__dirname,'private-server-'+p+'.log'), fd=fs.openSync(log,'a');
  const proc=cp.spawn('C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/java.exe',['-Duser.timezone=UTC','-jar',path.join(root,'target/uteexpress-0.1.0-SNAPSHOT.jar'),'--spring.profiles.active=local','--server.address=127.0.0.1','--management.health.mail.enabled=false',...extra],{env,cwd:root,stdio:['ignore',fd,fd],windowsHide:true});
  apps.push({proc,log,port:p});
  for(let i=0;i<120;i++){if(proc.exitCode!==null)throw Error('Application exited before readiness; inspect private server log');try{const r=await fetch(base+'/actuator/health');if(r.status===200){event({kind:'server-ready',port:p,http:200});return base;}}catch{}await sleep(500);}
  throw Error('Application readiness timeout');
}
function observe(page){
  page.on('response',r=>{let u=new URL(r.url());if(u.hostname==='127.0.0.1')event({kind:'response',method:r.request().method(),path:u.pathname,http:r.status()});else event({kind:'external-resource',host:u.hostname,path:u.pathname,http:r.status()});});
  page.on('requestfailed',r=>event({kind:'requestfailed',method:r.method(),path:new URL(r.url()).pathname,error:r.failure()?.errorText}));
  page.on('pageerror',err=>event({kind:'pageerror',name:err.name,message:err.message.slice(0,200)}));
}
const accounts={}; let shop,category,provider;
async function session(role='buyer',base=results.environment.base,width=1440){
  const context=await browser.newContext({viewport:{width,height:1000},locale:'vi-VN'});context.on('page',observe);const page=await context.newPage();
  await login(page,role,base);return {context,page,role,base};
}
async function login(page,role,base=results.environment.base){
  await page.goto(base+'/login');await page.locator('#identifier').fill(accounts[role].name);await page.locator('#password').fill(password);
  await Promise.all([page.waitForURL(u=>u.pathname!='/login'),page.locator('form[action="/login"] button[type=submit]').click()]);
  assert(!page.url().includes('/login')); await page.waitForLoadState('domcontentloaded');
}
async function request(page,url,body){return page.evaluate(async({url,body})=>{
  const panel=document.querySelector('[data-buyer-cancellation]');const token=panel?.dataset.csrfToken || document.querySelector('input[name="_csrf"]')?.value;
  if(!token)throw Error('No server-rendered masked CSRF token');
  const r=await fetch(url,{method:'POST',credentials:'same-origin',redirect:'error',headers:{'Content-Type':'application/json','Accept':'application/json','X-XSRF-TOKEN':token},body:JSON.stringify(body)});
  const text=await r.text();let data;try{data=JSON.parse(text)}catch{data={}}return {status:r.status,data};
},{url,body});}
async function createOrder(s,label){
  await s.page.goto(s.base+'/orders');
  const product=Number(db(`INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (${shop},${category},'QA67 synthetic ${label}',100000,10,'ACTIVE') RETURNING id;`));
  const voucher='QA67'+crypto.randomBytes(8).toString('hex').toUpperCase();
  db(`INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,created_by) VALUES (${q(voucher)},'SHOP','FIXED',${shop},10000,0,CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day',1,1,${accounts.seller.id});`);
  const cart=await request(s.page,'/user/cart/items',{productId:product,quantity:2});assert([200,201].includes(cart.status),'Fixture cart HTTP '+cart.status);
  const placed=await request(s.page,'/user/checkout/place-order',{checkoutKey:crypto.randomUUID(),items:[{productId:product,quantity:2}],addressId:accounts[s.role].address,shippingProviderId:provider,shippingServiceCode:'STANDARD',paymentMethod:'COD',voucherCode:voucher});
  assert.equal(placed.status,201,'Fixture checkout HTTP '+placed.status+' '+JSON.stringify(placed.data));
  const order={id:placed.data.orderId,product,label};assert(order.id);order.initial=state(order);assert.equal(order.initial.stock,8);assert.equal(order.initial.voucher,'REDEEMED');return order;
}
function state(o){return jsonDb(`SELECT json_build_object('status',o.status,'version',o.version,'released',o.inventory_released_at IS NOT NULL,'cancelled',o.cancelled_at IS NOT NULL,'stock',(SELECT stock FROM uteexpress.products WHERE id=${o.product}),'voucher',(SELECT status FROM uteexpress.voucher_usages WHERE order_id=o.id),'history',(SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=o.id),'cancelHistory',(SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=o.id AND to_status='CANCELLED'),'notifications',(SELECT count(*) FROM uteexpress.notifications WHERE order_id=o.id),'financial',json_build_object('subtotal',o.subtotal,'discount',o.discount_total,'shipping',o.shipping_fee,'grandTotal',o.grand_total,'items',(SELECT json_agg(json_build_object('id',id,'quantity',quantity,'price',unit_price,'total',line_total) ORDER BY id) FROM uteexpress.order_items WHERE order_id=o.id),'payment',(SELECT json_agg(json_build_object('id',id,'method',method,'status',status,'amount',amount) ORDER BY id) FROM uteexpress.payments WHERE order_id=o.id))) FROM uteexpress.orders o WHERE o.id=${o.id};`);}
function cancelled(o){const s=state(o);assert.equal(s.status,'CANCELLED');assert.equal(s.version,1);assert.equal(s.stock,10);assert.equal(s.voucher,'RELEASED');assert.equal(s.cancelHistory,1);assert.equal(s.history,o.initial.history+1);assert.equal(s.notifications,o.initial.notifications+2);assert.deepEqual(s.financial,o.initial.financial);assert(s.released&&s.cancelled);return s;}
async function detail(s,o){const r=await s.page.goto(s.base+'/orders/'+o.id);assert.equal(r.status(),200);await s.page.locator('[data-cancel-button]').waitFor();await s.page.waitForFunction(()=>!document.querySelector('[data-cancel-button]').disabled);}
async function shot(page,name){const rel='screenshots/'+name+'.png';await page.screenshot({path:path.join(evidence,rel),fullPage:true});return rel;}
async function act(page,decision='accept',key){page.once('dialog',async d=>{event({kind:'native-dialog',type:d.type(),message:d.message(),decision});await d[decision]();});if(key)await page.locator('[data-cancel-button]').press(key);else await page.locator('[data-cancel-button]').click();}
async function completed(page,o){await page.waitForFunction(()=>!document.querySelector('[data-buyer-cancellation]'));assert((await page.locator('.app-status-badge').innerText()).includes('CANCELLED'));return cancelled(o);}
async function feedback(page,fragment){await page.waitForFunction(f=>document.querySelector('[data-cancel-status]')?.textContent.includes(f),fragment);assert(await page.locator('[data-cancel-button]').isDisabled());assert(await page.locator('[data-cancel-refresh]').isVisible());return page.locator('[data-cancel-status]').innerText();}
async function test(id,role,width,steps,expected,fn){if(process.argv[2]&&process.argv[2]!==id)return;active=id;console.log('RUN '+id);const entry={id,viewport:{width,height:1000},role,steps,expected,started:stamp()};const from=results.events.length;try{Object.assign(entry,await fn());entry.status=entry.status||'PASS';}catch(e){entry.status='FAIL';entry.actual=e.message;console.log('FAIL '+id+': '+e.message.slice(0,200));}entry.finished=stamp();entry.http=results.events.slice(from).filter(e=>e.kind==='response' && (/cancel|login|logout/.test(e.path))).map(e=>({method:e.method,path:e.path,status:e.http}));results.cases.push(entry);save();console.log(entry.status+' '+id);}
async function main(){
  fs.mkdirSync(path.join(evidence,'screenshots'),{recursive:true});
  results.environment={runId:uuid,scope:'Loopback-only disposable PostgreSQL; synthetic accounts; real packaged Spring application and Chromium; no mocked success responses',jarSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'target/uteexpress-0.1.0-SNAPSHOT.jar'))).digest('hex'),database:'PostgreSQL 17.6 / qa67 / disposable',container};
  const denv={...process.env,POSTGRES_PASSWORD:password};exec('docker',['run','--rm','--detach','--name',container,'--label','qa67.run='+uuid,'-e','POSTGRES_PASSWORD','-e','POSTGRES_USER=qa67','-e','POSTGRES_DB=qa67','-p','127.0.0.1::5432','postgres:17.6'],undefined,denv);created=true;
  results.environment.databasePort=exec('docker',['port',container,'5432/tcp']).split(':').pop();
  for(let i=0;i<60;i++){try{db('SELECT 1;');break}catch{if(i===59)throw Error('Disposable database readiness timeout');await sleep(500);}}
  results.environment.base=await startApp();
  results.environment.migrations=jsonDb("SELECT json_build_object('applied',count(*),'success',bool_and(success),'latest',max(version)) FROM uteexpress.flyway_schema_history WHERE version IS NOT NULL;");
  const hash=exec('python',['-c','import sys,bcrypt; print(bcrypt.hashpw(sys.stdin.buffer.read(),bcrypt.gensalt()).decode())'],password);
  for(const [role,code] of Object.entries({buyer:'USER',foreign:'USER',seller:'VENDOR',vendor:'VENDOR'})){
    const name='qa67_'+role+'_'+uuid.slice(0,6);const id=Number(db(`INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (${q(name+'@example.test')},${q(name+'@example.test')},${q(name)},${q(name)},${q(hash)},'ACTIVE',CURRENT_TIMESTAMP) RETURNING id;`));
    db(`INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ${id},id FROM uteexpress.roles WHERE code=${q(code)};`);
    const address=Number(db(`INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (${id},'QA67 Synthetic Buyer','0900000000','VN','Test district','Synthetic test address',true) RETURNING id;`));accounts[role]={id,name,address};
  }
  shop=Number(db(`INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (${accounts.seller.id},'QA67 Synthetic Shop',${q('qa67-'+uuid)},'Synthetic pickup','APPROVED') RETURNING id;`));
  category=Number(db(`INSERT INTO uteexpress.categories(name,slug,active) VALUES ('QA67',${q('qa67-'+uuid)},true) RETURNING id;`));
  provider=Number(db(`INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES ('QA67','QA67 synthetic',true) RETURNING id;`));
  db(`INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (${provider},'STANDARD','VN',15000,true);INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7,CURRENT_TIMESTAMP-INTERVAL '1 day',${accounts.seller.id});`);
  browser=await chromium.launch({headless:true});results.environment.browser='Chromium '+browser.version();results.environment.playwright=require('C:/GestureHome-Web-HTN/node_modules/playwright/package.json').version;
  await require('./cases.cjs')({fs,path,assert,results,evidence,accounts,db,jsonDb,event,save,sleep,session,login,request,createOrder,state,cancelled,detail,shot,act,completed,feedback,test,startApp,browser});
}
main().catch(e=>{results.fatal={time:stamp(),message:e.message};console.log('FATAL '+e.message.slice(0,200));process.exitCode=1;}).finally(async()=>{
  active='CLEANUP';if(browser)await browser.close();
  for(const app of apps){app.proc.kill();}
  await sleep(1200);
  for(const app of apps){const lines=fs.readFileSync(app.log,'utf8').split(/\r?\n/).filter(l=>/Successfully (validated|applied)|migrat|Started Uteexpress|Started UteExpress|Tomcat started|ERROR|WARN|SQLState|constraint/i.test(l));fs.appendFileSync(path.join(evidence,'server-events.log'),lines.map(l=>l.replace(/Bearer\s+[^\s]+/gi,'Bearer [REDACTED]')).join('\n')+'\n');}
  if(created){try{assert.equal(exec('docker',['inspect','--format','{{ index .Config.Labels "qa67.run" }}',container]),uuid);exec('docker',['stop',container]);results.environment.cleanup='Owned disposable application processes stopped; owned ephemeral database container removed';}catch(e){results.environment.cleanup='Cleanup requires inspection: '+e.message.slice(0,100);}}
  results.finished=stamp();results.totals=results.cases.reduce((a,c)=>(a[c.status]=(a[c.status]||0)+1,a),{});if(results.totals.FAIL)process.exitCode=1;results.processExitCode=process.exitCode||0;save();console.log(JSON.stringify({totals:results.totals,fatal:results.fatal,evidence}));
});
