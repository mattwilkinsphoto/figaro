"""GVM-09 evidence plot and read-back-verified archive, not optimization code."""
import argparse,hashlib,json,os,subprocess,time,zipfile
from pathlib import Path
from gvm_study_io import grant,save,sha,text_file

def plot(summary,output):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import numpy as np
    data=json.loads(summary.read_text());pairs=data['pairs'];x=np.arange(len(pairs))
    fig,axes=plt.subplots(2,1,figsize=(12,7),layout='constrained')
    for shift,arm,color in ((-.18,'baseline','#637080'),(.18,'joint','#167c9a')):
        bars=axes[0].bar(x+shift,[p[arm+'Work']/1e6 for p in pairs],.36,label=arm,color=color)
        for bar,p in zip(bars,pairs):
            if not p[arm+'Complete']:bar.set_hatch('///')
    axes[0].set_ylabel('Recorded work (million units)');axes[0].legend()
    axes[0].set_title('All paired cases; hatched bars are incomplete workflows')
    differences=[p['scoreDifference'] if p['scoreDifference'] is not None else np.nan for p in pairs]
    axes[1].bar(x,differences,color='#167c9a');axes[1].axhline(-.02,color='#b33333',ls='--',label='Descriptive margin')
    axes[1].axhline(0,color='grey',lw=.7);axes[1].set_ylabel('Joint minus baseline\ntest score (nats/point)');axes[1].legend()
    labels=[f'd{p["d"]} {p["family"]}\nK={p["k"]}' for p in pairs]
    for ax in axes:
        ax.set_xticks(x,labels,rotation=65,ha='right',fontsize=7);ax.grid(axis='y',alpha=.2);ax.set_axisbelow(True)
    fig.suptitle('Joint GVM optimization feasibility — reused development cases, not confirmation')
    with output.open('xb') as stream:grant(output);fig.savefig(stream,format='png',dpi=150)
    plt.close(fig)

