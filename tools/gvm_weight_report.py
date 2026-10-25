"""Descriptive breakdowns only; never replace frozen GVM-08 gates."""
import argparse,collections,json
from pathlib import Path
import numpy as np
from gvm_study_io import save
from gvm_weight_study import ARMS


def report(source,output):
    result=json.loads(source.read_text());rows=result['records'];arms={};censored=[]
    for arm in ARMS:
        subset=[r for r in rows if r['method']==arm];by_k={}
        for k in (1,2,3):
            part=[r for r in subset if r['k']==k]
            unqualified=[r for r in part if r['fit'].get('diagnostic') and r['fit']['diagnostic']['maximum']>1e-4]
            by_k[str(k)]=dict(attempts=len(part),completed=sum(r['fit']['status']=='Stationary' for r in part),
                retainedStationary=sum(bool(r['fit'].get('diagnostic') and r['fit']['diagnostic']['maximum']<=1e-4) for r in part),
                medianWork=float(np.median([r['fit']['totalWork'] for r in part])),
                medianSeconds=float(np.median([r['fit']['seconds'] for r in part])),
                statuses=dict(collections.Counter(r['fit']['status'] for r in part)),
                dominantResidual=dict(collections.Counter(max(r['fit']['diagnostic']['blocks'],
                    key=r['fit']['diagnostic']['blocks'].get) for r in unqualified)))
        polishes=[p for r in subset for s in r['fit']['starts'] for p in s.get('polishes',[])]
        arms[arm]=dict(byK=by_k,polishOutcomes=dict(collections.Counter(p['status'] for p in polishes)),
            acceptedPolishes=sum(p.get('accepted',False) for p in polishes),
            totalWorkByCategory={key:sum(r['fit']['work'].get(key,0) for r in subset)
                                for key in ('phase','score','moments','gradient','weight')})
        for row in subset:
            fit=row['fit']
            points=[p['totalWork'] for s in fit['starts'] for p in s.get('checkpoints',[]) if p['gradient']<=1e-4]
            censored.append(dict(d=row['d'],family=row['family'],seed=row['seed'],k=row['k'],arm=arm,
                firstStationaryCheckpointWork=min(points) if points else None,
                observed=bool(points),checkpointReportingAvailable=arm!='baseline',
                finalWork=fit['totalWork'],status=fit['status']))
    # The unmodified baseline does not record checkpoint work. Do not invent it,
    # substitute final work, or call an unavailable time-to-event an observed failure.
    selection=[]
    for d,family,seed in sorted({(r['d'],r['family'],r['seed']) for r in rows}):
        for arm in ARMS:
            part=[r for r in rows if (r['d'],r['family'],r['seed'],r['method'])==(d,family,seed,arm)]
            eligible=[r for r in part if r['fit']['status']=='Stationary']
            winner=min(eligible,key=lambda r:(-r['validationScore'],r['k'])) if eligible else None
            selection.append(dict(d=d,family=family,seed=seed,arm=arm,k=winner['k'] if winner else None,
                testScore=winner['testScore'] if winner else None,totalSearchWork=sum(r['fit']['totalWork'] for r in part)))
    payload=dict(label='descriptive, not additional promotion tests',arms=arms,
        firstStationaryWork=censored,validationSelection=selection,
        gaussianStatuses=dict(collections.Counter(r['fit']['status'] for r in rows if r['method']=='wrapped')))
    save(output,payload);print(json.dumps(arms,indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('results',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();report(a.results,a.output)
