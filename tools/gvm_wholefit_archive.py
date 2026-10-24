"""Archive exact research evidence; read back and verify every manifest entry."""
import argparse,hashlib,json,os,subprocess,time,zipfile
from pathlib import Path
from gvm_study_io import grant,save,sha,text_file


def package(evidence,output):
    root=Path.cwd();cfg=json.loads(Path('tools/gvm_wholefit_protocol_v1.json').read_text())
    summary=json.loads(Path('tools/gvm_wholefit_summary_v1.json').read_text())
    assert summary['audit']['status']=='passed' and summary['fits']==560
    assert summary['resultsSha256']==sha(evidence/'confirmation/results.json')
    for path,digest in cfg['sourceSha256'].items():assert sha(path)==digest,path
    names=set(cfg['sourceSha256'])
    names.update(str(p).replace('\\','/') for p in Path('tools').glob('gvm_wholefit*') if p.is_file())
    names.update(['docs/GVM_WHOLEFIT_STATUS.md','docs/RESEARCH_REFERENCES.md','ROADMAP.md','WISHLIST.md'])
    files={'repo/'+name:root/name for name in names}
    for path in evidence.rglob('*'):
        relative=path.relative_to(evidence)
        if (path.is_file() and path.suffix!='.zip' and 'mpl' not in relative.parts
            and not path.name.startswith(('archive-build','archive-verification','evidence-manifest'))):
            files['repo/.cache/gvm-wholefit-v1/'+relative.as_posix()]=path
    if os.environ.get('FIGARO_ACCESS_HELPER'):
        helper=Path(os.environ['FIGARO_ACCESS_HELPER']);files['private/'+helper.name]=helper
    readme=f'''# Whole-fit GVM convergence and complexity evidence

Completion: 560 frozen confirmation fits; independent numerical audit passed.
Decision: {summary['decision']}. Production code, stopping, defaults and released
versions are unchanged. No public fitter, merge or release is authorized.

## Map from conclusions to evidence

- repo/docs/GVM_WHOLEFIT_STATUS.md: conclusions, counterevidence and limitations.
- repo/docs/GVM_WHOLEFIT_STUDY.md and repo/tools/gvm_wholefit_protocol_v1.json:
  frozen requirements, exact source hashes, parameters, seeds and decision gates.
- repo/tools/gvm_wholefit_summary_v1.json: every cell, paired score bound,
  validation-only model selection and independent audit maxima.
- repo/tools/gvm_wholefit_diagnostics_v1.json: explicitly descriptive breakdowns;
  these do not replace frozen gates or relabel exhausted budgets as success.
- repo/.cache/gvm-wholefit-v1/confirmation/results.json: all 560 fitted models,
  starts, accepted likelihood traces, gradient traces, budgets and scores.
- The same directory has the exact 80 generated input NPZ files, each containing
  raw training/validation/test observations and training-only transform. All are
  synthetic controls, not substituted application data. SHA-256 hashes are in
  results.json and MANIFEST.json. Per-fit JSON files preserve incremental output.
- development/results.json: 20 distinct-seed K=3 development fits. Initial
  controls-01 predecessor is named math-controls-01.log; authoritative frozen
  controls are controls-final.log (nine tests). The initial eight-test source
  revision was not separately saved; its actual log is retained, not reconstructed.
  Pre-freeze edits added zero-concentration/nonfinite-budget controls; confirmation
  source bytes are exactly frozen and no held-out repair/retuning was performed.
- *.log and *.command.json contain actual process output/errors, commands,
  timestamps and exit statuses. environment.json/log records versions/backend.
- comparison.png visualizes saved results, including unsuccessful outcomes.
- MANIFEST.json records exact archived names, sizes and SHA-256 digests.

## Reproduction

Extract in a new isolated directory; do not overwrite previous runs. Work in
repo/. Preserve LF source bytes, since the protocol intentionally detects changes.
Recorded core versions: {json.dumps(cfg['versions'],sort_keys=True)}.
Other versions/backend details are in environment.json/log. No R or external
dataset is required. Numerical Python dependencies must be installed separately;
their code is not vendored. Set OPENBLAS_NUM_THREADS=1 and OMP_NUM_THREADS=1.
Use an isolated MPLCONFIGDIR for plotting. On Windows, set FIGARO_ACCESS_HELPER
to an approved helper granting and verifying required explicit user access.
Private original command paths are execution evidence, not portable settings.
Create output directories before running; outputs use exclusive creation.

1. python -B tools/test_gvm_wholefit.py
2. python -B tools/gvm_wholefit_study.py development NEW_DEVELOPMENT
3. python -B tools/gvm_wholefit_study.py run NEW_CONFIRMATION --protocol
   tools/gvm_wholefit_protocol_v1.json
4. python -B tools/gvm_wholefit_audit.py NEW_CONFIRMATION
   tools/gvm_wholefit_protocol_v1.json NEW_SUMMARY.json
5. python -B tools/gvm_wholefit_diagnostics.py NEW_CONFIRMATION/results.json
   NEW_SUMMARY.json NEW_DIAGNOSTICS.json
6. python -B tools/gvm_wholefit_plot.py NEW_SUMMARY.json NEW_DIAGNOSTICS.json NEW_PLOT.png

Commands wrap above for readability; exact original argv are in captured command
JSON. Do not run freeze mode to reproduce this protocol. New methods/criteria need
a separately frozen revision and fresh seeds. Work units are logical row visits,
not FLOPs or equivalent to previous phase-only budgets. Timings are descriptive:
brief documentation/tooling checks ran concurrently with part of confirmation.
The independent audit checks numbers and inventory, not global optimality,
true-component recovery, arbitrary dimensions or inference accuracy guarantees.
'''
    readme_path=evidence/'EVIDENCE_README.md';text_file(readme_path,readme);files['README.md']=readme_path
    manifest=dict(createdUnix=time.time(),head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        files=[dict(path=name,bytes=path.stat().st_size,sha256=sha(path)) for name,path in sorted(files.items())])
    manifest_path=evidence/'evidence-manifest.json';save(manifest_path,manifest)
    with output.open('xb') as stream:
        grant(output)
        with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
            for name,path in sorted(files.items()):archive.write(path,name)
            archive.write(manifest_path,'MANIFEST.json')
    with zipfile.ZipFile(output) as archive:
        assert archive.testzip() is None
        for entry in manifest['files']:
            digest=hashlib.sha256()
            with archive.open(entry['path']) as stream:
                for block in iter(lambda:stream.read(1024*1024),b''):digest.update(block)
            assert digest.hexdigest()==entry['sha256'],entry['path']
    verified=dict(archive=output.name,bytes=output.stat().st_size,sha256=sha(output),
        verifiedEntries=len(manifest['files'])+1,status='read-back verified',completedUnix=time.time())
    save(evidence/'archive-verification.json',verified);print(json.dumps(verified,indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('evidence',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();package(a.evidence,a.output)
