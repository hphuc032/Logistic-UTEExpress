import json, os, pathlib, sys
sys.path.insert(0,str(pathlib.Path(__file__).parent))
import importlib.util
spec=importlib.util.spec_from_file_location('pr68_runner',pathlib.Path(__file__).with_name('pr68-run-regression.py'))
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
obj=json.loads((runner.ART/'execution.json').read_text(encoding='utf-8'))
env=os.environ.copy();env['PYTHONDONTWRITEBYTECODE']='1'
for name,cmd in [
    ('node-inprocess',['node','--experimental-test-isolation=none','--test','--test-reporter=tap','src/test/js/notifications.test.cjs','src/test/js/buyer-cancellation.test.cjs']),
    ('tooling-inherited-temp',[sys.executable,'-B',str(pathlib.Path(__file__).with_name('pr68-tooling-temp-adapter.py'))]),
]:
    rec=runner.run(name,cmd,env,120);obj['runs'].append(rec);runner.save(obj)
    print(json.dumps({'name':name,'exit_code':rec['exit_code'],'start':rec['start_utc'],'end':rec['end_utc'],'log_sha256':rec['log_sha256']}),flush=True)
obj['ended_utc']=runner.now();obj['final_source']=runner.identity();runner.save(obj)
