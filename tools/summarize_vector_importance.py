"""Validate the complete public proposal API acceptance grid, including refusals and warnings."""
import argparse
import csv
import io
import json
import math
import os
from pathlib import Path
import subprocess
from summarize_defensive_importance import SEEDS, REFERENCES, number, read

CASES = {name: {f'mean{i}': (m,.1*sd[i]) for i,m in enumerate(means)}
         for name,(means,sd) in REFERENCES.items()}
CASES.update({
    'separated-modes': {'mean': (0,.1*math.sqrt(25.09)), 'positive': (.5,.05)},
    'boundary-beta': {'mean': (.2,.1*math.sqrt(16/1100)), 'upper-half': (5/256,.01)},
    'cauchy-event': {'above-five': (.5-math.atan(5)/math.pi,.02)},
})
FIELDS='case,seed,query,reference,tolerance,mean,error,mcse,covered95,accurate,health,fit,pilotCalls,productionCalls,ess,seconds'.split(',')

def load(text): return read(text.replace('VI,','DI,'))

def validate(rows):
    expected={(name,str(seed),q) for name,queries in CASES.items() for seed in SEEDS for q in queries}
    seen,meta=set(),{}
    for r in rows:
        if set(r)!=set(FIELDS): raise ValueError('Unexpected fields')
        key=(r['case'],r['seed'],r['query'])
        if key not in expected or key in seen: raise ValueError('Unexpected/duplicate row')
        seen.add(key)
        ref,tol=CASES[key[0]][key[2]]
        for field,expectedValue in (('reference',ref),('tolerance',tol)):
            v=number(r[field])
            if v is None or not math.isclose(v,expectedValue,rel_tol=0,abs_tol=1e-12): raise ValueError('Changed reference/target')
        mean,error,mcse,ess,seconds=(number(r[k]) for k in ('mean','error','mcse','ess','seconds'))
        if seconds is None or seconds<=0: raise ValueError('Invalid time')
        if (mean is None)!=(error is None) or (mean is not None and not math.isclose(error,mean-ref,rel_tol=1e-10,abs_tol=1e-12)): raise ValueError('Wrong error')
        if mcse is not None and mcse<0 or ess is not None and not 0<ess<=10000+1e-8: raise ValueError('Invalid diagnostic')
        if r['pilotCalls']!='10000': raise ValueError('Pilot cost omitted')
        if r['fit'] not in ('Fitted','InsufficientPilot','DegeneratePilot','NumericalFailure'): raise ValueError('Unknown fit state')
        fitted=r['fit']=='Fitted'
        if r['productionCalls']!=('10000' if fitted else '0'): raise ValueError('Incorrect production cost')
        if r['health'] not in ('Danger','Warning','InsufficientEvidence','ChecksPassed','NotRun'): raise ValueError('Unknown health')
        if not fitted and (mean is not None or mcse is not None or ess is not None or r['health']!='NotRun'): raise ValueError('Refused fit produced estimate')
        if fitted and r['health']=='NotRun': raise ValueError('Missing production assessment')
        if r['health']=='ChecksPassed' and (mean is None or mcse is None or mcse<=0 or ess is None): raise ValueError('Missing diagnostics passed')
        accurate=error is not None and abs(error)<=tol
        covered=error is not None and mcse is not None and abs(error)<=1.959963984540054*mcse
        if r['accurate']!=str(accurate).lower() or r['covered95']!=str(covered).lower(): raise ValueError('Incorrect outcome flags')
        trial=key[:2]; details=tuple(r[k] for k in ('fit','pilotCalls','productionCalls','seconds'))
        if trial in meta and meta[trial]!=details: raise ValueError('Inconsistent repeated trial metadata')
        meta[trial]=details
    if seen!=expected: raise ValueError(f'Incomplete evidence: {len(seen)}/{len(expected)}')
    return len(meta)

def summaries(rows):
    for name,queries in CASES.items():
        group=[r for r in rows if r['case']==name]
        trials=[[r for r in group if int(r['seed'])==s] for s in SEEDS]
        yield {'case':name,'runs':30,'allAccurate':sum(all(r['accurate']=='true' for r in t) for t in trials),
               'dangerRuns':sum(any(r['health']=='Danger' for r in t) for t in trials),
               'refusals':sum(t[0]['fit']!='Fitted' for t in trials),
               'queries':{q:{'accurate':sum(r['accurate']=='true' for r in group if r['query']==q),
                             'covered95':sum(r['covered95']=='true' for r in group if r['query']==q)} for q in queries}}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('input',type=Path); p.add_argument('--export',type=Path); p.add_argument('--acl-script',type=Path)
    args=p.parse_args()
    rows=load(args.input.read_text(encoding='utf-8-sig'))
    trials=validate(rows)
    if args.export:
        if os.name=='nt' and not args.acl_script: raise ValueError('Windows export requires access verification')
        destination=args.export.resolve()
        if not destination.parent.is_dir(): raise ValueError('Output parent must exist')
        stream=io.StringIO(newline=''); writer=csv.DictWriter(stream,fieldnames=FIELDS,lineterminator='\n')
        writer.writeheader(); writer.writerows(rows)
        try: destination.write_text(stream.getvalue(),encoding='utf-8')
        finally:
            if destination.exists() and args.acl_script: subprocess.run(['pwsh','-NoProfile','-File',str(args.acl_script.resolve()),'-Paths',str(destination)],check=True)
    print(f'Validated {len(rows)} query rows / {trials} trials')
    for result in summaries(rows): print(json.dumps(result,sort_keys=True))

if __name__=='__main__': main()
