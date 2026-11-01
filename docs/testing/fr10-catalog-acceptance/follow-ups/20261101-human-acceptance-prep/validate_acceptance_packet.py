"""Read-only validation of additive human decisions, provenance and preserved evidence."""
from pathlib import Path
import hashlib
import json
import re

HERE = Path(__file__).resolve().parent
PACKET = HERE.parents[1]

def load(path):
    return json.loads(path.read_text(encoding='utf-8'))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

for name in ['artifact-manifest.json', 'follow-ups/20261101-4d57f30/artifact-manifest.json']:
    manifest = load(PACKET / name)
    for row in manifest['files']:
        assert digest(PACKET / row['path']) == row['sha256'], (name, row['path'])
before = load(HERE / 'preservation-before.json')
assert before['count'] == len(before['protectedFiles']) == 188
for path, expected in before['protectedFiles'].items():
    assert digest(PACKET / path) == expected, path
assert load(PACKET / 'browser-results.json')['uniqueFinalSummary'] == {
    'PASS':54, 'FAIL':12, 'BLOCKED':0, 'NOT RUN':0}

matrix = load(HERE / 'human-review-matrix.json')
decisions = load(HERE / 'human-acceptance-decisions.json')
assert len(matrix['items']) == len(decisions['decisions']) == 36
assert len({i['id'] for i in decisions['decisions']}) == 36
assert {i['id'] for i in decisions['decisions']} == {i['id'] for i in matrix['items']}
assert sum(i['technicalReview'] == 'EVIDENCE VERIFIED' for i in matrix['items']) == 27
assert sum(i['technicalReview'] == 'EVIDENCE INSUFFICIENT' for i in matrix['items']) == 9
assert all(i['result']=='PASS' and i['reviewer']=='HP' for i in decisions['decisions'])
assert all(i['signature'] is None and i['individualObservationTimestamp'] is None for i in decisions['decisions'])
assert decisions['reportedBrowser'] == decisions['browserAnswerVerbatim'] == 'brave'
assert all(a['answer']=='Đã tự kiểm tra: H01–H12 đều PASS' for a in decisions['answers'])
assert len(decisions['answers']) == 3
for i in matrix['items']:
    assert (HERE / i['screenshot']).is_file(), i['id']
proof = load(HERE / 'human-session-1/source-proof.json')
assert decisions['testedSource'] == proof['source'] == '4d57f30d7309a6d297b945b48e2d8b5a97a07cb7'
assert decisions['jarSha256'] == proof['jarSha256']
assert proof['browserDriverExecuted'] is False
assert digest(HERE / 'prepare_human_session.py') == proof['environmentRunnerSha256']
assert proof['primaryHeadBefore'] == proof['primaryHeadAfter']
assert proof['primaryIndexSha256Before'] == proof['primaryIndexSha256After']
database = load(HERE / 'human-session-1/database-observation.json')
assert database['before'] == database['after'] and len(database['before']) == 34
assert database['ordersCreated'] == database['paymentsCreated'] == 0
receipt = load(HERE / 'human-session-1/execution-receipt.json')
assert receipt['automatedTestsExecuted'] == 0
assert receipt['cleanup']['ownedJavaStopped'] and receipt['cleanup']['ownedContainerRemoved']
assert len(receipt['cleanup']['javaProcesses']) == 2

for md in HERE.rglob('*.md'):
    for target in re.findall(r'\]\(([^)]+)\)', md.read_text(encoding='utf-8')):
        if target.startswith(('http:', 'https:', '#')):
            continue
        assert (md.parent / target.split('#',1)[0]).exists(), (md, target)
manifest = load(HERE / 'artifact-manifest.json')
actual = {p.relative_to(PACKET).as_posix() for p in PACKET.rglob('*')
          if p.is_file() and p != HERE / 'artifact-manifest.json'}
assert {r['path'] for r in manifest['files']} == actual
for row in manifest['files']:
    assert digest(PACKET / row['path']) == row['sha256'], row['path']
print(json.dumps({'protectedPublishedFiles':188, 'originalHistoricalFiles':106,
                  'previousManifestMembers':187, 'currentManifestMembers':len(actual),
                  'technicalEvidenceVerified':27, 'partialAutomationEvidence':9,
                  'hpReportedPass':36, 'humanSignatureGenerated':False,
                  'humanSessionTablesUnchanged':34, 'status':'VALIDATED; INDEPENDENT REVIEW PENDING'}))
