"""Bounded GVM-06 experiment: fresh seeds, unchanged baseline, preserved outcomes."""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import platform
import subprocess
import time
import numpy as np
import scipy
from scipy.stats import t
import gvm_dimension_controls as c
import gvm_gradient_fit as g
from gvm_dimension_qualification import invoke, java_command, write_data
from gvm_study_io import grant, save, sha

FAMILIES=('curved','seam','local','wrapped')
SOURCES=('tools/gvm_gradient_fit.py','tools/gvm_gradient_study.py',
         'tools/test_gvm_gradient_fit.py','tools/GvmGradientKeys.java',
         'tools/GvmDimensionalFit.java','tools/gvm_dimension_controls.py',
         'tools/gvm_dimension_qualification.py','tools/gvm_dimension_audit.py',
         'tools/gvm_study_io.py','docs/GVM_GRADIENT_STUDY.md')


def freeze(path):
    save(path,dict(protocol='gvm-gradient-v1',seeds=list(range(940001,940009)),
        dimensions=[2,3],families=list(FAMILIES),components=[1,2,3],
        train=2000,test=20000,starts=3,phasePoints=20000000,iterations=50,
        angularCalls=400,maxSeconds=120,maxStudySeconds=10800,maxFits=640,
        expectedConfirmationFits=512,qualifiedRate=.95,completionGain=.20,
        noninferiority=.02,alpha=.05,comparisons=24,
        sourceSha256={name:sha(name) for name in SOURCES},
        versions=dict(python=platform.python_version(),numpy=np.__version__,scipy=scipy.__version__),
        frozenUnix=time.time()))


def keys(classes,n,seed):
    return np.array(json.loads(subprocess.check_output(
        ['java','-cp',str(classes),'GvmGradientKeys',str(n),str(seed)],text=True)))


def generate(d,family,seed,n,test_n):
    kind,law=c.truth(d,family);sample=c.sample_gvm if kind=='gvm' else c.sample_gaussian
    streams=np.random.SeedSequence([seed,d,FAMILIES.index(family)]).spawn(2)
    train=sample(np.random.Generator(np.random.PCG64DXSM(streams[0])),law,n)
    test=sample(np.random.Generator(np.random.PCG64DXSM(streams[1])),law,test_n)
    center,lower=c.whitening(train)
    return train,test,center,lower


def develop(output,classes,jar):
    records=[];command=java_command(classes,jar)
    for d in (2,3):
        for family in FAMILIES:
            seed=250000+d*100+FAMILIES.index(family)
            train,test,center,lower=generate(d,family,seed,500,2000)
            data=c.transform(train,center,lower);third=keys(classes,len(data),seed)
            path=output/f'dev-{d}-{family}.txt';write_data(path,data)
            for k in (1,2,3):
                fit=g.fit(data,k,seed,third_keys=third)
                score=float(c.gvm_log(data,fit).sum()) if fit['components'] else None
                if score is not None:assert abs(score-fit['trainScore'])<1e-8*len(data)
                assert fit['phasePoints']<=20000000
                records.append(dict(d=d,family=family,k=k,fit=fit))
                print(d,family,k,fit['status'],fit['phasePoints'],flush=True)
            # Baseline on each development cell for a pipeline smoke check.
            baseline=invoke(command,path,output/f'dev-{d}-{family}-baseline.log',2,seed)
            records.append(dict(d=d,family=family,k=2,method='bobyqa',fit=baseline))
    for d in (2,3):
        for j,label in enumerate(('weak','overlap','minority','misspecified')):
            rng=np.random.Generator(np.random.PCG64DXSM(260000+d*100+j));_,law=c.truth(d,'seam')
            if label=='weak':
                for comp in law['components']:comp['kappa']=.01
            if label=='overlap':
                for comp in law['components']:comp['mean']=[0.]*d
            if label=='minority':law['components'][0]['weight']=.97;law['components'][1]['weight']=.03
            raw=c.sample_gvm(rng,law,500)
            if label=='misspecified':
                raw[:,:-1]=rng.standard_t(4,(500,d));raw[:,-1]=c.wrap(np.sin(raw[:,0])+rng.vonmises(0,2,500))
            center,lower=c.whitening(raw);data=c.transform(raw,center,lower)
            write_data(output/f'adverse-{d}-{label}.txt',data)
            fit=g.fit(data,2,260000+d*100+j)
            if fit['components']:assert np.all(np.isfinite(c.gvm_log(data,fit)))
            records.append(dict(d=d,adverse=label,k=2,fit=fit))
            print('adverse',d,label,fit['status'],flush=True)
    save(output/'development.json',dict(records=records,heldOut=False))


