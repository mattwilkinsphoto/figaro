"""Branch-only stock sbt assessment: no adoption, publication or upstream submission."""
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import time
import xml.etree.ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validation_support import directory,download,sha,write
from pilot_support import IDENTITY,check_identity,check_failure,run
from branch_pilot import check_log,check_reports
from stock_assessment import copy_fixture,copy_file

ROOT=Path(__file__).resolve().parents[2]
SCRIPTS=Path(__file__).resolve().parent
BASELINE='2f8e6c54d78c9c5906edb4b94bab67f0db6d8bb6'
LAUNCHERS={'2.0.8':'5b4c25e234cfc43cc5103f1d4626cbdd713384bfe9c6402812b9ed3070021026',
 '2.0.11':'2069338b22ed795a3b65615da0858e5b8733fe3f9a3f73b2fcfd559421e11374'}
ACTIONS={'2.0.8':'95498714672cffa9f62d843d8a7d07e212704d5ecfc52c8b71164dedb20126a1',
 '2.0.11':'688505c9b1c30f505275826d5fb8dd14f274bef0e284942e392c75f5dcfc63be'}
DIST_SHA='d8409a244e97c3839f27c4f435f49421196c126a450d30f4cf53522d39e3c379'
RUNNER_SHA='299ff7e18dbfdcfdcd32ba724f22f4033552cbd90cc24e6f77c2380eac0cdbfe'
UPSTREAM={
 'slow-build.sbt':('sbt-app/src/sbt-test/tests/fork-slow-listener/build.sbt','9b1170c66be1399e603a956f1f6e0cbf73880a42175a37904f70fa415b15d9d5'),
 'slow-ManyTests.scala':('sbt-app/src/sbt-test/tests/fork-slow-listener/src/test/scala/ManyTests.scala','21c4c63444dd9dcd06e8fb3b4f08875fcf0cfcfbafb9d55bc461955adbac2f00'),
 'slow-test':('sbt-app/src/sbt-test/tests/fork-slow-listener/test','38bf7313fc875743c1b17950bcc07990c7e766851e4aa420be7bc9d538cc8029'),
 'LICENSE':('LICENSE','08b5cd75c4b20578413ad6878b93e8186a673b12bfda076a2a021a355f90c5a7'),
 'sbt':('sbt',RUNNER_SHA)}
PID='eval println("FIGARO_SERVER_PID=" + ProcessHandle.current().pid())'
CONSUMERS={'FigaroConsumerCheck':'Consumer acceptance passed:',
 'IntegrationConsumerCheck':'Integration APIs verified from artifact',
 'CapabilityConsumerCheck':'Capability APIs verified from artifact',
 'BudgetConsumerCheck':'Budget adviser API verified from artifact',
 'FixedBudgetConsumerCheck':'Fixed-budget public API verified from expected packaged artifact:',
 'GuidedFixedBudgetConsumerCheck':'Guided fixed-budget API verified from expected packaged artifact:'}


def check_pid(text,previous=None):
    values=re.findall(r'^FIGARO_SERVER_PID=(\d+)$',text,re.M)
    if len(values)!=1 or int(values[0])<=1:raise ValueError('Missing/ambiguous server PID')
    value=int(values[0])
    if previous is not None and value!=previous:raise ValueError('Server restarted between commands')
    return value


def check_slow(reports):
    matches=[r for r in reports if r.attrib.get('name')=='ManyTests']
    if len(matches)!=1:raise ValueError('Missing or duplicate upstream suite')
    r=matches[0];cases=r.findall('testcase')
    if Counter(c.attrib['name'] for c in cases)!=Counter('test '+str(i) for i in range(1,41)):
        raise ValueError('Incomplete upstream event identities')
    failed=[c.attrib['name'] for c in cases if c.find('failure') is not None]
    if failed!=['test 40']:
        raise ValueError('Final upstream failure was not retained')
    if any(int(r.attrib.get(k,-1))!=v for k,v in [('tests',40),('failures',1),('errors',0),('skipped',0)]):
        raise ValueError('Incorrect upstream outcome counts')


