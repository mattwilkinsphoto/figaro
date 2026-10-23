"""Isolated stock-version assessment; no production adoption or upstream submission.

Requires the captured upstream inputs and stock boot trees described in the output
protocol. Original Java controls and fixture sources are not edited. Deliberately
forced exceptions are diagnostic findings, not proof a race is fixed.
"""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET
from urllib.parse import urlparse
from urllib.request import url2pathname
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validation_support import access,directory,sha,write
from pilot_support import IDENTITY,check_identity,check_failure,run
from branch_pilot import check_log,check_reports
ROOT=Path(__file__).resolve().parents[2]
SCRIPTS=Path(__file__).resolve().parent


def copy_file(source,target):
    target=Path(target)
    if not target.parent.exists():
        target.parent.mkdir(parents=True);access(target.parent,recursive=True)
    with target.open('xb') as f:
        access(target);f.write(Path(source).read_bytes())


def copy_fixture(source,target,version):
    directory(target)
    for p in Path(source).rglob('*'):
        if p.is_file() and p.relative_to(source).as_posix()!='project/build.properties':
            copy_file(p,target/p.relative_to(source))
    if not (target/'project').exists():directory(target/'project')
    write(target/'project/build.properties','sbt.version='+version+'\n')
    access(target,recursive=True)


