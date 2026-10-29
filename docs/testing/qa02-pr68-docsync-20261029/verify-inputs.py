import datetime,hashlib,json,pathlib,subprocess

BASE=pathlib.Path(__file__).parent;ROOT=BASE/'pr68-docsync-20261029';ART=BASE/'pr68-docsync-evidence-20261029'
OLD='8705e8a865736387c96007aee7eb813a58dd1911';NEW='0bf7a4a2167c2513ebaf210696aff6ccbd66762b';DEV='fb7dcebd434c6887396881e9b34df1bf12d8250e'
ALLOW=['README.md','docs/MASTER_TASK_BOARD.md','docs/testing/QA-01-evidence-manifest.md','docs/testing/QA-01-findings.md','docs/testing/QA-01-security-matrix.md','docs/testing/QA-01-security-report.md']
def git(*args):return subprocess.check_output(['git','-C',str(ROOT),*args]).decode().strip()
def inventory(ref):
 result={}
 for row in subprocess.check_output(['git','-C',str(ROOT),'ls-tree','-rz','--full-tree',ref]).split(b'\0'):
  if row:
   mode,kind,rest=row.split(b' ',2);sha,path=rest.split(b'\t',1)
   result[path.decode()]={'mode':mode.decode(),'type':kind.decode(),'git_blob':sha.decode()}
 return result
assert git('rev-parse','HEAD')==NEW and not git('status','--porcelain')
assert git('show','-s','--format=%P',NEW)==OLD+' '+DEV
before,after=inventory(OLD),inventory(NEW)
changed=sorted(p for p in before.keys()|after.keys() if before.get(p)!=after.get(p))
assert changed==sorted(ALLOW)
assert all(p.endswith('.md') for p in changed)
stable=[{'path':p,**after[p]} for p in sorted(after) if p not in ALLOW]
assert all(before[r['path']]==after[r['path']] for r in stable)
for row in stable:
 old_bytes=(BASE/'pr68-postsync-20261029'/row['path']).read_bytes()
 new_bytes=(ROOT/row['path']).read_bytes()
 assert old_bytes==new_bytes,row['path']
 row['working_bytes_sha256']=hashlib.sha256(new_bytes).hexdigest()
canonical=json.dumps(stable,sort_keys=True,separators=(',',':')).encode()
groups={}
for p in ['src','src/main','src/test','src/main/resources/db/migration','scripts','scripts/qa','scripts/Test-DatabasePlan.ps1','pom.xml','.mvn','.github','.github/workflows','.gitattributes','.editorconfig','.env.example','mvnw','mvnw.cmd','docs/database-dependencies.json']:
 a,b=git('rev-parse',OLD+':'+p),git('rev-parse',NEW+':'+p);assert a==b
 groups[p]={'old':a,'new':b,'equal':True}
qd=ART/'qd-public-packet';env=json.loads((qd/'environment.json').read_text());ops=json.loads((qd/'operations-evidence.json').read_text());result=json.loads((qd/'independent-results.json').read_text());supp=json.loads((qd/'supplemental-results.json').read_text())
for p,value in env['git_objects'].items():assert groups[p]['old']==groups[p]['new']==value
runner_blob=subprocess.check_output(['git','-C',str(ROOT),'show',NEW+':scripts/qa/verify_operations.py'])
runner_checkout=(ROOT/'scripts/qa/verify_operations.py').read_bytes()
assert runner_checkout.replace(b'\r\n',b'\n')==runner_blob
assert hashlib.sha256(runner_checkout).hexdigest()==ops['runner_script_sha256']
assert result['source_sha']==ops['tested_source_sha']==OLD and result['java_total']==ops['java_total']==2057
assert result['maven_exit']==result['wrapper_exit']==ops['maven_exit_code']==0
assert result['unit']==705 and result['postgresql']==1352 and result['verified_xml_count']==141
assert supp['node']['exit']==supp['tooling']['exit']==0
proof={'verified_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'old_tested_source':OLD,'new_pr_candidate':NEW,'new_develop':DEV,'old_git_tree':git('rev-parse',OLD+'^{tree}'),'new_git_tree':git('rev-parse',NEW+'^{tree}'),'complete_git_tree_equal':False,'merge_parents':[OLD,DEV],'conflicts':[],
 'changed_files':[{'path':p,'before':before.get(p),'after':after.get(p)} for p in changed],
 'all_other_tracked_files_equal':True,'unchanged_file_count':len(stable),'unchanged_inventory_sha256':hashlib.sha256(canonical).hexdigest(),'unchanged_file_inventory':stable,
 'executable_build_test_config_objects':groups,'qd_environment_objects_match':True,'all_other_tracked_working_bytes_equal':True,
 'qa_runner_script_sha256':ops['runner_script_sha256'],'qa_runner_line_endings':{'git_canonical_lf_sha256':hashlib.sha256(runner_blob).hexdigest(),'windows_crlf_checkout_sha256':hashlib.sha256(runner_checkout).hexdigest(),'checkout_matches_qd_recorded_hash':True,'reason':'Unchanged .gitattributes text=auto with core.autocrlf=true; git ls-files --eol reports i/lf w/crlf. No script edit or assertion relaxation.'},
 'runtime_input_audit':'Production/test/resource/migration/script/config/workflow objects unchanged. Test sources and QA tooling contain no README/QA01 status-document reads; Test-DatabasePlan reads unchanged docs/database-dependencies.json. No generated build-info/Git plugin or custom resource inclusion was added.',
 'inheritance':{'source':OLD,'java_unit':705,'postgresql':1352,'java_total':2057,'node':33,'tooling':17,'provenance':'Independent QD run/COMMENTED review and public packet; not TD limited-provenance run','full_postgresql_executed_on_new_commit':False,'raw_qd_artifacts_rehashed_here':False,'public_packet_sha256_verified':8,'new_head_compatibility_signoff_by_qd':'PENDING; old review is not automatically a new-head review'},
 'regression_scope':'Docs-only merge with no conflicts: retain independently executed old-source full PostgreSQL evidence via proved input equality; run standard CI on new HEAD. No new local PostgreSQL/full-suite execution claimed. Reassess if target, executable inputs or merge resolution change.'}
(ART/'input-equality.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:proof[k] for k in ['old_tested_source','new_pr_candidate','old_git_tree','new_git_tree','complete_git_tree_equal','all_other_tracked_files_equal','unchanged_file_count','unchanged_inventory_sha256','qd_environment_objects_match']}))
