"""Check the full declared joint-block/query-aware graph grid, retaining zero-event estimates."""
import argparse,csv,io,json,math,os,statistics,subprocess
from pathlib import Path
from summarize_defensive_importance import read
FIELDS='case,method,seed,draws,mean,reference,ess,mcse,seconds'.split(',')
CASES={'hierarchical':1/1.02,'nonlinear':.5,'rare-event':3.167124183311997e-5}
METHODS=('prior','root','joint')
def load(text): return read(text.replace('JP,','DI,'))
def validate(rows):
    expected={(c,m,str(59000021+15485863*i)) for c in CASES for m in METHODS for i in range(100)}; seen=set()
    for r in rows:
        if set(r)!=set(FIELDS): raise ValueError('Wrong schema')
        k=tuple(r[x] for x in ('case','method','seed'))
        if k not in expected or k in seen: raise ValueError('Unexpected/duplicate row')
        seen.add(k)
        if r['draws']!='4000' or not math.isclose(float(r['reference']),CASES[k[0]],rel_tol=1e-13,abs_tol=1e-16): raise ValueError('Changed fixture')
        if not all(math.isfinite(float(r[x])) for x in ('mean','ess','mcse','seconds')): raise ValueError('Nonfinite row')
        if not 0<float(r['ess'])<=4000+1e-7 or float(r['mcse'])<0 or float(r['seconds'])<=0: raise ValueError('Invalid diagnostics')
        if k[0] in ('nonlinear','rare-event') and not 0<=float(r['mean'])<=1: raise ValueError('Invalid event')
    if seen!=expected: raise ValueError('Incomplete grid')
    return len(seen)
def summaries(rows):
    for c in CASES:
        for m in METHODS:
            rs=[r for r in rows if r['case']==c and r['method']==m]
            yield dict(case=c,method=m,rmse=math.sqrt(statistics.mean((float(r['mean'])-CASES[c])**2 for r in rs)),
                medianEss=statistics.median(float(r['ess']) for r in rs),meanSeconds=statistics.mean(float(r['seconds']) for r in rs),
                zeroEstimates=sum(float(r['mean'])==0 for r in rs),
                accurate=sum(abs(float(r['mean'])-CASES[c]) <= (.2*CASES[c] if c=='rare-event' else .025) for r in rs))
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('input',type=Path);p.add_argument('--export',type=Path);p.add_argument('--acl-script',type=Path);a=p.parse_args()
    rows=load(a.input.read_text(encoding='utf-8-sig')); print('Validated rows:',validate(rows))
    if a.export:
        if os.name=='nt' and not a.acl_script: raise ValueError('Windows export requires ACL hook')
        destination=a.export.resolve();s=io.StringIO(newline='');w=csv.DictWriter(s,fieldnames=FIELDS,lineterminator='\n');w.writeheader();w.writerows(rows)
        try: destination.write_text(s.getvalue(),encoding='utf-8')
        finally:
            if destination.exists() and a.acl_script: subprocess.run(['pwsh','-NoProfile','-File',str(a.acl_script.resolve()),'-Paths',str(destination)],check=True)
    for r in summaries(rows):print(json.dumps(r,sort_keys=True))
if __name__=='__main__':main()
