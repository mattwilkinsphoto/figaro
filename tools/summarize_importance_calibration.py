"""Validate every predeclared calibration outcome; summarize coverage with denominators."""
import argparse
import csv
import io
import json
import math
import os
from pathlib import Path
import subprocess
from summarize_defensive_importance import read, REFERENCES, wilson

SEEDS = [5000003 + 104729*i for i in range(200)]
BUDGETS = (2000, 10000, 40000)
CASES = {'normal': (0,.1), 'correlated12': (0,.1), 'banana4': (0,.1*math.sqrt(1.5)),
         'boundary': (.2,.1*math.sqrt(16/1100)), 'rare': (3.167124183311998e-5,1.583562091655999e-5),
         'rare-tilted': (3.167124183311998e-5,1.583562091655999e-5), 'heavy': (0,.1*math.sqrt(3)),
         'modes': (.5,.05), 'missed-mode': (.5,.05)}
CASES.update({k:(means[0],.1*sd[0]) for k,(means,sd) in REFERENCES.items() if k.endswith('heldout')})
FIELDS = 'case,seed,draws,pilotCalls,reference,tolerance,mean,rawMcse,batchMcse,varianceEss,queryK,health,coveredRaw,coveredBatch,accurate,queryFlag'.split(',')

def load(text): return read(text.replace('IC,','DI,'))

def validate(rows):
    expected = {(c,str(s),str(n)) for c in CASES for s in SEEDS for n in BUDGETS}
    seen = set()
    for r in rows:
        if set(r) != set(FIELDS): raise ValueError('Unexpected fields')
        key = r['case'],r['seed'],r['draws']
        if key not in expected or key in seen: raise ValueError('Unexpected/duplicate trial')
        seen.add(key)
        data = {k:float(r[k]) for k in ('reference','tolerance','mean','rawMcse','batchMcse','varianceEss')}
        if not all(math.isfinite(x) for x in data.values()): raise ValueError('Nonfinite evidence')
        ref,tol = CASES[key[0]]
        if not math.isclose(data['reference'],ref,rel_tol=0,abs_tol=1e-12) or not math.isclose(data['tolerance'],tol,rel_tol=0,abs_tol=1e-12): raise ValueError('Changed oracle')
        if data['rawMcse']<0 or data['batchMcse']<0 or not 0<=data['varianceEss']<=int(r['draws'])+1e-8: raise ValueError('Invalid uncertainty')
        k = None if r['queryK']=='NA' else float(r['queryK'])
        if k is not None and not math.isfinite(k): raise ValueError('Invalid tail')
        if r['pilotCalls'] != ('4000' if key[0].endswith('heldout') else '0'): raise ValueError('Wrong pilot cost')
        if r['health'] not in ('ChecksPassed','Warning','Danger','InsufficientEvidence'): raise ValueError('Unknown health')
        error = abs(data['mean']-ref)
        flags = {'coveredRaw': data['rawMcse']>0 and error<=1.959963984540054*data['rawMcse'],
                 'coveredBatch': data['batchMcse']>0 and error<=2.093024054408263*data['batchMcse'],
                 'accurate': error<=tol, 'queryFlag': data['varianceEss']<20 or k is not None and k>=.7}
        if any(r[name]!=str(value).lower() for name,value in flags.items()): raise ValueError('Wrong outcome flag')
    if seen != expected: raise ValueError(f'Incomplete grid: {len(seen)}/{len(expected)}')
    return len(seen)

def summaries(rows):
    for case in CASES:
        for n in BUDGETS:
            group=[r for r in rows if r['case']==case and int(r['draws'])==n]
            base=[r for r in group if r['health']=='ChecksPassed']
            selected=[r for r in base if r['queryFlag']=='false']
            covered=sum(r['coveredRaw']=='true' for r in group)
            yield dict(case=case,draws=n,runs=len(group),rawCovered=covered,
                rawWilson95=wilson(covered,len(group)),batchCovered=sum(r['coveredBatch']=='true' for r in group),
                accurate=sum(r['accurate']=='true' for r in group),basePassed=len(base),
                baseMissedAccuracy=sum(r['accurate']=='false' for r in base),
                queryPassed=len(selected),queryCovered=sum(r['coveredRaw']=='true' for r in selected),
                queryMissedAccuracy=sum(r['accurate']=='false' for r in selected),
                flaggedAccurate=sum(r['accurate']=='true' and r['queryFlag']=='true' for r in group))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('input',type=Path); p.add_argument('--export',type=Path); p.add_argument('--acl-script',type=Path)
    a=p.parse_args(); rows=load(a.input.read_text(encoding='utf-8-sig')); print('Validated trials:',validate(rows))
    if a.export:
        if os.name=='nt' and not a.acl_script: raise ValueError('Windows export requires ACL verification')
        destination=a.export.resolve()
        if not destination.parent.is_dir(): raise ValueError('Output parent must exist')
        stream=io.StringIO(newline=''); writer=csv.DictWriter(stream,fieldnames=FIELDS,lineterminator='\n')
        writer.writeheader(); writer.writerows(rows)
        try: destination.write_text(stream.getvalue(),encoding='utf-8')
        finally:
            if destination.exists() and a.acl_script: subprocess.run(['pwsh','-NoProfile','-File',str(a.acl_script.resolve()),'-Paths',str(destination)],check=True)
    for result in summaries(rows): print(json.dumps(result,sort_keys=True))

if __name__=='__main__': main()
