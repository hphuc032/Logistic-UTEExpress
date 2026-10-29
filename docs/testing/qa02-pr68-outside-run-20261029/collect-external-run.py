import datetime, hashlib, json, pathlib, re, shutil, subprocess, xml.etree.ElementTree as ET

BASE=pathlib.Path(__file__).parent
ROOT=BASE/'pr68-postsync-20261029'
ART=BASE/'pr68-outside-run-evidence-20261029'
ART.mkdir(exist_ok=True)
RAW=ART/'private-raw';RAW.mkdir(exist_ok=True)
SOURCE='8705e8a865736387c96007aee7eb813a58dd1911'
TREE='3cba80a0ab92b90e7a82e9b4726acdb9bc0f328c'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def git(*args):return subprocess.check_output(['git','-C',str(ROOT),*args]).decode().strip()
def utc(t):return datetime.datetime.fromtimestamp(t,datetime.timezone.utc).isoformat()

identity={'collected_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'source_sha':git('rev-parse','HEAD'),'tree':git('rev-parse','HEAD^{tree}'),'status':git('status','--porcelain=v1'),'reflog':git('reflog','-8','--date=iso-strict'),'tracked_inputs':{p:git('rev-parse','HEAD:'+p) for p in ['src/main','src/test','scripts/qa','pom.xml','.mvn','.github/workflows/ci.yml']}}
assert identity['source_sha']==SOURCE and identity['tree']==TREE and not identity['status']
log=ROOT/'pr68-postsync-full-postgres.log'
shutil.copy2(log,RAW/log.name)
raw=log.read_bytes();encoding='utf-16' if raw[:2] in [b'\xff\xfe',b'\xfe\xff'] else 'utf-8-sig'
text=raw.decode(encoding);lines=text.splitlines()
report={'schema_version':1,'run_id':'pr68-outside-powershell-20261029T045843Z','collection_identity':identity,
 'reported_execution_context':'User PowerShell outside Codex sandbox with Docker Desktop permission; not a Codex Docker execution',
 'process_exit':{'value':0,'evidence':'user-attested; no contemporaneous exit-code receipt found in raw log','independently_captured':False},
 'raw_log':{'file':log.name,'bytes':len(raw),'sha256':sha(log),'encoding':encoding,'mtime_utc':utc(log.stat().st_mtime)},
 'log_summary':{'build_success':lines.count('[INFO] BUILD SUCCESS')==1,'finished_at':next(x.split('Finished at: ',1)[1] for x in lines if 'Finished at: ' in x),'duration':next(x.split('Total time: ',1)[1] for x in lines if 'Total time: ' in x),'start_time_recorded':False,'start_time_note':'Exact process start was not captured; Maven duration 08:02 min is not an exact Popen timestamp'},
 'runtime':{},'unit':{},'postgres':{},'required_coverage':{},'log_suite_summaries':[]}
assert report['log_summary']['build_success']
versions={}
for folder,key in [('surefire-reports','unit'),('failsafe-reports','postgres')]:
 dest=RAW/folder;dest.mkdir(exist_ok=True)
 suites=[]
 for p in sorted((ROOT/'target'/folder).glob('TEST-*.xml')):
  shutil.copy2(p,dest/p.name)
  root=ET.parse(p).getroot();cases=list(root.iter('testcase'))
  rec={'file':p.name,'sha256':sha(p),'bytes':p.stat().st_size,'mtime_utc':utc(p.stat().st_mtime),'suite':root.attrib['name'],'timestamp':root.attrib.get('timestamp'),'duration_seconds':root.attrib.get('time'),**{k:int(root.attrib[k]) for k in ['tests','failures','errors','skipped']},'testcase_count':len(cases),
   'flakes':sum(len(list(root.iter(tag))) for tag in ['flakyFailure','flakyError','rerunFailure','rerunError']),
   'testcases':[{'class':c.attrib.get('classname'),'name':c.attrib.get('name'),'status':'FAIL' if c.find('failure') is not None else 'ERROR' if c.find('error') is not None else 'SKIP' if c.find('skipped') is not None else 'PASS'} for c in cases]}
  assert rec['tests']==len(cases)
  assert all(rec[k]==0 for k in ['failures','errors','skipped','flakes'])
  for prop in root.findall('./properties/property'):
   if prop.attrib['name'] in ['java.version','java.home','java.vm.version','java.vendor','basedir','user.dir']:
    versions.setdefault(prop.attrib['name'],set()).add(prop.attrib.get('value',''))
  suites.append(rec)
 report[key]={'suites':len(suites),**{k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped','flakes']},'reports':suites}
summary=ROOT/'target/failsafe-reports/failsafe-summary.xml';shutil.copy2(summary,RAW/'failsafe-summary.xml')
elem=ET.parse(summary).getroot()
report['failsafe_summary']={'sha256':sha(summary),'bytes':summary.stat().st_size,'timeout':elem.attrib['timeout'],'counts':{k:int(elem.find(k).text) for k in ['completed','errors','failures','skipped','flakes']}}
assert report['failsafe_summary']['counts']['completed']==report['postgres']['tests']==1352
assert report['unit']['tests']==705
report['java_total']=2057
report['runtime']['xml_properties']={k:sorted(v) for k,v in versions.items()}
assert versions['java.version']=={'21.0.12.1'} and versions['basedir']=={str(ROOT)}
report['runtime'].update({'docker_server_version':re.search(r'Server Version:\s*([^\r\n]+)',text).group(1).strip(),'postgresql_version':sorted(set(re.findall(r'\(PostgreSQL ([\d.]+)\)',text))),'testcontainers_version':sorted(set(re.findall(r'Testcontainers version: ([\d.]+)',text))),'maven_enforcer_java_rule_passed':'RequireJavaVersion passed' in text,'maven_enforcer_maven_rule_passed':'RequireMavenVersion passed' in text})
pattern=r'Tests run: ([\d,]+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), Time elapsed: .* -- in (\S+)'
for m in re.finditer(pattern,text):report['log_suite_summaries'].append({'suite':m[5],**{k:int(m[i].replace(',','')) for i,k in enumerate(['tests','failures','errors','skipped'],1)}})
assert len(report['log_suite_summaries'])==141
lookup={r['suite']:r for key in ['unit','postgres'] for r in report[key]['reports']}
for row in report['log_suite_summaries']:
 assert all(row[k]==lookup[row['suite']][k] for k in ['tests','failures','errors','skipped'])
for name in ['com.uteexpress.integration.PromotionBrowserFormsIT','com.uteexpress.promotion.VendorVoucherIT','com.uteexpress.promotion.ProductPromotionIT','com.uteexpress.security.Qa01CookieCsrfIT','com.uteexpress.security.Qa01OtpSecurityIT','com.uteexpress.catalog.controller.VendorProductPromotionBindingTest']:
 r=lookup[name];report['required_coverage'][name]={k:r[k] for k in ['tests','failures','errors','skipped','sha256']}
inventory={}
for folder in ['classes','test-classes']:
 dest=RAW/folder;dest.mkdir(exist_ok=True)
 records=[]
 for p in sorted((ROOT/'target'/folder).rglob('*')):
  if p.is_file():
   rel=p.relative_to(ROOT/'target'/folder);q=dest/rel;q.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,q)
   records.append({'path':rel.as_posix(),'sha256':sha(p),'bytes':p.stat().st_size})
 inventory[folder]=records
report['compiled_runtime_inventory']=inventory
report['jar_original']={'sha256':sha(ROOT/'target/uteexpress-0.1.0-SNAPSHOT.jar.original'),'bytes':(ROOT/'target/uteexpress-0.1.0-SNAPSHOT.jar.original').stat().st_size}
assert sha(log)==report['raw_log']['sha256']
(ART/'collected.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'source':identity['source_sha'],'tree':identity['tree'],'clean':not identity['status'],'total':report['java_total'],'unit':report['unit']['tests'],'postgres':report['postgres']['tests'],'log_sha256':report['raw_log']['sha256'],'runtime':report['runtime'],'coverage':report['required_coverage'],'runtime_files':{k:len(v) for k,v in inventory.items()}},ensure_ascii=True))
