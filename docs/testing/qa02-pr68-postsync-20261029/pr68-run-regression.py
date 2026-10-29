import datetime, hashlib, json, os, pathlib, re, subprocess, sys, time, xml.etree.ElementTree as ET

BASE=pathlib.Path(__file__).parent
REPO=BASE/'pr68-postsync-20261029'
ART=BASE/os.environ.get('PR68_ARTIFACT_DIR','pr68-postsync-artifacts-20261029')
ART.mkdir(exist_ok=True)
EXPECTED=os.environ.get('PR68_SOURCE_SHA','20a2b2dfd011794bb2b7bf9bd9b88fe9cb2ac454')

def now(): return datetime.datetime.now(datetime.timezone.utc).isoformat()
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def git(*args): return subprocess.check_output(['git','-C',str(REPO),*args]).decode().strip()
def identity():
    return {'sha':git('rev-parse','HEAD'),'tree':git('rev-parse','HEAD^{tree}'),'status':git('status','--porcelain=v1'),'parents':git('show','-s','--format=%P','HEAD').split()}
def save(obj): (ART/'execution.json').write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def run(name, command, env, timeout):
    rec={'name':name,'argv':command,'working_directory':str(REPO),'start_utc':now(),'source_before':identity(),'timeout_seconds':timeout}
    if rec['source_before']['sha']!=EXPECTED or rec['source_before']['status']:
        raise RuntimeError('Source guard failed')
    log=ART/(name+'.log')
    try:
        with log.open('wb') as output:
            p=subprocess.Popen(command,cwd=REPO,env=env,stdout=output,stderr=subprocess.STDOUT)
            rec['pid']=p.pid
            try:
                rec['exit_code']=p.wait(timeout=timeout);rec['timeout']=False
            except subprocess.TimeoutExpired:
                p.kill();rec['exit_code']=p.wait();rec['timeout']=True
    except OSError as exc:
        rec['exit_code']=None;rec['timeout']=False;rec['launch_error']=str(exc)
    rec.update(end_utc=now(),log=log.name,log_sha256=sha(log),source_after=identity())
    return rec

def xml_group(folder):
    reports=[]
    for path in sorted((REPO/'target'/folder).glob('TEST-*.xml')):
        root=ET.parse(path).getroot()
        counts={k:int(root.attrib[k]) for k in ['tests','failures','errors','skipped']}
        cases=list(root.iter('testcase'))
        reports.append({'file':path.name,'sha256':sha(path),'suite':root.attrib.get('name'),**counts,'testcase_count':len(cases),
            'flakes':sum(len(list(c.iter(tag))) for c in cases for tag in ['flakyFailure','flakyError','rerunFailure','rerunError']),
            'testcases':[{'class':c.attrib.get('classname'),'name':c.attrib.get('name'),'status':'FAIL' if c.find('failure') is not None else 'ERROR' if c.find('error') is not None else 'SKIP' if c.find('skipped') is not None else 'PASS'} for c in cases]})
    return {'classes':len(reports),**{k:sum(r[k] for r in reports) for k in ['tests','failures','errors','skipped','flakes']},'reports':reports}

if __name__=='__main__':
    env=os.environ.copy()
    env['JAVA_HOME']=r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
    env['PATH']=env['JAVA_HOME']+r'\bin;'+env['PATH']
    env['PYTHONDONTWRITEBYTECODE']='1'
    env['DOCKER_CONTEXT']='desktop-linux'
    obj={'schema_version':1,'run_id':'pr68-postsync-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'),'started_utc':now(),'source':identity(),'runs':[]}
    if obj['source']['sha']!=EXPECTED or obj['source']['status']: raise RuntimeError('Initial source guard failed')
    obj['source']['inputs']={p:git('rev-parse','HEAD:'+p) for p in ['src/main','src/test','scripts/qa','pom.xml','.github/workflows/ci.yml']}
    save(obj)
    for name,command,timeout in [
        ('docker-version',['docker','--context','desktop-linux','version'],60),
        ('java-version',[str(pathlib.Path(env['JAVA_HOME'])/'bin/java.exe'),'-version'],60),
        ('node-version',['node','--version'],60),
        ('python-version',[sys.executable,'--version'],60),
        ('maven-full',['cmd','/d','/c','mvnw.cmd','-B','--no-transfer-progress','-o','-Ppostgres-it','clean','verify'],1800),
        ('node-default',['node','--test','--test-reporter=tap','src/test/js/notifications.test.cjs','src/test/js/buyer-cancellation.test.cjs'],120),
        ('tooling-default',[sys.executable,'-B','-m','unittest','discover','-s','scripts/qa/tests','-v'],120),
    ]:
        rec=run(name,command,env,timeout);obj['runs'].append(rec)
        if name=='maven-full':
            obj['unit']=xml_group('surefire-reports');obj['postgres']=xml_group('failsafe-reports')
            summary=REPO/'target/failsafe-reports/failsafe-summary.xml'
            if summary.exists():obj['failsafe_summary']={'sha256':sha(summary),'xml':summary.read_text()}
        save(obj)
        print(json.dumps({'name':name,'exit_code':rec['exit_code'],'start':rec['start_utc'],'end':rec['end_utc'],'log_sha256':rec['log_sha256']}),flush=True)
    obj['ended_utc']=now();obj['final_source']=identity();save(obj)
    print(json.dumps({k:{i:v for i,v in obj[k].items() if i!='reports'} for k in ['unit','postgres']}),flush=True)
