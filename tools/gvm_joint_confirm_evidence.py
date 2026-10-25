"""Plot and archive confirmation evidence; no optimizer or gate changes."""
import argparse,hashlib,json,os,subprocess,time,zipfile
from pathlib import Path
from gvm_study_io import grant,save,sha,text_file


def plot(summary,output):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import numpy as np
    result=json.loads(summary.read_text());cells=result['cells'];x=np.arange(len(cells))
    fig,axes=plt.subplots(3,1,figsize=(13,10),layout='constrained')
    for shift,arm,color in ((-.18,'baseline','#637080'),(.18,'joint','#167c9a')):
        axes[0].bar(x+shift,[p[arm+'Complete'] for p in cells],.36,label=arm,color=color)
        values=[sum(p[arm+'Wall'] for p in result['pairs'] if all(p[k]==cell[k] for k in ('d','family','k'))) for cell in cells]
        axes[1].bar(x+shift,values,.36,color=color)
    axes[0].set_ylabel('Complete seed cases / 8');axes[0].set_ylim(0,9);axes[0].legend()
    axes[1].set_ylabel('Sum of median elapsed seconds\nAll eight cases, including failures')
    means=[p['score']['mean'] if p['score'] else np.nan for p in cells]
    lowers=[p['score']['lower'] if p['score'] else np.nan for p in cells]
    axes[2].errorbar(x,means,yerr=[np.asarray(means)-lowers,np.zeros(len(x))],fmt='o',color='#167c9a',capsize=3,label='Mean and one-sided simultaneous lower bound')
    axes[2].axhline(-.02,color='#b33333',ls='--',label='Noninferiority margin');axes[2].axhline(0,color='gray',lw=.7)
    axes[2].set_ylabel('Joint minus baseline test score\n(nats / point)');axes[2].legend(fontsize=8)
    labels=[f'd{p["d"]} {p["family"]}\nK={p["k"]}' for p in cells]
    for ax in axes:
        ax.set_xticks(x,labels,rotation=60,ha='right',fontsize=7);ax.grid(axis='y',alpha=.2);ax.set_axisbelow(True)
    fig.suptitle('Fresh joint GVM confirmation — all 160 paired cases retained')
    with output.open('xb') as stream:grant(output);fig.savefig(stream,format='png',dpi=140)
    plt.close(fig)


def archive(root,output):
    cfg=json.loads(Path('tools/gvm_joint_confirm_protocol_v1.json').read_text())
    summary=json.loads(Path('tools/gvm_joint_confirm_summary_v1.json').read_text())
    assert summary['fits']==cfg['expectedFits']
    for p,h in cfg['sourceSha256'].items():assert sha(p)==h,p
    assert sha(root/'confirmation/results.json')==summary['resultsSha256']
    names=set(cfg['sourceSha256'])|set(cfg['priorProtocolSha256'])
    names.update(p.as_posix() for p in Path('tools').glob('*gvm_joint_confirm*') if p.is_file())
    names.update(p.as_posix() for p in Path('docs').glob('GVM_JOINT_CONFIRM*') if p.is_file())
    names.update(['ROADMAP.md','WISHLIST.md','docs/RESEARCH_REFERENCES.md','docs/GVM_JOINT_STATUS.md'])
    files={'repo/'+p:Path(p) for p in names}
    for p in root.rglob('*'):
        rel=p.relative_to(root)
        if p.is_file() and not {'mpl','__pycache__'}.intersection(rel.parts) and p!=output and not p.name.startswith(('archive-build','archive-verification','MANIFEST','EVIDENCE_README')):
            files['repo/.cache/gvm-joint-confirm-v1/'+rel.as_posix()]=p
    for name in ('setup-01.log','setup-01.command.json'):
        p=Path('.cache/gvm-weight-restart-v1')/name;files['dependency-setup/'+name]=p
    if os.environ.get('FIGARO_ACCESS_HELPER'):
        p=Path(os.environ['FIGARO_ACCESS_HELPER']);files['private/'+p.name]=p
    readme=f'''# Fresh joint optimization confirmation evidence

Inventory: {summary['fits']} measured fits, 160 pairs, 80 synthetic datasets.
Audit: {summary['audit']['status']}. Decision: {summary['decision']}.
No promotion, runtime change, merge or release.

## Evidence map

- repo/docs/GVM_JOINT_CONFIRMATION_PROTOCOL.md and the corresponding tools JSON
  freeze seeds, source bytes, versions, budgets, timing boundary and gates.
- repo/docs/GVM_JOINT_CONFIRMATION_STATUS.md interprets every gate and limitation.
- repo/tools/gvm_joint_confirm_summary_v1.json contains all pairs/cells, residual
  diagnostics, simultaneous score/cost bounds, failures and independent errors.
- repo/.cache/gvm-joint-confirm-v1/confirmation contains all exact NPZ inputs,
  input SHA-256 map, 960 individual results, full result inventory and warmup.
  Repetition 0 is canonical for scores; all three repetitions count for completion
  and timing. No failed case is removed. Raw inputs are synthetic, not user data.
- Original *.log/*.command.json capture actual output/errors, argv, timestamps,
  exit codes and environment. *.sources.zip preserve command-time revisions.
  Initial pre-freeze logs retain a syntax error and a two-seed rehearsal audit
  failure: exponentiating a large log bound amplified floating-point absolute
  error. The audit now checks the log bound and relative exponent error, with a
  regression test. Preliminary protocol bytes remain in preflight-protocol-01.
  No fresh data existed at these corrections; frozen code is never retuned.
- environment.json/log records package/BLAS/clock details. Dependencies were
  reused read-only; original setup logs are included, not dependency source code.
- comparison.png displays every cell. MANIFEST.json lists input/source/output
  filenames, sizes and SHA-256; every archive entry is verified after writing.

## Rerun from repo in an isolated workspace

Use exact versions from environment.json, OPENBLAS_NUM_THREADS=1 and
OMP_NUM_THREADS=1. On Windows set FIGARO_ACCESS_HELPER to the approved access helper,
adapting its path scope with authorization. Recorded local paths are not portable
configuration. Set MPLCONFIGDIR to a new scratch directory. Preserve old outputs.

1. python -B tools/test_gvm_joint_confirm.py
2. Create a new empty study directory with required access, then run
   python -B tools/gvm_joint_confirm_study.py run NEW_STUDY
3. python -B tools/gvm_joint_confirm_audit.py NEW_STUDY NEW_SUMMARY.json
4. python -B tools/gvm_joint_confirm_evidence.py plot NEW_SUMMARY.json NEW_PLOT.png

Do not regenerate the protocol or overwrite original evidence. The two arms are
unchanged from feasibility. Total measured fit time includes training preparation
and the complete fitter return path, not data generation, held-out scoring or disk
output. Full study elapsed includes those overheads. Cost comparisons use the sum
of per-case medians; actual three-repetition totals are recorded separately.
Eight seeds, not 24 repetitions, determine inference. This is single-host evidence,
not an arbitrary-model reliability proof, global optimum or Scala benchmark.
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
