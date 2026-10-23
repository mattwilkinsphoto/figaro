"""INF-18 frozen all-outcome analysis. Failures are inventory, never discarded samples."""
import argparse,hashlib,json,math,sys
from pathlib import Path
import numpy as np
from continuous_filter_reference import Output
from multicomponent_reference import NAMES,SQUARE,METRICS,spec

COLS='name mode n seed t status logEvidence mean second positive tail r0 r1 r2 ess draws likelihoods targets proposals components guides h jac gaussians conditioning pilotDraws pilotLikelihoods pilotAncestors fitDensities fitSweeps ancestors stored unknownGaussian nanos fitStatus error'.split()
FLOATS=set(('logEvidence','ess')+METRICS)
INTS=set(COLS)-FLOATS-set(('name','mode','status','fitStatus','error'))
EXTRA=['big-branch','big-pilot','eval-branch','eval-pilot']
CANDIDATES=('branch','pilot')
def modes(name,confirmation):
    return ['bootstrap','branch','pilot','cubature','ekf']+(['manual'] if name in SQUARE else [])+(['exact'] if name=='affine' else [])+(EXTRA if confirmation else [])
def seeds(confirmation):return range(120001,120257) if confirmation else range(110001,110017)

def parse(path,confirmation):
    rows={};scientific=[];headers=0
    for line in Path(path).read_text(encoding='utf-8',errors='replace').splitlines():
        if 'MC_COLUMNS\t' in line:
            assert line.split('MC_COLUMNS\t',1)[1].split('\t')==COLS;headers+=1
        if 'MC_ROW\t' not in line:continue
        fields=line.split('MC_ROW\t',1)[1].split('\t');assert len(fields)==len(COLS)
        r=dict(zip(COLS,fields))
        for k in INTS:r[k]=int(r[k])
        for k in FLOATS:r[k]=float(r[k])
        key=(r['name'],r['mode'],r['seed'],r['t']);assert key not in rows
        assert r['status'] in ('ok','error','unavailable') and 64<=r['n']<=4096
        assert all(r[k]>=0 for k in INTS-{'seed'}) and r['stored']<=100000
        if r['status']=='ok':
            assert all(math.isfinite(r[k]) for k in FLOATS)
            assert r['draws']==r['n']*(r['t']+1)==r['likelihoods'] and r['unknownGaussian']==0
            assert abs(r['r0']+r['r1']+r['r2']-1)<1e-10
            assert all(-1e-12<=r[k]<=1+1e-12 for k in ('positive','tail','r0','r1','r2'))
            if r['mode']=='pilot':
                assert r['pilotDraws']==r['pilotLikelihoods']==512*(r['t']+1)
                assert r['pilotAncestors']==512*r['t'] and r['fitStatus']=='Fitted'
            else:assert r['pilotDraws']==r['pilotLikelihoods']==r['fitDensities']==0
        else:assert r['error'] and all(math.isnan(r[k]) for k in FLOATS)
        rows[key]=r;scientific.append('\t'.join(v for k,v in zip(COLS,fields) if k!='nanos'))
    expected={(n,m,s,t) for n in NAMES for m in modes(n,confirmation) for s in seeds(confirmation) for t in range(len(spec(n)['observations']))}
    assert headers==1 and set(rows)==expected,('inventory',headers,len(rows),len(expected))
    for name in NAMES:
        for mode in modes(name,confirmation):
            for seed in seeds(confirmation):
                previous=None;terminal=False
                for t in range(len(spec(name)['observations'])):
                    r=rows[name,mode,seed,t]
                    assert (r['status']=='unavailable')==terminal
                    if previous:
                        assert all(r[k]>=previous[k] for k in INTS-{'seed','n','t'})
                        if terminal:assert all(r[k]==previous[k] for k in INTS-{'seed','n','t','nanos'})
                    terminal=terminal or r['status']=='error';previous=r
    return rows,hashlib.sha256('\n'.join(sorted(scientific)).encode()).hexdigest()

def calibrate(path):
    rows,digest=parse(path,False);budgets={};details=[]
    for name in NAMES:
        nt=len(spec(name)['observations']);last=nt-1
        complete=lambda m:all(rows[name,m,s,t]['status']=='ok' for s in seeds(False) for t in range(nt))
        for candidate in CANDIDATES:
            available=complete(candidate) and complete('bootstrap')
            for metric,prefix in (('time','big'),('evaluations','eval')):
                def cost(mode):
                    return float(np.median([rows[name,mode,s,last]['nanos'] if metric=='time' else
                      sum(rows[name,mode,s,last][k] for k in ('h','jac','draws','pilotDraws')) for s in seeds(False)]))
                a=cost(candidate);b=cost('bootstrap');raw=math.ceil(64*a/b) if available else None
                n=min(4096,max(64,raw)) if available else 4096
                key=name+'/'+prefix+'-'+candidate;budgets[key]=n
                details.append(dict(name=name,candidate=candidate,metric=metric,available=available,
                  candidateMedian=a,bootstrapMedian=b,uncappedN=raw,n=n,capPreventedMatch=available and raw>4096))
    return dict(protocol='multicomponent-v1',budgets=budgets,details=details,scientificSha256=digest,
                developmentLogSha256=hashlib.sha256(Path(path).read_bytes()).hexdigest())

