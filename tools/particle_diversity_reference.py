"""INF-14: independent references and predeclared analysis; never an inference backend."""
import argparse
from fractions import Fraction as F
import hashlib
import itertools
import json
import math
import os
from pathlib import Path
import sys
import time
import numpy as np
from continuous_filter_reference import Output, normal, simpson_uniform

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def mh_matrix(target, q):
    total=sum(target); pi=[x/total for x in target]
    p=[[F(0),F(0)],[F(0),F(0)]]
    for i in range(2):
        j=1-i
        p[i][j]=q[i][j]*min(F(1),pi[j]*q[j][i]/(pi[i]*q[i][j])) if pi[i] else F(0)
        p[i][i]=1-p[i][j]
    assert all(sum(r)==1 for r in p)
    assert pi[0]*p[0][1]==pi[1]*p[1][0]
    return p

def evolve(weights,p,k):
    for _ in range(k): weights=[sum(weights[i]*p[i][j] for i in range(2)) for j in range(2)]
    return weights

def finite_control():
    prior=[F(1,3),F(2,3)];g0=[F(2,5),F(4,5)];g1=[F(9,10),F(1,10)]
    trans=[[F(3,4),F(1,4)],[F(1,5),F(4,5)]];q=[[F(2,3),F(1,3)],[F(3,4),F(1,4)]]
    gamma0=[prior[i]*g0[i] for i in range(2)]
    truth=[sum(gamma0[i]*trans[i][j] for i in range(2))*g1[j] for j in range(2)]
    controls=[]
    for k in (0,1,4,8):
        result=[F(0),F(0)]
        for initial in itertools.product(range(2),repeat=2):
            prob=math.prod(prior[i] for i in initial);z0=sum(g0[i] for i in initial)/2
            weights=[sum(g0[i] for i in initial if i==j)/(2*z0) for j in range(2)]
            moved=evolve(weights,mh_matrix(gamma0,q),k)
            for old in itertools.product(range(2),repeat=2):
                op=prob*math.prod(moved[i] for i in old)
                predictive=[sum(trans[i][j] for i in old)/2 for j in range(2)]
                gamma=[predictive[j]*g1[j] for j in range(2)]
                kernel=mh_matrix(gamma,q)
                for draw in itertools.product(range(2),repeat=2):
                    dp=math.prod(predictive[i] for i in draw);z1=sum(g1[i] for i in draw)/2
                    w=[sum(g1[i] for i in draw if i==j)/(2*z1) for j in range(2)]
                    post=evolve(w,kernel,k)
                    for j in range(2): result[j]+=op*dp*z0*z1*post[j]
        assert result==truth,(k,result,truth)
        controls.append(dict(sweeps=k,unnormalized=[str(x) for x in result],passed=True))
    zero=mh_matrix([F(1),F(0)],q)
    assert zero[0][1]==0
    return dict(two_step=controls,zero_support=True,matrix=[[str(v) for v in r] for r in mh_matrix(gamma0,q)])

