"""Run unchanged QA tooling tests using inherited permissions in this run's temp root.

Only temporary directory allocation is adapted; assertions and mocks stay unchanged.
No ACL, group, service or system configuration is changed.
"""
import os, pathlib, sys, tempfile, unittest, uuid

root=pathlib.Path(__file__).parent/os.environ.get('PR68_ARTIFACT_DIR','pr68-postsync-artifacts-20261029')/'tooling-temp'
root.mkdir(exist_ok=True)

def inherited_temp(suffix=None,prefix=None,dir=None):
    base=pathlib.Path(dir) if dir else root
    base.mkdir(parents=True,exist_ok=True)
    for _ in range(100):
        candidate=base/((prefix or 'tmp')+uuid.uuid4().hex+(suffix or ''))
        try:
            os.mkdir(candidate)
            return str(candidate)
        except FileExistsError:
            continue
    raise FileExistsError('Could not allocate unique task temporary directory')

tempfile.mkdtemp=inherited_temp
suite=unittest.defaultTestLoader.discover('scripts/qa/tests')
result=unittest.TextTestRunner(verbosity=2).run(suite)
sys.exit(0 if result.wasSuccessful() else 1)
