"""Additive forkError matrix and real framework-error fixture; original gates unchanged."""
import argparse
import json
import os
from pathlib import Path
import shutil
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validation_support import access,directory,write
from pilot_support import IDENTITY,check_failure,check_identity,run
REL=Path('scala-3.8.4/org.scala-sbt/sbt/2.0.8/actions_3-2.0.8.jar')
MODES=['fork-before-success','fork-before-exit','fork-during-exit','duplicate-fork',
       'success-before-fork','wrong-outer-id','wrong-inner-id']

def check_matrix(text,mode,boot):
    urls=[line.removeprefix('BINARY ') for line in text.splitlines() if line.startswith('BINARY ')]
    from urllib.parse import urlparse
    from urllib.request import url2pathname
    if len(urls)!=1 or Path(url2pathname(urlparse(urls[0]).path)).resolve()!=(boot/REL).resolve():
        raise ValueError('Notification control loaded incorrect React')
    rows=[json.loads(line) for line in text.splitlines() if line.startswith('{')]
    if [r['mode'] for r in rows]!=MODES:raise ValueError('Incomplete notification matrix')
    for row in rows:
        name=row['mode']
        expected=dict(mode=name,trials=20,duplicateExceptions=20 if mode=='stock' and name=='fork-during-exit' else 0,
          firstErrorPreserved=not name.startswith('wrong-') and name!='success-before-fork',
          lateErrorLimitation=name=='success-before-fork')
        if row!=expected:raise ValueError('Unexpected notification outcome')
    return rows

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['stock-boot','repaired-boot','launcher','user-home','output']:
        p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=directory(a.output.resolve());classes=directory(out/'classes')
    scripts=Path(__file__).parent
    def cp(boot):return os.pathsep.join([str(classes),str(boot/'scala-3.8.4/lib/*'),str(boot/REL.parent/'*')])
    try:run(['javac','-cp',cp(a.stock_boot.resolve()),'-d',classes,scripts/'ForkErrorCheck.java'],out,'compile',timeout=120)
    finally:access(classes,recursive=True)
    results={}
    for mode,boot in [('stock',a.stock_boot.resolve()),('repaired',a.repaired_boot.resolve())]:
        text=run(['java','-cp',cp(boot),'ForkErrorCheck',mode],out,mode,timeout=120)
        results[mode]=check_matrix(text,mode,boot)
    fixture=out/'fixture';shutil.copytree(scripts/'framework-fixture',fixture);access(fixture,recursive=True)
    temp=directory(out/'tmp');global_base=directory(out/'global')
    base=['java','-Xms256m','-Xmx2G','-Dsbt.supershell=false','-Dsbt.log.noformat=true',
      '-Dsbt.server.autostart=false',f'-Dsbt.boot.directory={a.repaired_boot.resolve()}',
      f'-Dsbt.global.base={global_base}',f'-Dsbt.global.localcache={out / "actions"}',
      f'-Duser.home={a.user_home.resolve()}',f'-Djava.io.tmpdir={temp}','-jar',a.launcher.resolve()]
    try:text=run(base+[IDENTITY,'show Test / loadedTestFrameworks','show Test / definedTests','testOnly FrameworkFailure'],out,'framework',cwd=fixture,expected=1,timeout=600)
    finally:access(out,recursive=True)
    check_identity(text,a.repaired_boot.resolve()/REL);check_failure(text)
    write(out/'results.json',dict(matrix=results,frameworkExit=1,notificationExceptionVerified=True,
      limitations='First success still wins over a later forkError; not protocol-drain validation'))
    print('280 notification cases and framework notification failure verified')

if __name__=='__main__':main()
