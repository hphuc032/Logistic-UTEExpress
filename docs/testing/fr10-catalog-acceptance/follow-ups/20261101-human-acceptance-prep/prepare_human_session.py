"""FR-10 human-session preparation: exact source and disposable fixture; no human decisions generated."""
from pathlib import Path
import subprocess,os,json,hashlib,secrets,uuid,tempfile,time,socket,urllib.request,sys,shutil,datetime,argparse
def utc():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
ap=argparse.ArgumentParser();ap.add_argument('--root',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);ap.add_argument('--java',type=Path,required=True);ap.add_argument('--node',type=Path,required=True);ap.add_argument('--playwright',required=True);ap.add_argument('--chrome',required=True);ap.add_argument('--browser-runner',type=Path,required=True);ap.add_argument('--case-filter');ap.add_argument('--expected-source',required=True);ap.add_argument('--primary-root',type=Path,required=True);ap.add_argument('--session-stop-file',type=Path,required=True);a=ap.parse_args()
root=a.root.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
private=Path(tempfile.mkdtemp(prefix='uteexpress-fr10-private-'));(private/'uploads/products').mkdir(parents=True)
receipt={'startedUtc':utc(),'scope':'Master Plan FR-10 Catalog only, HP clarification in current chat','commands':[],'privateArtifacts':'Outside repository, retained locally; no raw credentials/session data published','cleanup':{},'testsSkippedInBuild':True,'executionMode':'HUMAN_SESSION_PREPARATION_ONLY','automatedTestsExecuted':0};owned=None;app=None;applog=None
def cmd(argv,input=None,check=True,env=None,label=None):
 start=utc();r=subprocess.run([str(x) for x in argv],input=input,text=True,capture_output=True,cwd=root,env=env)
 receipt['commands'].append({'label':label or str(argv[0]),'argv':[str(x) for x in argv],'startedUtc':start,'finishedUtc':utc(),'exit':r.returncode})
 if r.returncode:(private/f'command-{len(receipt["commands"]):04d}.log').write_text(r.stdout+'\n'+r.stderr,encoding='utf8')
 if check and r.returncode:raise RuntimeError((label or str(argv[0]))+' failed exit '+str(r.returncode)+'; private diagnostics retained')
 return r
def git(*s):return cmd(['git',*s],label='git source guard').stdout.strip()
def freeport():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def sql(s):return cmd(['docker','exec','-i',owned,'psql','-U','fr10','-d','fr10_catalog','-v','ON_ERROR_STOP=1','-Atq'],input=s,label='owned PostgreSQL fixture/read query').stdout.strip()
def fingerprint():
 tables=sql("SELECT tablename FROM pg_tables WHERE schemaname='uteexpress' ORDER BY tablename;").splitlines();res={}
 for t in tables:
  # Identifiers originate only from pg_tables, not browser input.
  assert t.replace('_','').isalnum()
  text=sql(f"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM uteexpress.\"{t}\" t;")
  res[t]={'rows':int(sql(f'SELECT count(*) FROM uteexpress."{t}";')),'sha256':hashlib.sha256(text.encode()).hexdigest()}
 return res
def stopapp():
 global app,applog
 stopped_pid=app.pid if app else None
 if app and app.poll() is None:
  app.terminate()
  try:app.wait(20)
  except subprocess.TimeoutExpired:app.kill();app.wait(10)
 if app:receipt['cleanup'].setdefault('javaProcesses',[]).append({'pid':stopped_pid,'stopped':app.poll() is not None,'exitCode':app.poll()});assert app.poll() is not None,'Owned Java still running'
 if applog:applog.close()
 app=None;applog=None