def run(output,protocol,classes,jar):
    config=json.loads(protocol.read_text())
    for path,digest in config['sourceSha256'].items():assert sha(path)==digest,path
    begun=time.time();rows=[];hashes={};command=java_command(classes,jar)
    with (output/'outcomes.jsonl').open('x',encoding='utf-8') as journal:
        grant(output/'outcomes.jsonl')
        for d in config['dimensions']:
            for family in config['families']:
                for seed in config['seeds']:
                    if time.time()-begun>config['maxStudySeconds']:raise TimeoutError('Whole study budget exhausted')
                    raw,test,center,lower=generate(d,family,seed,config['train'],config['test'])
                    data=c.transform(raw,center,lower);evaluation=c.transform(test,center,lower)
                    name=f'd{d}-{family}-{seed}';path=output/(name+'.txt');write_data(path,data)
                    third=keys(classes,len(data),seed)
                    npz=output/(name+'.npz')
                    with npz.open('xb') as stream:
                        grant(npz);np.savez_compressed(stream,train=raw,test=test,center=center,lower=lower,thirdKeys=third)
                    hashes[path.name]=sha(path);hashes[npz.name]=sha(npz)
                    configurations=[('primary',k,20000000,50,m) for k in (1,2,3) for m in ('analytic','bobyqa')]
                    configurations.append(('gaussian',4,0,50,'wrapped'))
                    if seed in config['seeds'][:2]:
                        configurations.append(('diagnostic',4,0,200,'wrapped'))
                        if family in ('curved','seam'):
                            configurations += [('diagnostic',3,b,i,m) for b,i in ((2000000,50),(8000000,50),(20000000,200)) for m in ('analytic','bobyqa')]
                    for section,k,cap,iterations,method in configurations:
                        if len(rows)>=config['maxFits'] or time.time()-begun>config['maxStudySeconds']:
                            raise TimeoutError('Study fit/time ceiling exceeded')
                        label=f'{name}-{section}-{method}-{k}-{cap}-{iterations}'
                        if method=='analytic':
                            fit=g.fit(data,k,seed,max_points=cap,iterations=iterations,third_keys=third)
                        elif method=='bobyqa':
                            fit=invoke(command,path,output/(label+'.log'),k,seed,max_points=cap,max_iter=iterations)
                        else:fit=c.fit_gaussian(data,k,True,seed,iterations=iterations)
                        value=None
                        if fit['components']:
                            evaluate=c.gaussian_log if method=='wrapped' else c.gvm_log
                            value=float(evaluate(evaluation,fit).mean()-np.log(np.diag(lower)).sum())
                        row=dict(d=d,family=family,seed=seed,section=section,k=k,cap=cap,
                                 iterations=iterations,method=method,logScore=value,fit=fit)
                        rows.append(row);journal.write(json.dumps(row,allow_nan=False)+'\n');journal.flush()
                    print(f'{name}: {len(rows)} fits; {time.time()-begun:.1f}s',flush=True)
    assert len(rows)==config['expectedConfirmationFits']
    result=dict(protocolSha256=sha(protocol),startedUnix=begun,finishedUnix=time.time(),
                seconds=time.time()-begun,inputSha256=hashes,records=rows)
    save(output/'results.json',result)