def finite(value):
    if isinstance(value,dict):return {k:finite(v) for k,v in value.items()}
    if isinstance(value,list):return [finite(v) for v in value]
    if isinstance(value,float) and not math.isfinite(value):return None
    return value

def loss_components(values,truth):
    target=np.array([[r[k] for k in METRICS] for r in truth]);err=(values-target)**2
    denom=np.array([[max(1,r['second']-r['mean']**2),max(1,r['second']**2)] for r in truth])
    loss=(err[:,:,:2]/denom).sum(axis=2)+err[:,:,4:].sum(axis=2)
    return loss.mean(axis=1),err.mean(axis=1)

def analyze(paths,reference,budgets):
    ref=json.loads(Path(reference).read_text());assert ref['passed'] and len(ref['checks'])==1023
    cal=json.loads(Path(budgets).read_text());parsed=[parse(p,True) for p in paths];assert len(parsed)==3
    replay=len({d for _,d in parsed})==1;first=parsed[0][0]
    checks=[];groups={};summaries=[];failures=[]
    for name in NAMES:
        truth=ref['references'][name];nt=len(truth)
        target=np.array([[r[k] for k in METRICS] for r in truth])
        for mode in modes(name,True):
            runs=[[first[name,mode,s,t] for t in range(nt)] for s in seeds(True)]
            expected_n=cal['budgets'][name+'/'+mode] if mode in EXTRA else 64
            assert all(r['n']==expected_n for run in runs for r in run)
            bad=[r for run in runs for r in run if r['status']!='ok'];failures+=bad
            times=[float(np.median([rows[name,mode,s,nt-1]['nanos'] for s in seeds(True)]))/1e6 for rows,_ in parsed]
            summary=dict(name=name,mode=mode,n=expected_n,completed=sum(all(r['status']=='ok' for r in run) for run in runs),
                attempted=256,medianMs=times,counters={k:float(np.mean([run[-1][k] for run in runs])) for k in INTS-{'n','seed','t','nanos'}},
                refusals={status:sum(run[t]['status']=='error' and run[t]['fitStatus']==status for run in runs for t in range(nt))
                          for status in sorted({r['fitStatus'] for r in bad if r['status']=='error'})})
            if not bad:
                values=np.array([[[r[k] for k in METRICS] for r in run] for run in runs])
                loss,errors=loss_components(values,truth)
                eligible=(target[:,4:]>=.1)&(target[:,4:]<=.9)
                depleted=np.any((values[:,:,4:]<.01)&eligible,axis=(1,2))
                groups[name,mode]=dict(loss=loss,errors=errors,depleted=depleted,times=times)
                summary.update(meanLoss=float(loss.mean()),rootLoss=float(np.sqrt(loss.mean())),
                    mse=dict(zip(METRICS,errors.mean(axis=0).tolist())),depleted=int(depleted.sum()))
                for t,tr in enumerate(truth):
                    z=np.exp([run[t]['logEvidence'] for run in runs]);tz=math.exp(tr['logEvidence'])
                    for j,k in enumerate(('evidence',)+METRICS):
                        sample=z if j==0 else z*values[:,t,j-1];expected=tz if j==0 else tz*tr[k]
                        se=float(sample.std(ddof=1)/16);error=abs(float(sample.mean())-expected)
                        threshold=6*se+1e-4*max(1,abs(expected))
                        checks.append(dict(name=name,mode=mode,t=t,metric=k,error=error,threshold=threshold,passed=error<=threshold))
            summaries.append(summary)
    utility=[]
    for mode in CANDIDATES:
        primary=[];nonregression=[];depletion=[]
        for name in ('asymmetric','three'):
            gate=dict(name=name,available=(name,mode) in groups,passed=False)
            if gate['available'] and (name,'big-'+mode) in groups:
                a=groups[name,mode];b=groups[name,'big-'+mode];delta=a['loss']-b['loss']
                ratio=math.sqrt(float(a['loss'].mean()/b['loss'].mean()));upper=float(delta.mean()+2.81*delta.std(ddof=1)/16)
                d=next(d for d in cal['details'] if d['name']==name and d['candidate']==mode and d['metric']=='time')
                cost=all(x<=1.25*y for x,y in zip(a['times'],b['times']))
                gate.update(rootLossRatio=ratio,pairedDifferenceUpper=upper,costPassed=cost,
                    matchAvailable=d['available'],capPreventedMatch=d['capPreventedMatch'],
                    passed=ratio<=.9 and upper<0 and cost and d['available'] and not d['capPreventedMatch'])
            primary.append(gate)
        for name in NAMES:
            gate=dict(name=name,available=(name,mode) in groups and (name,'bootstrap') in groups,passed=False)
            if gate['available']:
                a=groups[name,mode];b=groups[name,'bootstrap'];am=a['errors'].mean(axis=0);bm=b['errors'].mean(axis=0)
                metrics={k:dict(mse=float(am[j]),limit=float(1.25*bm[j]+(1e-5 if k=='tail' else 1e-4)),
                               passed=bool(am[j]<=1.25*bm[j]+(1e-5 if k=='tail' else 1e-4)))
                         for j,k in enumerate(METRICS) if k in ('tail','r0','r1','r2')}
                aggregate=name not in ('affine','weak') or a['loss'].mean()<=1.25*b['loss'].mean()
                gate.update(metrics=metrics,aggregatePassed=bool(aggregate),passed=bool(aggregate) and all(v['passed'] for v in metrics.values()))
            nonregression.append(gate)
        for name in ('symmetric','asymmetric','three','narrow'):
            gate=dict(name=name,available=(name,mode) in groups and (name,'bootstrap') in groups,passed=False)
            if gate['available']:
                delta=groups[name,mode]['depleted'].astype(float)-groups[name,'bootstrap']['depleted'].astype(float)
                difference=int(delta.sum());mean=float(delta.mean());half=1.96*float(delta.std(ddof=1))/16
                gate.update(excessRuns=difference,rateDifference=mean,approximate95Interval=[mean-half,mean+half],passed=difference<=3)
            depletion.append(gate)
        relevant=lambda m:m not in CANDIDATES or m==mode
        correctness=replay and not any(relevant(r['mode']) for r in failures) and all(c['passed'] for c in checks if relevant(c['mode']))
        passed=correctness and all(g['passed'] for g in primary+nonregression+depletion)
        utility.append(dict(mode=mode,correctnessPassed=correctness,primary=primary,nonregression=nonregression,depletion=depletion,passed=passed))
    return finite(dict(protocol='multicomponent-v1',replay=replay,scientificDigests=[d for _,d in parsed],rowsPerJvm=len(first),
        checks=checks,failures=failures,summaries=summaries,utility=utility,
        promotionEligible=any(u['passed'] for u in utility),completeInventory=True))

