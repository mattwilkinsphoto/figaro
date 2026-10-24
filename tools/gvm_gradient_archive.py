"""Package exact GVM-06 evidence and verify every archived entry by SHA-256."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import zipfile
from gvm_study_io import grant,save,sha,text_file


def package(evidence,destination):
    root=Path.cwd();config=json.loads(Path('tools/gvm_gradient_protocol_v1.json').read_text())
    summary=json.loads(Path('tools/gvm_gradient_summary_v1.json').read_text())
    audit=json.loads((evidence/'independent-audit.json').read_text())
    assert summary['completeGrid'] and audit['status']=='passed'
    assert summary['resultsSha256']==audit['resultsSha256']==sha(evidence/'confirmation/results.json')
    names=set(config['sourceSha256']) | {'tools/gvm_gradient_protocol_v1.json','tools/gvm_gradient_summary_v1.json',
        'tools/gvm_gradient_audit.py','tools/gvm_gradient_plot.py','tools/gvm_gradient_archive.py',
        'tools/gvm_gradient_diagnostics.py','tools/gvm_gradient_diagnostics_v1.json',
        'docs/GVM_GRADIENT_STATUS.md','docs/RESEARCH_REFERENCES.md'}
    files={'repo/'+name:root/name for name in names}
    for path in evidence.rglob('*'):
        if path.is_file() and path.suffix!='.zip' and 'mpl' not in path.relative_to(evidence).parts:
            if path.name.startswith(('archive-build','archive-verification','evidence-manifest')):continue
            files['repo/.cache/gvm-gradient-v1/'+path.relative_to(evidence).as_posix()]=path
    jar=root/'.cache/expensive-likelihood-v1/commons-math3-3.6.1.jar'
    files['deps/'+jar.name]=jar
    if os.environ.get('FIGARO_ACCESS_HELPER'):
        helper=Path(os.environ['FIGARO_ACCESS_HELPER'])
        files['private/'+helper.name]=helper
    readme=f'''# Analytic-gradient GVM study evidence

Completion: {summary['fits']} frozen confirmation fits, independent audit passed.
Decision: {summary['decision']}. No production code, released stopping estimator,
defaults, shared pilot or release version changed. All data are synthetic controls.

## Evidence map

- repo/docs/GVM_GRADIENT_STATUS.md: conclusions and limitations.
- repo/docs/GVM_GRADIENT_STUDY.md and repo/tools/gvm_gradient_protocol_v1.json:
  frozen design, exact source hashes, seeds, budgets and acceptance criteria.
- repo/tools/gvm_gradient_summary_v1.json: statuses and paired score intervals.
- repo/.cache/gvm-gradient-v1/confirmation/results.json: every model, restart,
  likelihood trace, inner solve result, work counter and retained-fit score.
- The same directory holds original train/test NPZ files (including transforms
  and Java-generated third-start keys), transformed training text, actual Java
  output and command records. Input hashes are in results.json.
- independent-audit.json: 60-digit full inverse/determinant density audit,
  score/envelope checks and strict inventory checks. Final-responsibility phase
  gradients are diagnostic, not proof of global optimality.
- development/development.json and controls-01.log: development/adverse outcomes
  and independent gradient/solver/refusal controls before freezing.
- All *.log and *.command.json files are actual captured execution output, errors,
  commands, timestamps and exit status; not reconstructed prose logs.
- comparison.png: completion, work and score plots from saved results.
- MANIFEST.json: exact names, sizes and SHA-256 hashes for every archived file.

## Rerun in an isolated directory

Extract without overwriting any previous results. Work in repo/. Preserve source
bytes and LF line endings: source-hash checks intentionally detect changes.
Python environment: {json.dumps(config['versions'],sort_keys=True)};
mpmath 1.3.0, psutil 7.2.2, Matplotlib 3.11.1. Java 17.0.4 and Commons Math 3.6.1.
The dependency JAR retains its own license/notices. Numerical Python packages must
be installed separately at the recorded versions. No external dataset is needed.
Set OPENBLAS_NUM_THREADS=1 and OMP_NUM_THREADS=1. Set MPLCONFIGDIR to an isolated
cache when plotting. On Windows set FIGARO_ACCESS_HELPER to an approved helper
that grants/verifies required explicit user access; do not weaken access policies.
Original private command paths are evidence, not portable configuration.

1. python -B tools/test_gvm_gradient_fit.py
2. javac -cp ../deps/commons-math3-3.6.1.jar -d NEW_CLASSES
   tools/GvmDimensionalFit.java tools/GvmGradientKeys.java
3. python -B tools/gvm_gradient_study.py develop NEW_DEVELOPMENT --classes
   NEW_CLASSES --jar ../deps/commons-math3-3.6.1.jar
4. python -B tools/gvm_gradient_study.py run NEW_CONFIRMATION --protocol
   tools/gvm_gradient_protocol_v1.json --classes NEW_CLASSES --jar
   ../deps/commons-math3-3.6.1.jar
5. python -B tools/gvm_gradient_study.py summarize NEW_SUMMARY --results
   NEW_CONFIRMATION/results.json --protocol tools/gvm_gradient_protocol_v1.json
6. python -B tools/gvm_gradient_audit.py NEW_CONFIRMATION/results.json
   tools/gvm_gradient_protocol_v1.json NEW_AUDIT
7. python -B tools/gvm_gradient_plot.py NEW_CONFIRMATION/results.json NEW_SUMMARY NEW_PLOT

Create output directories before running. Commands above wrap for readability;
the captured command JSON files contain exact executed argument arrays. Do not
run freeze mode to reproduce this protocol. Revisions require a separate protocol,
fresh seeds and preserved originals. Timings depend on hardware/backend/scheduling;
phase-point counts do not measure all gradient arithmetic. Retained unqualified
fits are not silently discarded or upgraded to convergence.
'''
    readme_path=evidence/'EVIDENCE_README.md';text_file(readme_path,readme);files['README.md']=readme_path
    manifest=dict(createdUnix=time.time(),head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        files=[dict(path=name,bytes=path.stat().st_size,sha256=sha(path)) for name,path in sorted(files.items())])
    manifest_path=evidence/'evidence-manifest.json';save(manifest_path,manifest)
    with destination.open('xb') as stream:
        grant(destination)
        with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
            for name,path in sorted(files.items()):archive.write(path,name)
            archive.write(manifest_path,'MANIFEST.json')
    with zipfile.ZipFile(destination) as archive:
        assert archive.testzip() is None
        for entry in manifest['files']:
            digest=hashlib.sha256()
            with archive.open(entry['path']) as stream:
                for block in iter(lambda:stream.read(1024*1024),b''):digest.update(block)
            assert digest.hexdigest()==entry['sha256'],entry['path']
    verified=dict(archive=destination.name,bytes=destination.stat().st_size,sha256=sha(destination),
        verifiedEntries=len(manifest['files'])+1,status='read-back verified',completedUnix=time.time())
    save(evidence/'archive-verification.json',verified);print(json.dumps(verified,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('evidence',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args();package(args.evidence,args.output)
