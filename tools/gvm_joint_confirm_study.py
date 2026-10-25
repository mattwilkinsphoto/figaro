"""Frozen fresh-seed confirmation. No optimizer code or defaults are changed."""
import argparse,json,os,platform,time,traceback
from pathlib import Path
import numpy as np
import scipy
import gvm_dimension_controls as c
import gvm_wholefit_fit as baseline
import gvm_joint_fit as candidate
from gvm_wholefit_study import dataset,FAMILIES
from gvm_study_io import grant,save,sha


def config():
    old=json.loads(Path('tools/gvm_joint_protocol_v1.json').read_text())
    for p,h in old['sourceSha256'].items():assert sha(p)==h,p
    seeds=list(range(1250001,1250009))
    previous={}
    for p in Path('tools').glob('gvm*protocol*.json'):
        prior=json.loads(p.read_text())
        for key,value in prior.items():
            if 'seed' in key.lower() and isinstance(value,(int,list)):
                assert not set(seeds).intersection(value if isinstance(value,list) else [value]),(p,key)
        previous[p.as_posix()]=sha(p)
    sources=set(old['sourceSha256'])
    sources.update(p.as_posix() for p in Path('tools').glob('*gvm_joint_confirm*.py'))
    sources.add('docs/GVM_JOINT_CONFIRMATION_PROTOCOL.md')
    return dict(protocol='gvm-joint-confirmation-v1',seeds=seeds,dimensions=[2,3],families=list(FAMILIES),
        components=[2,3],arms=['baseline','joint'],repetitions=3,train=2000,validation=2000,test=20000,
        expectedDatasets=80,expectedPairs=160,expectedFits=960,warmupSeed=1150001,
        maxStudySeconds=10800,maxWork=20000000,maxSeconds=120,starts=3,prefixMaps=10,
        baselineMaps=200,jointIterations=200,jointCalls=2000,stationarity=1e-4,
        qualifiedRate=.95,completionGain=.2,maximumLostCompletions=0,scoreMargin=.02,
        alpha=.05,comparisons=21,maximumCostRatio=1.,
        sourceSha256={p:sha(p) for p in sorted(sources)},priorProtocolSha256=previous,
        versions=dict(python=platform.python_version(),numpy=np.__version__,scipy=scipy.__version__),
        frozenUnix=time.time())


def cases(cfg):
    return [(d,f,k,s) for s in cfg['seeds'] for d in cfg['dimensions']
            for f in cfg['families'] for k in cfg['components']]


def schedule(cfg):
    # Repeat-major order separates repeats in time. Each repeat has exactly half
    # baseline-first and half candidate-first pairs; reverse order on next repeat.
    for repeat in range(cfg['repetitions']):
        for index,(d,f,k,s) in enumerate(cases(cfg)):
            for position,arm in enumerate(cfg['arms'][::1 if (index+repeat)%2==0 else -1]):
                yield d,f,k,s,repeat,position,arm


def measured(raw,k,seed,arm,cfg):
    """Outer cost includes training whitening and the complete fit-return path."""
    wall=time.perf_counter_ns();cpu=time.process_time_ns();error=None;fit=None
    try:
        center,lower=c.whitening(raw)
        train=c.transform(raw,center,lower)
        common=dict(max_work=cfg['maxWork'],seconds=cfg['maxSeconds'],starts=cfg['starts'])
        if arm=='baseline':fit=baseline.fit(train,k,seed,method='squarem',maps=cfg['baselineMaps'],**common)
        else:fit=candidate.fit(train,k,seed,prefix_maps=cfg['prefixMaps'],
            iterations=cfg['jointIterations'],calls=cfg['jointCalls'],**common)
    except Exception:
        # Retain actual exceptions and their measured cost; never retry a failure.
        error=traceback.format_exc()
    return dict(fit=fit,error=error,wallSeconds=(time.perf_counter_ns()-wall)/1e9,
                cpuSeconds=(time.process_time_ns()-cpu)/1e9)


def execute(root,protocol):
    cfg=json.loads(protocol.read_text());begun=time.time();clock=time.perf_counter();inputs={};rows=[]
    for p,h in cfg['sourceSha256'].items():assert sha(p)==h,p
    for p,h in cfg['priorProtocolSha256'].items():assert sha(p)==h,p
    assert cfg['versions']==dict(python=platform.python_version(),numpy=np.__version__,scipy=scipy.__version__)
    assert all(os.environ.get(k)=='1' for k in ('OPENBLAS_NUM_THREADS','OMP_NUM_THREADS'))
    def budget():
        if time.perf_counter()-clock>cfg['maxStudySeconds']:raise RuntimeError('StudyTimeBudgetExhausted')
    warmup,_,_=dataset(2,'curved',cfg['warmupSeed'],(cfg['train'],0,1))
    warmup_result={a:measured(warmup[0],2,cfg['warmupSeed'],a,cfg) for a in cfg['arms']}
    save(root/'warmup.json',warmup_result)
    assert all(r['error'] is None for r in warmup_result.values()),'Warmup failed'
    for s in cfg['seeds']:
        for d in cfg['dimensions']:
            for family in cfg['families']:
                budget();blocks,center,lower=dataset(d,family,s,(cfg['train'],cfg['validation'],cfg['test']))
                path=root/f'd{d}-{family}-{s}.npz'
                with path.open('xb') as stream:
                    grant(path);np.savez_compressed(stream,train=blocks[0],validation=blocks[1],test=blocks[2],center=center,lower=lower)
                inputs[path.name]=sha(path)
    save(root/'inputs.json',inputs)
    for d,f,k,s,repeat,position,arm in schedule(cfg):
        budget();name=f'd{d}-{f}-{s}'
        with np.load(root/(name+'.npz')) as raw:
            # Disk input and held-out scoring are outside the fit timing boundary.
            train=raw['train'];test=c.transform(raw['test'],raw['center'],raw['lower'])
        row=dict(d=d,family=f,k=k,seed=s,repeat=repeat,position=position,arm=arm,
                 **measured(train,k,s,arm,cfg))
        fit=row['fit']
        row['testScore']=float(c.gvm_log(test,fit).mean()) if fit and fit['components'] else None
        save(root/f'{name}-k{k}-{arm}-r{repeat}.json',row);rows.append(row)
        print(json.dumps(dict(index=len(rows),d=d,family=f,k=k,seed=s,repeat=repeat,arm=arm,
            status=fit['status'] if fit else 'Exception',wallSeconds=row['wallSeconds'])),flush=True)
    assert len(rows)==cfg['expectedFits'] and len(inputs)==cfg['expectedDatasets']
    result=dict(protocolSha256=sha(protocol),inputSha256=inputs,records=rows,
        startedUnix=begun,finishedUnix=time.time(),elapsedSeconds=time.perf_counter()-clock,
        clockInfo={k:vars(time.get_clock_info(k)) for k in ('perf_counter','process_time')})
    save(root/'results.json',result)
    print(json.dumps(dict(fits=len(rows),elapsedSeconds=result['elapsedSeconds'])),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['freeze','run']);p.add_argument('path',type=Path)
    p.add_argument('--protocol',type=Path,default=Path('tools/gvm_joint_confirm_protocol_v1.json'));a=p.parse_args()
    if a.mode=='freeze':save(a.path,config())
    else:execute(a.path,a.protocol)
