"""INF-15 exact schedule controls and frozen all-outcome analysis; research only."""
import argparse
from collections import defaultdict
from fractions import Fraction as F
import hashlib
import itertools
import json
import math
from pathlib import Path
import sys
import time
import numpy as np
from continuous_filter_reference import Output, normal
from particle_diversity_reference import sha, binomial_upper

TRAIN=('growth','growth-sharp','persistent-modes','late','gaussian')
SCENARIOS=TRAIN+('switching',)
CANDIDATES=('strat','ess25','ess50','ess75')
BASELINES=[(m,n) for m in ('bootstrap','guided','auxiliary') for n in (64,256,1024)]
FIELDS='scenario method n seed t status logZ mean second variance positive ess parents work draws likelihoods targets proposals guides lookahead ancestors components resamplings nanos error'.split()
INTEGER='n seed t parents work draws likelihoods targets proposals guides lookahead ancestors components resamplings nanos'.split()
REAL='logZ mean second variance positive ess'.split()

def offspring(weights, scheme):
    """Exact N=2 parent-index probabilities, including independent stratified intervals."""
    assert len(weights)==2 and sum(weights)==1
    def p(j,i):
        if scheme=='multi':return weights[i]
        lo,hi=F(j,2),F(j+1,2)
        left=F(0) if i==0 else weights[0]
        right=weights[0] if i==0 else F(1)
        return 2*max(F(0),min(hi,right)-max(lo,left))
    return [(indices,math.prod(p(j,i) for j,i in enumerate(indices))) for indices in itertools.product(range(2),repeat=2)]

def finite():
    prior=[F(1,3),F(2,3)];g0=[F(2,5),F(4,5)];g1=[F(9,10),F(1,10)]
    trans=[[F(3,4),F(1,4)],[F(1,5),F(4,5)]]
    expected=[sum(prior[i]*g0[i]*trans[i][j] for i in range(2))*g1[j] for j in range(2)]
    results=[]
    for threshold in (F(0),F(1,4),F(1,2),F(3,4),F(1)):
        for scheme in ('multi','strat'):
            result=[F(0),F(0)]
            for initial in itertools.product(range(2),repeat=2):
                prob=math.prod(prior[i] for i in initial);z0=sum(g0[i] for i in initial)/2
                weights=[g0[i]/(2*z0) for i in initial]
                resample=threshold==1 or 1/(2*sum(w*w for w in weights))<threshold
                ancestors=offspring(weights,scheme) if resample else [((0,1),F(1))]
                base=[F(1,2)]*2 if resample else weights
                for a,ap in ancestors:
                    for draw in itertools.product(range(2),repeat=2):
                        dp=math.prod(trans[initial[a[i]]][draw[i]] for i in range(2))
                        for j in range(2):result[j]+=prob*ap*dp*z0*sum(base[i]*g1[j] for i in range(2) if draw[i]==j)
            assert result==expected,(threshold,scheme,result)
            results.append(dict(threshold=str(threshold),scheme=scheme,unnormalized=list(map(str,result))))
    assert all(p==0 for a,p in offspring([F(1),F(0)],'strat') if 1 in a)
    assert sum(F(1,2)*F(2,5) for _ in range(2))==F(2,5)
    return results

def prepare(out,base):
    base=Path(base);data=json.loads((base/'reference.json').read_text());assert data['passed']
    refs=data['references'];x=np.array([-1.,0.,1.]);p=np.array([.45,.1,.45]);z=0.;rows=[]
    transition=np.array([[.94,.05,.01],[.1,.8,.1],[.01,.05,.94]])
    for t in range(32):
        if t:p=p@transition
        values=p*normal(.8,np.abs(x) if t<24 else x,.35);increment=float(values.sum());p=values/increment;z+=math.log(increment)
        rows.append(dict(t=t,logZ=z,mean=float(p@x),second=float(p@(x*x)),positive=float(p[2])))
    refs['switching']=rows
    out.write('fixtures.tsv',(base/'fixtures.tsv').read_text())
    out.json('reference.json',dict(passed=True,references=refs,finite=finite(),base_reference_sha256=sha(base/'reference.json'),
        base_fixture_sha256=sha(base/'fixtures.tsv'),python=sys.version,numpy=np.__version__))
    print('Exact finite controls passed; original references preserved; switching forward recursion complete.')

def configs(chosen=None):
    return [(m,256) for m in ((chosen,) if chosen else CANDIDATES)+('single','double')]+BASELINES

