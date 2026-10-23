"""Run real forked sbt fixtures; nonzero exits are required, not suppressed."""
import argparse
import os
from pathlib import Path
import shutil
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validation_support import access, command, directory, write
from pilot_support import IDENTITY, check_identity, run

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--boot',type=Path,required=True)
    p.add_argument('--launcher',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--java',default='java')
    p.add_argument('--user-home',type=Path,required=True,help='Isolated sbt user home; no ordinary account profile')
    p.add_argument('--verify-identity',action='store_true',help='Pilot: verify loaded JAR/hash in every fixture JVM')
    a=p.parse_args();out=directory(a.output.resolve())
    fixture=out/'fixture';shutil.copytree(Path(__file__).with_name('fixtures'),fixture);access(fixture,recursive=True)
    temp=directory(out/'tmp');global_base=directory(out/'global')
    base=[a.java,'-Xms256m','-Xmx2G','-Dsbt.supershell=false','-Dsbt.log.noformat=true','-Dsbt.server.autostart=false',
          '-Dsbt.boot.directory='+str(a.boot.resolve()),'-Dsbt.global.base='+str(global_base),
          '-Duser.home='+str(a.user_home.resolve()),'-Djava.io.tmpdir='+str(temp),'-jar',str(a.launcher.resolve())]
    cases=[('passing','testOnly WorkerEvents -- -z event-',0,['Tests: succeeded 100, failed 0','All tests passed']),
           ('assertion','testOnly WorkerEvents',1,['Tests: succeeded 100, failed 1','SENTINEL_ASSERTION_FAILURE']),
           ('exit','testOnly WorkerExit',1,['Forked test process exited with code 17'])]
    results=[]
    for name,task,expected,markers in cases:
        try:
            if a.verify_identity:
                run(base+[IDENTITY,task],out,name,cwd=fixture,expected=expected,timeout=600)
            else:command(base+[task],out,name,cwd=fixture,expected=expected)
        finally: access(out,recursive=True)
        text=(out/f'{name}.log').read_text(errors='replace')
        if a.verify_identity:
            check_identity(text,a.boot.resolve()/'scala-3.8.4/org.scala-sbt/sbt/2.0.8/actions_3-2.0.8.jar')
        if any(marker not in text for marker in markers) or 'Promise already completed' in text:
            raise ValueError(f'{name}: incomplete outcomes or worker completion exception; inspect preserved log')
        results.append(dict(case=name,expectedExit=expected,markers=markers,status='verified'))
    write(out/'results.json',results)
    print('Real forked runner: 100 success events, 100+1 assertion outcomes, and exit 17 correctly propagated.')

if __name__=='__main__':main()
