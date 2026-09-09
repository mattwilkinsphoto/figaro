"""Validate the paired pilot-inclusive single/multi-component proposal study."""
import argparse, csv, io, json, math, os, statistics, subprocess
from pathlib import Path
from summarize_defensive_importance import read
SEEDS=[9000001+130363*i for i in range(100)]
CASES={'balanced':([.5,.5],[-5,5]),'unbalanced':([.9,.1],[-5,5]),'three-modes':([.5,.3,.2],[-6,0,6])}
FIELDS='case,seed,draws,method,fit,pilotCalls,fitCalls,productionCalls,reference,mean,ess,accurate,health'.split(',')
def load(text): return read(text.replace('MP,','DI,'))
def validate(rows):
    expected={(c,str(s),str(n),m) for c in CASES for s in SEEDS for n in (2000,10000) for m in ('single','multi')}
    seen=set(); meta={}
    for r in rows:
        if set(r)!=set(FIELDS): raise ValueError('Wrong fields')
        key=tuple(r[k] for k in ('case','seed','draws','method'))
        if key not in expected or key in seen: raise ValueError('Unexpected/duplicate trial')
        seen.add(key); weights,centers=CASES[key[0]]
        ref=sum(w*.5*math.erfc(-(m-centers[-1]+1)/(.3*math.sqrt(2))) for w,m in zip(weights,centers))
        if not math.isclose(float(r['reference']),ref,rel_tol=0,abs_tol=1e-12): raise ValueError('Changed reference')
        if r['pilotCalls']!='12000': raise ValueError('Missing pilot cost')
        calls=int(r['fitCalls'])
        if not 0<=calls<=2000000 or key[3]=='single' and calls!=0: raise ValueError('Wrong fitting cost')
        if r['fit'] not in ('Fitted','InsufficientPilot','DegeneratePilot','InsufficientComponent','NumericalFailure','IterationLimit','EvaluationLimit'): raise ValueError('Unknown fit')
        fitted=r['fit']=='Fitted'
        if int(r['productionCalls'])!=(int(key[2]) if fitted else 0): raise ValueError('Wrong production cost')
        if r['health'] not in ('ChecksPassed','Warning','Danger','InsufficientEvidence','NotRun'): raise ValueError('Unknown health')
        if fitted:
            mean,ess=float(r['mean']),float(r['ess'])
            if not 0<=mean<=1 or not 0<ess<=int(key[2])+1e-8 or r['health']=='NotRun': raise ValueError('Invalid estimate')
            accurate=abs(mean-ref)<=.025
        else:
            if r['mean']!='NA' or r['ess']!='NA' or r['health']!='NotRun': raise ValueError('Refusal produced estimate')
            accurate=False
        if r['accurate']!=str(accurate).lower(): raise ValueError('Wrong accuracy flag')
        trial=key[0],key[1],key[3]; details=r['fit'],r['pilotCalls'],r['fitCalls']
        if trial in meta and meta[trial]!=details: raise ValueError('Inconsistent reused fit')
        meta[trial]=details
    if seen!=expected: raise ValueError(f'Incomplete grid: {len(seen)}/{len(expected)}')
    return len(seen)
def summaries(rows):
    def median(values):
        values=list(values)
        return statistics.median(values) if values else None
    for c in CASES:
        for n in (2000,10000):
            group=[r for r in rows if r['case']==c and int(r['draws'])==n]
            report=dict(case=c,draws=n)
            for method in ('single','multi'):
                selected=[r for r in group if r['method']==method]
                report[method]=dict(accurate=sum(r['accurate']=='true' for r in selected),
                    fitted=sum(r['fit']=='Fitted' for r in selected),
                    medianEss=median(float(r['ess']) for r in selected if r['ess']!='NA'),
                    medianFitCalls=statistics.median(int(r['fitCalls']) for r in selected))
            yield report
def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('input',type=Path)
    p.add_argument('--export',type=Path); p.add_argument('--acl-script',type=Path); a=p.parse_args()
    rows=load(a.input.read_text(encoding='utf-8-sig')); print('Validated paired rows:',validate(rows))
    if a.export:
        if os.name=='nt' and not a.acl_script: raise ValueError('Windows export requires ACL verification')
        destination=a.export.resolve()
        if not destination.parent.is_dir(): raise ValueError('Output parent must exist')
        stream=io.StringIO(newline=''); writer=csv.DictWriter(stream,fieldnames=FIELDS,lineterminator='\n'); writer.writeheader(); writer.writerows(rows)
        try: destination.write_text(stream.getvalue(),encoding='utf-8')
        finally:
            if destination.exists() and a.acl_script: subprocess.run(['pwsh','-NoProfile','-File',str(a.acl_script.resolve()),'-Paths',str(destination)],check=True)
    for r in summaries(rows): print(json.dumps(r,sort_keys=True))
if __name__=='__main__': main()