def matrix_rows(text,jar,notification):
    urls=[s[7:] for s in text.splitlines() if s.startswith('BINARY ')]
    if len(urls)!=1 or Path(url2pathname(urlparse(urls[0]).path)).resolve()!=jar.resolve():
        raise ValueError('Incorrect controlled-class code source')
    rows=[json.loads(s) for s in text.splitlines() if s.startswith('{')]
    modes=(['fork-before-success','fork-before-exit','fork-during-exit','duplicate-fork',
        'success-before-fork','wrong-outer-id','wrong-inner-id'] if notification else
        ['response-first','exit-first','interleaved-success','error-first','interleaved-error',
         'nonzero-first','duplicate-exit','wrong-id'])
    if [r['mode'] for r in rows]!=modes or any(r['trials']!=20 for r in rows):
        raise ValueError('Incomplete original control matrix')
    # The original Java checks assert all winning outcomes. Do not redefine those checks.
    for r in rows:
        expected=20 if r['mode'] in ['interleaved-success','interleaved-error','fork-during-exit'] else 0
        if r['duplicateExceptions']!=expected:raise ValueError('Stock behavior changed; review, do not silently relabel')
    return rows


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--phase',choices=['controls','figaro'],required=True)
    parser.add_argument('--runner',type=Path,help='Official Linux runner: use --server --batch instead of direct Java')
    args=parser.parse_args();out=args.output.resolve();inputs=json.loads((out/'inputs.json').read_text())
    if args.runner and (sys.platform!='linux' or not args.runner.is_file()):
        raise ValueError('Foreground runner assessment requires an existing official Linux runner')
    inventory=json.loads((SCRIPTS/'pilot-inventory.json').read_text())
    for n,h in inputs['protected'].items():
        if sha(ROOT/n)!=h:raise ValueError('Protected input changed: '+n)
    evidence=directory(out/args.phase)
    snapshot=directory(evidence/'sources')
    for p in SCRIPTS.rglob('*'):
        if p.is_file() and '__pycache__' not in p.parts:copy_file(p,snapshot/'sbt-worker-repair'/p.relative_to(SCRIPTS))
    copy_file(ROOT/'tools/validation_support.py',snapshot/'validation_support.py')
    access(snapshot,recursive=True)
    # Check committed bytes, avoiding Windows checkout line-ending artifacts.
    for s in inventory['suites']:
        import hashlib
        data=subprocess.check_output(['git','show',inputs['head']+':'+s['source']],cwd=ROOT)
        if hashlib.sha256(data).hexdigest()!=s['sourceSha256']:raise ValueError('Frozen suite source changed')
    write(evidence/'protocol.json',{'phase':args.phase,'source':inputs['head'],
        'versions':['2.0.8','2.0.11'],'platform':sys.platform,'inventory':inventory,
        'invocation':'official --server --batch' if args.runner else 'direct Java launcher',
        'sources':{str(p.relative_to(snapshot)):sha(p) for p in snapshot.rglob('*') if p.is_file()},
        'controls':'Original 8x20 + 7x20 per stock binary; forced duplicates remain findings, not fixes',
        'fixtures':'Original 100 pass, 100+1 assertion, exit 17, framework error; all expected failures must surface',
        'slowListener':'Unmodified upstream 40 tests, 50ms/event, last test fails; require exit 1 and all 40 results on 2.0.11; baseline 2.0.8 exit 0 is a defect reproduction',
        'figaro':'Two fresh 131-test/11-suite stock 2.0.11 passes; exact identities and no cache-only acceptance',
        'scope':'Windows direct-launcher only when executed on Windows; no production toolchain/default change',
        'retries':False,'failurePolicy':'Record failed independent cases, continue predeclared remaining cases; never change acceptance'})
    results=[]

    def sbt(version,case,boot=None):
        base=out/version
        for name in ['home','tmp','global','actions','coursier','ivy']:
            if not (case/name).exists():directory(case/name)
        os.environ['COURSIER_CACHE']=str(case/'coursier');os.environ['XDG_RUNTIME_DIR']=str(out/'ipc')
        os.environ['TEMP']=os.environ['TMP']=str(case/'tmp')
        command=['java','-Xms512m','-Xmx6G','-Xss6M','-XX:ReservedCodeCacheSize=256M','-Dfile.encoding=UTF-8',
            '-Dsbt.supershell=false','-Dsbt.log.noformat=true','-Dsbt.server.autostart=false',
            f'-Dsbt.boot.directory={boot or base/"boot"}',f'-Dsbt.global.base={case/"global"}',
            f'-Dsbt.global.localcache={case/"actions"}',f'-Duser.home={case/"home"}',
            f'-Djava.io.tmpdir={case/"tmp"}',f'-Dsbt.ivy.home={case/"ivy"}',
            '-jar',base/('sbt-launch-'+version+'.jar')]
        if args.runner:
            return [args.runner,'--server','--batch','--sbt-jar',command[-1]]+[
                '-J'+str(v) for v in command[1:-2] if v!='-Dsbt.server.autostart=false']
        return command

    def case_run(name,action):
        try:
            detail=action();results.append({'case':name,'completed':True,'detail':detail})
            print(name+': verified',flush=True)
        except Exception as e:
            results.append({'case':name,'completed':False,'error':repr(e)})
            print('STATUS ALERT: '+name+' failed: '+repr(e),flush=True)
        write(evidence/(name+'-result.json'),results[-1])

    if args.phase=='controls':
        for version in ['2.0.8','2.0.11']:
            base=out/version;boot=base/'boot'
            jars=list(boot.rglob('actions_3-'+version+'.jar'))
            if len(jars)!=1:raise ValueError('Ambiguous stock actions binary')
            jar=jars[0];boot_scala=jar.parents[3]
            case=directory(evidence/version);classes=directory(case/'classes')
            cp=os.pathsep.join([str(classes),str(boot_scala/'lib/*'),str(jar.parent/'*')])
            try:run(['javac','-cp',cp,'-d',classes,SCRIPTS/'WorkerCompletionCheck.java',SCRIPTS/'ForkErrorCheck.java'],case,'compile',timeout=120)
            finally:access(classes,recursive=True)
            for main in ['WorkerCompletionCheck','ForkErrorCheck']:
                def control(main=main):
                    text=run(['java','-cp',cp,main,'stock'],case,main,timeout=120)
                    return {'rows':matrix_rows(text,jar,main=='ForkErrorCheck'),'jarSha256':sha(jar),
                        'meaning':'Retained stock behavior; forced exceptions indicate residual React non-atomic completion'}
                case_run(version+'-'+main,control)
            tasks=[('passing','testOnly WorkerEvents -- -z event-',0,['Tests: succeeded 100, failed 0','All tests passed']),
                ('assertion','testOnly WorkerEvents',1,['Tests: succeeded 100, failed 1','SENTINEL_ASSERTION_FAILURE']),
                ('exit','testOnly WorkerExit',1,['Forked test process exited with code 17']),
                ('framework','testOnly FrameworkFailure',1,['SENTINEL_FRAMEWORK_FORK_ERROR','ForkTestMain$ForkError']),
                ('slow','test',0 if version=='2.0.8' else 1,[])]
            for name,task,code,markers in tasks:
                def fixture(name=name,task=task,code=code,markers=markers):
                    f=directory(case/name);repo=f/'repo'
                    if name=='slow':
                        directory(repo);directory(repo/'project')
                        write(repo/'project/build.properties','sbt.version='+version+'\n')
                        copy_file(out/'upstream/slow-build.sbt',repo/'build.sbt')
                        copy_file(out/'upstream/slow-ManyTests.scala',repo/'src/test/scala/ManyTests.scala')
                    else:copy_fixture(SCRIPTS/('framework-fixture' if name=='framework' else 'fixtures'),repo,version)
                    try:text=run(sbt(version,f)+[IDENTITY,task],f,'execution',cwd=repo,expected=code,timeout=900)
                    finally:access(f,recursive=True);access(boot,recursive=True);access(out/'ipc',recursive=True)
                    check_identity(text,jar)
                    if any(m not in text for m in markers) or 'Promise already completed' in text:raise ValueError('Incomplete outcome or unforced worker exception')
                    if name=='framework':check_failure(text)
                    reports=[ET.parse(p).getroot() for p in repo.rglob('TEST-*.xml')]
                    counts={r.attrib.get('name'):{k:r.attrib.get(k) for k in ['tests','failures','errors','skipped']} for r in reports}
                    if name=='slow' and version=='2.0.11':
                        report=[r for r in reports if r.attrib.get('name')=='ManyTests']
                        if len(report)!=1:raise ValueError('Missing slow-listener JUnit report')
                        r=report[0];names=[c.attrib['name'] for c in r.findall('testcase')]
                        if Counter(names)!=Counter('test '+str(i) for i in range(1,41)):raise ValueError('Missing/duplicate slow-listener test identities')
                        if r.attrib.get('failures')!='1' or r.attrib.get('errors')!='0':raise ValueError('Lost final failure')
                    return {'exit':code,'reports':counts,'jarSha256':sha(jar),'oldFalseGreenReproduced':name=='slow' and version=='2.0.8'}
                case_run(version+'-'+name,fixture)
    else:
        archive=evidence/'checkout.zip'
        with archive.open('xb'):pass
        access(archive);run(['git','archive','--format=zip','--output',archive,inputs['head']],evidence,'checkout',cwd=ROOT,timeout=90)
        for number in [1,2]:
            def figaro(number=number):
                case=directory(evidence/('pass-'+str(number)));repo=directory(case/'repo')
                with zipfile.ZipFile(archive) as z:z.extractall(repo)
                access(repo,recursive=True)
                # Only the isolated checkout's toolchain selector changes; sources stay frozen.
                prop=repo/'project/build.properties'
                prop.write_text('sbt.version=2.0.11\n',encoding='utf-8');access(prop)
                boot=case/'boot';shutil.copytree(out/'2.0.11/boot',boot);access(boot,recursive=True)
                jar=next(boot.rglob('actions_3-2.0.11.jar'))
                task='figaro / Test / testOnly '+' '.join(s['suite'] for s in inventory['suites'])+' -- -l com.cra.figaro.test.Performance'
                try:text=run(sbt('2.0.11',case,boot)+[IDENTITY,'clean',task],case,'execution',cwd=repo,timeout=1800)
                finally:access(case,recursive=True);access(out/'ipc',recursive=True)
                # Normalize only path separators for the existing Linux-oriented
                # fresh-compilation regex; identity still resolves the actual path.
                check_log(text.replace('\\','/'),jar)
                reports=list((repo/'target/out/jvm/scala-3.9.0/figaro/test-reports').glob('TEST-*.xml'))
                check_reports([ET.parse(p).getroot() for p in reports],inventory)
                return {'tests':131,'suites':11,'reports':{p.name:sha(p) for p in reports},'jarSha256':sha(jar)}
            case_run('figaro-pass-'+str(number),figaro)
    for version in ['2.0.8','2.0.11']:
        base=out/version
        if json.loads((base/'boot-hashes.json').read_text())!={str(p.relative_to(base/'boot')):sha(p) for p in (base/'boot').rglob('*.jar')}:
            raise ValueError('Stock boot modified')
    write(evidence/'results.json',{'cases':results,'allPredeclaredChecksMet':all(r['completed'] for r in results),
        'stockBootsUnchanged':True,'warning':'Stock matrices meeting expected exceptions is not a fixed-defect claim'})
    if not all(r['completed'] for r in results):sys.exit(1)


if __name__=='__main__':main()
