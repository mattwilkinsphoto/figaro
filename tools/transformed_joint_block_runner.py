"""TJB-01 one-shot process-tree bounded capture, preserving incomplete attempts."""
import argparse,gzip,json,subprocess,threading,time
from pathlib import Path
import psutil
from gvm_study_io import grant,save,sha

def run(root,wrapper,phase):
    ref=json.loads((root/'joint-reference-v1.json').read_text())
    repo=Path(__file__).resolve().parents[1]
    assert sha(repo/'tools/transformed_joint_block_reference.py')==ref['sourceSha256']
    assert sha(repo/'docs/TRANSFORMED_JOINT_BLOCK_PROTOCOL.md')==ref['protocolSha256']
    if phase=='confirmation':
        review=json.loads((root/'development-analysis.json').read_text())
        assert review['complete'] and all(review['gates'].values()),'Firm exit: confirmation forbidden'
    log=root/(phase+'.log.gz'); receipt=root/(phase+'-execution.json')
    assert not log.exists() and not receipt.exists(),'Already attempted; no retry'
    subprocess.run(['git','diff','--exit-code','HEAD'],cwd=repo,check=True,stdout=subprocess.PIPE)
    files=subprocess.check_output(['git','ls-files'],cwd=repo,text=True).splitlines()
    source_hashes={f:sha(repo/f) for f in files if (repo/f).is_file()}
    revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip()
    save(root/(phase+'-inputs.json'),dict(revision=revision,sha256=source_hashes))
    args=['pwsh','-NoProfile','-File',str(wrapper),'-Commands',
          'figaro / Test / runMain com.cra.figaro.algorithm.sampling.TransformedJointBlockStudy '+phase]
    state=dict(bytes=0,error=None);start=time.time();limit=None;peak=0
    with log.open('xb') as raw:
        grant(log)
        with gzip.GzipFile(fileobj=raw,mode='wb',compresslevel=1,mtime=0) as out:
            process=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,cwd=repo)
            owner=psutil.Process(process.pid)
            def read():
                try:
                    while chunk:=process.stdout.read(262144):state['bytes']+=len(chunk);out.write(chunk)
                except BaseException as e:state['error']=repr(e)
            reader=threading.Thread(target=read,daemon=True);reader.start()
            while process.poll() is None:
                try:
                    rss=0
                    for child in [owner]+owner.children(recursive=True):
                        try:rss+=child.memory_info().rss
                        except psutil.NoSuchProcess:pass
                    peak=max(peak,rss)
                except psutil.NoSuchProcess:pass
                except psutil.AccessDenied as e:state['error']='Resource monitor: '+repr(e)
                if time.time()-start>ref['maxWallSecondsPerPhase']:limit='time'
                if peak>ref['maxProcessBytes']:limit='memory'
                if state['bytes']>ref['maxOutputBytes']:limit='output'
                if state['error']:limit='reader'
                if limit:
                    subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],check=True);break
                time.sleep(.5)
            code=process.wait();reader.join(timeout=30)
            assert not reader.is_alive(),'Reader did not terminate'
    if state['bytes']>ref['maxOutputBytes']:limit='output'
    save(receipt,dict(args=args,startedUnix=start,finishedUnix=time.time(),exitCode=code,
        limitExceeded=limit,readerError=state['error'],decodedOutputBytes=state['bytes'],peakTreeRssBytes=peak,
        processMonitorVersion=psutil.__version__,sha256=sha(log),referenceSha256=sha(root/'joint-reference-v1.json'),
        revision=revision,sourceUnchanged=all((repo/f).is_file() and sha(repo/f)==h for f,h in source_hashes.items())))
    print(receipt.read_text(),flush=True)
    assert code==0 and limit is None and state['error'] is None,'Incomplete experiment; no retry'

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('root',type=Path);p.add_argument('wrapper',type=Path)
    p.add_argument('phase',choices=['development','confirmation']);a=p.parse_args()
    run(a.root.resolve(),a.wrapper.resolve(),a.phase)