def boot(demo=False):
 global app,applog
 port=freeport();base=f'http://127.0.0.1:{port}';env={k:v for k,v in os.environ.items() if k.upper() in {'SYSTEMROOT','WINDIR','PATH','TEMP','TMP','COMSPEC','PATHEXT','USERPROFILE','LOCALAPPDATA'}}
 env.update(DB_URL=f'jdbc:postgresql://127.0.0.1:{dbport}/fr10_catalog',DB_USERNAME='fr10',DB_PASSWORD=password,MAIL_HOST='127.0.0.1',MAIL_FROM='fr10@example.invalid',MAIL_SMTP_AUTH='false',MAIL_STARTTLS='false',MANAGEMENT_HEALTH_MAIL_ENABLED='false',JWT_SECRET_BASE64=secrets.token_urlsafe(48),OTP_PEPPER_BASE64=secrets.token_urlsafe(48),JWT_COOKIE_SECURE='false',UPLOAD_DIR=str(private/'uploads'),SERVER_ADDRESS='127.0.0.1',SERVER_PORT=str(port))
 # urlsafe strings must be converted to standard Base64 for the existing validators.
 import base64
 env['JWT_SECRET_BASE64']=base64.b64encode(secrets.token_bytes(48)).decode();env['OTP_PEPPER_BASE64']=base64.b64encode(secrets.token_bytes(48)).decode()
 argv=[str(a.java),'-Duser.timezone=UTC','-jar',str(jar),'--spring.profiles.active=local'+(',demo' if demo else '')]
 if demo:argv+=['--uteexpress.demo.catalog.enabled=true']
 logfile=private/('demo-app.log' if demo else 'migrate-app.log');applog=logfile.open('w',encoding='utf8');start=utc();app=subprocess.Popen(argv,cwd=root,env=env,stdout=applog,stderr=subprocess.STDOUT)
 for _ in range(100):
  if app.poll() is not None:raise RuntimeError('Application boot failed; see private log')
  try:
   with urllib.request.urlopen(base+'/actuator/health',timeout=2) as r:
    if r.status==200:
     receipt['commands'].append({'label':'catalog app boot; SMTP health deliberately out of scope','argv':argv,'startedUtc':start,'readyUtc':utc(),'pid':app.pid,'httpHealth':200,'demoSeeder':demo});return base
  except Exception:pass
  time.sleep(.5)
 raise RuntimeError('Application readiness timeout')
