"""Create and read-back verify a private, reproducible GVM-05 evidence archive."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import zipfile
from gvm_study_io import grant, save, sha, text_file


def package(evidence, destination):
    root=Path.cwd();summary=json.loads((root/'tools/gvm_dimension_summary_v1.json').read_text())
    audit=json.loads((evidence/'independent-audit.json').read_text())
    config=json.loads((root/'tools/gvm_dimension_protocol_v1.json').read_text())
    assert summary['completeGrid'] and audit['status']=='passed'
    assert summary['resultsSha256']==sha(evidence/'confirmation/confirmation-results.json')
    source_names=set(config['sourceSha256']) | {
        'tools/gvm_dimension_protocol_v1.json','tools/gvm_dimension_summary_v1.json',
        'tools/gvm_dimension_audit.py','tools/gvm_dimension_plot.py','tools/gvm_dimension_archive.py',
        'docs/GVM_HIGH_DIMENSIONAL_STATUS.md','docs/GVM_HIGH_DIMENSIONAL_FITTING_RESEARCH.md'}
    files={('repo/'+name):root/name for name in source_names}
    for path in evidence.rglob('*'):
        if path.is_file() and path.suffix!='.zip' and 'mpl' not in path.relative_to(evidence).parts:
            if path.name.startswith(('archive-build','archive-verification','evidence-manifest')):
                continue
            files['repo/.cache/gvm-dimensional-v1/'+path.relative_to(evidence).as_posix()]=path
    # Include the actual compiler/runtime JARs used by the successful consumer check.
    for command_name in ('kernel-compile-02.command.json','kernel-compatibility-02.command.json','compile-05.command.json'):
        command=json.loads((evidence/command_name).read_text())['args']
        for arg in command:
            for item in arg.split(';'):
                if item.endswith('.jar'):
                    path=Path(item);path=path if path.is_absolute() else root/path
                    assert path.is_file(),path
                    name='deps/'+path.name
                    if name in files:assert sha(files[name])==sha(path),name
                    files[name]=path
    readme=f'''# Higher-dimensional GVM evidence

Completion: full frozen grid of {summary['fits']} fits; decision {summary['decision']}.
Independent numerical audit: {audit['highPrecisionPoints']} high-precision points
across every finite fitted model; {audit['inputHashesVerified']} input hashes verified.
This is test-only synthetic density-fitting research, not application-data validation.
Released stopping/defaults and all production sources were unchanged.

## Map conclusions to evidence

- `repo/tools/gvm_dimension_summary_v1.json`: all-outcome status counts, paired
  intervals, numerical representation metrics, cost observations and gate decision.
- `repo/.cache/gvm-dimensional-v1/confirmation/confirmation-results.json`: all
  768 fitted models, statuses/traces/counters and independent evaluation results.
- The same directory contains original generated train/validation/test NPZ files,
  transformed training text, per-case results and actual Java output/command records.
- `independent-audit.json` in the evidence directory: all-model numerical audit.
- `comparison.png`: plot of the saved summary; failures remain in the status panel.
- `stage1-*`, `stage2-*`, `development-*`, `kernel-*` logs and command JSON files:
  actual captured output, errors, timestamps and exits. The first development run
  and first kernel compile failed; these are retained, not reported as passed.
- `revision-01/`: pre-repair sources. The original partial `development.json`
  is retained as a failed serialization artifact, not a valid summary.
- `repo/tools/gvm_dimension_protocol_v1.json`: exact frozen source hashes,
  seeds, dimensions, constraints, budgets and acceptance criteria.
- `MANIFEST.json`: SHA-256, filename and size for every archived input/artifact.

## Reproduce

Extract to a new workspace; do not overwrite old evidence. The archive's `repo/`
is the working directory for scripts. Retain exact source bytes (including LF line
endings), because source-hash checks intentionally detect newline conversion.
Python versions used: {json.dumps(config['versions'],sort_keys=True)};
mpmath 1.3.0; Java 17.0.4; Scala compiler/library 3.9.0; Commons Math 3.6.1.
Create an isolated environment with these versions. Numerical dependencies are
NumPy, SciPy, mpmath and psutil; Matplotlib is needed only to redraw the figure.
Set OPENBLAS_NUM_THREADS=1 and OMP_NUM_THREADS=1 for the recorded execution policy.

On Windows, set FIGARO_ACCESS_HELPER to an approved local PowerShell helper that
grants/verifies the user's required explicit access on each created output. The
recorded helper's private path is machine-specific; do not weaken access safeguards.
On other operating systems, the output helper performs no Windows ACL operation.

1. Run `python -B tools/test_gvm_dimension_controls.py`.
2. Compile `tools/GvmDimensionalFit.java` with javac, using
   `../deps/commons-math3-3.6.1.jar`, into a new classes directory.
3. Run `tools/gvm_dimension_qualification.py qualify NEW_OUTPUT --classes CLASSES
   --jar ../deps/commons-math3-3.6.1.jar`, then its develop/adverse modes in fresh
   output directories. Actual original commands are in the command JSON files.
4. Compile/run `tools/GvmKernelCompatibility.scala` with the included Scala 3.9.0
   compiler and runtime JARs. Reuse the retained kernel fixture and scalar-control
   inputs; adapt only dependency/output paths from kernel-compile-02 and
   kernel-compatibility-02 command records. Put standalone Scala libraries before
   the assembled JAR, as in the successful command. This uses Figaro 7.4.0 unchanged.
5. Run `python -B tools/gvm_dimension_study.py run NEW_CONFIRMATION --protocol
   tools/gvm_dimension_protocol_v1.json --classes CLASSES --jar
   ../deps/commons-math3-3.6.1.jar`. Use the existing frozen protocol, not freeze mode.
6. Run study `summarize NEW_SUMMARY --protocol tools/gvm_dimension_protocol_v1.json
   --results NEW_CONFIRMATION/confirmation-results.json`, then run
   `gvm_dimension_audit.py RESULTS PROTOCOL NEW_AUDIT` and
   `gvm_dimension_plot.py NEW_SUMMARY NEW_PLOT`.

The run records wall-clock costs rather than guaranteeing them across machines.
Seed replay assumes matching RNG/library versions. Source/parameter changes require
a new protocol and fresh confirmation seeds, not replacement of these results.
No external dataset or GPL implementation source is incorporated. Included JARs
retain their own license/notices. Paths and original logs are private evidence,
not intended for publication with the library.
'''
    readme_path=evidence/'EVIDENCE_README.md';text_file(readme_path,readme)
    files['README.md']=readme_path
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
