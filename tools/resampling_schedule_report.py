"""Descriptive INF-15 plots/tables; never changes frozen selection or gates."""
import argparse
import json
import os
from pathlib import Path
import numpy as np
from continuous_filter_reference import Output
from resampling_schedule_reference import read, configs, SCENARIOS

def report(out,decision_path,log,reference):
    d=json.loads(Path(decision_path).read_text());refs=json.loads(Path(reference).read_text())['references']
    groups,_,failures=read(log,configs(d['candidate']),range(40001,40257),SCENARIOS)
    assert not failures
    rows=[]
    for (s,m,n),group in groups.items():
        for t,truth in enumerate(refs[s]):
            step=[r for r in group if r['t']==t]
            rows.append(dict(scenario=s,method=m,n=n,t=t,reference_positive=truth['positive'],
                sign_rmse=float(np.sqrt(np.mean([(r['positive']-truth['positive'])**2 for r in step]))),
                mean_ess=float(np.mean([r['ess'] for r in step])),mean_distinct_parents=float(np.mean([r['parents'] for r in step])),
                mean_cumulative_resamplings=float(np.mean([r['resamplings'] for r in step])),
                depleted=sum(not .01<=r['positive']<=.99 for r in step) if .1<=truth['positive']<=.9 else None))
    out.json('step-diagnostics.json',rows)
    cfg=out.root/'matplotlib-config';cfg.mkdir();out.grant(cfg);os.environ['MPLCONFIGDIR']=str(cfg)
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(2,3,figsize=(13,7))
    for col,s in enumerate(('persistent-modes','late','switching')):
        summaries=[r for r in d['summary'][0] if r['scenario']==s]
        for m in ('bootstrap','guided','auxiliary'):
            points=sorted([r for r in summaries if r['method']==m],key=lambda r:r['n'])
            axes[0,col].plot([r['mean_final']['work'] for r in points],[r['integrated_rmse'] for r in points],'o-',label=m)
        for m in (d['candidate'],'single','double'):
            r=next(r for r in summaries if r['method']==m)
            axes[0,col].scatter(r['mean_final']['work'],r['integrated_rmse'],label=m,marker='*',s=75)
            curve=sorted([r for r in rows if r['scenario']==s and r['method']==m],key=lambda r:r['t'])
            axes[1,col].plot([r['t'] for r in curve],[r['sign_rmse'] for r in curve],label=m)
        m,n=d['baseline'];curve=sorted([r for r in rows if (r['scenario'],r['method'],r['n'])==(s,m,n)],key=lambda r:r['t'])
        axes[1,col].plot([r['t'] for r in curve],[r['sign_rmse'] for r in curve],label=f'{m} N={n}')
        axes[0,col].set_xscale('log');axes[0,col].set_xlabel('Mean total counted work');axes[0,col].set_ylabel('Ambiguous-period sign RMSE');axes[0,col].set_title(s)
        axes[1,col].set_xlabel('Step');axes[1,col].set_ylabel('Sign-probability RMSE')
        for ax in axes[:,col]:ax.legend(fontsize=7)
    fig.suptitle('Held-out point estimates; 256 independent seeds. Three JVMs are timing repeats.')
    fig.tight_layout();path=out.write('schedule-comparison.png','');fig.savefig(path,dpi=140);plt.close(fig)
    lines=['# Descriptive schedule comparison','',f"Candidate: {d['candidate']}; baseline: {d['baseline']}; experiment gates: {d['experiment_gates_pass']}",'',
           '| Fixture | Integrated RMSE ratio | Family-wise upper | Candidate / baseline depleted | Timing gates |','| --- | ---: | ---: | --- | --- |']
    for r in d['comparisons']:lines.append(f"| {r['scenario']} | {r['ratio']:.6g} | {r['upper_familywise']:.6g} | {r['candidate_depleted']} / {r['baseline_depleted']} | {r['timing_gates']} |")
    out.write('summary.md','\n'.join(lines)+'\n')

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for key in ('output','decision','log','reference'):p.add_argument('--'+key,required=True)
    p.add_argument('--acl-script');a=p.parse_args()
    report(Output(a.output,a.acl_script),a.decision,a.log,a.reference)
