import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
EXPECTED = 'c593352492184da2a9ca46ebea4a55b802ac6416'
JDK = r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()

def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()

def reports(directory):
    rows = []
    for path in sorted(directory.glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        rows.append(dict(file=path.name, sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                         name=suite.get('name'), tests=int(suite.get('tests', 0)),
                         failures=int(suite.get('failures', 0)), errors=int(suite.get('errors', 0)),
                         skipped=int(suite.get('skipped', 0)), time=suite.get('time')))
    return dict(totals={key: sum(r[key] for r in rows) for key in ('tests','failures','errors','skipped')}, suites=rows)

mode = sys.argv[1]
if git('rev-parse', 'HEAD') != EXPECTED:
    raise SystemExit('HEAD changed; refusing to test a different source')
env = os.environ.copy()
env['JAVA_HOME'] = JDK
env['PATH'] = JDK + r'\bin;' + env['PATH']
commands = {
    'docker': [['docker', 'version'], ['docker', 'info'], ['docker', 'ps']],
    'focused': [[JDK + r'\bin\java.exe', '-version'], ['cmd', '/d', '/c', 'mvnw.cmd', '-B', '--no-transfer-progress', '-o', '-Ppostgres-it', '-Dit.test=PromotionBrowserFormsIT,ProductPromotionIT,VendorVoucherIT', 'clean', 'verify']],
    'full': [[JDK + r'\bin\java.exe', '-version'], ['cmd', '/d', '/c', 'mvnw.cmd', '-B', '--no-transfer-progress', '-o', '-Ppostgres-it', 'clean', 'verify']],
    'node': [['node', '--test', 'src/test/js/notifications.test.cjs', 'src/test/js/buyer-cancellation.test.cjs']],
}
record = dict(mode=mode, started_utc=now(), source_sha=EXPECTED, source_tree=git('rev-parse','HEAD^{tree}'),
              status_before=git('status','--porcelain=v1'), java_home=JDK,
              docker_environment={k:env.get(k) for k in ('DOCKER_HOST','DOCKER_CONTEXT','DOCKER_API_VERSION','TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE')}, commands=[])
for index, command in enumerate(commands[mode]):
    log = OUT / f'{mode}-{index}.log'
    item = dict(command=command, started_utc=now(), log=log.name, timed_out=False)
    print(json.dumps(dict(starting=command, log=str(log))), flush=True)
    with log.open('wb') as stream:
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
        try:
            item['exit_code'] = process.wait(timeout=1800)
        except subprocess.TimeoutExpired:
            item['timed_out'] = True
            subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'], capture_output=True)
            item['exit_code'] = process.wait(timeout=30)
    item.update(finished_utc=now(), log_sha256=hashlib.sha256(log.read_bytes()).hexdigest())
    record['commands'].append(item)
    print(json.dumps(item), flush=True)
    if item['exit_code'] != 0:
        break
if mode in ('focused', 'full'):
    for kind in ('surefire','failsafe'):
        source = ROOT / 'target' / (kind + '-reports')
        dest = OUT / (mode + '-' + kind + '-reports')
        if source.exists():
            shutil.copytree(source,dest,dirs_exist_ok=False)
            record[kind] = reports(dest)
record.update(finished_utc=now(), source_sha_after=git('rev-parse','HEAD'), status_after=git('status','--porcelain=v1'))
(OUT / (mode + '.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in record.items() if k not in ('surefire','failsafe')},indent=2),flush=True)
for kind in ('surefire','failsafe'):
    if kind in record:
        print(kind, json.dumps(record[kind]['totals']),flush=True)
sys.exit(0 if all(c['exit_code']==0 for c in record['commands']) else 1)
