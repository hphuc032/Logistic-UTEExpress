from pathlib import Path
import subprocess,os,json,hashlib,secrets,uuid,tempfile,time,socket,urllib.request,urllib.parse,http.cookiejar,re,html,base64,datetime
r=Path(r'C:/Users/Phuc/.codex/worktrees/promo03-platform-integration/UTEExpress');w=Path('D:/UTEExpress/.work/promo03')
out=w/('complete-browser-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S'));out.mkdir()
private=Path(tempfile.mkdtemp(prefix='uteexpress-promo03-browser-private-'));owned=None;app=None;handle=None
utc=lambda:datetime.datetime.now(datetime.timezone.utc).isoformat()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
receipt={'startedUtc':utc(),'commands':[],'privateDirectory':str(private),'outputDirectory':str(out),'cleanup':{}}
def cmd(args,input=None,env=None):
 res=subprocess.run(args,input=input,text=True,capture_output=True,cwd=r,env=env)
 if res.returncode:raise RuntimeError(str(args[0])+' failed: '+res.stderr[:150])
 return res.stdout.strip()
def sql(q):return cmd(['docker','exec','-i',owned,'psql','-U','promo03','-d','promo03_browser','-v','ON_ERROR_STOP=1','-Atq'],q)
def freeport():
 with socket.socket() as sock:sock.bind(('127.0.0.1',0));return sock.getsockname()[1]
