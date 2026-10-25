"""Frozen reporting logic: seed pairs, not timing repetitions, are replicates."""
import math,statistics
from scipy.stats import t


def bounds(values,alpha):
    if len(values)<2 or not all(math.isfinite(x) for x in values):raise ValueError('Invalid paired sample')
    mean=statistics.mean(values);radius=float(t.ppf(1-alpha,len(values)-1))*statistics.stdev(values)/math.sqrt(len(values))
    return dict(mean=mean,lower=mean-radius,upper=mean+radius,n=len(values))


def report(rows,cfg):
    pairs=[];cells=[];lookup={}
    for r in rows:lookup.setdefault((r['d'],r['family'],r['k'],r['seed'],r['arm']),[]).append(r)
    for d in cfg['dimensions']:
        for f in cfg['families']:
            for k in cfg['components']:
                cellpairs=[]
                for seed in cfg['seeds']:
                    p=dict(d=d,family=f,k=k,seed=seed)
                    canonical={}
                    for arm in cfg['arms']:
                        records=sorted(lookup[d,f,k,seed,arm],key=lambda r:r['repeat']);canonical[arm]=records[0]
                        p[arm+'Complete']=all(r['fit'] is not None and r['fit']['status']=='Stationary' for r in records)
                        p[arm+'Wall']=statistics.median(r['wallSeconds'] for r in records)
                        p[arm+'Cpu']=statistics.median(r['cpuSeconds'] for r in records)
                        p[arm+'WallRange']=[min(r['wallSeconds'] for r in records),max(r['wallSeconds'] for r in records)]
                        p[arm+'Work']=records[0]['fit']['totalWork'] if records[0]['fit'] else None
                    scores=[canonical[a]['testScore'] for a in ('joint','baseline')]
                    p['scoreDifference']=None if None in scores else scores[0]-scores[1]
                    pairs.append(p);cellpairs.append(p)
                differences=[p['scoreDifference'] for p in cellpairs]
                ni=None if None in differences else bounds(differences,cfg['alpha']/cfg['comparisons'])
                cells.append(dict(d=d,family=f,k=k,baselineComplete=sum(p['baselineComplete'] for p in cellpairs),
                    jointComplete=sum(p['jointComplete'] for p in cellpairs),score=ni,
                    scorePassed=ni is not None and ni['lower']>=-cfg['scoreMargin']))
    seed_cost=[]
    for seed in cfg['seeds']:
        totals={a:sum(p[a+'Wall'] for p in pairs if p['seed']==seed) for a in cfg['arms']}
        seed_cost.append(dict(seed=seed,**totals,logRatio=math.log(totals['joint']/totals['baseline'])))
    cost=bounds([s['logRatio'] for s in seed_cost],cfg['alpha']/cfg['comparisons'])
    cost['ratioUpper']=math.exp(cost['upper']);cost['geometricMeanRatio']=math.exp(cost['mean'])
    completed={a:sum(p[a+'Complete'] for p in pairs) for a in cfg['arms']}
    lost=sum(p['baselineComplete'] and not p['jointComplete'] for p in pairs)
    gates=dict(perCellReliability=all(p['jointComplete']/len(cfg['seeds'])>=cfg['qualifiedRate'] for p in cells),
        completionGain=(completed['joint']-completed['baseline'])/len(pairs)>=cfg['completionGain'],
        noLostCompletions=lost<=cfg['maximumLostCompletions'],scoreNoninferiority=all(c['scorePassed'] for c in cells),
        totalCostNonincrease=cost['ratioUpper']<=cfg['maximumCostRatio'])
    totals={a:dict(actualWall=sum(r['wallSeconds'] for r in rows if r['arm']==a),
        actualCpu=sum(r['cpuSeconds'] for r in rows if r['arm']==a),
        sumMedianWall=sum(p[a+'Wall'] for p in pairs),sumMedianCpu=sum(p[a+'Cpu'] for p in pairs),
        canonicalWork=sum(p[a+'Work'] or 0 for p in pairs),missingWork=sum(p[a+'Work'] is None for p in pairs)) for a in cfg['arms']}
    return dict(completed=completed,lostBaselineCases=lost,gates=gates,cells=cells,pairs=pairs,
                seedCost=seed_cost,costBound=cost,totals=totals)