def archive(root,output):
    cfg=json.loads(Path('tools/gvm_joint_protocol_v1.json').read_text())
    summary=json.loads(Path('tools/gvm_joint_summary_v1.json').read_text())
    assert summary['audit']['status']=='passed' and summary['fits']==40
    for p,h in cfg['sourceSha256'].items():assert sha(p)==h,p
    assert sha(root/'screen/results.json')==summary['resultsSha256']
    names=set(cfg['sourceSha256'])
    names.update(str(p).replace('\\','/') for p in Path('tools').glob('*gvm_joint*') if p.is_file())
    names.update(str(p).replace('\\','/') for p in Path('docs').glob('GVM_JOINT*') if p.is_file())
    names.update(['ROADMAP.md','WISHLIST.md','docs/RESEARCH_REFERENCES.md'])
    files={'repo/'+p:Path(p) for p in names}
    for p in root.rglob('*'):
        rel=p.relative_to(root)
        if p.is_file() and not {'mpl','__pycache__'}.intersection(rel.parts) and p!=output and not p.name.startswith(('archive-build','archive-verification','MANIFEST','EVIDENCE_README')):
            files['repo/.cache/gvm-joint-v1/'+rel.as_posix()]=p
    old=Path('.cache/gvm-weight-restart-v1')
    files['repo/'+(old/'confirmation/results.json').as_posix()]=old/'confirmation/results.json'
    geometry=json.loads(Path('tools/gvm_joint_geometry_v1.json').read_text())
    assert sha(old/'confirmation/results.json')==geometry['previousResultsSha256']
    for name,h in cfg['inputSha256'].items():
        path=old/'confirmation'/name;assert sha(path)==h
        files['repo/'+path.as_posix()]=path
    for name in ('setup-01.log','setup-01.command.json'):files['dependency-setup/'+name]=old/name
    if os.environ.get('FIGARO_ACCESS_HELPER'):
        path=Path(os.environ['FIGARO_ACCESS_HELPER']);files['private/'+path.name]=path
    readme=f'''# Joint optimization feasibility evidence

Complete: 20 paired development cases / 40 fits. Audit: passed.
Decision: {summary['decision']}. No promotion, merge, release or runtime change.

## Evidence map

- repo/docs/GVM_JOINT_PROTOCOL.md pins scope, method, budgets and original gates.
- repo/docs/GVM_JOINT_STATUS.md interprets results and limitations.
- repo/tools/gvm_joint_protocol_v1.json hashes frozen sources and all ten inputs.
- repo/tools/gvm_joint_summary_v1.json records every pair, independent errors,
  conditioning indicators and gates. Geometry JSON records the old-model Hessian
  screen; these are coordinate-dependent diagnostics, not identifiability proofs.
- repo/.cache/gvm-joint-v1/screen holds exact NPZ data, every result, traces,
  trial/constraint counters, ledgers and original statuses. Data are declared
  synthetic cases reused for development, not new independent confirmation.
- *.log/*.command.json preserve actual output, errors, argv, exit status,
  environment and timestamps. *.sources.zip preserve source revisions per run.
  The first docs-prefit log records missing reference navigation; docs-prefit-02
  records its repair. No numerical criterion/source changed to fix that failure.
- environment.json/log records Python/packages/BLAS/thread settings. Existing
  dependencies were reused read-only; original dependency setup logs are included.
  Installed package code is not redistributed.
- comparison.png includes all pairs, not a success-only timing comparison.
- MANIFEST.json contains each archived filename, size and SHA-256.

## Rerun

Extract into an isolated workspace; run from repo. Use pinned packages in
environment.json, set OPENBLAS_NUM_THREADS=1 and OMP_NUM_THREADS=1. Set MPLCONFIGDIR
to a fresh scratch directory. On Windows set FIGARO_ACCESS_HELPER to the approved
private helper after adapting its path scope with authorization; recorded local
paths are not portable configuration. Give created outputs required user access.

1. python -B tools/test_gvm_joint.py
2. python -B tools/gvm_joint_geometry.py .cache/gvm-weight-restart-v1/confirmation NEW_GEOMETRY.json
3. Create a new empty screen directory, then:
   python -B tools/gvm_joint_study.py run NEW_SCREEN --protocol tools/gvm_joint_protocol_v1.json
4. python -B tools/gvm_joint_audit.py NEW_SCREEN NEW_SUMMARY.json --protocol tools/gvm_joint_protocol_v1.json
5. python -B tools/gvm_joint_evidence.py plot NEW_SUMMARY.json NEW_PLOT.png

Do not overwrite existing evidence or regenerate the frozen protocol. Exact
original command arguments are in command JSON. Work units count data passes,
not dense linear algebra FLOPs. Timings are advisory. The screen cannot establish
population reliability, a global optimum or superiority to Gaussian mixtures.
'''
    path=root/'EVIDENCE_README.md';text_file(path,readme);files['README.md']=path
    manifest=dict(createdUnix=time.time(),head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        files=[dict(path=name,bytes=p.stat().st_size,sha256=sha(p)) for name,p in sorted(files.items())])
    path=root/'MANIFEST.json';save(path,manifest)
    with output.open('xb') as stream:
        grant(output)
        with zipfile.ZipFile(stream,'w',zipfile.ZIP_DEFLATED) as z:
            for name,p in sorted(files.items()):z.write(p,name)
            z.write(path,'MANIFEST.json')
    with zipfile.ZipFile(output) as z:
        assert z.testzip() is None
        for item in manifest['files']:assert hashlib.sha256(z.read(item['path'])).hexdigest()==item['sha256'],item['path']
    verification=dict(status='passed',entries=len(files),bytes=output.stat().st_size,sha256=sha(output))
    save(root/'archive-verification.json',verification);print(json.dumps(verification,indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['plot','archive']);p.add_argument('source',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();(plot if a.mode=='plot' else archive)(a.source,a.output)
