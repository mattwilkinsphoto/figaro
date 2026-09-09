"""Validate the complete predeclared defensive-proposal grid; never discard bad accuracy rows."""
import argparse
import csv
import io
import json
import math
import os
from pathlib import Path
import statistics
import subprocess

SEEDS = [1009 + 7919*i for i in range(30)]
BUDGETS = (2000, 20000, 200000)
METHODS = ('prior', 'defensive', 'slice')
REFERENCES = {
    'gamma-original': ((2.107869973169636,1.844729346924452),(.1967401705530933,.1970468052314994)),
    'dirichlet-original': ((.841016150722901,1.910950755236635,2.640339278723853),(.0608399843880491,.1434315061608727,.2005600807508122)),
    'gamma-heldout': ((2.1344473479226362,1.8545503427937535),(.19937384381701873,.19794033663822933)),
    'dirichlet-heldout': ((.9522859595106329,1.9694768223230366,3.023988742447311),(.06891111207876513,.14679812158777752,.228495626039935)),
}
FIELDS = 'target,seed,budget,method,coordinate,mean,error,mcse,ess,status,maxWeight,k,covered95,accurate,pilotCalls,pilotDraws,fallback,draws,evaluations,seconds'.split(',')

def read(text):
    lines = text.splitlines()
    if any(line.startswith('DI,') for line in lines):
        lines = [line[3:] for line in lines if line.startswith('DI,')]
    return list(csv.DictReader(lines))

def number(value):
    if value == 'NA': return None
    x = float(value)
    if not math.isfinite(x): raise ValueError('Nonfinite number')
    return x

