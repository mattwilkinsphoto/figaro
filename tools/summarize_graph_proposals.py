"""Validate the complete fixed-budget explicit graph proposal comparison."""
import argparse, csv, io, json, math, os, statistics, subprocess
from pathlib import Path
from summarize_defensive_importance import read
FIELDS='case,seed,method,attempts,priorCalls,proposalDraws,rejected,reference,mean,ess,accurate,health'.split(',')
CASES={'normal':(.8,.025),'hierarchical':(1/3,.05),'mixture':(.24/.38,.025)}
SEEDS=[19000001+15485863*i for i in range(100)]

def load(text): return read(text.replace('GP,','DI,'))

def validate(rows):
    expected={(c,str(s),m) for c in CASES for s in SEEDS for m in ('prior','informed')}
    seen=set()
    for r in rows:
        if set(r)!=set(FIELDS): raise ValueError('Wrong fields')
        key=tuple(r[k] for k in ('case','seed','method'))
        if key not in expected or key in seen: raise ValueError('Unexpected/duplicate trial')
        seen.add(key); ref,tol=CASES[key[0]]
        if not math.isclose(float(r['reference']),ref,rel_tol=0,abs_tol=1e-12): raise ValueError('Changed reference')
        if any(r[k]!='2000' for k in ('attempts','priorCalls','proposalDraws')): raise ValueError('Wrong work accounting')
        if not 0<=int(r['rejected'])<=2000: raise ValueError('Wrong rejection count')
        mean,ess=float(r['mean']),float(r['ess'])
        if not math.isfinite(mean) or not 0<ess<=2000+1e-8: raise ValueError('Invalid estimate')
        if key[0]=='mixture' and not 0<=mean<=1: raise ValueError('Invalid probability')
        if r['accurate']!=str(abs(mean-ref)<=tol).lower(): raise ValueError('Changed accuracy flag')
        if r['health'] not in ('ChecksPassed','Warning','Danger','InsufficientEvidence'): raise ValueError('Unknown health')
    if seen!=expected: raise ValueError(f'Incomplete grid: {len(seen)}/{len(expected)}')
    return len(seen)

def summaries(rows):
    for case in CASES:
        result=dict(case=case)
        for method in ('prior','informed'):
            selected=[r for r in rows if r['case']==case and r['method']==method]
            result[method]=dict(accurate=sum(r['accurate']=='true' for r in selected),
                medianEss=statistics.median(float(r['ess']) for r in selected),
                rmse=math.sqrt(statistics.mean((float(r['mean'])-CASES[case][0])**2 for r in selected)))
        yield result

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('input',type=Path)
    p.add_argument('--export',type=Path); p.add_argument('--acl-script',type=Path); a=p.parse_args()
    rows=load(a.input.read_text(encoding='utf-8-sig')); print('Validated graph rows:',validate(rows))
    if a.export:
        if os.name=='nt' and not a.acl_script: raise ValueError('Windows export requires ACL verification')
        destination=a.export.resolve()
        if not destination.parent.is_dir(): raise ValueError('Output parent must exist')
        stream=io.StringIO(newline=''); writer=csv.DictWriter(stream,fieldnames=FIELDS,lineterminator='\n'); writer.writeheader(); writer.writerows(rows)
        try: destination.write_text(stream.getvalue(),encoding='utf-8')
        finally:
            if destination.exists() and a.acl_script: subprocess.run(['pwsh','-NoProfile','-File',str(a.acl_script.resolve()),'-Paths',str(destination)],check=True)
    for result in summaries(rows): print(json.dumps(result,sort_keys=True))
if __name__=='__main__': main()
