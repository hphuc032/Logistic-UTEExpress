from pathlib import Path
import json,subprocess,hashlib,datetime,re
R=Path('D:/UTEExpress/.work/doc01-pr76/docs/report/hp')
OUT=R/'evidence/follow-ups/20261101-validation'
SHA='a83c4aa03fb3806bd5aba47f0f9fa5ccf0f0b473'
OLD='6a26370659a2fa7b95c6a1d00d2598820de6bb34'
def blob(ref,path):return subprocess.check_output(['git','show',ref+':'+path],cwd=R)
def digest(b):return hashlib.sha256(b).hexdigest()
def write(name,obj):(OUT/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
functions=json.loads((R/'evidence/functions.json').read_text(encoding='utf8'))
rows=[]
for f in functions:
    row={'function':f['num'],'routes':f['routes'],'files':[],'testReferences':[]}
    for key in ['controller','service']:
        rel=f[key] if f[key].startswith('src/') else 'src/main/java/com/uteexpress/'+f[key]
        b=blob(SHA,rel);anchor=f['anchor'] if key=='service' else None
        assert not anchor or anchor in b.decode(),(rel,anchor)
        row['files'].append({'kind':key,'path':rel,'sha256':digest(b),'unchangedSinceHistorical':b==blob(OLD,rel),'anchor':anchor,'anchorLine':next((i for i,l in enumerate(b.decode().splitlines(),1) if anchor and anchor in l),None)})
    for ref in f['tests'].split(';'):
        ref=ref.strip();name=ref.split(' (')[0]
        if name.startswith('src/'):
            rel=name;present=(R.parents[2]/rel).is_file()
        elif re.fullmatch(r'\w+(IT|Test)',name):
            matches=list((R.parents[2]/'src/test').rglob(name+'.java'));assert len(matches)==1,(name,matches)
            rel=matches[0].relative_to(R.parents[2]).as_posix();present=True
        else:rel=None;present=None
        row['testReferences'].append({'original':ref,'path':rel,'present':present,'execution':'NOT RUN by source audit; narrative/CI references retain historical provenance'})
    rows.append(row)
excerpts=[]
for x in json.loads((R/'evidence/source-excerpts.json').read_text(encoding='utf8')):
    new=blob(SHA,x['path']);old=blob(OLD,x['path']);assert new==old,x['path']
    normalized=old.replace(b'\r\n',b'\n')
    assert x['sha256'] in {digest(old),digest(normalized),digest(normalized.replace(b'\n',b'\r\n'))},x['path']
    excerpts.append({**x,'verifiedAgainst':SHA,'wholeBlobUnchanged':True,'currentBlobSha256':digest(new),'historicalHashByteConvention':'Original file bytes, allowing explicitly verified CRLF checkout representation; Git blob hash recorded separately'})
master=Path('D:/JavaWeb/Project/UTEExpress_Master_Project_Plan.docx')
from docx import Document
plan=Document(master);text='\n'.join(p.text for p in plan.paragraphs)+'\n'+'\n'.join(c.text for t in plan.tables for row in t.rows for c in row.cells)
references=[l for l in text.splitlines() if 'DOC-01' in l or re.search(r'Chương\s+(1|2|4|5|12)\b',l,re.I)]
write('source-validation.json',{'checkedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'acceptedSource':SHA,'historicalSource':OLD,'functions':rows,'excerpts':excerpts,'fullRegressionRerun':False,'masterPlan':{'path':str(master),'sha256':digest(master.read_bytes()),'matchesOriginalHash':digest(master.read_bytes())=='18523e581e6e2b5d2aa29d709aa4d344927e3d3a2ce21983481c4442aab68b7a','relevantRows':references}})
print(json.dumps({'functionsVerified':len(rows),'historicalExcerptBlobsUnchanged':len(excerpts),'masterPlanRelevantRows':len(references)}))
