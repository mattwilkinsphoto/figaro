"""Isolated pilot I/O: bounded processes and in-process runner identity checks."""
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validation_support import access,sha,write

IDENTITY = ('eval { val p = java.nio.file.Paths.get(Class.forName("sbt.React")'
 '.getProtectionDomain.getCodeSource.getLocation.toURI); '
 'println("FIGARO_REACT_PATH=" + p); '
 'println("FIGARO_REACT_SHA256=" + java.util.HexFormat.of().formatHex('
 'java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(p)))) }')

def run(args,out,name,cwd=None,expected=0,timeout=900):
    """Retain failed/time-limited attempts; never retry or accept an unexpected exit."""
    args=list(map(str,args));out=Path(out);log=out/(name+'.log')
    start=time.time();timed_out=False;code=None
    with log.open('xb') as stream:
        access(log)
        process=subprocess.Popen(args,cwd=cwd,stdout=stream,stderr=subprocess.STDOUT,
                                 start_new_session=os.name!='nt')
        try:code=process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out=True
            if os.name=='nt':subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],check=False)
            else:os.killpg(process.pid,signal.SIGKILL)
            code=process.wait()
    write(out/(name+'.json'),dict(args=args,cwd=str(cwd),startedUnix=start,finishedUnix=time.time(),
          exitCode=code,expectedExit=expected,timeoutSeconds=timeout,timedOut=timed_out,logSha256=sha(log)))
    if timed_out or code!=expected:raise RuntimeError(f'{name}: exit={code}, timedOut={timed_out}; inspect {log}')
    return log.read_text(encoding='utf-8',errors='replace')

def check_identity(text,jar):
    paths=re.findall(r'^FIGARO_REACT_PATH=(.+)$',text,re.M)
    hashes=re.findall(r'^FIGARO_REACT_SHA256=([0-9a-f]{64})$',text,re.M)
    if len(paths)!=1 or Path(paths[0].strip()).resolve()!=Path(jar).resolve() or hashes!=[sha(jar)]:
        raise ValueError('Missing or incorrect in-process React path/hash')

def check_failure(text):
    # ForkError is sbt's deserialized notification exception, not merely stderr text.
    if 'SENTINEL_FRAMEWORK_FORK_ERROR' not in text or 'ForkTestMain$ForkError' not in text:
        raise ValueError('Framework error notification not established')
    if 'Promise already completed' in text or 'All tests passed' in text:
        raise ValueError('Framework fixture hid or mishandled its failure')
