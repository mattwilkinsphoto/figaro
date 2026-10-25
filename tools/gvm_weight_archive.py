"""Read-back-verified GVM-08 evidence; excludes installed dependency code."""
import argparse,hashlib,json,os,subprocess,time,zipfile
from pathlib import Path
from gvm_study_io import grant,save,sha,text_file

def package(evidence,output):
    root=Path.cwd();cfg=json.loads(Path('tools/gvm_weight_protocol_v1.json').read_text())
    summary=json.loads(Path('tools/gvm_weight_summary_v1.json').read_text())
    assert summary['audit']['status']=='passed' and summary['fits']==1040
    assert summary['resultsSha256']==sha(evidence/'confirmation/results.json')
    for path,digest in cfg['sourceSha256'].items():assert sha(path)==digest,path
    names=set(cfg['sourceSha256'])
    names.update(str(p).replace('\\','/') for p in Path('tools').glob('*gvm_weight*') if p.is_file())
    names.update(str(p).replace('\\','/') for p in Path('docs').glob('GVM_WEIGHT*') if p.is_file())
    names.update(['docs/RESEARCH_REFERENCES.md','ROADMAP.md','WISHLIST.md'])
    files={'repo/'+name:root/name for name in names}
    for path in evidence.rglob('*'):
        rel=path.relative_to(evidence)
        if (path.is_file() and not {'deps','mpl','tmp','__pycache__'}.intersection(rel.parts)
            and path!=output and not path.name.startswith(('archive-build','archive-verification','evidence-manifest','EVIDENCE_README'))):
            files['repo/.cache/gvm-weight-restart-v1/'+rel.as_posix()]=path
    # Exact old inputs needed to reproduce the deliberately development-only screen.
    old=root/'.cache/gvm-wholefit-v1/confirmation'
    files['repo/.cache/gvm-wholefit-v1/confirmation/results.json']=old/'results.json'
    q=json.loads((evidence/'qualification/results.json').read_text())
    assert sha(old/'results.json')==q['previousResultsSha256']
    for name,digest in q['inputSha256'].items():
        assert sha(old/name)==digest
        files['repo/.cache/gvm-wholefit-v1/confirmation/'+name]=old/name
    if os.environ.get('FIGARO_ACCESS_HELPER'):
        helper=Path(os.environ['FIGARO_ACCESS_HELPER']);files['private/'+helper.name]=helper
    readme=f'''# GVM weight and restart evidence

Completion: 40 fixed-shape relevance cases,40 development fits,1040 confirmation
fits. Independent numerical audit passed. Decision: {summary['decision']}.
No runtime/default/stopping changes, merge, release or promotion.

## Conclusions and exact evidence

- repo/docs/GVM_WEIGHT_STATUS.md explains results, counterevidence and limitations.
- repo/docs/GVM_WEIGHT_PROTOCOL.md and repo/tools/gvm_weight_protocol_v1.json pin
  the design, sources, versions, seeds, bounds and original acceptance criteria.
- repo/tools/gvm_weight_summary_v1.json contains independent numerical maxima,
  every primary cell and all30 adjusted score comparisons. The separate diagnostics
  JSON is descriptive and does not replace gates or reclassify incomplete fits.
- repo/.cache/gvm-weight-restart-v1/confirmation contains all80 raw synthetic input
  NPZ files, models, scores, traces and work; results.json records their hashes.
- qualification/ retains all40 old-model fixed-shape cases and exact old inputs.
  The old source results and required subset of NPZ files are also included at their
  original relative path. These cases are development, not independent confirmation.
- development/ retains40 fresh development fits. Per-command *.sources.zip files
  preserve exact source revisions. The initial development outputs precede one
  allocation-label metadata addition; no fitting algebra changed. Auditing infers
  that label from the arm. No confirmation sources were changed after freeze.
- *.command.json/*.log contain actual argv, environment, timestamps, output/errors
  and exit status. environment.json/log and setup-01.log record dependencies and
  BLAS details. Logs are original output, not reconstructed summaries.
- plot.log preserves the initial CLI string/path error; plot-02.log records the
  successful reporting-only repair. Exact old/new snapshots are retained. Frozen
  fitting and numerical audit sources were not modified to repair the plot.
- comparison.png displays every attempt, distinguishing retained stationarity from
  completed search. MANIFEST.json contains SHA-256, byte sizes and archive names.

## Rerun

Extract to a new isolated location. Run from repo, preserving exact frozen source
bytes. Do not run freeze mode or overwrite any evidence directory. Install pinned
versions from environment.json into an isolated environment; installed dependency
code is not bundled. Core versions: {json.dumps(cfg['versions'],sort_keys=True)}.
Set OPENBLAS_NUM_THREADS=1 and OMP_NUM_THREADS=1. Set MPLCONFIGDIR to a separate
scratch directory for plotting. Windows grants require an approved access helper
through FIGARO_ACCESS_HELPER; original private paths are not portable settings.
Create fresh output directories with required user access before execution.

1. python -B -m unittest discover -s tools -p "test_gvm_weight*.py"
2. python -B tools/gvm_weight_qualification.py .cache/gvm-wholefit-v1/confirmation
   NEW_QUALIFICATION NEW_QUALIFICATION_SUMMARY.json
3. python -B tools/gvm_weight_study.py development NEW_DEVELOPMENT
4. python -B tools/gvm_weight_study.py run NEW_CONFIRMATION --protocol
   tools/gvm_weight_protocol_v1.json
5. python -B tools/gvm_weight_audit.py NEW_CONFIRMATION NEW_SUMMARY.json --protocol
   tools/gvm_weight_protocol_v1.json
6. python -B tools/gvm_weight_report.py NEW_CONFIRMATION/results.json NEW_DIAGNOSTICS.json
7. python -B tools/gvm_weight_plot.py NEW_DIAGNOSTICS.json NEW_PLOT.png

Commands wrap for readability; exact original arguments are saved in command JSON.
All data are declared synthetic controls, not substitutes for supplied real data.
The Gaussian control uses a different convergence/work contract. No universal
reliability, best-restart, parameter-identifiability or global-optimum claim follows.
Wall times are advisory; lightweight documentation and monitoring occurred during
confirmation. Logical work is not FLOPs. Qualification timing is unavailable for
the unmodified baseline; no success-only speedup is inferred from that absence.
'''
    readme_path=evidence/'EVIDENCE_README.md';text_file(readme_path,readme);files['README.md']=readme_path
    manifest=dict(createdUnix=time.time(),head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        files=[dict(path=name,bytes=path.stat().st_size,sha256=sha(path)) for name,path in sorted(files.items())])
    path=evidence/'evidence-manifest.json';save(path,manifest)
    with output.open('xb') as stream:
        grant(output)
        with zipfile.ZipFile(stream,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
            for name,file in sorted(files.items()):archive.write(file,name)
            archive.write(path,'MANIFEST.json')
    with zipfile.ZipFile(output) as archive:
        assert archive.testzip() is None
        for entry in manifest['files']:
            digest=hashlib.sha256(archive.read(entry['path'])).hexdigest();assert digest==entry['sha256'],entry['path']
    result=dict(status='passed',entries=len(files),bytes=output.stat().st_size,sha256=sha(output))
    save(evidence/'archive-verification.json',result);print(json.dumps(result,indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('evidence',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();package(a.evidence,a.output)
