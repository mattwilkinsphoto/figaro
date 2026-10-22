"""Independent Decimal oracle and frozen all-outcome prototype screens (INF-15)."""
import argparse
from bisect import bisect_right
from collections import Counter
from decimal import Decimal as D, localcontext
import hashlib
import json
import math
from pathlib import Path
import sys
import time
import numpy as np
from continuous_filter_reference import Output
from resampling_schedule_reference import read, SCENARIOS


def reference(logs, us, precision):
    with localcontext() as ctx:
        ctx.prec=precision
        peak=D.from_float(max(logs))
        w=[(D.from_float(x)-peak).exp() if math.isfinite(x) else D(0) for x in logs]
        raw=[D(0)]
        for x in w:raw.append(raw[-1]+x)
        cdf=[x/raw[-1] for x in raw[1:]]
        n=len(logs)
        groups={}
        for i,x in enumerate(logs):
            if math.isfinite(x):groups.setdefault(x,[]).append(i)
        answers=[]
        for j,u in enumerate(us):
            q=D(j)+D.from_float(u)
            index=bisect_right(cdf,q/D(n))
            # Exact equal-log group symmetry resolves boundaries without pretending
            # Decimal's finite prefix has infinitely many digits.
            for k in (index,index+1):
                if 0<k<n and all(D(n*sum(i<k for i in ix))==q*len(ix) for ix in groups.values()):
                    index=k
                    while index<n and not math.isfinite(logs[index]):index+=1
            assert 0<=index<n and math.isfinite(logs[index])
            answers.append(index)
        return answers,raw,cdf


def oracle(path,out):
    rows=[];exps=[];seen=set();maxdiff=D(0)
    for line in Path(path).open(encoding='utf-8-sig'):
        if 'DECISION_CASE ' in line:
            f=line.split('DECISION_CASE ',1)[1].strip().split('\t')
            assert len(f)==8 and f[0] not in seen;seen.add(f[0])
            logs,us=[[float.fromhex(x) for x in text.split(',')] for text in f[1:3]]
            chosen=list(map(int,f[3].split(',')))
            lower,upper=[[D.from_float(float.fromhex(x)) for x in text.split(',')] for text in f[4:6]]
            a100,r100,c100=reference(logs,us,100)
            a180,r180,c180=reference(logs,us,180)
            with localcontext() as ctx:
                ctx.prec=180
                delta=max(abs(a-b) for a,b in zip(r100+c100,r180+c180))
            maxdiff=max(maxdiff,delta)
            resolved=chosen==a100==a180
            enclosing=all(lo<=x<=hi for lo,x,hi in zip(lower,r180,upper))
            rows.append(dict(id=f[0],decisions=len(chosen),matched=resolved,enclosing=enclosing,
                             refined=int(f[6]),precision=int(f[7]),difference=str(delta)))
            if len(rows)%256==0:print('ORACLE_CASES',len(rows),flush=True)
        elif 'EXP_CASE ' in line:
            f=line.split('EXP_CASE ',1)[1].strip().split('\t');x=float.fromhex(f[0])
            with localcontext() as ctx:
                ctx.prec=180
                expected=(-D.from_float(x)).exp()
                enclosed=D(f[2])<=expected<=D(f[3])
            exps.append(dict(x=x,precision=int(f[1]),enclosed=enclosed,reference=str(expected)))
    assert len(rows)==6159 and len(exps)==30
    assert {int(r['id']) for r in rows if not r['id'].startswith('adversarial')}==set(range(6144))
    passed=all(r['matched'] and r['enclosing'] for r in rows) and all(r['enclosed'] for r in exps) and maxdiff<D('1e-80')
    result=dict(passed=passed,cases=len(rows),decisions=sum(r['decisions'] for r in rows),
                mismatched_cases=sum(not r['matched'] for r in rows),failed_enclosures=sum(not r['enclosing'] for r in rows),
                refined=sum(r['refined'] for r in rows),max_precision=max(r['precision'] for r in rows),
                max_reference_difference=str(maxdiff),exponentials=exps,rows=rows,log_sha256=sha(path),python=sys.version)
    out.json('oracle.json',result)
    print(json.dumps({k:v for k,v in result.items() if k not in ('rows','exponentials')}),flush=True)
    if not passed:raise SystemExit(1)


