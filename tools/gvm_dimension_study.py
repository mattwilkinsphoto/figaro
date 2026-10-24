"""Frozen confirmation runner and all-outcome analysis for GVM-05.

Only invoked after qualification. Writes new evidence, never replaces previous
results. Inputs are explicitly generated synthetic controls, not application data.
"""
import argparse
from collections import Counter
import json
import math
import os
from pathlib import Path
import platform
import time
import numpy as np
import scipy
from scipy.stats import t
import psutil
import gvm_dimension_controls as c
from gvm_dimension_qualification import invoke, java_command, write_data
from gvm_study_io import grant, save, sha, text_file

SOURCES = ['tools/GvmDimensionalFit.java','tools/GvmKernelCompatibility.scala',
           'tools/gvm_dimension_controls.py','tools/test_gvm_dimension_controls.py',
           'tools/gvm_dimension_qualification.py','tools/gvm_dimension_study.py','tools/gvm_study_io.py',
           'docs/GVM_HIGH_DIMENSIONAL_PROTOCOL.md']
FAMILIES = ('curved','seam','local','wrapped')
METHODS = ('gvm','chart','wrapped')


def freeze(path):
    config = dict(protocol='gvm-dimensional-v1', dimensions=[2,3], families=list(FAMILIES), methods=list(METHODS),
        confirmationSeeds=list(range(830001,830017)), developmentSeedRange=[150000,179999],
        train=2000, validation=2000, test=20000, predictive=20000,
        budgets={'2':[12,39], '3':[20,62]}, components={'gvm':[1,3],'chart':[1,4],'wrapped':[1,4]},
        starts=3,maxIterations=50,maxAngularEvaluations=400,maxPhasePoints=20000000,maxFitSeconds=120,
        maxTotalFitAttempts=1024,maxTotalSerialSeconds=28800,previousDevelopmentAndControlFitsUpperBound=128,
        practicalGainNats=.05, gaussianNoninferiorityNats=.02, qualifiedRate=.95,
        confidenceFamilyAlpha=.05, plannedComparisons=32,
        statusRequired='Converged', sourceSha256={p:sha(p) for p in SOURCES},
        versions=dict(python=platform.python_version(),numpy=np.__version__,scipy=scipy.__version__,psutil=psutil.__version__),
        frozenUnix=time.time())
    save(path,config);print(json.dumps(config,indent=2))


def masks(data):
    return np.column_stack((data[:,0]<0, np.abs(c.wrap(data[:,-1]-math.pi))<.5,
                            (data[:,0]>0)&(data[:,-1]>0), np.linalg.norm(data[:,:-1],axis=1)>3))


def evaluate(data, validation, fit, method, truth_log, center, lower, rng):
    if not fit['components']:
        return dict(logScore=None, forwardKl=None, validationLogScore=None, regionErrors=None,
                    evaluationSeconds=0., samplingSeconds=0.)
    scorer = c.gvm_log if method=='gvm' else c.gaussian_log
    started=time.perf_counter(); scores=scorer(data,fit); validation_scores=scorer(validation,fit)
    evaluation=time.perf_counter()-started
    if not np.all(np.isfinite(scores)) or not np.all(np.isfinite(validation_scores)):
        raise ArithmeticError('Nonfinite held-out density')
    sampler=c.sample_gvm if method=='gvm' else c.sample_gaussian
    started=time.perf_counter(); draws=sampler(rng,fit,len(data)); sampling=time.perf_counter()-started
    original=np.column_stack((data[:,:-1]@lower.T+center,data[:,-1]))
    draws[:,:-1]=draws[:,:-1]@lower.T+center
    errors=masks(draws).mean(axis=0)-masks(original).mean(axis=0)
    return dict(logScore=float(scores.mean()),forwardKl=float(np.mean(truth_log-scores)),
                validationLogScore=float(validation_scores.mean()),regionErrors=errors.tolist(),
                evaluationSeconds=evaluation,samplingSeconds=sampling)


