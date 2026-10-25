"""GVM-08 frozen factorial study. No source/default changes or seed replacement."""
import argparse,json,platform,time
from pathlib import Path
import numpy as np
import scipy
import gvm_dimension_controls as c
import gvm_wholefit_fit as baseline
import gvm_wholefit_study as previous
import gvm_weight_fit as candidate
from gvm_study_io import grant,save,sha

ARMS=('baseline','weights','balanced','combined')


def config():
    cfg=previous.config()
    cfg.update(protocol='gvm-weight-restart-v1',seeds=list(range(1150001,1150009)),
        maxFitsIncludingDevelopment=1200,expectedConfirmationFits=1040,arms=list(ARMS),
        developmentSeed=450101,developmentFits=40,
        weightGap=1e-8,weightFloor=1e-8,weightLocalWork=1000000,weightCalls=200,weightIterations=100)
    sources=set(cfg['sourceSha256'])
    sources.update('tools/'+x for x in ('gvm_weight_solver.py','gvm_weight_reference.py',
        'gvm_weight_qualification.py','gvm_weight_fit.py','gvm_weight_study.py',
        'gvm_weight_audit.py','test_gvm_weight_solver.py','test_gvm_weight_fit.py','test_gvm_weight_audit.py'))
    sources.add('tools/gvm_wholefit_audit.py');sources.add('docs/GVM_WEIGHT_PROTOCOL.md')
    cfg['sourceSha256']={p:sha(p) for p in sorted(sources) if Path(p).exists()}
    cfg['versions']=dict(python=platform.python_version(),numpy=np.__version__,scipy=scipy.__version__)
    return cfg


def execute(root,protocol,development=False):
    begun=time.time();cfg=config() if development else json.loads(protocol.read_text())
    if not development:
        assert cfg['expectedConfirmationFits']==1040
        for path,digest in cfg['sourceSha256'].items():assert sha(path)==digest,path
    rows=[];inputs={};counts=(cfg['train'],cfg['validation'],cfg['test'])
    seeds=[cfg['developmentSeed']] if development else cfg['seeds']
    def check():
        if time.time()-begun+cfg.get('developmentElapsedSeconds',0)>cfg['maxStudySeconds']:
            raise RuntimeError('StudyTimeBudgetExhausted')
    for d in cfg['dimensions']:
        for family in cfg['families']:
            for seed in seeds:
                check();blocks,center,lower=previous.dataset(d,family,seed,counts)
                key=f'd{d}-{family}-{seed}';file=root/(key+'.npz')
                with file.open('xb') as stream:
                    grant(file);np.savez_compressed(stream,train=blocks[0],validation=blocks[1],test=blocks[2],center=center,lower=lower)
                inputs[file.name]=sha(file)
                train,validation,test=[c.transform(x,center,lower) for x in blocks]
                choices=[(k,arm) for k in ([3] if development else cfg['components']) for arm in ARMS]
                if not development:choices.append((4,'wrapped'))
                if (seed+d+previous.FAMILIES.index(family))%2:choices.reverse()
                for k,arm in choices:
                    check()
                    kwargs=dict(max_work=cfg['maxWork'],maps=cfg['maxMapsPerStart'],seconds=cfg['maxSeconds'],
                                tolerance=cfg['stationarity'],starts=cfg['starts'])
                    if arm=='wrapped':fit=c.fit_gaussian(train,k,True,seed,iterations=cfg['gaussianIterations'],max_seconds=cfg['maxSeconds'])
                    elif arm=='baseline':fit=baseline.fit(train,k,seed,method='squarem',**kwargs)
                    else:fit=candidate.fit(train,k,seed,polish=arm in ('weights','combined'),
                                          schedule='balanced' if arm in ('balanced','combined') else 'sequential',**kwargs)
                    evaluator=c.gaussian_log if arm=='wrapped' else c.gvm_log
                    row=dict(d=d,family=family,seed=seed,k=k,method=arm,fit=fit,
                        validationScore=float(evaluator(validation,fit).mean()) if fit['components'] else None,
                        testScore=float(evaluator(test,fit).mean()) if fit['components'] else None)
                    save(root/f'{key}-k{k}-{arm}.json',row);rows.append(row)
                    print(json.dumps(dict(case=key,k=k,arm=arm,status=fit['status'],seconds=fit['seconds'])),flush=True)
    result=dict(protocolSha256=None if development else sha(protocol),inputSha256=inputs,
        records=rows,development=development,elapsedSeconds=time.time()-begun)
    assert len(rows)==(40 if development else 1040)
    save(root/'results.json',result);print(json.dumps(dict(fits=len(rows),elapsedSeconds=result['elapsedSeconds'])),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['development','freeze','run']);p.add_argument('path',type=Path)
    p.add_argument('--protocol',type=Path,default=Path('tools/gvm_weight_protocol_v1.json'))
    p.add_argument('--development',type=Path)
    a=p.parse_args()
    if a.mode=='freeze':
        cfg=config();dev=json.loads(a.development.read_text());assert len(dev['records'])==40
        cfg['developmentElapsedSeconds']=dev['elapsedSeconds'];cfg['developmentResultsSha256']=sha(a.development)
        for path in Path('tools').glob('gvm*protocol*.json'):
            assert not set(cfg['seeds']).intersection(json.loads(path.read_text()).get('seeds',[])),path
        assert cfg['developmentSeed'] not in cfg['seeds']
        save(a.path,cfg)
    else:execute(a.path,a.protocol,a.mode=='development')