def plot(result,out):
    # Small multiples, no overlap: measured primary cases only, complete groups only.
    lines=['<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="680" viewBox="0 0 1000 680">',
      '<rect width="1000" height="680" fill="white"/><g font-family="sans-serif" font-size="13" fill="#222">',
      '<text x="25" y="25">Multi-component v1: held-out primary loss and measured cost</text>',
      '<text x="25" y="48">256 seeds; first JVM median milliseconds. Missing candidate groups are refusals, not zero loss.</text>']
    for panel,name in enumerate(('asymmetric','three')):
        data=[s for s in result['summaries'] if s['name']==name]
        peak=max(s.get('rootLoss',0) for s in data);x=25+panel*490
        lines.append(f'<text x="{x}" y="85">{name} (bar = root loss; shorter is better)</text>')
        for i,s in enumerate(data):
            y=115+43*i;w=210*s.get('rootLoss',0)/max(peak,1e-100)
            color='#27679a' if s['mode']=='branch' else '#946432' if s['mode']=='pilot' else '#a5adb3'
            lines.append(f'<text x="{x}" y="{y}">{s["mode"]}</text><rect x="{x+105}" y="{y-12}" width="{w:.2f}" height="15" fill="{color}"/>')
            label=f'{s["rootLoss"]:.4g}; {s["medianMs"][0]:.3g} ms' if 'rootLoss' in s else f'{s["completed"]}/256 complete; not compared'
            lines.append(f'<text x="{x+105}" y="{y+17}">{label}</text>')
    lines+=['<text x="25" y="645">Synthetic controls only. Cost calibration and all negative gates are retained in results.json.</text>','</g></svg>']
    out.write('comparison.svg','\n'.join(lines))

def main():
    p=argparse.ArgumentParser();p.add_argument('action',choices=['calibrate','analyze']);p.add_argument('--logs',nargs='+',required=True)
    p.add_argument('--reference');p.add_argument('--budgets');p.add_argument('--output',required=True);p.add_argument('--acl-script');a=p.parse_args()
    out=Output(a.output,a.acl_script)
    if a.action=='calibrate':
        assert len(a.logs)==1;r=calibrate(a.logs[0]);out.json('budgets.json',r)
        out.write('budgets.tsv','\n'.join(key.replace('/','\t')+'\t'+str(n) for key,n in r['budgets'].items())+'\n')
        print(json.dumps(r));return
    r=analyze(a.logs,a.reference,a.budgets);out.json('results.json',r);plot(r,out)
    print(json.dumps({k:r[k] for k in ('replay','rowsPerJvm','completeInventory','promotionEligible')}))
    print('Checks:',len(r['checks']),'failed:',sum(not c['passed'] for c in r['checks']),'failure rows:',len(r['failures']))
    for u in r['utility']:print(json.dumps(u))
    # Negative results are valid completed experiments, not a successful promotion check.
    sys.exit(0 if r['replay'] and all(u['correctnessPassed'] for u in r['utility']) else 1)
if __name__=='__main__':main()