def late_grid(limit,dx):
    x=np.linspace(-limit,limit,round(2*limit/dx)+1);dx=x[1]-x[0]
    quadrature=np.full(len(x),dx);quadrature[[0,-1]]*=.5
    prediction=(normal(x,-4,.5)+normal(x,4,.5))/2
    rows=[];logz=0.;mass=None
    for t in range(40):
        if t:
            prediction=np.empty_like(x)
            for lo in range(0,len(x),128): prediction[lo:lo+128]=normal(x[lo:lo+128,None],x[None,:],.3)@mass
        pm=float(prediction@quadrature)
        weighted=prediction*normal(4.,x*x/4 if t<32 else x,.5)*quadrature
        z=float(weighted.sum());mass=weighted/z;logz+=math.log(z)
        pos=simpson_uniform((mass/quadrature)[len(x)//2:],dx)
        rows.append(dict(t=t,logZ=logz,mean=float(mass@x),second=float(mass@(x*x)),positive=pos,predictive_mass=pm))
    return rows

def prepare(out,base):
    base=Path(base);ref=json.loads((base/'reference.json').read_text());assert ref['passed']
    out.write('fixtures.tsv',(base/'fixtures.tsv').read_text())
    refs=dict(ref['references']);grids=[late_grid(12,.025),late_grid(12,.0125),late_grid(16,.0125)]
    metrics=('logZ','mean','second','positive')
    changes=[np.max(np.abs(np.array([[r[k] for k in metrics] for r in grids[j]])-
                                   np.array([[r[k] for k in metrics] for r in grids[1]])),axis=0).tolist() for j in (0,2)]
    passed=all(np.all(np.array(c)<=np.array([1e-5,1e-5,1e-5,2e-4])) for c in changes)
    mass_error=max(abs(r['predictive_mass']-1) for rows in grids for r in rows)
    passed=passed and mass_error<=1e-8
    refs['late']=grids[-1];rows=[];m=0.;v=4.;logz=0.
    for t in range(20):
        if t:m=.8*m;v=.64*v+.3
        y=math.sin(t);logz+=-.5*(math.log(2*math.pi*(v+.5))+(y-m)**2/(v+.5))
        gain=v/(v+.5);m+=gain*(y-m);v*=1-gain
        rows.append(dict(t=t,logZ=logz,mean=m,second=v+m*m,positive=.5*(1+math.erf(m/math.sqrt(2*v)))))
    refs['gaussian']=rows
    result=dict(passed=bool(passed),references=refs,late_grid_changes=changes,late_mass_error=mass_error,
                finite=finite_control(),original_reference_sha256=sha(base/'reference.json'),
                original_fixture_sha256=sha(base/'fixtures.tsv'),python=sys.version,numpy=np.__version__)
    out.json('reference.json',result)
    print(json.dumps({k:v for k,v in result.items() if k!='references'}),flush=True)
    assert passed,'Independent late reference convergence failed'

def diagnostics(out,log,reference):
    names='scenario mode seed t logZ mean second positive ess parents concentration expected attempts draws likelihoods densities lookups nanos'.split()
    lines=[l.split('DIVERSITY_DIAG ',1)[1] for l in Path(log).read_text(encoding='utf-8-sig').splitlines() if 'DIVERSITY_DIAG ' in l]
    rows=[dict(zip(names,l.split('\t'))) for l in lines]
    assert all(len(l.split('\t'))==len(names) for l in lines)
    keys={(r['scenario'],r['mode'],int(r['seed']),int(r['t'])) for r in rows}
    modes=('bootstrap256','bootstrap1024','auxiliary256','auxiliary1024','floor32','floor128')
    expected={(s,m,k,t) for s in ('growth','growth-sharp','persistent-modes') for m in modes for k in range(1,257) for t in range(20)}
    assert len(rows)==len(keys) and keys==expected,'Missing or duplicate diagnostics'
    refs=json.loads(Path(reference).read_text())['references'];summary=[]
    for s in refs:
        for m in modes:
            group=[r for r in rows if r['scenario']==s and r['mode']==m]
            final=[r for r in group if int(r['t'])==19]
            if not final:continue
            truth=refs[s][-1]['positive']
            summary.append(dict(scenario=s,mode=m,rmse=math.sqrt(np.mean([(float(r['positive'])-truth)**2 for r in final])),
              depleted=sum(not .01<=float(r['positive'])<=.99 for r in final) if s=='persistent-modes' else None,
              mean_ess=float(np.mean([float(r['ess']) for r in group])),
              mean_parents=float(np.mean([float(r['parents']) for r in group if int(r['t'])>0])),
              concentration=float(np.mean([float(r['concentration']) for r in group if int(r['t'])>0])),
              draws=float(np.mean([float(r['draws']) for r in final]))))
    out.write('diagnostics.tsv','\t'.join(names)+'\n'+'\n'.join(lines)+'\n')
    out.json('diagnostics.json',dict(runs=4608,rows=len(rows),summary=summary,input_sha256=sha(log)))
    print(json.dumps(summary),flush=True)

SCENARIOS=('growth','growth-sharp','persistent-modes','late','gaussian')
CANDIDATES=('none','multi','strat','local1','local4','local8','global1','global4','global8')
FIELDS='scenario method n seed t status logZ mean second positive ess parents work targets likelihoods proposals draws moves accepted nanos error'.split()

def read_experiment(path,configs,seeds):
    lines=[l.split('DIVERSITY_ROW ',1)[1] for l in Path(path).read_text(encoding='utf-8-sig').splitlines() if 'DIVERSITY_ROW ' in l]
    assert all(len(l.split('\t'))==len(FIELDS) for l in lines)
    rows=[]
    for line in lines:
        r=dict(zip(FIELDS,line.split('\t')))
        for k in ('n','seed','t','parents','work','targets','likelihoods','proposals','draws','moves','accepted','nanos'):r[k]=int(r[k])
        for k in ('logZ','mean','second','positive','ess'):r[k]=float(r[k])
        rows.append(r)
    expected={(s,m,n,k,t) for s in SCENARIOS for m,n in configs for k in seeds for t in range(40 if s=='late' else 20)}
    keys=[(r['scenario'],r['method'],r['n'],r['seed'],r['t']) for r in rows]
    assert len(keys)==len(set(keys)) and set(keys)==expected,'Missing/duplicate/unexpected experiment records'
    return rows,lines

def select(out,log,reference):
    configs=[(m,64) for m in CANDIDATES]+[('baseline',n) for n in (64,256,1024,4096)]
    rows,lines=read_experiment(log,configs,range(2001,2017))
    out.write('calibration.tsv','\t'.join(FIELDS)+'\n'+'\n'.join(lines)+'\n')
    failures=[r for r in rows if r['status']!='ok']
    if failures:
        out.json('selection.json',dict(passed=False,failure_count=len(failures)))
        raise ValueError('Calibration contains failed outcomes; no selection')
    refs=json.loads(Path(reference).read_text())['references']
    summaries=[]
    for m,n in configs:
        parts=[]
        for s in SCENARIOS:
            group=[r for r in rows if (r['method'],r['n'],r['scenario'],r['t'])==(m,n,s,len(refs[s])-1)]
            parts.append(dict(scenario=s,mse=float(np.mean([(r['positive']-refs[s][-1]['positive'])**2 for r in group])),
                              work=float(np.mean([r['work'] for r in group])),nanos=float(np.mean([r['nanos'] for r in group]))))
        designated=[p for p in parts if p['scenario'] in ('persistent-modes','late')]
        summaries.append(dict(method=m,n=n,parts=parts,score=sum(p['mse'] for p in designated)*sum(p['work'] for p in designated)))
    chosen=min((r for r in summaries if r['method'] not in ('none','baseline')),key=lambda r:(r['score'],r['method']))
    candidates=[p for p in chosen['parts'] if p['scenario'] in ('persistent-modes','late')]
    eligible=[]
    for r in summaries:
        if r['method']=='baseline':
            parts=[p for p in r['parts'] if p['scenario'] in ('persistent-modes','late')]
            if all(p['work']<=c['work'] for p,c in zip(parts,candidates)):
                eligible.append((sum(p['mse'] for p in parts),r['n']))
    assert eligible,'No matched-cost baseline; promotion unavailable'
    baseline=min(eligible)[1]
    result=dict(passed=True,method=chosen['method'],baseline_n=baseline,calibration=summaries,
                input_sha256=sha(log),reference_sha256=sha(reference),seeds=[2001,2016])
    out.json('selection.json',result)
    out.write('selection.txt',f"{chosen['method']} {baseline}\n")
    print(json.dumps(dict(method=chosen['method'],baseline_n=baseline,ranking=sorted(
        [dict(method=r['method'],n=r['n'],score=r['score']) for r in summaries],key=lambda r:r['score']))),flush=True)

def binomial_upper(k,n,alpha=.05):
    if k==n:return 1.
    lo,hi=0.,1.
    for _ in range(70):
        p=(lo+hi)/2
        cdf=sum(math.comb(n,j)*p**j*(1-p)**(n-j) for j in range(k+1))
        if cdf>alpha:lo=p
        else:hi=p
    return (lo+hi)/2

def confirm(out,logs,reference,selection):
    chosen=json.loads(Path(selection).read_text());m=chosen['method'];n=chosen['baseline_n']
    configs=list(dict.fromkeys([(m,64),('none',64)]+[('baseline',b) for b in (64,256,1024,4096)]))
    runs=[]
    for index,path in enumerate(logs):
        rows,lines=read_experiment(path,configs,range(10001,10257));runs.append(rows)
        out.write(f'confirmation-{index+1}.tsv','\t'.join(FIELDS)+'\n'+'\n'.join(lines)+'\n')
    assert len(runs)==3
    failures=sum(r['status']!='ok' for rows in runs for r in rows)
    if failures:
        out.json('decision.json',dict(promote=False,failed_outcomes=failures))
        raise ValueError('Confirmation has failures; preserve all outcomes, promotion fails')
    scientific=lambda rows:[[r[k] for k in FIELDS if k not in ('nanos',)] for r in rows]
    assert scientific(runs[0])==scientific(runs[1])==scientific(runs[2]),'Fresh-JVM scientific replay mismatch'
    refs=json.loads(Path(reference).read_text())['references'];rows=runs[0];comparisons=[];regressions=[];controls=[];summary=[]
    for s in SCENARIOS:
        for method,budget in configs:
            group=[r for r in rows if (r['scenario'],r['method'],r['n'])==(s,method,budget)]
            final=sorted([r for r in group if r['t']==len(refs[s])-1],key=lambda r:r['seed'])
            errors={metric:float(np.sqrt(np.mean([(r[metric]-refs[s][-1][metric])**2 for r in final]))) for metric in ('mean','second','positive')}
            summary.append(dict(scenario=s,method=method,n=budget,rmse=errors,work=float(np.mean([r['work'] for r in final])),
                nanos=[float(np.mean([r['nanos'] for r in rep if (r['scenario'],r['method'],r['n'],r['t'])==(s,method,budget,len(refs[s])-1)])) for rep in runs]))
            for t in (0,len(refs[s])//2,len(refs[s])-1):
                step=[r for r in group if r['t']==t];truth=refs[s][t]
                for metric in ('evidence','mean','second','positive'):
                    values=np.array([math.exp(r['logZ']-truth['logZ'])*(1 if metric=='evidence' else r[metric]) for r in step])
                    expected=1 if metric=='evidence' else truth[metric]
                    se=float(values.std(ddof=1)/16);avg=float(values.mean())
                    controls.append(dict(scenario=s,method=method,n=budget,t=t,metric=metric,mean=avg,se=se,expected=expected,
                        passed=abs(avg-expected)<=6*se+1e-4*max(1,abs(expected))))
        candidate=next(r for r in summary if (r['scenario'],r['method'],r['n'])==(s,m,64))
        baseline=next(r for r in summary if (r['scenario'],r['method'],r['n'])==(s,'baseline',n))
        if s in ('persistent-modes','late'):
            final=lambda method,budget:sorted([r for r in rows if (r['scenario'],r['method'],r['n'],r['t'])==(s,method,budget,len(refs[s])-1)],key=lambda r:r['seed'])
            c=final(m,64);b=final('baseline',n);truth=refs[s][-1]['positive']
            ce=np.array([(r['positive']-truth)**2 for r in c]);be=np.array([(r['positive']-truth)**2 for r in b])
            rng=np.random.Generator(np.random.PCG64(91317));indices=rng.integers(256,size=(10000,256))
            denominators=be[indices].mean(axis=1)
            available=bool(be.mean()>0 and np.all(denominators>0))
            ratio=float(np.sqrt(ce.mean()/be.mean())) if available else None
            upper=float(np.quantile(np.sqrt(ce[indices].mean(axis=1)/denominators),.975)) if available else None
            interval=np.quantile(np.sqrt(ce[indices].mean(axis=1)/denominators),[.025,.975]).tolist() if available else None
            def depleted(method,budget):
                return len({r['seed'] for r in rows if (r['scenario'],r['method'],r['n'])==(s,method,budget)
                     and .1<=refs[s][r['t']]['positive']<=.9 and not .01<=r['positive']<=.99})
            dc,db=depleted(m,64),depleted('baseline',n)
            # Conservative measured Pareto test: no extrapolation or invented timing interpolation.
            timings=[]
            for j in range(3):
                eligible=[r for r in summary if r['scenario']==s and r['method']=='baseline' and r['nanos'][j]<=candidate['nanos'][j]]
                timings.append(bool(eligible) and candidate['rmse']['positive']<=.8*min(r['rmse']['positive'] for r in eligible))
            cost=candidate['work']>=baseline['work']
            comparisons.append(dict(scenario=s,rmse_ratio=ratio,interval95=interval,upper_familywise=upper,
                candidate_depleted=dc,baseline_depleted=db,candidate_depletion_upper=binomial_upper(dc,256),
                baseline_depletion_upper=binomial_upper(db,256),matched_evaluation_cost=cost,timing_gates=timings,
                passed=available and ratio<=.8 and upper<1 and dc<=db and cost and all(timings)))
        if s in ('gaussian','growth'):
            for metric in ('mean','second','positive'):
                tolerance=1.25*baseline['rmse'][metric]+.01*max(1,abs(refs[s][-1][metric]))
                regressions.append(dict(scenario=s,metric=metric,rmse=candidate['rmse'][metric],limit=tolerance,
                                        passed=candidate['rmse'][metric]<=tolerance))
    result=dict(promote=all(c['passed'] for c in comparisons+regressions+controls),comparisons=comparisons,
                regressions=regressions,controls=controls,summary=summary,independent_seeds=256,jvm_repetitions=3,
                failed_outcomes=failures,input_sha256=[sha(p) for p in logs],selection_sha256=sha(selection),reference_sha256=sha(reference))
    out.json('decision.json',result)
    print(json.dumps({k:result[k] for k in ('promote','comparisons','regressions','failed_outcomes')}),flush=True)

def estimation_errors(rows, truth):
    """Descriptive errors only; never used to select or promote a candidate."""
    variance=truth['second']-truth['mean']**2
    errors={key:[] for key in ('mean','variance','positive','relative_evidence','log_evidence')}
    negative_variances=0
    for r in rows:
        v=r['second']-r['mean']**2
        negative_variances+=int(v<0)
        errors['mean'].append(r['mean']-truth['mean'])
        errors['variance'].append(v-variance)
        errors['positive'].append(r['positive']-truth['positive'])
        delta=r['logZ']-truth['logZ']
        errors['relative_evidence'].append(math.expm1(delta))
        errors['log_evidence'].append(delta)
    result={key:dict(bias=float(np.mean(values)),rmse=float(np.sqrt(np.mean(np.square(values)))))
            for key,values in errors.items()}
    assert all(math.isfinite(v) for metrics in result.values() for v in metrics.values())
    return dict(errors=result,negative_variance_estimates=negative_variances)

def supplement(out,log,reference,selection):
    chosen=json.loads(Path(selection).read_text())
    configs=list(dict.fromkeys([(chosen['method'],64),('none',64)]+[('baseline',b) for b in (64,256,1024,4096)]))
    rows,_=read_experiment(log,configs,range(10001,10257))
    assert all(r['status']=='ok' for r in rows),'Preserve failures; supplemental estimates unavailable'
    refs=json.loads(Path(reference).read_text())['references'];records=[]
    for s in SCENARIOS:
        for method,n in configs:
            for t in (0,len(refs[s])//2,len(refs[s])-1):
                group=[r for r in rows if (r['scenario'],r['method'],r['n'],r['t'])==(s,method,n,t)]
                assert len(group)==256
                record=dict(scenario=s,method=method,n=n,t=t,independent_seeds=len(group),
                            **estimation_errors(group,refs[s][t]))
                record['mean_cumulative_counts']={key:float(np.mean([r[key] for r in group]))
                    for key in ('work','targets','likelihoods','proposals','draws','moves','accepted')}
                records.append(record)
    out.json('estimation-errors.json',dict(descriptive_only=True,changes_acceptance_gates=False,
        log_sha256=sha(log),reference_sha256=sha(reference),selection_sha256=sha(selection),records=records))
    print(json.dumps(dict(records=len(records),negative_variance_estimates=sum(r['negative_variance_estimates'] for r in records))),flush=True)

def report(out,path):
    result=json.loads(Path(path).read_text())
    config=out.root/'matplotlib-config';config.mkdir();out.grant(config)
    os.environ['MPLCONFIGDIR']=str(config)
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(1,2,figsize=(10,4))
    for ax,s in zip(axes,('persistent-modes','late')):
        base=sorted([r for r in result['summary'] if r['scenario']==s and r['method']=='baseline'],key=lambda r:r['n'])
        ax.plot([np.mean(r['nanos'])/1e6 for r in base],[r['rmse']['positive'] for r in base],'o-',label='Bootstrap, N=64..4096')
        for r in result['summary']:
            if r['scenario']==s and r['method'] not in ('baseline','none'):
                ax.scatter(np.mean(r['nanos'])/1e6,r['rmse']['positive'],marker='*',s=130,label=r['method']+', N=64')
        ax.set_xscale('log');ax.set_xlabel('Mean elapsed ms / trajectory (3 JVMs)');ax.set_ylabel('Final sign-mass RMSE')
        ax.set_title(s);ax.legend(fontsize=8)
    fig.suptitle('Held-out point estimates; 256 independent seeds, not 768')
    fig.tight_layout();target=out.write('accuracy-cost.png','');fig.savefig(target,dpi=150);plt.close(fig)
    lines=['# Held-out diversity decision','',f"Promote: **{result['promote']}**",'',
           '| Fixture | RMSE ratio | Family-wise upper ratio | Candidate / baseline depleted | Timing gates |',
           '| --- | ---: | ---: | --- | --- |']
    for c in result['comparisons']:
        lines.append(f"| {c['scenario']} | {c['rmse_ratio']} | {c['upper_familywise']} | {c['candidate_depleted']} / {c['baseline_depleted']} | {c['timing_gates']} |")
    lines+=['','Repeated JVMs assess timing/replay only. Full gates and all outcomes are in decision.json and TSV files.']
    out.write('summary.md','\n'.join(lines)+'\n')

def main():
    p=argparse.ArgumentParser();p.add_argument('command',choices=['prepare','diagnostics','select','confirm','supplement','report'])
    p.add_argument('--output',required=True);p.add_argument('--base');p.add_argument('--log');p.add_argument('--reference');p.add_argument('--acl-script')
    p.add_argument('--selection');p.add_argument('--logs',nargs='+');p.add_argument('--decision')
    a=p.parse_args();out=Output(a.output,a.acl_script)
    print('START_UTC',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),flush=True)
    if a.command=='prepare':prepare(out,a.base)
    elif a.command=='diagnostics':diagnostics(out,a.log,a.reference)
    elif a.command=='select':select(out,a.log,a.reference)
    elif a.command=='confirm':confirm(out,a.logs,a.reference,a.selection)
    elif a.command=='supplement':supplement(out,a.log,a.reference,a.selection)
    else:report(out,a.decision)
    print('COMPLETE_UTC',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),flush=True)
if __name__=='__main__':main()
