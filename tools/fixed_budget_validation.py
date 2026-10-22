"""INF-15 frozen integration gates; no threshold selection or success-only ranking."""
import argparse
import json
from pathlib import Path
import sys
import numpy as np
from continuous_filter_reference import Output
from resampling_schedule_reference import read, finite, SCENARIOS

CONFIGS=[(f'{m}-{p}',256) for m in ('multi','strat') for p in ('always','25','50','75')]

def analyze(paths, reference):
    refs=json.loads(Path(reference).read_text())['references']
    checks=[]; summaries=[]; digests=[]; gates=[]
    finite_results=finite()
    for repetition,path in enumerate(paths,1):
        groups,digest,failures=read(path,CONFIGS,range(50001,50257),SCENARIOS)
        digests.append(digest)
        for (scenario,method,n),rows in groups.items():
            rows=sorted(rows,key=lambda r:(r['seed'],r['t']))
            sequences=[[r for r in rows if r['seed']==seed] for seed in range(50001,50257)]
            complete=[all(r['status']=='ok' for r in run) for run in sequences]
            refusals=[r for r in rows if r['status']=='refused']
            unexpected=[r for r in rows if r['status'] not in ('ok','refused','unavailable')]
            valid_failures=all(r['error']=='StratifiedPrecisionLoss' for r in refusals)
            # Every incomplete trajectory must have exactly one refusal, then unavailable.
            for run,ok in zip(sequences,complete):
                if not ok:
                    failed=[i for i,r in enumerate(run) if r['status']=='refused']
                    valid_failures &= len(failed)==1 and all(r['status']=='ok' for r in run[:failed[0]]) and all(r['status']=='unavailable' for r in run[failed[0]+1:]) if failed else False
            gates.append(not unexpected and valid_failures and (all(complete) if method.startswith('multi') else True))
            summary=dict(repetition=repetition,scenario=scenario,method=method,n=n,completed=sum(complete),refused=len(refusals),
                         failures=len(unexpected),first_refusal_steps=[r['t'] for r in refusals],
                         inclusive_mean_work=float(np.mean([run[-1]['work'] for run in sequences])),
                         inclusive_mean_ms=float(np.mean([run[-1]['nanos'] for run in sequences])/1e6))
            if all(complete):
                truth=refs[scenario];eligible=[t for t,r in enumerate(truth) if .1<=r['positive']<=.9]
                positive=np.array([[r['positive'] for r in run] for run in sequences])
                error=positive-np.array([r['positive'] for r in truth])
                summary['integrated_sign_rmse']=float(np.sqrt(np.mean(error[:,eligible]**2))) if eligible else None
                summary['depleted']=int(np.sum(np.any((positive[:,eligible]<.01)|(positive[:,eligible]>.99),axis=1))) if eligible else None
                summary['mean_resamplings']=float(np.mean([run[-1]['resamplings'] for run in sequences]))
                summary['final_rmse']={k:float(np.sqrt(np.mean([(run[-1][k]-truth[-1][k])**2 for run in sequences]))) for k in ('mean','second','positive')}
                if repetition==1:
                    for t in sorted({0,len(truth)//2,len(truth)-1}):
                        scale=np.exp(np.array([run[t]['logZ'] for run in sequences])-truth[t]['logZ'])
                        for metric in ('evidence','mean','second','positive'):
                            target=1.0 if metric=='evidence' else truth[t][metric]
                            values=scale if metric=='evidence' else scale*np.array([run[t][metric] for run in sequences])
                            se=float(np.std(values,ddof=1)/16)
                            estimate=float(np.mean(values));tolerance=6*se+1e-4*max(1,abs(target))
                            passed=bool(np.all(np.isfinite(values)) and abs(estimate-target)<=tolerance)
                            checks.append(dict(scenario=scenario,method=method,t=t,metric=metric,estimate=estimate,target=target,se=se,tolerance=tolerance,passed=passed))
            else:
                summary['comparison']='Unavailable: incomplete group; no conditional-success RMSE or efficiency ranking.'
            summaries.append(summary)
    replay=len(set(digests))==1
    return dict(passed=bool(all(gates) and replay and all(c['passed'] for c in checks)),
                scope='Numerical/lifecycle integration, not universal stratified applicability or superiority',
                python=sys.version,numpy=np.__version__,finite=finite_results,replay=replay,digests=digests,
                checks=checks,summaries=summaries)

def main():
    p=argparse.ArgumentParser();p.add_argument('--logs',nargs=3,required=True);p.add_argument('--reference',required=True)
    p.add_argument('--output',required=True);p.add_argument('--acl-script');a=p.parse_args()
    result=analyze(a.logs,a.reference);out=Output(a.output,a.acl_script);out.json('results.json',result)
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    rows=[r for r in result['summaries'] if r['repetition']==1]
    fig,axes=plt.subplots(2,3,figsize=(15,8),layout='constrained')
    for ax,scenario in zip(axes.flat,SCENARIOS):
        group=[r for r in rows if r['scenario']==scenario]
        ax.bar([r['method'] for r in group],[r['completed'] for r in group],color=['#305f82' if r['method'].startswith('multi') else '#b0782e' for r in group])
        ax.set_title(scenario);ax.set_ylim(0,270);ax.set_ylabel('Completed / 256 (refusals retained)');ax.tick_params(axis='x',rotation=65)
    fig.suptitle('Fixed-budget public API: completion by explicit policy; not an accuracy ranking')
    image=out.write('completion.png','');fig.savefig(image,dpi=140);plt.close(fig)
    print(json.dumps(dict(passed=result['passed'],replay=result['replay'],checks=len(result['checks']),
                         failed_checks=sum(not c['passed'] for c in result['checks']),
                         completed=sum(r['completed'] for r in rows),refused=sum(r['refused'] for r in rows))))
    if not result['passed']:raise SystemExit(1)

if __name__=='__main__': main()
