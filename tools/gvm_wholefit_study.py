"""Generate isolated synthetic controls, freeze, and execute GVM-07 once."""
import argparse, json, platform, time
from pathlib import Path
import numpy as np
import scipy
import gvm_dimension_controls as c
import gvm_wholefit_fit as f
from gvm_study_io import grant, save, sha

FAMILIES = ('curved', 'seam', 'three-separated', 'three-overlap', 'three-wrapped')


def truth(d, family):
    if family in ('curved', 'seam'): return c.truth(d, family)
    if family not in FAMILIES: raise ValueError('Unknown family')
    components = []
    for j, weight in enumerate((.3, .4, .3)):
        if family == 'three-wrapped':
            lower = np.eye(d+1)*.8
            lower[-1, :-1] = np.linspace(.5, -.2, d); lower[-1, -1] = .45
            mean = np.zeros(d+1); mean[0] = (j-1)*1.5; mean[-1] = 2.8+j*.4
            components.append(dict(weight=weight, mean=mean.tolist(), cov=(lower@lower.T).tolist()))
        else:
            overlap = family == 'three-overlap'
            mean = np.zeros(d); mean[0] = (j-1)*(.5 if overlap else 2.)
            mean[1] = (-.15, .2, -.1)[j] if overlap else (-.6, .7, -.2)[j]
            coeff = np.r_[np.linspace(.35, -.2, d)*(1-.4*j),
                          [(.4 if a == b else -.25)*(1-.6*j) for a in range(d) for b in range(a, d)]]
            components.append(dict(weight=weight, mean=mean.tolist(), cov=(.6*np.eye(d)+.15*np.ones((d,d))).tolist(),
                alpha=(-.5,.1,.7)[j] if overlap else (-2.8,.1,2.8)[j],
                coeff=coeff.tolist(), kappa=(7.,9.,6.)[j]))
    return ('gaussian' if family == 'three-wrapped' else 'gvm'), dict(components=components)


def dataset(d, family, seed, counts):
    kind, model = truth(d, family); sample = c.sample_gvm if kind == 'gvm' else c.sample_gaussian
    blocks = [sample(np.random.Generator(np.random.PCG64DXSM(np.random.SeedSequence([seed,d,FAMILIES.index(family),stream]))), model, n)
              for stream, n in enumerate(counts)]
    center, lower = c.whitening(blocks[0])
    return blocks, center, lower


def config():
    sources = ['tools/'+name for name in (
        'gvm_wholefit_math.py','gvm_wholefit_fit.py','gvm_wholefit_study.py',
        'test_gvm_wholefit.py','gvm_wholefit_audit.py','gvm_gradient_fit.py',
        'gvm_dimension_controls.py','gvm_dimension_audit.py','gvm_study_io.py')]
    sources += ['docs/GVM_WHOLEFIT_STUDY.md']
    return dict(protocol='gvm-wholefit-v1', seeds=list(range(1050001,1050009)), dimensions=[2,3],
        families=list(FAMILIES), components=[1,2,3], train=2000, validation=2000, test=20000,
        starts=3, maxWork=20000000, maxMapsPerStart=200, maxSeconds=120, stationarity=1e-4,
        gaussianIterations=200, maxStudySeconds=10800, maxFitsIncludingDevelopment=640,
        expectedConfirmationFits=560, qualifiedRate=.95, completionGain=.2,
        noninferiority=.02, alpha=.05, comparisons=30,
        sourceSha256={path:sha(path) for path in sources},
        versions=dict(python=platform.python_version(),numpy=np.__version__,scipy=scipy.__version__),
        frozenUnix=time.time())


def execute(root, protocol, development=False):
    started=time.time(); cfg=json.loads(protocol.read_text()) if not development else config()
    if not development:
        for path, digest in cfg['sourceSha256'].items(): assert sha(path)==digest, path
    cases=[(d,family,seed) for d in cfg['dimensions'] for family in cfg['families']
           for seed in ([350101] if development else cfg['seeds'])]
    records=[]; inputs={}; counts=(cfg['train'],cfg['validation'],cfg['test'])
    for d,family,seed in cases:
        if time.time()-started>cfg['maxStudySeconds']: raise RuntimeError('StudyTimeBudgetExhausted')
        blocks,center,lower=dataset(d,family,seed,counts)
        key=f'd{d}-{family}-{seed}'; file=root/(key+'.npz')
        with file.open('xb') as stream:
            grant(file); np.savez_compressed(stream,train=blocks[0],validation=blocks[1],test=blocks[2],center=center,lower=lower)
        inputs[file.name]=sha(file)
        train,validation,test=[c.transform(a,center,lower) for a in blocks]
        choices=[(k,method) for k in ([3] if development else cfg['components']) for method in ('em','squarem')]
        if not development: choices.append((4,'wrapped'))
        # Alternating order reduces, but does not remove, wall-clock scheduling bias.
        if (seed+d+FAMILIES.index(family))%2: choices.reverse()
        for k,method in choices:
            if time.time()-started>cfg['maxStudySeconds']: raise RuntimeError('StudyTimeBudgetExhausted')
            if method=='wrapped':
                fit=c.fit_gaussian(train,k,True,seed,iterations=cfg['gaussianIterations'],max_seconds=cfg['maxSeconds'])
            else:
                fit=f.fit(train,k,seed,method,max_work=cfg['maxWork'],maps=cfg['maxMapsPerStart'],
                          seconds=cfg['maxSeconds'],tolerance=cfg['stationarity'],starts=cfg['starts'])
            evaluate=c.gaussian_log if method=='wrapped' else c.gvm_log
            row=dict(d=d,family=family,seed=seed,k=k,method=method,fit=fit,
                validationScore=float(evaluate(validation,fit).mean()) if fit['components'] else None,
                testScore=float(evaluate(test,fit).mean()) if fit['components'] else None)
            save(root/f'{key}-k{k}-{method}.json',row); records.append(row)
            print(json.dumps(dict(case=key,k=k,method=method,status=fit['status'],seconds=fit['seconds'],
                                  gradient=(fit.get('diagnostic') or {}).get('maximum'))),flush=True)
    result=dict(protocolSha256=None if development else sha(protocol),inputSha256=inputs,records=records,
                elapsedSeconds=time.time()-started,development=development)
    if not development: assert len(records)==cfg['expectedConfirmationFits']
    save(root/'results.json',result)
    print(json.dumps(dict(fits=len(records),elapsedSeconds=result['elapsedSeconds'])),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['freeze','development','run'])
    p.add_argument('path',type=Path);p.add_argument('--protocol',type=Path,default=Path('tools/gvm_wholefit_protocol_v1.json'))
    args=p.parse_args()
    if args.mode=='freeze': save(args.path,config())
    else: execute(args.path,args.protocol,args.mode=='development')