try:
 source=git('rev-parse','HEAD');tree=git('rev-parse','HEAD^{tree}');assert len(a.expected_source)==40 and all(c in '0123456789abcdef' for c in a.expected_source);assert source==a.expected_source,'Exact source mismatch'
 assert git('status','--porcelain')==''
 jar=root/'target/uteexpress-0.1.0-SNAPSHOT.jar';assert jar.is_file()
 primary=a.primary_root.resolve();assert primary!=root,'Execution must use a separate checkout';primary_head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=primary,text=True).strip();primary_index_path=Path(subprocess.check_output(['git','rev-parse','--path-format=absolute','--git-path','index'],cwd=primary,text=True).strip());primary_index=sha(primary_index_path);primary_status=subprocess.check_output(['git','status','--porcelain=v1'],cwd=primary,text=True)
 proof={'source':source,'tree':tree,'acceptedDevelop':source,'jarSha256':sha(jar),'driverSha256':sha(a.browser_runner),'environmentRunnerSha256':sha(Path(__file__)),'runnerVersion':'human-session-1','browserDriverExecuted':False,'primaryRepository':str(primary),'primaryStatusBefore':primary_status,'caseFilter':a.case_filter,'primaryHeadBefore':primary_head,'primaryIndexSha256Before':primary_index,'cleanExecutionWorktreeBefore':True,'readinessChecklist':'NOT LOCATED in fetched refs; Master Plan and HP clarification govern this packet','pr69':'Out of scope; never executed or changed here','g02ExecutedSource':'5089a8ed17e5f8486e44847a99b309ff13d0d3b8; historical, not this execution'};write(out/'source-proof.json',proof)
 for argv,label in [([a.java,'-version'],'Java version'),([a.node,'--version'],'Node version'),(['docker','version','--format','{{.Client.Version}} / {{.Server.Version}}'],'Docker version')]:
  r=cmd(argv,label=label);receipt.setdefault('versions',{})[label]=(r.stdout+r.stderr).strip()
 name='uteexpress-fr10-human-'+uuid.uuid4().hex[:12];password=secrets.token_urlsafe(32);env=dict(os.environ,POSTGRES_PASSWORD=password)
 argv=['docker','run','--detach','--name',name,'--label','uteexpress.task=fr10-catalog','--publish','127.0.0.1::5432','--env','POSTGRES_DB=fr10_catalog','--env','POSTGRES_USER=fr10','--env','POSTGRES_PASSWORD','postgres:17.6']
 owned=cmd(argv,env=env,label='create own disposable PostgreSQL only').stdout.strip();assert len(owned)==64
 receipt['ownedContainer']={'id':owned,'name':name,'image':'postgres:17.6','database':'fr10_catalog'}
 for i in range(80):
  ping=cmd(['docker','exec',owned,'pg_isready','-U','fr10','-d','fr10_catalog'],check=False,label='owned DB readiness')
  if ping.returncode==0:break
  time.sleep(.25)
 else:raise RuntimeError('Owned PostgreSQL readiness failed')
 dbport=int(cmd(['docker','port',owned,'5432/tcp'],label='owned loopback port').stdout.strip().split(':')[-1]);boot()
 expected_migrations=sorted(p.name.split('__',1)[0][1:] for p in (root/'src/main/resources/db/migration').glob('V*__*.sql'));actual_migrations=sql("SELECT version FROM uteexpress.flyway_schema_history WHERE type='SQL' AND success ORDER BY version;").splitlines();assert actual_migrations==expected_migrations,'Migration inventory mismatch';receipt['migrations']={'expected':expected_migrations,'applied':actual_migrations,'count':len(actual_migrations),'schemaValidation':'Application readiness passed with ddl-auto=validate'}
 stopapp()
 sql("""INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES ('fr10-owner@example.invalid','fr10-owner@example.invalid','fr10_owner','fr10_owner','DEMO_ACCOUNT_DISABLED','DISABLED');
 INSERT INTO uteexpress.categories(name,slug,active) VALUES ('FR10 Catalog category','demo-category',true);
 INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) SELECT id,'FR10 Demo shop','demo-shop','Synthetic fixture pickup','APPROVED' FROM uteexpress.users WHERE normalized_username='fr10_owner';""")
 base=boot(True)
 for slug,status in [('fr10-empty-shop','APPROVED'),('fr10-suspended','SUSPENDED'),('fr10-pending','PENDING'),('fr10-rejected','REJECTED')]:
  username=slug.replace('-','_');reason="'Synthetic rejection'" if status=='REJECTED' else 'NULL';moderation="'Synthetic moderation'" if status=='SUSPENDED' else 'NULL'
  sql(f"INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES ('{slug}@example.invalid','{slug}@example.invalid','{username}','{username}','DEMO_ACCOUNT_DISABLED','DISABLED'); INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status,rejection_reason,moderation_reason) SELECT id,'{slug}','{slug}','Synthetic pickup','{status}',{reason},{moderation} FROM uteexpress.users WHERE normalized_username='{username}';")
 sql("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('FR10 Empty','fr10-empty',true),('FR10 Disabled','fr10-disabled',false),('FR10 Escape fixture','fr10-xss',true);")
 def cat(slug):return int(sql(f"SELECT id FROM uteexpress.categories WHERE slug='{slug}';"))
 def shop(slug):return int(sql(f"SELECT id FROM uteexpress.shops WHERE slug='{slug}';"))
 sql(f"INSERT INTO uteexpress.products(shop_id,category_id,name,description,price,stock,status) VALUES ({shop('fr10-suspended')},{cat('demo-category')},'FR10 suspended product','Fixture',125000,2,'ACTIVE'),({shop('demo-shop')},{cat('fr10-disabled')},'FR10 disabled-category','Fixture',125000,2,'ACTIVE');")
 xssName='<script>window.__fr10Xss=1</script>'
 sql(f"INSERT INTO uteexpress.products(shop_id,category_id,name,description,price,stock,status,created_at) VALUES ({shop('demo-shop')},{cat('fr10-xss')},'{xssName}','Vendor text fixture',125000,1,'ACTIVE','2020-01-01T00:00:00Z');")
 product=int(sql("SELECT id FROM uteexpress.products WHERE name='Bưu kiện mẫu tiêu chuẩn';"));hidden=int(sql("SELECT id FROM uteexpress.products WHERE name='Bưu kiện mẫu ẩn';"));zero=int(sql("SELECT id FROM uteexpress.products WHERE name='Bưu kiện mẫu hết hàng';"))
 image=root/'docs/testing/qa03-assignment-ui/browser/fixture.png';key='products/'+str(uuid.uuid4())+'.png';shutil.copyfile(image,private/'uploads'/key)
 imageid=int(sql(f"INSERT INTO uteexpress.product_images(product_id,storage_key,position,alt_text) VALUES ({product},'{key}',0,'FR10 fixture product image') RETURNING id;"));hiddenimage=int(sql(f"INSERT INTO uteexpress.product_images(product_id,storage_key,position,alt_text) VALUES ({hidden},'{key}',0,'Hidden fixture') RETURNING id;"))
 fixture={'product':product,'hidden':hidden,'zeroStock':zero,'image':imageid,'hiddenImage':hiddenimage,'imageSha256':sha(image),'nonpublicProducts':[int(x) for x in sql("SELECT id FROM uteexpress.products WHERE status IN ('HIDDEN','MODERATED') OR name IN ('FR10 suspended product','FR10 disabled-category') ORDER BY id;").splitlines()],'xss':int(sql("SELECT id FROM uteexpress.products WHERE category_id="+str(cat('fr10-xss')))),'xssName':xssName,'scope':'Catalog-only SQL prerequisites matching PublicCatalogIT + real ProductDemoSeeder. Disabled non-login owners. No Order/Payment state or full shared seed.', 'publicCategory':'demo-category','publicShop':'demo-shop'}
 before=fingerprint();write(out/'dataset-manifest.json',{'fixture':fixture,'before':before,'source':source,'realSharedSeedAcceptanceClaimed':False})
 assert before['orders']['rows']==0 and before['payments']['rows']==0
 stop_file=a.session_stop_file.resolve();assert stop_file.parent==out,'Stop file must be inside the new session output directory'
 ready={'mode':'HUMAN_SESSION_READY; NO ACCEPTANCE CLAIMED','source':source,'jarSha256':sha(jar),'baseUrl':base,'applicationPid':app.pid,'fixture':fixture,'stopFile':str(stop_file),'startedUtc':utc(),'leaseSeconds':5400,'humanDecisions':None}
 write(out/'session-ready.json',ready);print('HUMAN_SESSION_READY '+base,flush=True)
 deadline=time.monotonic()+5400
 while not stop_file.exists() and time.monotonic()<deadline:
  if app.poll() is not None:raise RuntimeError('Owned human-session application exited unexpectedly')
  time.sleep(1)
 after=fingerprint();write(out/'database-observation.json',{'before':before,'after':after,'allTableFingerprintsUnchanged':before==after,'ordersCreated':after['orders']['rows'],'paymentsCreated':after['payments']['rows']});assert before==after,'Unexpected Catalog database mutation'
 receipt['technicalResult']='HUMAN SESSION STOPPED; ACCEPTANCE REQUIRES HP DECISIONS' if stop_file.exists() else 'HUMAN SESSION LEASE EXPIRED; NOT ACCEPTED'
 proof.update({'primaryHeadAfter':subprocess.check_output(['git','rev-parse','HEAD'],cwd=primary,text=True).strip(),'primaryIndexSha256After':sha(primary_index_path),'cleanExecutionWorktreeAfter':git('status','--porcelain')==''});assert proof['primaryHeadAfter']==primary_head and proof['primaryIndexSha256After']==primary_index;proof['primaryStatusAfter']=subprocess.check_output(['git','status','--porcelain=v1'],cwd=primary,text=True);assert proof['primaryStatusAfter']==primary_status;assert proof['cleanExecutionWorktreeAfter'];write(out/'source-proof.json',proof)