def execute(out,mode):
    branch=subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()
    if branch!='codex/stock-sbt-2-0-11-validation':raise ValueError('Branch-only validation')
    protected={p:sha(ROOT/p) for p in ['build.sbt','project/build.properties','.github/workflows/ci.yml',
      '.github/workflows/sbt-repair-linux.yml','tools/sbt-worker-repair/prepare.py']}
    inventory=json.loads((SCRIPTS/'pilot-inventory.json').read_text())
    for s in inventory['suites']:
        raw=subprocess.check_output(['git','show',BASELINE+':'+s['source']],cwd=ROOT)
        if hashlib.sha256(raw).hexdigest()!=s['sourceSha256']:raise ValueError('Frozen source changed')
    write(out/'protocol.json',{'mode':mode,'baseline':BASELINE,'harnessCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
      'platform':platform.platform(),'python':sys.version,'image':os.environ.get('ImageVersion'),
      'protected':protected,'inventory':inventory,'launcherPins':LAUNCHERS,'actionsPins':ACTIONS,'distributionPin':DIST_SHA,
      'stages':'original stock controls + two fresh 131-test passes per direct/foreground mode; server adds persistent same-PID controls; packaging separately instruments 131 tests and checks reproducibility and six consumers',
      'acceptance':'No retries; original baseline reproduction failure remains failed and makes its job non-green. Continue independent predeclared stages, retain candidate findings separately.',
      'scope':'No production/workaround/default change, release, merge, upstream submission or publish task',
      'semantics':'--server is foreground; --client is persistent; same PID required for successive client commands'})
    sources=directory(out/'sources')
    shutil.copytree(SCRIPTS,sources/'sbt-worker-repair',ignore=shutil.ignore_patterns('__pycache__'))
    copy_file(ROOT/'tools/validation_support.py',sources/'validation_support.py')
    for name in ['tools/test_stock_linux.py','tools/test_stock_assessment.py','.github/workflows/sbt-stock-linux.yml']:
        copy_file(ROOT/name,sources/name)
    write(out/'source-hashes.json',{str(p.relative_to(sources)):sha(p) for p in sources.rglob('*') if p.is_file()})
    run(['java','-version'],out,'java-version',timeout=30);run(['javac','-version'],out,'javac-version',timeout=30)
    upstream=directory(out/'upstream')
    for name,(path,pin) in UPSTREAM.items():download('https://raw.githubusercontent.com/sbt/sbt/v2.0.11/'+path,upstream/name,pin)
    write(out/'inputs.json',{'head':BASELINE,'protected':protected,'upstreamFiles':{p.name:sha(p) for p in upstream.iterdir()}})
    ipc=directory(out/'ipc');os.environ['XDG_RUNTIME_DIR']=str(ipc)
    for version in LAUNCHERS:
        base=directory(out/version)
        for n in ['boot','home','tmp','global','actions','coursier','ivy','bootstrap']:directory(base/n)
        directory(base/'bootstrap/project');write(base/'bootstrap/project/build.properties','sbt.version='+version+'\n')
        write(base/'bootstrap/build.sbt','scalaVersion := "3.9.0"\n')
        launcher=base/('sbt-launch-'+version+'.jar')
        download(f'https://repo.maven.apache.org/maven2/org/scala-sbt/sbt-launch/{version}/sbt-launch-{version}.jar',launcher,LAUNCHERS[version])
        os.environ['COURSIER_CACHE']=str(base/'coursier');os.environ['TMPDIR']=str(base/'tmp')
        run(['java','-Xmx2G','-Dsbt.server.autostart=false',f'-Dsbt.boot.directory={base/"boot"}',
          f'-Duser.home={base/"home"}',f'-Djava.io.tmpdir={base/"tmp"}',f'-Dsbt.global.base={base/"global"}',
          f'-Dsbt.global.localcache={base/"actions"}',f'-Dsbt.ivy.home={base/"ivy"}',
          '-jar',launcher,'show sbtVersion'],base,'bootstrap',cwd=base/'bootstrap',timeout=600)
        jar=next((base/'boot').rglob('actions_3-'+version+'.jar'))
        if sha(jar)!=ACTIONS[version]:raise ValueError('Unofficial actions binary')
        write(base/'boot-hashes.json',{str(p.relative_to(base/'boot')):sha(p) for p in (base/'boot').rglob('*.jar')})
    runner=None
    if mode!='direct':
        distribution=out/'sbt-2.0.11.tgz'
        download('https://github.com/sbt/sbt/releases/download/v2.0.11/sbt-2.0.11.tgz',distribution,DIST_SHA)
        runtime=directory(out/'runtime')
        with tarfile.open(distribution) as t:t.extractall(runtime,filter='data')
        runner=runtime/'sbt/bin/sbt';native=runtime/'sbt/bin/sbtn-x86_64-pc-linux'
        if not runner.is_file() or not native.is_file():raise ValueError('Official runner/native client missing')
        # Distribution may substitute its release version into the source script;
        # pin the entire distribution and record the actual extracted binary hashes.
        write(out/'distribution.json',{'archiveSha256':sha(distribution),'runnerSha256':sha(runner),
          'nativeClientSha256':sha(native),'taggedSourceSha256':sha(upstream/'sbt')})
        os.environ['SBTN_CMD']=str(native)
    results=[]
    def stage(name,action):
        try:detail=action();result={'stage':name,'passed':True,'detail':detail};print(name+': passed',flush=True)
        except Exception as e:result={'stage':name,'passed':False,'error':repr(e)};print('STATUS ALERT: '+name+': '+repr(e),flush=True)
        results.append(result);write(out/(name+'-result.json'),result)

    if mode in ['direct','server']:
        for phase in ['controls','figaro']:
            def assess(phase=phase):
                command=[sys.executable,'-B',SCRIPTS/'stock_assessment.py','--output',out,'--phase',phase]
                if runner:command+=['--runner',runner]
                try:run(command,out,phase+'-execution',cwd=ROOT,timeout=1700)
                finally:
                    if phase=='controls':
                        reports=list((out/'controls/2.0.11/slow/repo').rglob('TEST-*.xml'))
                        if reports:check_slow([ET.parse(p).getroot() for p in reports])
                return json.loads((out/phase/'results.json').read_text())
            stage(phase,assess)

    def configure(case,boot,cache='actions'):
        for n in ['home','tmp','global','coursier','ivy',cache]:
            if not (case/n).exists():directory(case/n)
        os.environ['COURSIER_CACHE']=str(case/'coursier');os.environ['TMPDIR']=str(case/'tmp')
        options=['-Xms512m','-Xmx6G','-Xss6M','-XX:ReservedCodeCacheSize=256M','-Dfile.encoding=UTF-8',
          '-Dsbt.supershell=false','-Dsbt.log.noformat=true','-Dsbt.color=false',f'-Dsbt.boot.directory={boot}',
          f'-Duser.home={case/"home"}',f'-Djava.io.tmpdir={case/"tmp"}',f'-Dsbt.global.base={case/"global"}',
          f'-Dsbt.global.localcache={case/cache}',f'-Dsbt.ivy.home={case/"ivy"}']
        if any(any(c.isspace() for c in x) for x in options):raise ValueError('Unquoted JVM option path')
        os.environ['JAVA_OPTS']=' '.join(options)
        return next(boot.rglob('actions_3-2.0.11.jar'))

    if mode=='server':
        def persistent():
            case=directory(out/'persistent');repo=case/'repo';copy_fixture(SCRIPTS/'fixtures',repo,'2.0.11')
            boot=case/'boot';shutil.copytree(out/'2.0.11/boot',boot);jar=configure(case,boot)
            steps=[('cold-pass','testFull -- -z event-',0,'Tests: succeeded 100, failed 0'),
              ('assertion','testOnly WorkerEvents',1,'SENTINEL_ASSERTION_FAILURE'),
              ('worker-exit','testOnly WorkerExit',1,'Forked test process exited with code 17'),
              ('recovery','testFull -- -z event-',0,'Tests: succeeded 100, failed 0')]
            pid=None;records=[]
            try:
                for name,task,code,marker in steps:
                    text=run([runner,'--client','--batch','-v',IDENTITY,PID,task],case,name,cwd=repo,expected=code,timeout=600)
                    check_identity(text,jar);pid=check_pid(text,pid)
                    if marker not in text or 'Promise already completed' in text or 'running jvm client' in text or 'running native client' not in text:
                        raise ValueError('Incorrect persistent outcome or client fallback')
                    os.kill(pid,0)
                    record={'name':name,'serverPid':pid,'exit':code,'nativeClient':True}
                    reports=directory(case/(name+'-reports'))
                    for p in repo.rglob('TEST-*.xml'):copy_file(p,reports/p.name)
                    records.append(record)
            finally:
                run([runner,'--client','--no-server','--batch','shutdown'],case,'shutdown',cwd=repo,timeout=60)
            if pid:
                for _ in range(50):
                    if not Path('/proc',str(pid)).exists():break
                    time.sleep(.1)
                else:raise ValueError('Owned persistent server did not stop')
            return {'commands':records,'sameServerVerified':True,'shutdownVerified':True,
                    'limits':'Foreground fixtures separately cover framework error and slow listener; persistent scope is pass/assertion/exit/recovery.'}
        stage('persistent',persistent)

    if mode=='packaging':
        def packaging():
            case=directory(out/'packaging');repo=case/'repo'
            run(['git','clone','--shared','--no-checkout',ROOT,repo],case,'clone',timeout=120)
            run(['git','checkout','--detach',BASELINE],case,'checkout',cwd=repo,timeout=120)
            (repo/'project/build.properties').write_text('sbt.version=2.0.11\n')
            boot=case/'boot';shutil.copytree(out/'2.0.11/boot',boot);jar=configure(case,boot,'coverage-actions')
            suites=' '.join(s['suite'] for s in inventory['suites'])
            def invoke(name,commands,cwd=repo,timeout=900):
                text=run([runner,'--server','--batch',IDENTITY]+commands,case,name,cwd=cwd,timeout=timeout)
                check_identity(text,jar)
                if 'Promise already completed' in text or 'Exception in thread' in text:raise ValueError('Unexpected worker error')
                return text
            text=invoke('coverage-tests',['clean','coverage','figaro / Test / testOnly '+suites+' -- -l com.cra.figaro.test.Performance'])
            check_log(text,jar)
            reportdir=directory(case/'coverage-reports')
            for p in (repo/'target/out/jvm/scala-3.9.0/figaro/test-reports').glob('TEST-*.xml'):copy_file(p,reportdir/p.name)
            check_reports([ET.parse(p).getroot() for p in reportdir.glob('*.xml')],inventory)
            invoke('coverage-report',['coverage','figaro / coverageReport'])
            coverage=list(repo.rglob('scoverage.xml'))
            if not coverage:raise ValueError('Missing instrumentation coverage report')
            for i,p in enumerate(coverage):copy_file(p,case/('scoverage-'+str(i)+'.xml'))
            invoke('coverage-off',['coverageOff','clean'])
            artifacts=repo/'target/out/jvm/scala-3.9.0/figaro'
            before=None
            for i in [1,2]:
                configure(case,boot,'repro-actions-'+str(i))
                text=invoke('repro-'+str(i),['clean','figaro / packageBin','figaro / assembly'])
                if not re.search(r'compiling [1-9][0-9]* Scala sources to .*figaro/classes',text):raise ValueError('Rebuild used cached compilation')
                hashes={n:sha(artifacts/n) for n in ['figaro_3-7.4.0.jar','figaro_3-7.4.0-fat.jar']}
                write(case/('repro-'+str(i)+'-hashes.json'),hashes)
                if before and hashes!=before:raise ValueError('Clean builds not byte-identical')
                before=hashes
            invoke('classifiers',['figaro / packageSrc','figaro / packageDoc','figaro / makePom'])
            run([sys.executable,'-B',repo/'tools/check_acceptance_artifacts.py',artifacts,'--version','7.4.0'],case,'artifact-check',timeout=120)
            run([sys.executable,'-B',repo/'tools/build_release_bundle.py',artifacts,case/'candidate-bundle','--candidate','--version','7.4.0'],case,'bundle',timeout=120)
            invoke('binary-compatibility',['figaro / mimaReportBinaryIssues'])
            invoke('tasty-compatibility72',['compatibility / runMain CompatibilityCheck '+str(artifacts/'figaro_3-7.4.0.jar')])
            invoke('tasty-compatibility73',['compatibility73 / runMain CompatibilityCheck '+str(artifacts/'figaro_3-7.4.0.jar')+' 7.3.0'])
            consumer=repo/'tools/acceptance-consumer'
            (consumer/'project/build.properties').write_text('sbt.version=2.0.11\n')
            configure(case,boot,'consumer-actions')
            os.environ.update(FIGARO_VERSION='7.4.0',FIGARO_CAPABILITY_CHECK='true',FIGARO_BUDGET_CHECK='true',
              FIGARO_FIXED_BUDGET_CHECK='true',FIGARO_GUIDED_FIXED_CHECK='true',
              FIGARO_RELEASE_MAVEN_ROOT=str(case/'candidate-bundle/maven'),FIGARO_EXPECTED_SHA256=before['figaro_3-7.4.0.jar'])
            os.environ.pop('FIGARO_CENTRAL_ONLY',None)
            for name,marker in CONSUMERS.items():
                text=invoke('consumer-'+name,['runMain '+name],cwd=consumer)
                if marker not in text:raise ValueError('Missing consumer completion: '+name)
            run(['git','diff','--','project/build.properties','tools/acceptance-consumer/project/build.properties'],case,'isolated-selector-changes',cwd=repo,timeout=30)
            packaged=directory(case/'retained-artifacts')
            for p in artifacts.glob('figaro_3-7.4.0*'):
                if p.suffix in ['.jar','.pom']:copy_file(p,packaged/p.name)
            return {'coverageTests':131,'suites':11,'reproducible':before,'consumers':list(CONSUMERS),
              'candidateOnly':True,'caveat':'Existing bundle README names sbt 2.0.8; this isolated candidate was built with 2.0.11. Not a release.'}
        stage('packaging',packaging)

    for version in LAUNCHERS:
        base=out/version
        if json.loads((base/'boot-hashes.json').read_text())!={str(p.relative_to(base/'boot')):sha(p) for p in (base/'boot').rglob('*.jar')}:
            raise ValueError('Stock boot changed')
    if protected!={p:sha(ROOT/p) for p in protected}:raise ValueError('Production input changed')
    write(out/'results.json',{'mode':mode,'stages':results,'allPredeclaredChecksMet':all(r['passed'] for r in results),
      'protectedUnchanged':True,'stockBootsUnchanged':True,'scope':'Validation only; no workaround replacement or upstream submission'})
    if not all(r['passed'] for r in results):raise RuntimeError('Some predeclared checks failed; retain results for review')


