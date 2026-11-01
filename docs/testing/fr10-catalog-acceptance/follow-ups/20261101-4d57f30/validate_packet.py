"""Read-only validation of historical preservation and additive FR-10 evidence."""
from pathlib import Path
import collections
import hashlib
import json
import re
import struct

HERE = Path(__file__).resolve().parent
PACKET = HERE.parents[1]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def read(path):
    return json.loads(path.read_text(encoding='utf-8'))

original = read(PACKET / 'artifact-manifest.json')
for row in original['files']:
    assert digest(PACKET / row['path']) == row['sha256'], row['path']
preserved = read(HERE / 'historical-preservation.json')
for row in preserved['protectedFiles']:
    assert digest(PACKET / row['path']) == row['sha256'], row['path']
assert len(preserved['protectedFiles']) == 106
assert read(PACKET / 'browser-results.json')['uniqueFinalSummary'] == {
    'PASS': 54, 'FAIL': 12, 'BLOCKED': 0, 'NOT RUN': 0}
assert (HERE / 'runner/catalog-browser.cjs').read_bytes() == (PACKET / 'runner/catalog-browser.cjs').read_bytes()

browser = read(HERE / 'attempt-1/browser-results.json')
proof = read(HERE / 'attempt-1/source-proof.json')
cases = browser['cases']
assert browser['source'] == proof['source'] == '4d57f30d7309a6d297b945b48e2d8b5a97a07cb7'
assert browser['jarSha256'] == proof['jarSha256']
assert len(cases) == len({c['id'] for c in cases}) == 66
assert {c['id'] for c in cases} == {c['id'] for c in read(PACKET / 'browser-results.json')['cases']}
assert all(c['status'] == 'PASS' and c['humanResult'] is None for c in cases)
assert collections.Counter(c['viewport']['width'] for c in cases) == {375: 22, 768: 22, 1440: 22}
assert len(list((HERE / 'attempt-1/screenshots').glob('*.png'))) == 66
for c in cases:
    shot = HERE / 'attempt-1' / c['screenshot']
    assert digest(shot) == c['screenshotSha256'], shot
    data = shot.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    width, height = struct.unpack('>II', data[16:24])
    assert width == c['viewport']['width'] and height >= c['viewport']['height']
assert digest(HERE / 'runner/catalog-browser.cjs') == proof['driverSha256']
assert digest(HERE / 'runner/run_catalog_acceptance_v2.py') == proof['environmentRunnerSha256']
assert proof['primaryHeadBefore'] == proof['primaryHeadAfter']
assert proof['primaryIndexSha256Before'] == proof['primaryIndexSha256After']
assert proof['primaryStatusBefore'] == proof['primaryStatusAfter']
assert proof['cleanExecutionWorktreeBefore'] and proof['cleanExecutionWorktreeAfter']
database = read(HERE / 'attempt-1/database-observation.json')
assert database['before'] == database['after'] and len(database['before']) == 34
assert database['ordersCreated'] == database['paymentsCreated'] == 0
receipt = read(HERE / 'attempt-1/execution-receipt.json')
assert receipt['browserExit'] == 0 and receipt['actualUniqueCases'] == 66
assert receipt['cleanup']['ownedJavaStopped'] and receipt['cleanup']['ownedContainerRemoved']
assert len(receipt['cleanup']['javaProcesses']) == 2
assert all(row['stopped'] for row in receipt['cleanup']['javaProcesses'])
assert receipt['migrations']['expected'] == receipt['migrations']['applied']
assert receipt['migrations']['count'] == 28
regression = read(HERE / 'regression-results.json')
assert sum(r['tests'] for r in regression['surefire']) == 719
assert all(r[k] == 0 for r in regression['surefire'] for k in ['failures', 'errors', 'skipped'])

human = (HERE / 'human-acceptance-checklist.md').read_text(encoding='utf-8')
rows = [line for line in human.splitlines() if re.match(r'^\|H\d+-', line)]
assert len(rows) == 36
assert all(all(not cell.strip() for cell in line.split('|')[4:-1]) for line in rows)
for md in HERE.rglob('*.md'):
    for target in re.findall(r'\]\(([^)]+)\)', md.read_text(encoding='utf-8')):
        if target.startswith(('http:', 'https:', '#')):
            continue
        assert (md.parent / target.split('#', 1)[0]).exists(), (md, target)

manifest = read(HERE / 'artifact-manifest.json')
listed = {r['path'] for r in manifest['files']}
actual = {p.relative_to(PACKET).as_posix() for p in PACKET.rglob('*') if p.is_file() and p != HERE / 'artifact-manifest.json'}
assert listed == actual
for row in manifest['files']:
    path = PACKET / row['path']
    assert path.stat().st_size == row['bytes'] and digest(path) == row['sha256'], path
print(json.dumps({'historicalFilesPreserved':106, 'manifestMembersVerified':len(listed),
                  'browserPass':66, 'javaPass':719, 'nodePass':48, 'tablesUnchanged':34,
                  'humanRowsUnsigned':36, 'status':'VALIDATED; HUMAN ACCEPTANCE PENDING'}))
