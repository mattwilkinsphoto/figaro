"""Validate the declared graph cost grid; do not filter inaccurate or refused fits."""
import argparse, csv, io, json, math, os, statistics, subprocess
from pathlib import Path
from summarize_defensive_importance import read

FIELDS='jvm,case,method,draws,seed,status,mean,ess,rhat,attempts,pilotTransitions,visits,pilotSeconds,fitSeconds,totalSeconds,reference'.split(',')
CASES={'concentrated':3/1.01,'hierarchical':1/1.02,'mixture':.24/.38}
METHODS=('legacy','prior','single','mixture','mh')
SEEDS=[23000003+15485863*i for i in range(20)]
MATCHED_COUNTS=((58112,116584),(44100,88416),(35104,73080),(23168,63196),(45744,92732),
 (43792,87804),(34824,69696),(29384,61512),(11160,41528),(46584,94824),
 (102232,204768),(47620,95408),(35864,74768),(29192,62548),(42832,86692))
def counts(c,m,matched): return MATCHED_COUNTS[list(CASES).index(c)*len(METHODS)+METHODS.index(m)] if matched else (2000,8000)
def load(text):
    rows=read(text.replace('GC,','DI,'))
    # Each fresh JVM emits its own identical header, never an evidence row.
    return [r for r in rows if r!=dict(zip(FIELDS,FIELDS))]

def validate(rows, jvms=(1,2,3),matched=False):
    seeds=[41000017+15485863*i for i in range(20)] if matched else SEEDS
    expected={(str(j),c,m,str(n),str(s)) for j in jvms for c in CASES for m in METHODS for n in counts(c,m,matched) for s in seeds}
    seen=set()
    for r in rows:
        if set(r)!=set(FIELDS): raise ValueError('Wrong schema')
        key=tuple(r[k] for k in ('jvm','case','method','draws','seed'))
        if key not in expected or key in seen: raise ValueError('Unexpected/duplicate trial')
        seen.add(key)
        j,c,m,n,s=key
        if not math.isclose(float(r['reference']),CASES[c],abs_tol=1e-13): raise ValueError('Changed reference')
        times=[float(r[k]) for k in ('pilotSeconds','fitSeconds','totalSeconds')]
        if not all(math.isfinite(t) and t>=0 for t in times) or times[2]<=0 or sum(times[:2])>times[2]: raise ValueError('Invalid time')
        if int(r['pilotTransitions']) != (3000 if m in ('single','mixture') else 1000 if m=='mh' else 0): raise ValueError('Missing training cost')
        if int(r['visits'])<0: raise ValueError('Invalid graph visits')
        ok=r['status'] in ('Completed','Fitted')
        allowed=('Completed',) if m in ('legacy','prior','mh') else ('Fitted','InsufficientPilot','DegeneratePilot','NumericalFailure','InsufficientComponent','IterationLimit','EvaluationLimit')
        if r['status'] not in allowed: raise ValueError('Unknown fit status')
        if int(r['attempts']) != (int(n) if ok else 0): raise ValueError('Wrong production count')
        mean=float(r['mean'])
        if ok and not math.isfinite(mean): raise ValueError('Invalid estimate')
        if not ok and not math.isnan(mean): raise ValueError('Estimate after refused fit')
        if ok and m in ('prior','single','mixture') and not 0<float(r['ess'])<=int(n)+1e-8: raise ValueError('Invalid ESS')
    if seen!=expected: raise ValueError(f'Incomplete grid: {len(seen)}/{len(expected)}')
    return len(seen)

def summaries(rows,matched=False):
    for c in CASES:
        for m in METHODS:
            for b,n in enumerate(counts(c,m,matched)):
                selected=[r for r in rows if r['case']==c and r['method']==m and int(r['draws'])==n]
                successful=[r for r in selected if r['status'] in ('Completed','Fitted')]
                # A refusal prevents unconditional RMSE reporting, rather than improving it by selection.
                mse=statistics.mean((float(r['mean'])-CASES[c])**2 for r in successful) if len(successful)==len(selected) else None
                seconds=statistics.mean(float(r['totalSeconds']) for r in selected)
                yield dict(case=c,method=m,draws=n,trials=len(selected),refusals=len(selected)-len(successful),
                    targetSeconds=(.08,.16)[b] if matched else None,
                    rmse=math.sqrt(mse) if mse is not None else None,meanSeconds=seconds,
                    mseSeconds=mse*seconds if mse is not None else None,
                    accurate=sum(abs(float(r['mean'])-CASES[c])<=.025 for r in successful))

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('inputs',nargs='+',type=Path)
    p.add_argument('--jvm',type=int); p.add_argument('--matched',action='store_true'); p.add_argument('--export',type=Path); p.add_argument('--acl-script',type=Path); a=p.parse_args()
    rows=[r for path in a.inputs for r in load(path.read_text(encoding='utf-8-sig'))]
    print('Validated rows:',validate(rows,(a.jvm,) if a.jvm else (1,2,3),a.matched))
    if a.export:
        if os.name=='nt' and not a.acl_script: raise ValueError('Windows export requires ACL hook')
        destination=a.export.resolve(); stream=io.StringIO(newline='')
        writer=csv.DictWriter(stream,fieldnames=FIELDS,lineterminator='\n'); writer.writeheader(); writer.writerows(rows)
        try: destination.write_text(stream.getvalue(),encoding='utf-8')
        finally:
            if destination.exists() and a.acl_script: subprocess.run(['pwsh','-NoProfile','-File',str(a.acl_script.resolve()),'-Paths',str(destination)],check=True)
    for result in summaries(rows,a.matched): print(json.dumps(result,sort_keys=True))
if __name__=='__main__': main()