def write(p,obj):p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
try:
 source=cmd(['git','rev-parse','HEAD']);tree=cmd(['git','rev-parse','HEAD^{tree}']);assert source.startswith('15fb1dd')
 assert not cmd(['git','diff','--name-only',source,'--','src','pom.xml','.github'])
 jar=r/'target/uteexpress-0.1.0-SNAPSHOT.jar';assert jar.exists()
 receipt.update(source=source,tree=tree,jarSha256=sha(jar),runnerSha256=sha(Path(__file__)),browserDriverSha256=sha(w/'admin-browser.cjs'))
 env=dict(os.environ,POSTGRES_PASSWORD=secrets.token_urlsafe(32));name='uteexpress-promo03-browser-'+uuid.uuid4().hex[:10]
 owned=cmd(['docker','run','--detach','--name',name,'--label','uteexpress.task=promo03-browser','--publish','127.0.0.1::5432','--env','POSTGRES_DB=promo03_browser','--env','POSTGRES_USER=promo03','--env','POSTGRES_PASSWORD','postgres:17.6'],env=env)
 receipt['container']={'id':owned,'name':name,'image':'postgres:17.6'}
 for _ in range(60):
  if subprocess.run(['docker','exec',owned,'pg_isready','-U','promo03','-d','promo03_browser'],capture_output=True).returncode==0:break
  time.sleep(.5)
 dbport=cmd(['docker','port',owned,'5432/tcp']).split(':')[-1];port=freeport();base=f'http://127.0.0.1:{port}'
 env.update(DB_URL=f'jdbc:postgresql://127.0.0.1:{dbport}/promo03_browser',DB_USERNAME='promo03',DB_PASSWORD=env['POSTGRES_PASSWORD'],MAIL_HOST='127.0.0.1',MAIL_FROM='promo03@example.invalid',MAIL_SMTP_AUTH='false',MAIL_STARTTLS='false',MANAGEMENT_HEALTH_MAIL_ENABLED='false',JWT_SECRET_BASE64=base64.b64encode(secrets.token_bytes(48)).decode(),OTP_PEPPER_BASE64=base64.b64encode(secrets.token_bytes(48)).decode(),JWT_COOKIE_SECURE='false',UPLOAD_DIR=str(private/'uploads'),SERVER_ADDRESS='127.0.0.1',SERVER_PORT=str(port))
 handle=(private/'application.log').open('w',encoding='utf-8')
 app=subprocess.Popen(['C:/Program Files/Java/jdk-21/bin/java.exe','-Duser.timezone=UTC','-jar',str(jar),'--spring.profiles.active=local'],cwd=r,env=env,stdout=handle,stderr=subprocess.STDOUT)
 receipt['application']={'pid':app.pid,'startedUtc':utc(),'port':port}
 for _ in range(140):
  if app.poll() is not None:raise RuntimeError('application exited; private log retained')
  try:
   if urllib.request.urlopen(base+'/actuator/health',timeout=2).status==200:break
  except Exception:pass
  time.sleep(.5)
 else:raise RuntimeError('readiness timeout')
 receipt['application']['readyUtc']=utc()
 username='promoadmin_'+uuid.uuid4().hex[:12];password='BrowserTest1!'+secrets.token_hex(4)
 jar_cookies=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar_cookies))
 page=opener.open(base+'/register').read().decode()
 token=html.unescape(re.search(r'name="_csrf" value="([^"]+)"',page).group(1))
 response=opener.open(base+'/register',urllib.parse.urlencode({'_csrf':token,'email':username+'@example.invalid','username':username,'password':password,'confirmPassword':password}).encode())
 assert response.status==200
 actor=int(sql(f"SELECT id FROM uteexpress.users WHERE normalized_username='{username}';"))
 sql(f"UPDATE uteexpress.users SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE id={actor}; INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT {actor},id FROM uteexpress.roles WHERE code='ADMIN';")
 category=int(sql("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Browser category','browser-promo-category',true) RETURNING id;"))
 shop=int(sql(f"INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES ({actor},'Browser shop','browser-promo-shop','Synthetic pickup','APPROVED') RETURNING id;"))
 product=int(sql(f"INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES ({shop},{category},'Browser eligible product',125000,10,'ACTIVE') RETURNING id;"))
 assert int(sql("SELECT count(*) FROM uteexpress.flyway_schema_history WHERE type='SQL' AND success;"))==28
 config={'baseUrl':base,'source':source,'tree':tree,'jarSha256':sha(jar),'username':username,'password':password,'actor':actor,'product':product,'container':owned,'outputDirectory':str(out),'chrome':'C:/Program Files/Google/Chrome/Application/chrome.exe'}
 write(private/'config.json',config)
 benv=dict(os.environ,PROMO_PLAYWRIGHT_MODULE='C:/Users/Phuc/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
 res=subprocess.run(['C:/Users/Phuc/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe',str(w/'admin-browser.cjs'),str(private/'config.json')],capture_output=True,text=True,cwd=r,env=benv)
 (private/'browser.log').write_text(res.stdout+'\n'+res.stderr,encoding='utf-8');print(res.stdout,flush=True)
 receipt['browserExitCode']=res.returncode
 receipt['databaseChecks']=json.loads(sql(f"select json_build_object('platformVouchers',(select count(*) from uteexpress.vouchers where scope='PLATFORM' and shop_id is null and created_by={actor}),'disabledVouchers',(select count(*) from uteexpress.vouchers where active=false and created_by={actor}),'promotions',(select count(*) from uteexpress.promotions where product_id={product} and created_by={actor}),'disabledPromotions',(select count(*) from uteexpress.promotions where product_id={product} and active=false),'audits',(select count(*) from uteexpress.audit_logs where actor_id={actor}),'orders',(select count(*) from uteexpress.orders),'stock',(select stock from uteexpress.products where id={product}));"))
 if res.returncode:raise RuntimeError('browser test failure; see retained browser-results.json')
 assert receipt['databaseChecks']=={'platformVouchers':3,'disabledVouchers':3,'promotions':3,'disabledPromotions':3,'audits':18,'orders':0,'stock':10},receipt['databaseChecks']
 receipt['status']='PASS'
except Exception as e:
 receipt['status']='FAIL';receipt['error']=str(e)[:1000];print(receipt['error'],flush=True)
finally:
 if app:
  app.terminate()
  try:app.wait(timeout=30)
  except subprocess.TimeoutExpired:app.kill();app.wait(timeout=10)
  receipt['cleanup']['applicationExitCode']=app.returncode
 if handle:handle.close();receipt['privateAppLogSha256']=sha(private/'application.log')
 if owned:
  assert owned==receipt['container']['id'] and len(owned)==64
  removal=subprocess.run(['docker','rm','-f',owned],capture_output=True,text=True)
  receipt['cleanup']['ownedContainerRemoveExitCode']=removal.returncode
  inspect=subprocess.run(['docker','inspect',owned],capture_output=True,text=True)
  receipt['cleanup']['ownedContainerAbsent']=inspect.returncode!=0 and 'no such' in inspect.stderr.lower()
 receipt['finishedUtc']=utc();receipt['cleanup']['unrelatedResourcesDeleted']=False
 write(out/'environment-receipt.json',receipt);print(json.dumps({'status':receipt['status'],'outputDirectory':str(out),'cleanup':receipt['cleanup']}),flush=True)
if receipt.get('status')!='PASS':raise SystemExit(1)
