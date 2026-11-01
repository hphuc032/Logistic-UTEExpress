from pathlib import Path
import os, subprocess, json, secrets, socket, time, urllib.request, uuid, hashlib, base64, datetime

WORK=Path(__file__).resolve().parent
SOURCE=Path('D:/UTEExpress/.work/doc01-source-a83')
REPORT=Path('D:/UTEExpress/.work/doc01-pr76/docs/report/hp')
OUT=REPORT/'evidence/follow-ups/20261101-validation/attempt-3'
RUNTIME=Path('C:/Users/Phuc/.cache/codex-runtimes/codex-primary-runtime/dependencies')
NODE=RUNTIME/'node/bin/node.exe'
MODULES=RUNTIME/'node/node_modules'
if OUT.exists():
    raise SystemExit('Refuse to overwrite published capture evidence; select a new attempt directory first')
OUT.mkdir(parents=True,exist_ok=False)
(REPORT/'figures/screenshots/current/attempt-3').mkdir(parents=True,exist_ok=True)
def utc(): return datetime.datetime.now(datetime.timezone.utc).isoformat()
def run(args,data=None,env=None):
    r=subprocess.run([str(x) for x in args],input=data,text=True,capture_output=True,env=env,check=False)
    if r.returncode: raise RuntimeError('Command failed: '+str(args[0])+'; '+r.stderr[:200])
    return r.stdout.strip()
def port():
    with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