def run(output, protocol, command):
    config=json.loads(protocol.read_text())
    for path,digest in config['sourceSha256'].items():
        assert sha(path)==digest, 'Frozen source drift: '+path
    begin=time.time();records=[];input_hashes={};fits=0
    for d in config['dimensions']:
        for family_index,family in enumerate(config['families']):
            kind,law=c.truth(d,family)
            for index,seed in enumerate(config['confirmationSeeds']):
                key=f'd{d}-{family}-{seed}';child=np.random.SeedSequence([seed,d,family_index]).spawn(10)
                rngs=[np.random.Generator(np.random.PCG64DXSM(s)) for s in child]
                sampler=c.sample_gvm if kind=='gvm' else c.sample_gaussian
                original=[sampler(rngs[j],law,config[name]) for j,name in enumerate(('train','validation','test'))]
                preprocessing_begin=time.perf_counter()
                center,lower=c.whitening(original[0]);data=[c.transform(a,center,lower) for a in original]
                preprocessing_seconds=time.perf_counter()-preprocessing_begin
                path=output/(key+'.npz')
                with path.open('xb') as stream:
                    grant(path);np.savez_compressed(stream,train=original[0],validation=original[1],test=original[2],center=center,lower=lower)
                input_hashes[path.name]=sha(path)
                training_path=output/(key+'.txt');write_data(training_path,data[0]);input_hashes[training_path.name]=sha(training_path)
                truth_log=(c.gvm_log if kind=='gvm' else c.gaussian_log)(original[2],law)+np.log(np.diag(lower)).sum()
                cell=[]
                for budget in range(2):
                    # Rotate execution order by seed to avoid a fixed method/order effect.
                    order=list(METHODS);shift=(index+budget)%3;order=order[shift:]+order[:shift]
                    for method in order:
                        if fits+config['previousDevelopmentAndControlFitsUpperBound']>=config['maxTotalFitAttempts']:
                            raise RuntimeError('Study fit limit; incomplete grid cannot pass')
                        if time.time()-begin>config['maxTotalSerialSeconds']:
                            raise RuntimeError('Study time limit; incomplete grid cannot pass')
                        k=config['components'][method][budget];fit_seed=seed+100*d+budget
                        start=time.perf_counter()
                        if method=='gvm':
                            fit=invoke(command,training_path,output/f'{key}-b{budget}-gvm.log',k,fit_seed,
                                config['maxAngularEvaluations'],config['maxIterations'],config['maxPhasePoints'],config['maxFitSeconds'])
                        else:
                            fit=c.fit_gaussian(data[0],k,method=='wrapped',fit_seed)
                        elapsed=time.perf_counter()-start;fits+=1
                        metrics=evaluate(data[2],data[1],fit,method,truth_log,center,lower,rngs[3+budget*3+METHODS.index(method)])
                        record=dict(d=d,family=family,seed=seed,budget=budget,parameterCeiling=config['budgets'][str(d)][budget],
                            method=method,k=k,status=fit['status'],fit=fit,
                            fitEndToEndSeconds=fit.get('processSeconds',elapsed)+preprocessing_seconds,
                            sharedPreprocessingSeconds=preprocessing_seconds,
                            pythonProcessRss=psutil.Process().memory_info().rss,**metrics)
                        cell.append(record);records.append(record)
                save(output/(key+'-results.json'),cell)
                print(json.dumps(dict(completed=fits,cell=key,statuses=[r['status'] for r in cell],elapsedSeconds=time.time()-begin)),flush=True)
    assert fits==768
    save(output/'confirmation-results.json',dict(protocolSha256=sha(protocol),sourceSha256=config['sourceSha256'],
         startedUnix=begin,finishedUnix=time.time(),fits=fits,inputSha256=input_hashes,records=records))