def inventory(rows,seeds):
    expected=set()
    for d in (2,3):
        for family in FAMILIES:
            for seed in seeds:
                expected.update((d,family,seed,'primary',k,20000000,50,m) for k in (1,2,3) for m in ('analytic','bobyqa'))
                expected.add((d,family,seed,'gaussian',4,0,50,'wrapped'))
                if seed in seeds[:2]:
                    expected.add((d,family,seed,'diagnostic',4,0,200,'wrapped'))
                    if family in ('curved','seam'):
                        expected.update((d,family,seed,'diagnostic',3,b,i,m) for b,i in ((2000000,50),(8000000,50),(20000000,200)) for m in ('analytic','bobyqa'))
    fields=('d','family','seed','section','k','cap','iterations','method')
    actual=[tuple(r[f] for f in fields) for r in rows]
    if len(actual)!=len(set(actual)) or set(actual)!=expected:raise ValueError('Missing/duplicate/unexpected fits')


def summarize(results,protocol,output):
    result=json.loads(results.read_text());config=json.loads(protocol.read_text());rows=result['records']
    assert result['protocolSha256']==sha(protocol);inventory(rows,config['seeds'])
    groups=[];comparisons=[];all_complete=True;all_noninferior=True
    for d in (2,3):
        for family in FAMILIES:
            for k in (1,2,3):
                paired={}
                for method in ('analytic','bobyqa'):
                    selected=[r for r in rows if (r['section'],r['d'],r['family'],r['k'],r['method'])==('primary',d,family,k,method)]
                    paired[method]={r['seed']:r for r in selected};counts=Counter(r['fit']['status'] for r in selected)
                    groups.append(dict(d=d,family=family,k=k,method=method,status=dict(counts),
                        medianSeconds=float(np.median([r['fit']['seconds'] for r in selected])),
                        medianPhasePoints=float(np.median([r['fit']['phasePoints'] for r in selected]))))
                    if method=='analytic':all_complete &= counts['Converged']/len(selected)>=config['qualifiedRate']
                diffs=[paired['analytic'][s]['logScore']-paired['bobyqa'][s]['logScore'] for s in config['seeds']
                       if paired['analytic'][s]['logScore'] is not None and paired['bobyqa'][s]['logScore'] is not None]
                mean=lo=hi=None;passed=False
                if len(diffs)==len(config['seeds']):
                    mean=float(np.mean(diffs));radius=float(t.ppf(1-config['alpha']/(2*config['comparisons']),len(diffs)-1)*np.std(diffs,ddof=1)/np.sqrt(len(diffs)))
                    lo=mean-radius;hi=mean+radius;passed=lo > -config['noninferiority']
                all_noninferior &= passed
                comparisons.append(dict(d=d,family=family,k=k,pairs=len(diffs),mean=mean,lower=lo,upper=hi,passed=passed))
    primary=[r for r in rows if r['section']=='primary']
    rates={m:float(np.mean([r['fit']['status']=='Converged' for r in primary if r['method']==m and r['k']>1])) for m in ('analytic','bobyqa')}
    gain=rates['analytic']-rates['bobyqa']
    counts={f'{section}/{method}':dict(Counter(r['fit']['status'] for r in rows if r['section']==section and r['method']==method))
            for section,method in sorted(set((r['section'],r['method']) for r in rows))}
    summary=dict(fits=len(rows),completeGrid=True,seconds=result['seconds'],counts=counts,groups=groups,
        pairedScoreComparisons=comparisons,multicomponentCompletion=rates,completionGain=gain,
        gates=dict(candidateCellCompletion=bool(all_complete),scoreNoninferiority=bool(all_noninferior),
                   completionGain=bool(gain>=config['completionGain'])),
        decision='test-only; no promotion',resultsSha256=sha(results),protocolSha256=sha(protocol))
    save(output,summary);print(json.dumps({k:v for k,v in summary.items() if k not in ('groups','pairedScoreComparisons')},indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['freeze','develop','run','summarize'])
    parser.add_argument('output',type=Path);parser.add_argument('--protocol',type=Path)
    parser.add_argument('--results',type=Path);parser.add_argument('--classes',type=Path);parser.add_argument('--jar',type=Path)
    args=parser.parse_args()
    if args.mode=='freeze':freeze(args.output)
    elif args.mode=='develop':develop(args.output,args.classes,args.jar)
    elif args.mode=='run':run(args.output,args.protocol,args.classes,args.jar)
    else:summarize(args.results,args.protocol,args.output)
