"""Opt-in, disposable-only QA-02 refund seed verification. No gateway or shared DB access.

Build first: mvnw -DskipTests package
Run: python scripts/qa/refund_acceptance_seed.py --execute
All credentials are generated in memory (or QA02_REFUND_DEMO_PASSWORD is supplied).
Only an owned, loopback PostgreSQL container is created and removed. Raw logs stay in .work.
"""
from __future__ import annotations
import argparse,datetime,hashlib,json,os,pathlib,secrets,shutil,subprocess,time,uuid,zipfile

ROOT=pathlib.Path(__file__).resolve().parents[2]
JAVA=pathlib.Path(os.environ.get('JAVA_HOME',''))/'bin'/('java.exe' if os.name=='nt' else 'java')
if not JAVA.is_file():JAVA=pathlib.Path(shutil.which('java') or 'java')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute',action='store_true',help='Explicitly permit creation of a new disposable fixture container')
    parser.add_argument('--output',type=pathlib.Path)
    args=parser.parse_args()
    if not args.execute:parser.error('--execute is required; no database was changed')
    identity=str(uuid.uuid4());name='qa02-refund-seed-'+identity[:8]
    out=args.output or ROOT/'.work'/('qa02-refund-seed-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+identity[:4])
    out=out.resolve();out.mkdir(parents=True,exist_ok=False)
    jar=ROOT/'target/uteexpress-0.1.0-SNAPSHOT.jar';seed_classes=ROOT/'target/test-classes/qa02/seed'
    if not jar.is_file() or not seed_classes.is_dir():raise RuntimeError('Build current source with mvnw -DskipTests package first')
    migration=ROOT/'src/main/resources/db/migration/V20261028081110__td_qa02_cancelled_paid_refunds.sql'
    with zipfile.ZipFile(jar) as z:
        if z.read('BOOT-INF/classes/db/migration/'+migration.name)!=migration.read_bytes():raise RuntimeError('JAR migration is stale; rebuild')
        z.getinfo('BOOT-INF/classes/com/uteexpress/order/service/CancelledPaidRefundService.class')
    shutil.copyfile(jar,out/'application.jar');shutil.copytree(seed_classes,out/'seed-classes/qa02/seed')
    env=dict(os.environ)
    for key in list(env):
        if key.startswith(('DB_','JWT_','OTP_','MAIL_','SPRING_','SERVER_','UPLOAD_','QA02_')) or key in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']:env.pop(key,None)
    db_password=secrets.token_urlsafe(32)
    demo_password=os.environ.get('QA02_REFUND_DEMO_PASSWORD') or secrets.token_urlsafe(24)
    flags={'creationflags':subprocess.CREATE_NO_WINDOW} if os.name=='nt' else {}
    def command(argv,input=None,timeout=90,cmd_env=None):
        return subprocess.run(argv,cwd=ROOT,input=input,capture_output=True,text=True,encoding='utf-8',errors='replace',check=True,timeout=timeout,env=cmd_env,**flags).stdout.strip()
    def sql(text):return command(['docker','exec','-i',name,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','qa02','-d','qa02_refund_seed'],text)
    def count():
        tables=['users','user_roles','shops','categories','products','addresses','shipping_providers','shipping_rates','commission_policies','carts','cart_items','orders','order_items','payments','voucher_usages','vouchers','order_status_history','return_requests','shipments','refunds','notifications','audit_logs']
        return json.loads(sql('SELECT json_build_object('+','.join("'"+t+"',(SELECT count(*) FROM uteexpress."+t+")" for t in tables)+');'))
    def fingerprint():
        # Hash exact persisted values, not just counts; never export private account hashes or proof values.
        tables=json.loads(sql("SELECT coalesce(json_agg(tablename ORDER BY tablename),'[]'::json) FROM pg_tables WHERE schemaname='uteexpress' AND tablename<>'flyway_schema_history';"));parts=[]
        for table in tables:
            assert table.isidentifier(),'Unexpected database identifier'
            text=sql('SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,\'[]\') FROM uteexpress.'+table+' t;')
            parts.append(table+':'+hashlib.sha256(text.encode()).hexdigest())
        return hashlib.sha256('\n'.join(parts).encode()).hexdigest()
    inputs=command(['git','ls-files','--cached','--others','--exclude-standard']).splitlines()
    tracked_inputs={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in inputs if (ROOT/p).is_file() and (p.startswith(('src/','scripts/qa/','.mvn/','.github/')) or p in ['pom.xml','mvnw','mvnw.cmd'])}
    input_digest=hashlib.sha256(json.dumps(tracked_inputs,sort_keys=True).encode()).hexdigest()
    receipt={'worktreeInputsSha256':input_digest,'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'sourceSha':command(['git','rev-parse','HEAD']),'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'container':name,'database':'qa02_refund_seed, newly created disposable PostgreSQL 17.6','runs':[],'credentials':'Generated in memory; not exported','scope':'QA02 refund fixture module; does not claim all Master Plan M6 shared seed stages'}
    created=False
    try:
        command(['docker','run','--rm','-d','--name',name,'--label','qa02.seed='+identity,'-e','POSTGRES_PASSWORD','-e','POSTGRES_USER=qa02','-e','POSTGRES_DB=qa02_refund_seed','-p','127.0.0.1::5432','postgres:17.6'],cmd_env={**env,'POSTGRES_PASSWORD':db_password});created=True
        port=command(['docker','port',name,'5432/tcp']).split(':')[-1]
        for attempt in range(60):
            try:sql('SELECT 1;');break
            except subprocess.CalledProcessError:
                if attempt==59:raise
                time.sleep(.25)
        env.update(DB_URL=f'jdbc:postgresql://127.0.0.1:{port}/qa02_refund_seed',DB_USERNAME='qa02',DB_PASSWORD=db_password,
            JWT_SECRET_BASE64=__import__('base64').b64encode(secrets.token_bytes(48)).decode(),OTP_PEPPER_BASE64=__import__('base64').b64encode(secrets.token_bytes(48)).decode(),
            MAIL_HOST='127.0.0.1',MAIL_PORT='9',MAIL_FROM='qa02-refund@example.test',MAIL_SMTP_AUTH='false',MAIL_STARTTLS='false',
            SERVER_PORT='0',UPLOAD_DIR=str(out/'uploads'),QA02_DISPOSABLE_SEED_CONFIRM='YES',QA02_REFUND_DEMO_PASSWORD=demo_password)
        java_args=[str(JAVA),'-Duser.timezone=UTC','-Dloader.path='+str(out/'seed-classes'),'-Dloader.main=qa02.seed.RefundAcceptanceSeed','-cp',str(out/'application.jar'),'org.springframework.boot.loader.launch.PropertiesLauncher','--spring.profiles.active=local,demo','--server.address=127.0.0.1','--management.health.mail.enabled=false','--uteexpress.demo.shipping.enabled=false','--uteexpress.demo.catalog.enabled=false']
        for number in [1,2]:
            log=out/f'seed-{number}.log'
            with log.open('wb') as stream:r=subprocess.run(java_args,cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT,timeout=180,**flags)
            if r.returncode:raise RuntimeError(f'Seed run {number} exit {r.returncode}; inspect private log {log}')
            lines=log.read_text(encoding='utf-8',errors='replace').splitlines();line=next(s for s in lines if s.startswith('QA02_REFUND_SEED_RESULT='));data=json.loads(line.split('=',1)[1])
            current={'number':number,'applicationExitCode':r.returncode,'counts':count(),'dataDigest':fingerprint(),'result':data};receipt['runs'].append(current)
            if number==1:
                assert current['counts']['orders']==6 and current['counts']['refunds']==2
                outcomes={s['scenario']:s for s in data['scenarios']}
                assert outcomes['returned-ready']['status']=='RETURNED' and outcomes['returned-ready']['payment_status']=='PAID'
                assert outcomes['returned-refunded']['status']=='REFUNDED'
                assert outcomes['cancelled-paid-ready']['status']=='CANCELLED' and outcomes['cancelled-paid-ready']['payment_status']=='PAID'
                assert outcomes['cancelled-paid-refunded']['status']=='CANCELLED' and outcomes['cancelled-paid-refunded']['refund_count']==1
                # Simulate a later catalog edit. A rerun must preserve it and all existing business progress.
                sql("UPDATE uteexpress.products SET price=123456 WHERE name='QA02 refund cancelled-paid-ready';")
                receipt['expectedBeforeRerun']={'counts':count(),'dataDigest':fingerprint()}
            else:
                assert current['counts']==receipt['expectedBeforeRerun']['counts'],'Rerun duplicated rows'
                assert current['dataDigest']==receipt['expectedBeforeRerun']['dataDigest'],'Rerun rewrote existing data/progress'
        receipt['result']='PASS';receipt['idempotency']='Exact digest of all business tables (excluding Flyway history) and fixture counts unchanged on rerun, including a deliberate catalog edit.'
        receipt['constraints']=json.loads(sql("SELECT json_build_object('validated',bool_and(convalidated),'count',count(*)) FROM pg_constraint WHERE connamespace='uteexpress'::regnamespace;"))
        assert receipt['constraints']['validated']
    except Exception as error:
        receipt['result']='FAIL';receipt['error']=str(error);raise
    finally:
        if created:
            labels=json.loads(command(['docker','inspect','--format','{{json .Config.Labels}}',name]));assert labels['qa02.seed']==identity
            command(['docker','stop',name]);receipt['cleanup']='Owned container/databases removed; all other applications/data untouched'
        receipt['finishedUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat();(out/'seed-results.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps({'result':receipt.get('result'),'evidence':str(out/'seed-results.json'),'runs':len(receipt['runs'])}))
if __name__=='__main__':main()
