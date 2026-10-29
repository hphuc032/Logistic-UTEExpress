import datetime,hashlib,json,pathlib,re,subprocess,sys,tarfile,xml.etree.ElementTree as ET
BASE=pathlib.Path(__file__).parent;ROOT=BASE/'pr70-post68-20261029';ART=BASE/'pr70-post68-evidence-20261029'
SOURCE='321ccf33528fbd214cf9d5399cadbc19df218148';TREE='31a913354b423726e4f97d40e2233db8429fbfc3'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def git(*args):return subprocess.check_output(['git','-C',str(ROOT),*args]).decode().strip()
assert git('rev-parse','HEAD')==SOURCE and not git('status','--porcelain')
execution=json.loads((ART/'hosted-execution.json').read_text(encoding='utf-8'))
assert execution['status']=='PASS' and execution['source']==execution['source_after']=={'sha':SOURCE,'tree':TREE,'clean':True}
assert sha(ART/'raw-artifacts.cms.der')==execution['raw_archive']['encrypted_sha256']
assert sha(ART/'artifact-public-certificate.pem')==execution['raw_archive']['public_certificate_sha256']
private=ART/'private-raw';private.mkdir(exist_ok=True)
archive=private/'raw-artifacts.tar.gz'
subprocess.run([r'C:/msys64/ucrt64/bin/openssl.exe','cms','-decrypt','-binary','-inform','DER','-in',str(ART/'raw-artifacts.cms.der'),'-recip',str(ART/'artifact-public-certificate.pem'),'-inkey',str(ART/'artifact-private-key.pem'),'-out',str(archive)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
assert sha(archive)==execution['raw_archive']['plaintext_archive_sha256']
with tarfile.open(archive,'r:gz') as package:
 members=package.getmembers();assert sum(m.size for m in members)<256*1024*1024
 for m in members:
  assert not m.issym() and not m.islnk() and not m.isdev()
  assert (private/m.name).resolve().is_relative_to(private.resolve())
 package.extractall(private,filter='data')
raw=private/'raw';before=json.loads((raw/'execution-state.json').read_text())
assert before=={k:v for k,v in execution.items() if k!='raw_archive'}
for run in execution['runs']:
 assert run['exit_code']==0 and not run['timeout']
 assert run['source_before']==run['source_after']==execution['source']
 assert sha(raw/run['log'])==run['log_sha256']
counts={};xml_entries=[];log_summaries={};versions={}
for group in ['full-maven','targeted-refund']:
 log=(raw/(group+'.log')).read_text(errors='replace')
 assert '[INFO] BUILD SUCCESS' in log
 log_summaries[group]={'build_success':True,'pg_blocking_observations':len(re.findall(r'QA02 cancelled-paid[^\n]*pg_blocking_pids=verified',log)),
  'warnings':sum(bool(re.search(r'\bWARN(?:ING)?\b',line)) for line in log.splitlines()),'closed_connection_lines':log.count('Failed to validate connection'),'connection_refused_lines':log.count('Connection refused'),'forced_fork_lines':log.count('Surefire is going to kill self fork JVM')}
 assert log_summaries[group]['pg_blocking_observations']==execution[group]['cancelled_paid_pg_blocking_observations']==15
 actual={}
 for name,folder in [('unit','surefire-reports'),('postgresql','failsafe-reports')]:
  suites=execution[group][name]['reports'];assert len(suites)==execution[group][name]['classes']
  stats={'tests':0,'failures':0,'errors':0,'skipped':0,'flakes':0,'classes':len(suites)}
  for row in suites:
   p=raw/group/folder/('TEST-'+row['suite']+'.xml');assert sha(p)==row['sha256']
   elem=ET.parse(p).getroot();cases=list(elem.findall('testcase'))
   assert len(cases)==int(elem.attrib['tests'])==row['tests']
   for key in ['tests','failures','errors','skipped']:
    assert int(elem.attrib[key])==row[key];stats[key]+=row[key]
   assert all(row[key]==0 for key in ['failures','errors','skipped'])
   assert not any(list(elem.iter(tag)) for tag in ['flakyFailure','flakyError','rerunFailure','rerunError','failure','error','skipped'])
   selected={p.attrib['name']:p.attrib.get('value','') for p in elem.findall('./properties/property') if p.attrib['name'] in ['java.version','java.home','java.vendor','basedir','user.dir']}
   assert selected['java.version'].startswith('21.')
   for key,value in selected.items():versions.setdefault(key,set()).add(value)
   xml_entries.append({'group':group,'suite':row['suite'],'raw_sha256':sha(p),'tests':row['tests'],'testcases':[c.attrib['name'] for c in cases]})
  assert stats['tests']==execution[group][name]['tests'];actual[name]=stats
  assert {p.name for p in (raw/group/folder).glob('TEST-*.xml')}=={'TEST-'+s['suite']+'.xml' for s in suites}
 summary=raw/group/'failsafe-reports/failsafe-summary.xml';assert sha(summary)==execution[group]['summary_sha256']
 e=ET.parse(summary).getroot();assert e.attrib['timeout']=='false' and int(e.findtext('completed'))==actual['postgresql']['tests']
 assert all(int(e.findtext(k))==0 for k in ['errors','failures','skipped','flakes'])
 counts[group]=actual
assert counts['full-maven']['unit']['tests']+counts['full-maven']['postgresql']['tests']==execution['full-maven']['java_total']
node=(raw/'node-all.log').read_text();assert '# tests 33' in node and '# pass 33' in node and '# fail 0' in node
tool=(raw/'qa-tooling.log').read_text();assert re.search(r'Ran 17 tests',tool) and tool.rstrip().endswith('OK')
result={'verified_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'source':SOURCE,'git_tree':TREE,'source_clean':True,'status':'PASS','actual_process_exits_verified':True,'source_guards_before_after_verified':True,'encrypted_archive_sha256':execution['raw_archive']['encrypted_sha256'],'decrypted_archive_sha256':sha(archive),'raw_log_digests_recomputed':len(execution['runs']),'raw_xml_digests_recomputed':len(xml_entries),'counts':counts,'versions_from_xml':{k:sorted(v) for k,v in versions.items()},'log_diagnostics':log_summaries,'node_tests':33,'tooling_tests':17,'full_and_targeted_overlap_not_added':True,'manual_ops':'NOT RUN','raw_plaintext_published':False}
(ART/'raw-verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
(ART/'sanitized-testcase-index.json').write_text(json.dumps(xml_entries,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
