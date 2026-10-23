"""INF-16 frozen independent exact oracle and all-outcome confirmation analysis."""
import argparse
from fractions import Fraction as F
import itertools
import json
import math
from pathlib import Path
import sys
import numpy as np
from continuous_filter_reference import Output
from resampling_schedule_reference import offspring, read, SCENARIOS

CONFIGS=[(m,512 if m=='big-s-50' else 128) for m in
         ('b-m-a','g-m-a','a-m-a','b-s-50','g-s-50','a-s-50','bad-s-50','big-s-50','old-guided','old-auxiliary')]

def finite():
    """Exact rational enumeration; not a call into Figaro's weight implementation."""
    prior=[F(1,3),F(2,3)]; q0=[F(4,5),F(1,5)]
    trans=[[F(3,4),F(1,4)],[F(1,5),F(4,5)]]
    proposal=[[F(1,4),F(3,4)],[F(4,5),F(1,5)]]
    g0=[F(2,5),F(4,5)];g1=[F(9,10),F(1,10)]
    expected=[sum(prior[i]*g0[i]*trans[i][j] for i in range(2))*g1[j] for j in range(2)]
    results=[]
    for policy,scheme,eta,defense in itertools.product(('always','carry','ess50','ess75'),('multi','strat'),
            ([F(1),F(1)],[F(1,9),F(8)],[F(0),F(0)]),(F(1,5),F(1))):
        result=[F(0),F(0)]
        for initial in itertools.product(range(2),repeat=2):
            prob=math.prod(q0[x] for x in initial)
            raw=[prior[x]*g0[x]/q0[x]/2 for x in initial];z0=sum(raw);w=[x/z0 for x in raw]
            resample=policy=='always' or (policy.startswith('ess') and 1/(2*sum(x*x for x in w))<F(int(policy[3:]),100))
            score=[eta[x] for x in initial];norm=sum(a*b for a,b in zip(w,score))
            r=w if not norm else [defense*w[i]+(1-defense)*w[i]*score[i]/norm for i in range(2)]
            choices=offspring(r,scheme) if resample else [((0,1),F(1))]
            for a,ap in choices:
                for draw in itertools.product(range(2),repeat=2):
                    dp=math.prod(proposal[initial[a[i]]][draw[i]] for i in range(2))
                    masses=[(w[a[i]]/r[a[i]]/2 if resample else w[i])*
                            trans[initial[a[i]]][draw[i]]*g1[draw[i]]/proposal[initial[a[i]]][draw[i]] for i in range(2)]
                    for j in range(2):result[j]+=prob*ap*dp*z0*sum(masses[i] for i in range(2) if draw[i]==j)
        assert result==expected,(policy,scheme,eta,defense,result)
        results.append(dict(policy=policy,scheme=scheme,eta=list(map(str,eta)),defense=str(defense),mass=list(map(str,result))))
    assert expected==[F(93,500),F(23,500)]
    return results

