"""Complete static/owned graph cost evidence; retain accuracy and negative heap deltas."""
import argparse
import csv
import io
import math
from pathlib import Path
import statistics

FIELDS='jvm seed model draws method mean truth ess nanos heap_delta'.split()


def parse_log(text,jvm):
    rows=[]
    for line in text.splitlines():
        if not line.startswith('STATIC_STUDY,') or line.startswith('STATIC_STUDY,seed,'): continue
        parts=next(csv.reader([line]))[1:]
        if len(parts)!=9: raise ValueError('Malformed static evidence')
        rows.append(dict(zip(FIELDS,[str(jvm)]+parts)))
    return rows


def validate(rows,seeds=20,jvms=3):
    expected={(j,s,m,n,a) for j in range(jvms) for s in range(seeds) for m in range(3) for n in (2000,20000) for a in range(4)}
    seen={}
    for r in rows:
        key=tuple(int(r[f]) for f in FIELDS[:5])
        if key not in expected or key in seen: raise ValueError('Duplicate or unexpected key')
        mean,truth,ess=(float(r[f]) for f in ('mean','truth','ess'))
        if not all(math.isfinite(x) for x in (mean,truth,ess)) or not 1-1e-9<=ess<=key[3]+1e-9 or int(r['nanos'])<=0:
            raise ValueError('Invalid numeric result')
        int(r['heap_delta'])  # signed: collection can make this negative
        a=.9999**(64 if key[2]==2 else 0)
        variance=.01 if key[2]==1 else 1
        if abs(truth-a*a/(variance+a*a))>1e-12: raise ValueError('Wrong target reference')
        seen[key]=r
    if set(seen)!=expected: raise ValueError('Incomplete static grid')
    for (j,s,m,n,method),r in seen.items():
        if method>1:
            base=seen[(j,s,m,n,1)]
            if any(r[f]!=base[f] for f in ('mean','ess')): raise ValueError('Worker-count replay changed')
    return rows


def summarize(rows):
    lookup={tuple(int(r[f]) for f in FIELDS[:5]):r for r in rows}
    for model in range(3):
        for n in (2000,20000):
            bases=[(key,r) for key,r in lookup.items() if key[2:]==(model,n,0)]
            for method in (1,2,3):
                pairs=[(r,lookup[key[:4]+(method,)]) for key,r in bases]
                ratio=statistics.median(int(a['nanos'])/int(b['nanos']) for a,b in pairs)
                rmse=math.sqrt(statistics.mean((float(b['mean'])-float(b['truth']))**2 for _,b in pairs))
                eps=statistics.median(float(b['ess'])*1e9/int(b['nanos']) for _,b in pairs)
                print(f'model={model} draws={n} workers={2**(method-1)} paired_speedup_vs_owned={ratio:.3f} rmse={rmse:.6f} median_ess_per_second={eps:.0f}')
            scale=statistics.median(int(lookup[key[:4]+(1,)]['nanos'])/int(lookup[key[:4]+(3,)]['nanos']) for key,_ in bases)
            print(f'model={model} draws={n} static_1_to_4_worker_speedup={scale:.3f}')


if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('paths',nargs='+',type=Path); p.add_argument('--output',type=Path)
    p.add_argument('--seeds',type=int,default=20); p.add_argument('--jvms',type=int,default=3); args=p.parse_args()
    rows=list(csv.DictReader(io.StringIO(args.paths[0].read_text(encoding='utf-8')))) if len(args.paths)==1 and args.paths[0].suffix=='.csv' else [r for j,path in enumerate(args.paths) for r in parse_log(path.read_text(encoding='utf-8'),j)]
    validate(rows,args.seeds,args.jvms)
    if args.output:
        with args.output.open('w',encoding='utf-8',newline='') as out:
            w=csv.DictWriter(out,fieldnames=FIELDS); w.writeheader(); w.writerows(rows)
    summarize(rows)