except Exception as exc:
 receipt['technicalResult']='BLOCKED';receipt['failure']=str(exc)[:800];print(receipt['failure'],flush=True);(private/'failure.txt').write_text(str(exc),encoding='utf8')
finally:
 stopapp();receipt['cleanup']['ownedJavaStopped']=app is None
 if owned:
  label=cmd(['docker','inspect','--format','{{ index .Config.Labels "uteexpress.task" }}',owned],check=False,label='verify owned container label before removal');assert label.returncode==0 and label.stdout.strip()=='fr10-catalog','Container ownership mismatch; no deletion performed'
  r=cmd(['docker','rm','--force',owned],check=False,label='remove exact owned FR10 container only');check=cmd(['docker','ps','--all','--quiet','--filter','id='+owned],check=False,label='verify exact owned container absent');receipt['cleanup']['ownedContainerRemoved']=r.returncode==0 and check.returncode==0 and not check.stdout.strip()
 receipt['finishedUtc']=utc();receipt['privateArtifactHashes']=[{'name':p.relative_to(private).as_posix(),'bytes':p.stat().st_size,'sha256':sha(p)} for p in private.rglob('*') if p.is_file()]
 write(out/'execution-receipt.json',receipt);print('FR10 private artifacts retained at '+str(private));print('FR10 technical result: '+receipt.get('technicalResult','NOT RUN'))
 if receipt.get('technicalResult')=='BLOCKED':sys.exit(1)