def read(path,configurations,seeds,scenarios):
    groups=defaultdict(list);seen=set();digest=hashlib.sha256();failures=[]
    with Path(path).open(encoding='utf-8-sig') as f:
        for line in f:
            if 'SCHEDULE_ROW ' not in line:continue
            fields=line.split('SCHEDULE_ROW ',1)[1].rstrip('\r\n').split('\t')
            assert len(fields)==len(FIELDS)
            r=dict(zip(FIELDS,fields))
            for k in INTEGER:r[k]=int(r[k])
            for k in REAL:r[k]=float(r[k])
            key=(r['scenario'],r['method'],r['n'],r['seed'],r['t'])
            assert key not in seen,'Duplicate row';seen.add(key)
            digest.update('\t'.join(v for k,v in zip(FIELDS,fields) if k!='nanos').encode())
            if r['status']!='ok':failures.append(key+(r['status'],r['error']))
            groups[key[:3]].append(r)
    expected={(s,m,n,k,t) for s in scenarios for m,n in configurations for k in seeds for t in range(40 if s=='late' else 32 if s=='switching' else 20)}
    assert seen==expected,'Missing/unexpected outcome rows'
    return groups,digest.hexdigest(),failures

def summarize(groups,refs):
    results=[]
    for (s,m,n),rows in groups.items():
        byseed=defaultdict(list)
        for r in rows:byseed[r['seed']].append(r)
        mse=[];depleted=0
        for seed,rs in sorted(byseed.items()):
            eligible=[r for r in rs if .1<=refs[s][r['t']]['positive']<=.9]
            mse.append(float(np.mean([(r['positive']-refs[s][r['t']]['positive'])**2 for r in eligible])) if eligible else None)
            depleted+=any(not .01<=r['positive']<=.99 for r in eligible)
        final=[r for r in rows if r['t']==len(refs[s])-1];truth=refs[s][-1]
        rmse={metric:float(np.sqrt(np.mean([(r[metric]-(truth['second']-truth['mean']**2 if metric=='variance' else truth[metric]))**2 for r in final]))) for metric in ('mean','variance','positive')}
        errors={}
        for label,values in [('relative_evidence',[math.expm1(r['logZ']-truth['logZ']) for r in final]),('log_evidence',[r['logZ']-truth['logZ'] for r in final])]:
            errors[label]=dict(bias=float(np.mean(values)),rmse=float(np.sqrt(np.mean(np.square(values)))))
        result=dict(scenario=s,method=m,n=n,mse_by_seed=mse,integrated_rmse=math.sqrt(np.mean(mse)) if all(x is not None for x in mse) else None,
            depleted=depleted,depletion_upper=binomial_upper(depleted,len(byseed)),rmse=rmse,evidence_errors=errors,
            mean_final={k:float(np.mean([r[k] for r in final])) for k in ['work','nanos','draws','likelihoods','targets','proposals','guides','lookahead','ancestors','components','resamplings']})
        results.append(result)
    return results

def select(out,log,reference):
    refs=json.loads(Path(reference).read_text())['references']
    groups,_,failures=read(log,configs(),range(30001,30017),TRAIN)
    out.json('failures.json',failures)
    assert not failures,'Calibration failures; do not select successful-only runs'
    summary=summarize(groups,refs)
    def parts(m,n):return [r for r in summary if r['method']==m and r['n']==n and r['scenario'] in ('persistent-modes','late')]
    scores=[dict(method=m,score=sum(np.mean(r['mse_by_seed']) for r in parts(m,256))*sum(r['mean_final']['work'] for r in parts(m,256))) for m in CANDIDATES]
    chosen=min(scores,key=lambda r:(r['score'],r['method']))['method'];candidate=parts(chosen,256)
    eligible=[(m,n) for m,n in BASELINES if all(next(r for r in parts(m,n) if r['scenario']==c['scenario'])['mean_final']['work']<=c['mean_final']['work'] for c in candidate)]
    assert eligible,'No affordable baseline: no promotion'
    baseline=min(eligible,key=lambda pair:(sum(np.mean(r['mse_by_seed']) for r in parts(*pair)),*pair))
    out.json('selection.json',dict(method=chosen,baseline=list(baseline),scores=scores,summary=summary,log_sha256=sha(log),reference_sha256=sha(reference)))
    print(json.dumps(dict(method=chosen,baseline=baseline,scores=scores)))

