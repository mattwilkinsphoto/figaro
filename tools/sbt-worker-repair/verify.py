"""Validate stock/repaired actual sbt classes. Real fork fixtures are separate."""
import argparse
import os
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validation_support import access, command, directory

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stock-boot',type=Path,required=True)
    p.add_argument('--repaired-boot',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--java',default='java');p.add_argument('--javac',default='javac')
    a=p.parse_args();out=directory(a.output.resolve());classes=directory(out/'classes')
    try: command([a.javac,'-d',classes,Path(__file__).with_name('WorkerCompletionCheck.java')],out,'compile')
    finally: access(classes,recursive=True)
    for mode,boot in [('stock',a.stock_boot),('repaired',a.repaired_boot)]:
        boot=boot.resolve()
        cp=os.pathsep.join([str(classes),str(boot/'scala-3.8.4/lib/*'),str(boot/'scala-3.8.4/org.scala-sbt/sbt/2.0.8/*')])
        command([a.java,'-cp',cp,'WorkerCompletionCheck',mode],out,mode)
    print('Both 160-case matrices passed; stock races reproduced, repaired races absent, first failures preserved.')

if __name__=='__main__':main()
