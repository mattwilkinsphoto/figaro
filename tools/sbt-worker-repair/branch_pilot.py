"""Two frozen fresh Linux passes for the branch pilot or named CI test gates."""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import xml.etree.ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validation_support import directory,download,sha,write
from pilot_support import IDENTITY,check_identity,run
from linux_validation import LAUNCHER_SHA,LAUNCHER_URL,REL
ROOT=Path(__file__).resolve().parents[2]
SCRIPTS=Path(__file__).resolve().parent

def check_pins(actual,expected):
    if actual!=expected:raise ValueError('Compiler inputs differ from reviewed Linux baseline')

def check_log(text,jar):
    text=re.sub(r'\x1b\[[0-9;]*m','',text)
    check_identity(text,jar)
    markers=['Total number of tests run: 131','Suites: completed 11, aborted 0',
      'Tests: succeeded 131, failed 0, canceled 0, ignored 0, pending 0','All tests passed.']
    if any(text.count(m)!=1 for m in markers):raise ValueError('Incomplete or duplicate pilot outcomes')
    if not re.search(r'compiling [1-9][0-9]* Scala sources to .*figaro/test-classes',text):
        raise ValueError('No fresh Figaro test compilation; cached-only execution is insufficient')
    if any(x in text for x in ['Promise already completed','*** FAILED ***','Exception in thread','[error]']):
        raise ValueError('Unexpected test or worker error')

def check_reports(reports,inventory):
    expected={s['suite']:s['tests'] for s in inventory['suites']}
    if len(reports)!=len(expected) or Counter(r.attrib.get('name') for r in reports)!=Counter(expected.keys()):
        raise ValueError('Missing, extra or duplicate suite reports')
    for report in reports:
        cases=report.findall('testcase');names=[c.attrib['name'] for c in cases]
        if Counter(names)!=Counter(expected[report.attrib['name']]):raise ValueError('Test identity drift')
        if len(names)!=len(set(names)):raise ValueError('Duplicate tests')
        if int(report.attrib.get('tests','-1'))!=len(cases):raise ValueError('Test count drift')
        if any(int(report.attrib.get(k,'0')) for k in ['errors','failures','skipped']):raise ValueError('Nonpassing suite')
        if any(c.tag in ['failure','error','skipped'] for case in cases for c in case):raise ValueError('Nonpassing test')

def collect(out):
    """Preserve bounded evidence on failure without uploading homes or dependency caches."""
    evidence=directory(out/'evidence')
    patterns=['*.json','*.log','*.jar','sources/**/*','prepared/*.*','prepared/classes/**/*',
      'matrix/*.json','matrix/*.log','integration/*.json','integration/*.log',
      'failure-controls/*.json','failure-controls/*.log','pass-*/*.json','pass-*/*.log','pass-*/reports/*.xml',
      'pass-*/repo/target/out/jvm/scala-3.9.0/figaro/test-reports/*.xml']
    paths={p for pattern in patterns for p in out.glob(pattern) if p.is_file()}
    for p in sorted(paths):
        target=evidence/p.relative_to(out);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
    write(evidence/'evidence-manifest.json',{str(p.relative_to(evidence)):sha(p) for p in evidence.rglob('*') if p.is_file()})

def check_mode(branch,ci_gate,environ):
    if ci_gate:
        if environ.get('GITHUB_ACTIONS')!='true' or environ.get('GITHUB_WORKFLOW')!='Java 17, Scala 3, and sbt 2 modernization':
            raise ValueError('Named CI gate requires the approved GitHub Actions workflow')
        return 'named direct-launcher CI gates; other invocations remain stock; no merge or release'
    if branch!='codex/guided-fixed-budget':raise ValueError('Pilot allowed only on approved branch')
    return 'branch-only; stock CI retained; no promotion'