receipt={'startedUtc':utc(),'scope':'DOC01 isolated screenshots; not full functional regression','testsRerun':False,'containers':[],'productionDataAccessed':False}
containers=[]; app=None
password=secrets.token_urlsafe(24)
env={k:v for k,v in os.environ.items() if k.upper() in {'SYSTEMROOT','WINDIR','PATH','TEMP','TMP','COMSPEC','PATHEXT','USERPROFILE','LOCALAPPDATA'}}
def sql(text):return run(['docker','exec','-i',db,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','doc01','-d','doc01'],text)
def counts():return json.loads(sql("SELECT json_object_agg(tablename,n) FROM (SELECT 'users' tablename,count(*) n FROM uteexpress.users UNION ALL SELECT 'addresses',count(*) FROM uteexpress.addresses UNION ALL SELECT 'product_images',count(*) FROM uteexpress.product_images UNION ALL SELECT 'otp_tokens',count(*) FROM uteexpress.otp_tokens UNION ALL SELECT 'orders',count(*) FROM uteexpress.orders UNION ALL SELECT 'payments',count(*) FROM uteexpress.payments) x;"))
try:
    sha=run(['git','-C',SOURCE,'rev-parse','HEAD']);assert sha=='a83c4aa03fb3806bd5aba47f0f9fa5ccf0f0b473'
    assert not run(['git','-C',SOURCE,'status','--porcelain'])
    receipt['source']=sha;jar=SOURCE/'target/uteexpress-0.1.0-SNAPSHOT.jar';receipt['jarSha256']=hashlib.sha256(jar.read_bytes()).hexdigest()
    ident=uuid.uuid4().hex[:12]
    dbname='doc01-pg-'+ident;mailname='doc01-mail-'+ident
    db=run(['docker','run','-d','--name',dbname,'--label','uteexpress.task=doc01','-p','127.0.0.1::5432','-e','POSTGRES_DB=doc01','-e','POSTGRES_USER=doc01','-e','POSTGRES_PASSWORD','postgres:17.6'],env=dict(env,POSTGRES_PASSWORD=password));containers.append(db)
    for _ in range(80):
        if subprocess.run(['docker','exec',db,'pg_isready','-U','doc01','-d','doc01'],capture_output=True).returncode==0:break
        time.sleep(.25)
    dbport=int(run(['docker','port',db,'5432/tcp']).rsplit(':',1)[1])
    mail=run(['docker','run','-d','--name',mailname,'--label','uteexpress.task=doc01','-p','127.0.0.1::1025','axllent/mailpit']);containers.append(mail)
    mailport=int(run(['docker','port',mail,'1025/tcp']).rsplit(':',1)[1]);webport=port()
    uploads=WORK/'uploads';uploads.mkdir(exist_ok=True)
    env.update(DB_URL=f'jdbc:postgresql://127.0.0.1:{dbport}/doc01',DB_USERNAME='doc01',DB_PASSWORD=password,MAIL_HOST='127.0.0.1',MAIL_PORT=str(mailport),MAIL_FROM='doc01@example.invalid',MAIL_SMTP_AUTH='false',MAIL_STARTTLS='false',JWT_SECRET_BASE64=base64.b64encode(secrets.token_bytes(48)).decode(),OTP_PEPPER_BASE64=base64.b64encode(secrets.token_bytes(48)).decode(),UPLOAD_DIR=str(uploads),SERVER_PORT=str(webport),SERVER_ADDRESS='127.0.0.1')
    with (WORK/'app.private.log').open('w',encoding='utf8') as log:
        app=subprocess.Popen(['C:/Program Files/Java/jdk-21/bin/java.exe','-Duser.timezone=UTC','-jar',str(jar),'--spring.profiles.active=local'],cwd=SOURCE,env=env,stdout=log,stderr=log)
    base=f'http://127.0.0.1:{webport}'
    for _ in range(150):
        if app.poll() is not None:raise RuntimeError('App exited; see local private log')
        try:
            with urllib.request.urlopen(base+'/actuator/health',timeout=2) as r:
                if r.status==200:break
        except Exception:pass
        time.sleep(.5)
    else:raise RuntimeError('Readiness timeout')
    receipt['migrations']=json.loads(sql("SELECT json_build_object('count',count(*),'latest',max(version),'success',bool_and(success)) FROM uteexpress.flyway_schema_history WHERE type='SQL';"))
    receipt['hibernateValidation']='PASS — real app boot with ddl-auto=validate'
    hashed=run([NODE,'-e',"const fs=require('fs');const b=require(process.argv[1]);process.stdout.write(b.hashSync(fs.readFileSync(0,'utf8'),10));",str(MODULES/'bcryptjs')],password)
    for name,status,role in [('doc01_buyer','ACTIVE','USER'),('doc01_vendor','ACTIVE','VENDOR'),('doc01_pending','PENDING_VERIFICATION','USER')]:
        sql(f"INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES ('{name}@example.invalid','{name}@example.invalid','{name}','{name}','{hashed}','{status}'); INSERT INTO uteexpress.user_roles SELECT u.id,r.id FROM uteexpress.users u CROSS JOIN uteexpress.roles r WHERE u.username='{name}' AND r.code='{role}';")
    shop=int(sql("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) SELECT id,'DOC01 Synthetic Shop','doc01-shop','Synthetic pickup','APPROVED' FROM uteexpress.users WHERE username='doc01_vendor' RETURNING id;"))
    category=int(sql("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('DOC01 category','doc01-category',true) RETURNING id;"))
    product=None
    for i in range(13):
        pid=int(sql(f"INSERT INTO uteexpress.products(shop_id,category_id,name,description,price,stock,status) VALUES ({shop},{category},'DOC01 Product {i+1:02}','Synthetic documentation fixture',125000,30,'ACTIVE') RETURNING id;"))
        if product is None:product=pid
    receipt['before']=counts()
    config={'base':base,'source':sha,'jarSha256':receipt['jarSha256'],'chrome':'C:/Program Files/Google/Chrome/Application/chrome.exe','output':str(REPORT/'figures/screenshots/current/attempt-3'),'imagePrefix':'figures/screenshots/current/attempt-3/','results':str(OUT/'new-screenshots.json'),'password':password,'product':product,'image':str(SOURCE/'docs/testing/qa03-assignment-ui/browser/fixture.png')}
    private=WORK/'capture.private.json';private.write_text(json.dumps(config),encoding='utf8')
    result=subprocess.run([str(NODE),str(WORK/'capture.cjs'),str(private)],capture_output=True,text=True,env=dict(env,DOC01_NODE_MODULES=str(MODULES)))
    (WORK/'capture.log').write_text(result.stdout+'\n'+result.stderr,encoding='utf8')
    receipt['browserExitCode']=result.returncode
    receipt['after']=counts();assert receipt['after']['orders']==0 and receipt['after']['payments']==0
    if result.returncode:raise RuntimeError('Browser capture failed; see local capture.log; partial evidence preserved')
    receipt['status']='PASS';print(result.stdout)
except Exception as e:
    receipt['status']='BLOCKED';receipt['reason']=str(e);print(str(e))
finally:
    if app:
        if app.poll() is None:
            app.terminate()
            try:app.wait(20)
            except subprocess.TimeoutExpired:app.kill();app.wait(10)
        receipt['ownedJava']={'pid':app.pid,'stopped':app.poll() is not None}
    for cid in containers:
        assert run(['docker','inspect','--format','{{ index .Config.Labels "uteexpress.task" }}',cid])=='doc01'
        run(['docker','rm','-f',cid])
        absent=not run(['docker','ps','-a','-q','--filter','id='+cid])
        receipt['containers'].append({'id':cid,'removedAndAbsent':absent})
    receipt['finishedUtc']=utc()
    (OUT/'capture-execution.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    # Private passwords/config are never copied into the documentation packet.
    if (WORK/'capture.private.json').exists():(WORK/'capture.private.json').unlink()
    print(json.dumps({'status':receipt['status'],'cleanupContainers':len(containers)}))
