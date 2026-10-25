"""Frozen 40-fit development feasibility screen; never labeled confirmation."""
import argparse,json,platform,shutil,time
from pathlib import Path
import numpy as np
import scipy
import gvm_dimension_controls as c
import gvm_wholefit_fit as baseline
import gvm_joint_fit as candidate
from gvm_study_io import grant,save,sha

FAMILIES=('curved','seam','three-separated','three-overlap','three-wrapped')

def freeze(path,old):
    previous=json.loads(Path('tools/gvm_weight_protocol_v1.json').read_text())
    sources=set(previous['sourceSha256'])
    sources.update(str(p).replace('\\','/') for p in Path('tools').glob('*gvm_joint*.py'))
    sources.add('docs/GVM_JOINT_PROTOCOL.md')
    files=[f'd{d}-{family}-1150001.npz' for d in (2,3) for family in FAMILIES]
    recorded=json.loads((old/'results.json').read_text())['inputSha256']
    for name in files:assert sha(old/name)==recorded[name]
    save(path,dict(protocol='gvm-joint-feasibility-v1',developmentOnly=True,seed=1150001,
        dimensions=[2,3],families=list(FAMILIES),components=[2,3],arms=['baseline','joint'],expectedFits=40,
        maxWork=20000000,maxSeconds=120,starts=3,prefixMaps=10,baselineMaps=200,jointIterations=200,jointCalls=2000,
        maxStudySeconds=7200,stationarity=1e-4,minimumCompletionGain=4,maximumLostCompletions=0,scoreMargin=.02,
        sourceSha256={p:sha(p) for p in sorted(sources)},inputSha256={p:sha(old/p) for p in files},
        versions=dict(python=platform.python_version(),numpy=np.__version__,scipy=scipy.__version__),frozenUnix=time.time()))

def execute(root,old,protocol):
    cfg=json.loads(protocol.read_text());begun=time.time();rows=[]
    for name,digest in cfg['sourceSha256'].items():assert sha(name)==digest,name
    for d in cfg['dimensions']:
        for family in cfg['families']:
            key=f'd{d}-{family}-{cfg["seed"]}';name=key+'.npz'
            assert sha(old/name)==cfg['inputSha256'][name]
            target=root/name
            with target.open('xb') as out:
                grant(target)
                with (old/name).open('rb') as source:shutil.copyfileobj(source,out)
            with np.load(target) as data:
                train,test=[c.transform(data[n],data['center'],data['lower']) for n in ('train','test')]
            for k in cfg['components']:
                arms=cfg['arms'] if (d+k+FAMILIES.index(family))%2==0 else list(reversed(cfg['arms']))
                for arm in arms:
                    if time.time()-begun>cfg['maxStudySeconds']:raise RuntimeError('StudyTimeBudgetExhausted')
                    common=dict(max_work=cfg['maxWork'],seconds=cfg['maxSeconds'],starts=cfg['starts'])
                    if arm=='baseline':fit=baseline.fit(train,k,cfg['seed'],method='squarem',maps=cfg['baselineMaps'],**common)
                    else:fit=candidate.fit(train,k,cfg['seed'],prefix_maps=cfg['prefixMaps'],iterations=cfg['jointIterations'],calls=cfg['jointCalls'],**common)
                    row=dict(d=d,family=family,k=k,arm=arm,fit=fit,
                        testScore=float(c.gvm_log(test,fit).mean()) if fit['components'] else None)
                    save(root/f'{key}-k{k}-{arm}.json',row);rows.append(row)
                    print(json.dumps(dict(d=d,family=family,k=k,arm=arm,status=fit['status'],work=fit['totalWork'])),flush=True)
    assert len(rows)==40
    save(root/'results.json',dict(developmentOnly=True,protocolSha256=sha(protocol),inputSha256=cfg['inputSha256'],
        records=rows,elapsedSeconds=time.time()-begun))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['freeze','run']);p.add_argument('path',type=Path)
    p.add_argument('--old',type=Path,default=Path('.cache/gvm-weight-restart-v1/confirmation'))
    p.add_argument('--protocol',type=Path,default=Path('tools/gvm_joint_protocol_v1.json'));a=p.parse_args()
    if a.mode=='freeze':freeze(a.path,a.old)
    else:execute(a.path,a.old,a.protocol)