def execute(out,ci_gate=False):
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    branch=subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()
    scope=check_mode(branch,ci_gate,os.environ)
    inventory=json.loads((SCRIPTS/'pilot-inventory.json').read_text())
    assert len(inventory['suites'])==11 and sum(len(s['tests']) for s in inventory['suites'])==131
    for s in inventory['suites']:
        if sha(ROOT/s['source'])!=s['sourceSha256']:raise ValueError('Unreviewed test source drift: '+s['source'])
    source=directory(out/'sources');shutil.copytree(SCRIPTS,source/'sbt-worker-repair')
    shutil.copyfile(ROOT/'tools/validation_support.py',source/'validation_support.py')
    write(out/'protocol.json',dict(commit=commit,branch=branch,platform=platform.platform(),python=sys.version,
      runnerImage=os.environ.get('ImageOS'),imageVersion=os.environ.get('ImageVersion'),
      sourceHashes={str(p.relative_to(source)):sha(p) for p in source.rglob('*') if p.is_file()},
      passes=2,inventory=inventory,controls='original 320 cases + original 3 fixtures; additive 280 cases + framework error',
      acceptance='all gates required; no retries or fallback; no server-mode or release promotion',scope=scope))
    run(['java','-version'],out,'java-version',timeout=30)
    run(['javac','-version'],out,'javac-version',timeout=30)
    launcher=out/'sbt-launch-2.0.8.jar';download(LAUNCHER_URL,launcher,LAUNCHER_SHA)
    home=directory(out/'home');temp=directory(out/'tmp');global_base=directory(out/'global')
    stock=directory(out/'stock');cache=directory(out/'coursier')
    os.environ['COURSIER_CACHE']=str(cache);os.environ['XDG_RUNTIME_DIR']=str(temp)
    def sbt(boot,h,t,g,actions):
        return ['java','-Xms512m','-Xmx6G','-Xss6M','-XX:ReservedCodeCacheSize=256M','-Dfile.encoding=UTF-8',
          '-Dsbt.supershell=false','-Dsbt.log.noformat=true','-Dsbt.server.autostart=false',
          f'-Dsbt.boot.directory={boot}',f'-Dsbt.global.base={g}',f'-Dsbt.global.localcache={actions}',
          f'-Duser.home={h}',f'-Djava.io.tmpdir={t}','-jar',launcher]
    bootstrap=directory(out/'bootstrap');directory(bootstrap/'project')
    write(bootstrap/'project/build.properties','sbt.version=2.0.8\n');write(bootstrap/'build.sbt','scalaVersion := "3.9.0"\n')
    run(sbt(stock,home,temp,global_base,out/'bootstrap-actions')+['show sbtVersion'],out,'bootstrap',cwd=bootstrap)
    inputs=list((stock/'scala-3.8.4/lib').glob('*.jar'))+list((stock/REL.parent).glob('*.jar'))
    actual={str(p.relative_to(stock)):sha(p) for p in inputs}
    check_pins(actual,json.loads((SCRIPTS/'compiler-inputs.json').read_text())['files'])
    write(out/'compiler-inputs.json',actual)
    stock_hashes={str(p.relative_to(stock)):sha(p) for p in stock.rglob('*.jar')}
    write(out/'stock-inputs.json',stock_hashes);shutil.copyfile(stock/REL,out/'actions.stock.jar')
    run([sys.executable,'-B',SCRIPTS/'prepare.py','--boot',stock,'--output',out/'prepared'],out,'prepare')
    repaired=out/'prepared/boot'
    run([sys.executable,'-B',SCRIPTS/'verify.py','--stock-boot',stock,'--repaired-boot',repaired,
         '--output',out/'matrix'],out,'matrix',timeout=180)
    run([sys.executable,'-B',SCRIPTS/'integration.py','--boot',repaired,'--launcher',launcher,
         '--user-home',home,'--output',out/'integration','--verify-identity'],out,'integration',timeout=1200)
    run([sys.executable,'-B',SCRIPTS/'failure_controls.py','--stock-boot',stock,'--repaired-boot',repaired,
         '--launcher',launcher,'--user-home',home,'--output',out/'failure-controls'],out,'failure-controls',timeout=900)
    archive=out/'checkout.tar'
    run(['git','archive','--format=tar','--output',archive,commit],out,'source-archive',cwd=ROOT,timeout=60)
    results=[]
    for i in [1,2]:
        case=directory(out/f'pass-{i}');checkout=directory(case/'repo')
        with tarfile.open(archive) as tar:tar.extractall(checkout,filter='data')
        h=directory(case/'home');t=directory(case/'tmp');g=directory(case/'global');c=directory(case/'coursier')
        boot=case/'boot';shutil.copytree(repaired,boot)
        if (checkout/'target').exists():raise ValueError('Checkout includes stale target output')
        os.environ['COURSIER_CACHE']=str(c);os.environ['XDG_RUNTIME_DIR']=str(t)
        task='figaro / Test / testOnly '+' '.join(s['suite'] for s in inventory['suites'])+' -- -l com.cra.figaro.test.Performance'
        text=run(sbt(boot,h,t,g,case/'actions')+[IDENTITY,'clean',task],case,'figaro',cwd=checkout,timeout=1200)
        check_log(text,boot/REL)
        report_root=checkout/'target/out/jvm/scala-3.9.0/figaro/test-reports'
        report_files=sorted(report_root.glob('TEST-*.xml'))
        reports=directory(case/'reports')
        for path in report_files:shutil.copyfile(path,reports/path.name)
        check_reports([ET.parse(p).getroot() for p in report_files],inventory)
        if sha(boot/REL)!=sha(repaired/REL):raise ValueError('Repaired boot changed')
        write(case/'result.json',dict(passNumber=i,tests=131,suites=11,identityAndReportsVerified=True,
          repairedJarSha256=sha(boot/REL),reports={p.name:sha(p) for p in reports.glob('*.xml')}))
        results.append(json.loads((case/'result.json').read_text()))
    if stock_hashes!={str(p.relative_to(stock)):sha(p) for p in stock.rglob('*.jar')}:raise ValueError('Stock boot changed')
    run(['git','diff','--exit-code'],out,'source-integrity',cwd=ROOT,timeout=30)
    write(out/'result.json',dict(status='all pilot gates passed',commit=commit,passes=results,
      stockUnchanged=True,scope=scope,
      limitation='First success still wins; no exhaustive protocol or server-mode validation'))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--ci-gate',action='store_true',help='Adopt only the frozen named test gates in the approved workflow')
    a=p.parse_args()
    if platform.system()!='Linux':raise RuntimeError('Linux pilot only')
    out=directory(a.output.resolve())
    try:execute(out,ci_gate=a.ci_gate)
    finally:collect(out)

if __name__=='__main__':main()