def analyze(paths,reference):
    refs=json.loads(Path(reference).read_text())['references'];checks=[];summaries=[];digests=[];gates=[]
    for rep,path in enumerate(paths,1):
        groups,digest,failures=read(path,CONFIGS,range(70001,70257),SCENARIOS)
        digests.append(digest);gates.append(not failures)
        for (scenario,method,n),rows in groups.items():
            rows=sorted(rows,key=lambda r:(r['seed'],r['t']))
            runs=[[r for r in rows if r['seed']==seed] for seed in range(70001,70257)]
            complete=[all(r['status']=='ok' for r in run) for run in runs]
            summary=dict(repetition=rep,scenario=scenario,method=method,n=n,completed=sum(complete),
                         mean_work=float(np.mean([run[-1]['work'] for run in runs])),
                         mean_ms=float(np.mean([run[-1]['nanos'] for run in runs])/1e6),
                         failure_rows=[{k:r[k] for k in ('seed','t','status','error')} for r in rows if r['status']!='ok'])
            if all(complete):
                truth=refs[scenario];eligible=[t for t,r in enumerate(truth) if .1<=r['positive']<=.9]
                positive=np.array([[r['positive'] for r in run] for run in runs]);error=positive-np.array([r['positive'] for r in truth])
                summary['integrated_rmse']=float(np.sqrt(np.mean(error[:,eligible]**2))) if eligible else None
                summary['depleted']=int(np.sum(np.any((positive[:,eligible]<.01)|(positive[:,eligible]>.99),axis=1))) if eligible else None
                summary['final_rmse']={k:float(np.sqrt(np.mean([(run[-1][k]-truth[-1][k])**2 for run in runs]))) for k in ('mean','second','positive')}
                summary['mean_resamplings']=float(np.mean([run[-1]['resamplings'] for run in runs]))
                summary['work_parts']={k:float(np.mean([run[-1][k] for run in runs])) for k in ('draws','likelihoods','targets','proposals','guides','lookahead','ancestors','components')}
                if rep==1:
                    for t in sorted({0,len(truth)//2,len(truth)-1}):
                        scale=np.exp(np.array([run[t]['logZ'] for run in runs])-truth[t]['logZ'])
                        for metric in ('evidence','mean','second','positive'):
                            target=1.0 if metric=='evidence' else truth[t][metric]
                            values=scale if metric=='evidence' else scale*np.array([run[t][metric] for run in runs])
                            se=float(np.std(values,ddof=1)/16);estimate=float(np.mean(values));tol=6*se+1e-4*max(1,abs(target))
                            checks.append(dict(scenario=scenario,method=method,t=t,metric=metric,estimate=estimate,target=target,se=se,tolerance=tol,
                                               passed=bool(np.all(np.isfinite(values)) and abs(estimate-target)<=tol)))
            else:summary['comparison']='Unavailable: no success-only comparison.'
            summaries.append(summary)
    utility=[]
    for s in ('growth','growth-sharp','persistent-modes','late'):
        group={r['method']:r for r in summaries if r['scenario']==s and r['repetition']==1}
        base=group['b-s-50']
        for m in ('g-m-a','a-m-a','g-s-50','a-s-50'):
            r=group[m]
            if r['completed']==256 and base['completed']==256:
                ratio=r['integrated_rmse']/base['integrated_rmse'];cost=r['mean_work']/base['mean_work']
                utility.append(dict(scenario=s,method=m,rmse_ratio=ratio,work_ratio=cost,mse_work_ratio=ratio**2*cost,
                                    depletion_difference=r['depleted']-base['depleted'],
                                    passed=ratio<=.9 and ratio**2*cost<=.9 and r['depleted']<=base['depleted']+5))
    winners=[m for m in ('g-m-a','a-m-a','g-s-50','a-s-50') if sum(r['passed'] for r in utility if r['method']==m)>=2]
    passed=all(gates) and len(set(digests))==1 and all(c['passed'] for c in checks)
    return dict(passed=passed,utility_passed=bool(winners),promotion_eligible=passed and bool(winners),winners=winners,
                finite=finite(),python=sys.version,numpy=np.__version__,replay=len(set(digests))==1,digests=digests,
                checks=checks,summaries=summaries,utility=utility)

def main():
    p=argparse.ArgumentParser();p.add_argument('--logs',nargs=3);p.add_argument('--reference');p.add_argument('--output',required=True);p.add_argument('--acl-script');a=p.parse_args()
    result=analyze(a.logs,a.reference) if a.logs else dict(passed=True,finite=finite(),python=sys.version,numpy=np.__version__)
    out=Output(a.output,a.acl_script);out.json('results.json',result)
    if a.logs:
        import matplotlib;matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        fig,axes=plt.subplots(1,2,figsize=(12,5),layout='constrained')
        for s in SCENARIOS:
            group=[r for r in result['summaries'] if r['repetition']==1 and r['scenario']==s and r['completed']==256]
            for ax,cost in zip(axes,('mean_work','mean_ms')):
                ax.scatter([r[cost] for r in group],[r['integrated_rmse'] for r in group],label=s)
                for r in group:ax.annotate(r['method'],(r[cost],r['integrated_rmse']),fontsize=5)
                ax.set_xlabel(cost);ax.set_ylabel('Integrated sign-mass RMSE');ax.set_xscale('log');ax.set_yscale('log')
        axes[0].legend(fontsize=7);fig.suptitle('Frozen guided fixed-budget comparison: named synthetic fixtures only')
        image=out.write('comparison.png','');fig.savefig(image,dpi=160);plt.close(fig)
        print(json.dumps({k:result[k] for k in ('passed','utility_passed','promotion_eligible','winners','replay')}))
        print('Checks:',len(result['checks']),'failed:',sum(not r['passed'] for r in result['checks']))
    else:print('Independent rational configurations verified:',len(result['finite']))
    if not result['passed']:raise SystemExit(1)

if __name__=='__main__':main()