def confirm(out,logs,reference,selection):
    assert len(logs)==3
    chosen=json.loads(Path(selection).read_text());refs=json.loads(Path(reference).read_text())['references']
    summaries=[];digests=[];controls=[];failures=[]
    for j,path in enumerate(logs):
        groups,digest,bad=read(path,configs(chosen['method']),range(40001,40257),SCENARIOS)
        failures.extend(bad);digests.append(digest)
        if bad:out.json('failed-outcomes.json',failures);raise ValueError('Failed confirmation outcomes')
        summaries.append(summarize(groups,refs))
        if j==0:
            for (s,m,n),rows in groups.items():
                for t in (0,len(refs[s])//2,len(refs[s])-1):
                    step=[r for r in rows if r['t']==t];truth=refs[s][t]
                    for metric in ('evidence','mean','second','positive'):
                        values=np.array([math.exp(r['logZ']-truth['logZ'])*(1 if metric=='evidence' else r[metric]) for r in step])
                        expected=1 if metric=='evidence' else truth[metric];mean=float(values.mean());se=float(values.std(ddof=1)/16)
                        controls.append(dict(scenario=s,method=m,n=n,t=t,metric=metric,mean=mean,se=se,expected=expected,
                            passed=abs(mean-expected)<=6*se+1e-4*max(1,abs(expected))))
        del groups
    assert len(set(digests))==1,'Scientific replay differs across JVMs'
    def get(j,s,m,n):return next(r for r in summaries[j] if (r['scenario'],r['method'],r['n'])==(s,m,n))
    comparisons=[];regressions=[]
    for s in ('persistent-modes','late','switching'):
        c=get(0,s,chosen['method'],256);b=get(0,s,*chosen['baseline'])
        ce=np.array(c['mse_by_seed']);be=np.array(b['mse_by_seed'])
        ix=np.random.Generator(np.random.PCG64(491317)).integers(256,size=(10000,256))
        available=bool(be.mean()>0 and np.all(be[ix].mean(axis=1)>0))
        ratios=np.sqrt(ce[ix].mean(axis=1)/be[ix].mean(axis=1)) if available else None
        ratio=float(np.sqrt(ce.mean()/be.mean())) if available else None
        upper=float(np.quantile(ratios,1-.05/3)) if available else None
        work_front=[get(0,s,m,n) for m,n in BASELINES if get(0,s,m,n)['mean_final']['work']<=c['mean_final']['work']]
        work_pass=bool(work_front) and c['integrated_rmse']<=.8*min(r['integrated_rmse'] for r in work_front)
        timing=[]
        for j in range(3):
            cj=get(j,s,chosen['method'],256)
            affordable=[get(j,s,m,n) for m,n in BASELINES if get(j,s,m,n)['mean_final']['nanos']<=cj['mean_final']['nanos']]
            timing.append(bool(affordable) and cj['integrated_rmse']<=.8*min(r['integrated_rmse'] for r in affordable))
        depletion=c['depleted']<=b['depleted'] and c['depletion_upper']<=b['depletion_upper']
        comparisons.append(dict(scenario=s,ratio=ratio,upper_familywise=upper,interval95=np.quantile(ratios,[.025,.975]).tolist() if available else None,
            candidate_depleted=c['depleted'],baseline_depleted=b['depleted'],candidate_upper=c['depletion_upper'],baseline_upper=b['depletion_upper'],
            work_gate=work_pass,timing_gates=timing,passed=bool(available and ratio<=.8 and upper<1 and depletion and work_pass and all(timing))))
    for s in ('gaussian','growth'):
        c=get(0,s,chosen['method'],256);b=get(0,s,*chosen['baseline']);truth=refs[s][-1]
        for metric in ('mean','variance','positive'):
            value=truth['second']-truth['mean']**2 if metric=='variance' else truth[metric]
            limit=1.25*b['rmse'][metric]+.01*max(1,abs(value))
            regressions.append(dict(scenario=s,metric=metric,rmse=c['rmse'][metric],limit=limit,passed=c['rmse'][metric]<=limit))
    result=dict(candidate=chosen['method'],baseline=chosen['baseline'],experiment_gates_pass=all(r['passed'] for r in comparisons+controls+regressions),
        comparisons=comparisons,controls=controls,regressions=regressions,summary=summaries,independent_seeds=256,jvm_repetitions=3,failed_outcomes=failures,
        scientific_digests=digests,log_sha256=[sha(p) for p in logs],reference_sha256=sha(reference),selection_sha256=sha(selection),
        frozen_regression='Separate unchanged 450-check run required; not inferred here.')
    out.json('decision.json',result)
    print(json.dumps({k:result[k] for k in ('candidate','baseline','experiment_gates_pass','comparisons','regressions')}))

def main():
    p=argparse.ArgumentParser();p.add_argument('command',choices=['prepare','select','confirm'])
    for name in ('output','base','log','reference','selection','acl-script'):p.add_argument('--'+name,required=name=='output')
    p.add_argument('--logs',nargs='+');a=p.parse_args();out=Output(a.output,a.acl_script)
    print('START_UTC',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),flush=True)
    if a.command=='prepare':prepare(out,a.base)
    elif a.command=='select':select(out,a.log,a.reference)
    else:confirm(out,a.logs,a.reference,a.selection)
    print('COMPLETE_UTC',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),flush=True)
if __name__=='__main__':main()
