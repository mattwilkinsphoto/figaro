"""INF-17 frozen all-outcome analysis. Never reruns or selects successful seeds."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys
import numpy as np
from continuous_filter_reference import Output
from nonlinear_proposal_reference import NAMES,spec

COLS='name mode n seed t status logEvidence mean second positive ess draws likelihoods targets proposals components guides h jac gaussians cholesky solves look ancestors nanos error'.split()
INTS=set('n seed t draws likelihoods targets proposals components guides h jac gaussians cholesky solves look ancestors nanos'.split())
FLOATS=set('logEvidence mean second positive ess'.split())

def modes(name,confirmation):
    return ['bootstrap','cubature','ekf','aux','bad']+(['manual'] if name in ('symmetric','asymmetric') else [])+(['exact'] if name=='affine' else [])+(['big'] if confirmation else [])

def parse(path,confirmation):
    rows={};scientific=[];headers=0
    for line in Path(path).read_text(encoding='utf-8',errors='replace').splitlines():
        if 'NP_COLUMNS\t' in line:
            assert line.split('NP_COLUMNS\t',1)[1].split('\t')==COLS;headers+=1
        if 'NP_ROW\t' not in line:continue
        fields=line.split('NP_ROW\t',1)[1].split('\t');assert len(fields)==len(COLS)
        r=dict(zip(COLS,fields))
        for k in INTS:r[k]=int(r[k])
        for k in FLOATS:r[k]=float(r[k])
        key=(r['name'],r['mode'],r['seed'],r['t']);assert key not in rows
        if r['status']=='ok':
            assert all(math.isfinite(r[k]) for k in FLOATS)
            assert r['draws']==r['n']*(r['t']+1)==r['likelihoods']
        rows[key]=r
        scientific.append('\t'.join(v for k,v in zip(COLS,fields) if k!='nanos'))
    seeds=range(90001,90129) if confirmation else range(89001,89017)
    expected={(n,m,s,t) for n in NAMES for m in modes(n,confirmation) for s in seeds for t in range(len(spec(n)['observations']))}
    assert headers==1 and set(rows)==expected,('inventory',headers,len(rows),len(expected))
    return rows,hashlib.sha256('\n'.join(sorted(scientific)).encode()).hexdigest()

def calibrate(path):
    rows,digest=parse(path,False);budgets={};details=[]
    assert all(r['status']=='ok' for r in rows.values()),'Development failures block calibration'
    for name in NAMES:
        t=len(spec(name)['observations'])-1
        times={m:float(np.median([rows[name,m,s,t]['nanos'] for s in range(89001,89017)])) for m in modes(name,False)}
        raw=math.ceil(64*max(times[m] for m in ('cubature','ekf','aux'))/times['bootstrap'])
        budgets[name]=min(4096,max(64,raw))
        details.append(dict(name=name,medianNanos=times,uncappedN=raw,n=budgets[name],capPreventedMatch=raw>4096))
    return dict(budgets=budgets,details=details,scientificSha256=digest)

def analyze(paths,reference,budgets):
    ref=json.loads(Path(reference).read_text());assert ref['passed']
    cal=json.loads(Path(budgets).read_text());parsed=[parse(p,True) for p in paths]
    assert len(parsed)==3
    replay=len({d for _,d in parsed})==1
    first=parsed[0][0];checks=[];groups={};summaries=[];failures=[]
    for name in NAMES:
        truth=ref['references'][name];nt=len(truth)
        target=np.array([[r[k] for k in ('mean','second','positive')] for r in truth])
        denom=np.array([[max(1,r['second']-r['mean']**2),max(1,r['second']**2),1.] for r in truth])
        for mode in modes(name,True):
            runs=[[first[name,mode,s,t] for t in range(nt)] for s in range(90001,90129)]
            assert all(r['n']==(cal['budgets'][name] if mode=='big' else 64) for run in runs for r in run)
            bad=[r for run in runs for r in run if r['status']!='ok'];failures+=bad
            times=[float(np.median([rows[name,mode,s,nt-1]['nanos'] for s in range(90001,90129)]))/1e6 for rows,_ in parsed]
            summary=dict(name=name,mode=mode,completed=sum(all(r['status']=='ok' for r in run) for run in runs),medianMs=times,
                         counters={k:float(np.mean([run[-1][k] for run in runs])) for k in INTS if k not in ('n','seed','t','nanos')})
            if not bad:
                values=np.array([[[r[k] for k in ('mean','second','positive')] for r in run] for run in runs])
                components=((values-target)**2/denom).mean(axis=1)
                loss=components.sum(axis=1);eligible=(target[:,2]>=.1)&(target[:,2]<=.9)
                depleted=np.any(((values[:,:,2]<.01)|(values[:,:,2]>.99))&eligible,axis=1)
                groups[name,mode]=dict(loss=loss,depleted=depleted,times=times)
                summary.update(meanLoss=float(loss.mean()),rootLoss=float(np.sqrt(loss.mean())),
                               componentLosses=components.mean(axis=0).tolist(),depleted=int(depleted.sum()))
                for t,tr in enumerate(truth):
                    z=np.exp([run[t]['logEvidence'] for run in runs]);tz=math.exp(tr['logEvidence'])
                    for j,k in enumerate(('evidence','mean','second','positive')):
                        sample=z if j==0 else z*values[:,t,j-1]
                        expected=tz if j==0 else tz*tr[k]
                        se=float(sample.std(ddof=1)/math.sqrt(128));error=abs(float(sample.mean())-expected)
                        threshold=6*se+1e-4*max(1,abs(expected))
                        checks.append(dict(name=name,mode=mode,t=t,metric=k,error=error,threshold=threshold,passed=error<=threshold))
            summaries.append(summary)
    utility=[]
    for mode in ('cubature','ekf','aux'):
        gates=[]
        for name in ('mono','sharp'):
            if (name,mode) not in groups or (name,'big') not in groups:continue
            a=groups[name,mode];b=groups[name,'big'];delta=a['loss']-b['loss']
            ratio=math.sqrt(float(a['loss'].mean()/b['loss'].mean()))
            upper=float(delta.mean()+2.62*delta.std(ddof=1)/math.sqrt(128))
            cost=all(x<=1.25*y for x,y in zip(a['times'],b['times']))
            cap=next(d['capPreventedMatch'] for d in cal['details'] if d['name']==name)
            gates.append(dict(name=name,rootLossRatio=ratio,pairedDifferenceUpper=upper,costPassed=cost,
                              capPreventedMatch=cap,passed=ratio<=.9 and upper<0 and cost and not cap))
        weak=False;depletion=[]
        if ('weak',mode) in groups and ('weak','bootstrap') in groups:
            weak=bool(groups['weak',mode]['loss'].mean()<=1.25*groups['weak','bootstrap']['loss'].mean())
        for name in ('symmetric','asymmetric'):
            if (name,mode) in groups and (name,'bootstrap') in groups:
                difference=int(groups[name,mode]['depleted'].sum()-groups[name,'bootstrap']['depleted'].sum())
                depletion.append(dict(name=name,excessRuns=difference,passed=difference<=3))
        utility.append(dict(mode=mode,primary=gates,weakPassed=weak,depletion=depletion,
                            passed=len(gates)==2 and all(g['passed'] for g in gates) and weak and len(depletion)==2 and all(d['passed'] for d in depletion)))
    correctness=replay and not failures and all(c['passed'] for c in checks)
    return dict(protocol='nonlinear-v1',correctnessPassed=correctness,replay=replay,scientificDigests=[d for _,d in parsed],
                rowsPerJvm=len(first),checks=checks,failures=failures,summaries=summaries,utility=utility,
                promotionEligible=correctness and any(u['passed'] for u in utility))

def plot(result,out):
    # Dependency-free SVG: real measured values, log axes, all complete configurations.
    vals=[s for s in result['summaries'] if 'rootLoss' in s]
    lo=min(math.log10(s['rootLoss']) for s in vals);hi=max(math.log10(s['rootLoss']) for s in vals)
    xmax=max(math.log10(s['medianMs'][0]) for s in vals);xmin=min(math.log10(s['medianMs'][0]) for s in vals)
    lines=['<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="660" viewBox="0 0 1000 660">',
           '<rect width="1000" height="660" fill="white"/><g font-family="sans-serif" font-size="11" fill="black">',
           '<text x="30" y="25">Nonlinear proposal v1: measured median time vs root primary loss (JVM 1)</text>',
           '<text x="30" y="45">Both axes log10; lower-left is better. Synthetic controls only; no general speedup claim.</text>']
    colors=['#105aa0','#b14132','#2b7744','#874996','#916600','#333333','#bb3388','#008888']
    for s in vals:
        x=70+800*(math.log10(s['medianMs'][0])-xmin)/max(.001,xmax-xmin)
        y=540-440*(math.log10(s['rootLoss'])-lo)/max(.001,hi-lo)
        color=colors[NAMES.index(s['name'])]
        lines.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="4" fill="{color}"/><text x="{x+5:.2f}" y="{y:.2f}">{s["name"]}/{s["mode"]}</text>')
    lines += [f'<text x="70" y="590">log10 milliseconds: {xmin:.2f} to {xmax:.2f}</text>',
              f'<text x="70" y="610">log10 root loss: {lo:.2f} to {hi:.2f}; labels show exact fixture/method.</text>','</g></svg>']
    out.write('comparison.svg','\n'.join(lines))

def main():
    p=argparse.ArgumentParser();p.add_argument('action',choices=['calibrate','analyze']);p.add_argument('--logs',nargs='+',required=True)
    p.add_argument('--reference');p.add_argument('--budgets');p.add_argument('--output',required=True);p.add_argument('--acl-script');a=p.parse_args()
    out=Output(a.output,a.acl_script)
    if a.action=='calibrate':
        assert len(a.logs)==1;r=calibrate(a.logs[0]);out.json('budgets.json',r)
        out.write('budgets.tsv','\n'.join(n+'\t'+str(r['budgets'][n]) for n in NAMES)+'\n');print(json.dumps(r));return
    r=analyze(a.logs,a.reference,a.budgets);out.json('results.json',r);plot(r,out)
    print(json.dumps({k:r[k] for k in ('correctnessPassed','replay','rowsPerJvm','utility','promotionEligible')}))
    print('Checks:',len(r['checks']),'failed:',sum(not c['passed'] for c in r['checks']),'failure rows:',len(r['failures']))
    sys.exit(0 if r['correctnessPassed'] else 1)

if __name__=='__main__':main()