def summarize(results_path, protocol, destination):
    result=json.loads(results_path.read_text());config=json.loads(protocol.read_text())
    assert result['protocolSha256']==sha(protocol)
    rows=result['records'];expected={(d,f,s,b,m) for d in (2,3) for f in FAMILIES
                                    for s in config['confirmationSeeds'] for b in (0,1) for m in METHODS}
    keys=[(r['d'],r['family'],r['seed'],r['budget'],r['method']) for r in rows]
    assert len(keys)==len(set(keys)) and set(keys)==expected
    for r in rows:
        assert r['k']==config['components'][r['method']][r['budget']]
        for start in r['fit']['starts']:
            assert np.all(np.diff(start['trace']) >= -config['train']*1e-8)
        if r['method']=='gvm':assert r['fit']['phasePoints']<=config['maxPhasePoints']
        else:assert all(s['maximumRelativeWindingTail']<=1e-12 for s in r['fit']['starts'])
    by_key={k:r for k,r in zip(keys,rows)};cells=[];comparisons=[]
    reliability=True;utility=True
    for d in (2,3):
        for f in FAMILIES:
            for b in (0,1):
                for m in METHODS:
                    selected=[by_key[d,f,s,b,m] for s in config['confirmationSeeds']]
                    statuses=Counter(r['status'] for r in selected)
                    qualified=statuses['Converged']/len(selected);reliability &= qualified>=.95
                    finite=[r for r in selected if r['logScore'] is not None]
                    cells.append(dict(d=d,family=f,budget=b,method=m,statuses=dict(statuses),qualifiedRate=qualified,
                        finiteModels=len(finite),meanLogScore=float(np.mean([r['logScore'] for r in finite])) if finite else None,
                        meanForwardKl=float(np.mean([r['forwardKl'] for r in finite])) if finite else None,
                        maxAbsoluteRegionError=max((max(map(abs,r['regionErrors'])) for r in finite),default=None),
                        medianFitSeconds=float(np.median([r['fit']['seconds'] for r in selected])),
                        medianEndToEndSeconds=float(np.median([r['fitEndToEndSeconds'] for r in selected])),
                        medianEvaluationSeconds=float(np.median([r['evaluationSeconds'] for r in finite])) if finite else None,
                        medianSamplingSeconds=float(np.median([r['samplingSeconds'] for r in finite])) if finite else None))
                for m in ('chart','wrapped'):
                    pairs=[(by_key[d,f,s,b,'gvm'],by_key[d,f,s,b,m]) for s in config['confirmationSeeds']]
                    differences=[a['logScore']-z['logScore'] for a,z in pairs if a['logScore'] is not None and z['logScore'] is not None]
                    count=len(differences);mean=float(np.mean(differences)) if count else None
                    half=float(t.ppf(1-.05/(2*32),count-1)*np.std(differences,ddof=1)/math.sqrt(count)) if count>1 else None
                    lower=mean-half if half is not None else None;upper=mean+half if half is not None else None
                    gate='descriptive';passed=None
                    if f in ('curved','seam') and b==1:
                        gate='curved-gain';passed=count==16 and lower is not None and lower>.05
                    if f in ('local','wrapped'):
                        gate='gaussian-noninferiority';passed=count==16 and lower is not None and lower>=-.02
                    if passed is not None:utility &= passed
                    comparisons.append(dict(d=d,family=f,budget=b,control=m,finitePairs=count,
                        meanGvmMinusControl=mean,simultaneousLower=lower,simultaneousUpper=upper,gate=gate,passed=passed,
                        interpretation='finite-model descriptive comparison; unqualified fits do not become qualified'))
    summary=dict(protocolSha256=sha(protocol),resultsSha256=sha(results_path),completeGrid=True,fits=len(rows),
        decision='eligible-for-separate-review' if reliability and utility else 'no-promotion',
        reliabilityGate=reliability,utilityGate=utility,statusCounts={m:dict(Counter(r['status'] for r in rows if r['method']==m)) for m in METHODS},
        elapsedSeconds=result['finishedUnix']-result['startedUnix'],cells=cells,comparisons=comparisons,
        limitations=['Synthetic bounded controls only','Local optimization; no global optimum or mode completeness guarantee',
                     'Cost is backend/implementation dependent','Conditional numerical metrics include finite budget-limited models; statuses remain failures'])
    save(destination,summary);print(json.dumps({k:v for k,v in summary.items() if k not in ('cells','comparisons')},indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['freeze','run','summarize'])
    parser.add_argument('output',type=Path);parser.add_argument('--protocol',type=Path)
    parser.add_argument('--classes',type=Path);parser.add_argument('--jar',type=Path);parser.add_argument('--results',type=Path)
    args=parser.parse_args()
    if args.mode=='freeze':freeze(args.output)
    elif args.mode=='run':run(args.output,args.protocol,java_command(args.classes,args.jar))
    else:summarize(args.results,args.protocol,args.output)
