"""Post-fit descriptive breakdowns; never replace frozen decision gates."""
import argparse,json
from collections import Counter
from pathlib import Path
import numpy as np
from gvm_study_io import save,sha


def describe(results,summary,output):
    rows=json.loads(results.read_text())['records'];report=json.loads(summary.read_text())
    assert report['resultsSha256']==sha(results)
    primary=[r for r in rows if r['method']!='wrapped'];groups=[]
    for family in ('all','curved','seam','three-separated','three-overlap','three-wrapped'):
        for k in (1,2,3):
            for method in ('em','squarem'):
                group=[r for r in primary if r['k']==k and r['method']==method and (family=='all' or r['family']==family)]
                status=dict(Counter(r['fit']['status'] for r in group))
                finite=[r for r in group if r['fit']['diagnostic'] is not None]
                groups.append(dict(family=family,k=k,method=method,count=len(group),statuses=status,
                    retainedGradientWithinTolerance=sum(r['fit']['diagnostic']['maximum']<=1e-4 for r in finite),
                    workLimitWithSmallRetainedGradient=sum(r['fit']['status']=='WorkBudgetExhausted' and r['fit']['diagnostic']['maximum']<=1e-4 for r in finite),
                    medianWork=float(np.median([r['fit']['totalWork'] for r in group])),
                    medianSeconds=float(np.median([r['fit']['seconds'] for r in group])),
                    medianMaps=float(np.median([sum(s['maps'] for s in r['fit']['starts']) for r in group])),
                    medianGradient=float(np.median([r['fit']['diagnostic']['maximum'] for r in finite])) if finite else None,
                    dominantBlocks=dict(Counter(max(r['fit']['diagnostic']['blocks'],key=r['fit']['diagnostic']['blocks'].get) for r in finite)),
                    workByCategory={name:sum(r['fit']['work'][name] for r in group) for name in ('phase','score','moments','gradient')},
                    extrapolationSteps=sum(len(s['alphas']) for r in group for s in r['fit']['starts']),
                    stepCapHits=sum(a==-4. for r in group for s in r['fit']['starts'] for a in s['alphas']),
                    accepted=sum(sum(s['accepted'] for s in r['fit']['starts']) for r in group),
                    rejected=sum(sum(s['rejected'] for s in r['fit']['starts']) for r in group)))
    selection=[]
    for family in ('all','curved','seam','three-separated','three-overlap','three-wrapped'):
        for method in ('em','squarem'):
            group=[r for r in report['selection'] if r['method']==method and (family=='all' or r['family']==family)]
            selection.append(dict(family=family,method=method,counts=dict(Counter(str(r['k']) for r in group)),
                medianTotalWork=float(np.median([r['totalWork'] for r in group]))))
    out=dict(scope='descriptive only; frozen gates unchanged',resultsSha256=sha(results),summarySha256=sha(summary),
             groups=groups,selection=selection)
    save(output,out)
    print(json.dumps(dict(groups=[r for r in groups if r['family']=='all'],selection=selection),indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('results',type=Path);p.add_argument('summary',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();describe(a.results,a.summary,a.output)