def study(paths,reference_path,out):
    refs=json.loads(Path(reference_path).read_text())['references']
    configs=[(f'{m}-{p}',256) for m in ('multi','proto') for p in ('always','25','50','75')]
    digests=[];summaries=[];checks=[];failures=[];work=[]
    for repetition,path in enumerate(paths,1):
        groups,digest,failed=read(path,configs,range(60001,60257),SCENARIOS)
        digests.append(digest);failures.extend([list(x) for x in failed])
        for (scenario,method,n),rows in groups.items():
            rows=sorted(rows,key=lambda r:(r['seed'],r['t']))
            sequences=[[r for r in rows if r['seed']==seed] for seed in range(60001,60257)]
            complete=all(r['status']=='ok' for r in rows)
            summary=dict(repetition=repetition,scenario=scenario,method=method,complete=complete,
                         inclusive_total_ms=sum(run[-1]['nanos'] for run in sequences)/1e6,
                         mean_work=float(np.mean([run[-1]['work'] for run in sequences])))
            if complete:
                truth=refs[scenario]
                summary['final_mean_rmse']=float(np.sqrt(np.mean([(run[-1]['mean']-truth[-1]['mean'])**2 for run in sequences])))
                if repetition==1:
                    for t in sorted({0,len(truth)//2,len(truth)-1}):
                        scale=np.exp(np.array([run[t]['logZ'] for run in sequences])-truth[t]['logZ'])
                        for metric in ('evidence','mean','second','positive'):
                            target=1. if metric=='evidence' else truth[t][metric]
                            values=scale if metric=='evidence' else scale*np.array([run[t][metric] for run in sequences])
                            se=float(np.std(values,ddof=1)/16);estimate=float(values.mean())
                            tolerance=6*se+1e-4*max(1,abs(target))
                            checks.append(dict(scenario=scenario,method=method,t=t,metric=metric,estimate=estimate,
                                               target=target,se=se,tolerance=tolerance,passed=bool(np.all(np.isfinite(values)) and abs(estimate-target)<=tolerance)))
            summaries.append(summary)
        current=[]
        for line in Path(path).open(encoding='utf-8-sig'):
            if 'DECISION_WORK ' in line:
                f=line.split('DECISION_WORK ',1)[1].strip().split('\t')
                current.append(dict(scenario=f[0],method=f[1],seed=int(f[2]),refinements=int(f[3]),tables=int(f[4]),precision=int(f[5])))
        assert len(current)==6144
        work.append(current)
    passed=not failures and len(set(digests))==1 and work[0]==work[1] and all(c['passed'] for c in checks) and len(checks)==576
    result=dict(passed=passed,trajectories_per_jvm=12288,repetitions=2,independent_seeds_per_group=256,
                failures=failures,replay=len(set(digests))==1 and work[0]==work[1],digests=digests,
                checks=checks,summaries=summaries,refinements_per_jvm=[sum(r['refinements'] for r in w) for w in work],
                work=work,log_sha256=[sha(p) for p in paths],reference_sha256=sha(reference_path),python=sys.version,numpy=np.__version__)
    out.json('study.json',result)
    print(json.dumps(dict(passed=passed,checks=len(checks),failed_checks=sum(not c['passed'] for c in checks),
                         failed_rows=len(failures),replay=result['replay'],refinements=result['refinements_per_jvm'])),flush=True)
    if not passed:raise SystemExit(1)


def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    p=argparse.ArgumentParser();p.add_argument('command',choices=['oracle','study']);p.add_argument('--logs',nargs='+',required=True)
    p.add_argument('--reference');p.add_argument('--output',required=True);p.add_argument('--acl-script');a=p.parse_args()
    print('START_UTC',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),flush=True)
    out=Output(a.output,a.acl_script)
    if a.command=='oracle':oracle(a.logs[0],out)
    else:study(a.logs,a.reference,out)
    print('COMPLETE_UTC',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),flush=True)


if __name__=='__main__':main()