def collect(out):
    evidence=directory(out/'evidence')
    excluded={'evidence','.git','home','tmp','global','coursier','ivy','boot','runtime','ipc','__pycache__'}
    for path,dirs,names in os.walk(out):
        current=Path(path)
        dirs[:]=[d for d in dirs if d not in excluded and not d.endswith('actions') and not d.startswith('repro-actions')]
        for name in names:
            p=current/name;rel=p.relative_to(out)
            selected=p.suffix in ['.log','.json','.xml'] or 'sources' in rel.parts or 'upstream' in rel.parts
            selected=selected or ('retained-artifacts' in rel.parts and p.suffix in ['.jar','.pom'])
            selected=selected or (len(rel.parts)==2 and name.startswith('sbt-launch-') and p.suffix=='.jar')
            selected=selected or (rel.parts[0]=='controls' and 'repo' in rel.parts and 'target' not in rel.parts and p.suffix in ['.scala','.java','.sbt','.properties'])
            selected=selected or name=='checkout.zip'
            if selected:copy_file(p,evidence/rel)
    for version in LAUNCHERS:
        for p in (out/version/'boot').rglob('actions_3-'+version+'.jar'):copy_file(p,evidence/version/p.name)
    write(evidence/'manifest.json',{str(p.relative_to(evidence)):sha(p) for p in evidence.rglob('*') if p.is_file()})


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--mode',choices=['direct','server','packaging'],required=True);a=p.parse_args()
    if platform.system()!='Linux':raise RuntimeError('Linux execution only; local unit checks do not claim Linux validation')
    out=directory(a.output.resolve())
    try:execute(out,a.mode)
    finally:collect(out)

if __name__=='__main__':main()