def validate(rows, coverage=False):
    targets = [t for t in REFERENCES if t.endswith('heldout')] if coverage else REFERENCES
    seeds = [1000000007+7919*i for i in range(200)] if coverage else SEEDS
    budgets = (20000,) if coverage else BUDGETS
    methods = ('defensive',) if coverage else METHODS
    expected = {(target,str(seed),str(budget),method,str(i))
                for target in targets for seed in seeds
                for budget in budgets for method in methods for i in range(len(REFERENCES[target][0]))}
    seen, trials = set(), {}
    for row in rows:
        if set(row) != set(FIELDS): raise ValueError('Unexpected fields')
        key = tuple(row[k] for k in FIELDS[:5])
        if key not in expected or key in seen: raise ValueError('Unexpected or duplicate row')
        seen.add(key)
        target,seed,budget,method,index = key
        budget,index = int(budget),int(index)
        values = {k:number(row[k]) for k in ('mean','error','mcse','ess','maxWeight','k','seconds')}
        if row['status'] not in ('Danger','Warning','InsufficientEvidence','ChecksPassed'): raise ValueError('Unknown health status')
        for k in ('covered95','accurate','fallback'):
            if row[k] not in ('true','false'): raise ValueError('Invalid boolean')
        for k in ('pilotCalls','pilotDraws','draws','evaluations'):
            if not row[k].isdigit(): raise ValueError('Invalid count')
        if int(row['evaluations']) != budget: raise ValueError('Unmatched total work')
        pilot = min(10000,budget//2) if method == 'defensive' else 0
        if int(row['pilotCalls']) != pilot: raise ValueError('Uncharged/incorrect pilot work')
        if method != 'defensive' and (row['fallback'] != 'false' or row['pilotDraws'] != '0'): raise ValueError('Unexpected pilot')
        draws = int(row['draws'])
        if method != 'slice' and draws != budget-pilot: raise ValueError('Production draw count differs')
        if method == 'slice' and (draws%4 or draws > budget): raise ValueError('Invalid aligned chains')
        if values['seconds'] is None or values['seconds'] <= 0: raise ValueError('Invalid time')
        for k in ('mcse','ess','maxWeight'):
            if values[k] is not None and values[k] < 0: raise ValueError('Negative diagnostic')
        if values['ess'] is not None and values['ess'] > draws+1e-8: raise ValueError('Impossible ESS')
        if values['maxWeight'] is not None and values['maxWeight'] > 1: raise ValueError('Impossible weight')
        if method == 'slice' and (values['maxWeight'] is not None or values['k'] is not None): raise ValueError('Weighted metric on MCMC')
        if row['status']=='ChecksPassed' and any(values[k] is None for k in ('mean','mcse','ess')): raise ValueError('Missing diagnostics cannot pass')
        if method != 'slice':
            if ((values['k'] is not None and values['k'] >= .7) or
                (values['maxWeight'] is not None and values['maxWeight'] >= .5)) and row['status'] != 'Danger':
                raise ValueError('Severe weight diagnostics must retain danger')
            if row['status']=='ChecksPassed' and (values['k'] is None or values['k'] >= min(.7,1-1/math.log10(draws)) or
                values['ess'] < 100 or values['ess']/draws < .01 or values['maxWeight'] is None or values['maxWeight'] >= .1):
                raise ValueError('Failed weight checks mislabeled as passed')
        ref,sd = REFERENCES[target][0][index],REFERENCES[target][1][index]
        if (values['mean'] is None) != (values['error'] is None): raise ValueError('Missing estimate/error mismatch')
        if values['mean'] is not None and not math.isclose(values['error'],values['mean']-ref,rel_tol=1e-10,abs_tol=1e-12): raise ValueError('Incorrect reference error')
        accurate = values['error'] is not None and abs(values['error']) <= .1*sd
        covered = values['error'] is not None and values['mcse'] is not None and abs(values['error']) <= 1.959963984540054*values['mcse']
        if row['accurate'] != str(accurate).lower() or row['covered95'] != str(covered).lower(): raise ValueError('Incorrect accuracy/coverage flag')
        trial = key[:4]
        meta = tuple(row[k] for k in ('pilotCalls','pilotDraws','fallback','draws','evaluations','seconds'))
        if trial in trials and trials[trial] != meta: raise ValueError('Inconsistent repeated trial metadata')
        trials[trial] = meta
    if seen != expected: raise ValueError(f'Incomplete grid: {len(seen)}/{len(expected)} rows')
    return len(trials)

def summarize(rows, seeds=SEEDS):
    for target,(means,_) in REFERENCES.items():
        for budget in BUDGETS:
            for method in METHODS:
                selected = [r for r in rows if r['target']==target and int(r['budget'])==budget and r['method']==method and int(r['seed']) in seeds]
                if not selected: continue
                coordinates = [[r for r in selected if int(r['coordinate'])==i] for i in range(len(means))]
                complete = [[r for r in selected if int(r['seed'])==s] for s in seeds]
                first = coordinates[0]
                ess = [number(r['ess']) for r in first if r['ess'] != 'NA']
                yield dict(target=target,budget=budget,method=method,runs=len(seeds),
                    allAccurate=sum(all(r['accurate']=='true' for r in trial) for trial in complete),
                    coveredByCoordinate=[sum(r['covered95']=='true' for r in c) for c in coordinates],
                    rmse=[math.sqrt(statistics.mean(float(r['error'])**2 for r in c)) if all(r['error']!='NA' for r in c) else None for c in coordinates],
                    dangerRuns=sum(any(r['status']=='Danger' for r in trial) for trial in complete),
                    passedRuns=sum(all(r['status']=='ChecksPassed' for r in trial) for trial in complete),
                    fallbacks=sum(r['fallback']=='true' for r in first),
                    medianFirstCoordinateEss=statistics.median(ess) if ess else None,
                    medianSeconds=statistics.median(float(r['seconds']) for r in first))

def wilson(successes, total):
    if total <= 0 or successes < 0 or successes > total: raise ValueError('Invalid binomial counts')
    z=1.959963984540054
    p=successes/total
    center=(p+z*z/(2*total))/(1+z*z/total)
    half=z*math.sqrt(p*(1-p)/total+z*z/(4*total*total))/(1+z*z/total)
    return [center-half,center+half]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('input',type=Path)
    p.add_argument('--export',type=Path)
    p.add_argument('--acl-script',type=Path)
    p.add_argument('--batch',choices=('all','first','second'),default='all')
    p.add_argument('--coverage',action='store_true')
    args=p.parse_args()
    rows=read(args.input.read_text(encoding='utf-8-sig'))
    trials=validate(rows,args.coverage)
    if args.export:
        if os.name=='nt' and not args.acl_script: raise ValueError('Windows export requires exact-path ACL verification')
        destination=args.export.resolve()
        if not destination.parent.is_dir(): raise ValueError('Output parent must exist')
        stream=io.StringIO(newline='')
        writer=csv.DictWriter(stream,fieldnames=FIELDS,lineterminator='\n')
        writer.writeheader(); writer.writerows(rows)
        try: destination.write_text(stream.getvalue(),encoding='utf-8')
        finally:
            if destination.exists() and args.acl_script:
                subprocess.run(['pwsh','-NoProfile','-File',str(args.acl_script.resolve()),'-Paths',str(destination)],check=True)
    print(f'Validated {len(rows)} coordinate rows / {trials} trials')
    seeds=([1000000007+7919*i for i in range(200)] if args.coverage else
           SEEDS if args.batch=='all' else SEEDS[:15] if args.batch=='first' else SEEDS[15:])
    for result in summarize(rows,seeds):
        if args.coverage: result['coverageWilson95']=[wilson(n,len(seeds)) for n in result['coveredByCoordinate']]
        print(json.dumps(result,sort_keys=True))

if __name__=='__main__': main()
