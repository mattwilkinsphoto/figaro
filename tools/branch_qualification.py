"""INF-19 independent LSODA posterior and natural-spline qualification.

No Figaro guide, particle samples, or JVM solver is used as posterior truth.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import time
import numpy as np
import scipy
from scipy.integrate import solve_ivp, simpson
from scipy.interpolate import CubicSpline
from scipy.optimize import brentq
from scipy.special import ndtr

OBS = [-.5, 0., .5, 0., -.5, .5]
GRIDS = [(8,.01),(8,.005),(8,.0025),(10,.0025)]
GRANT = os.environ.get('FIGARO_ACCESS_HELPER')


def grant(path):
    if os.name == 'nt':
        if not GRANT:
            raise RuntimeError('Set FIGARO_ACCESS_HELPER to the workspace access helper on Windows')
        subprocess.run(['pwsh','-NoProfile','-File',GRANT,'-Paths',str(path.resolve())],check=True,stdout=subprocess.DEVNULL)


def save(path, obj):
    with path.open('x',encoding='utf-8') as f:
        grant(path)
        json.dump(obj,f,indent=2,allow_nan=False)


def arrays(path, **items):
    with path.open('xb') as f:
        grant(path)
        np.savez_compressed(f,**items)


def solve(x, horizon, tol=1e-11, tangent=False, linear=False):
    def equation(t,z):
        q,v=z[:2]
        base=[v,-(q if linear else math.sin(q))-.15*v]
        return base+([z[3],-(1 if linear else math.cos(q))*z[2]-.15*z[3]] if tangent else [])
    result=solve_ivp(equation,(0,horizon),[.2,x,0,1] if tangent else [.2,x],
                     method='LSODA',rtol=tol,atol=tol,max_step=.5,t_eval=[horizon])
    if not result.success or result.nfev>100000 or not np.all(np.isfinite(result.y)):
        raise ArithmeticError(str(result.message))
    return result.y[2 if tangent else 0,-1]


def weights(n,step):
    assert n%2==1
    w=np.full(n,2.);w[1::2]=4;w[[0,-1]]=1
    return w*step/3


def interval(x,p,a,b):
    inside=(x>a)&(x<b)
    xx=np.concatenate(([a],x[inside],[b]))
    pp=np.concatenate(([np.interp(a,x,p)],p[inside],[np.interp(b,x,p)]))
    return float(simpson(pp,x=xx))


def density(x,mean,var):
    return np.exp(-.5*(x-mean)**2/var)/math.sqrt(2*math.pi*var)


def recursion(x, response, bounds):
    """Both horizons/alternatives at once, without transition-row renormalization."""
    step=x[1]-x[0];w=weights(len(x),step)
    p=np.repeat(density(x,0,1.21)[:,None],response.shape[1],axis=1)
    records=[];hist=[];logs=np.zeros(response.shape[1]);v=1.21;exits=0.
    for t,y in enumerate(OBS):
        if t:
            pred=np.empty_like(p)
            wp=w[:,None]*p
            for start in range(0,len(x),128):
                kernel=density(x[start:start+128,None],.7*x[None,:],.64)
                pred[start:start+128]=kernel@wp
            p=pred;v=.49*v+.64
        exits+=2*ndtr(-max(abs(x))/math.sqrt(v))
        p=p*density(y,response,.04)
        z=w@p
        assert np.all(z>0) and np.all(np.isfinite(z))
        logs+=np.log(z);p/=z
        hist.append(p.copy())
        for j in range(p.shape[1]):
            cut=[x[0]]+list(bounds[j])+[x[-1]]
            rec=dict(column=j,step=t,mean=float(w@(p[:,j]*x)),second=float(w@(p[:,j]*x*x)),
                positive=interval(x,p[:,j],0,x[-1]),tail=interval(x,p[:,j],x[0],-3)+interval(x,p[:,j],3,x[-1]),
                branches=[interval(x,p[:,j],a,b) for a,b in zip(cut,cut[1:])],
                logEvidence=float(logs[j]),pathExitScreen=float(exits*(1/math.sqrt(2*math.pi*.04))**(t+1)/math.exp(logs[j])))
            records.append(rec)
    return records,np.array(hist)


def differences(a,b):
    assert len(a)==len(b)
    metrics=[]
    for u,v in zip(a,b):
        assert (u['column'],u['step'])==(v['column'],v['step'])
        for k in ('mean','second','positive','tail','logEvidence'):
            metrics.append(abs(u[k]-v[k])/(max(1,abs(v[k])) if k in ('mean','second') else 1))
        metrics.extend(abs(i-j) for i,j in zip(u['branches'],v['branches']))
    return max(metrics)


def reference(out, previous):
    started=time.time();controls=[]
    old=[json.loads(line) for line in Path(previous).read_text().splitlines()]
    old=[r for r in old if r['type']=='point']
    assert len(old)==642
    for r in old:
        x,t=r['x'],r['T'];h=solve(x,t);tight=solve(x,t,1e-12)
        omega=math.sqrt(1-.075**2)
        analytic=math.exp(-.075*t)*(.2*math.cos(omega*t)+(x+.015)/omega*math.sin(omega*t))
        controls.append(dict(x=x,T=t,javaError=abs(h-r['h']),refinementError=abs(h-tight),
                             linearError=abs(solve(x,t,linear=True)-analytic)))
    save(out/'solver-controls.json',controls)
    assert max(max(c[k] for k in ('javaError','refinementError','linearError')) for c in controls)<=1e-6
    roots=[brentq(lambda x:solve(x,6,tangent=True),a,b,xtol=1e-12) for a,b in [(-2.05,-2),(2,2.05)]]
    cache={}
    def response(xs,t):
        vals=[]
        for x in xs:
            key=(round(float(x),12),t)
            if key not in cache:cache[key]=solve(float(x),t)
            vals.append(cache[key])
        return np.array(vals)
    levels=[];stored={}
    for radius,step in GRIDS+[(8,.00125),(10,.00125)]:
        if step==.00125 and len(levels)==4 and levels[2]['comparison']<=1e-5 and levels[3]['comparison']<=1e-5:break
        x=np.linspace(-radius,radius,round(2*radius/step)+1)
        h=np.stack([response(x,t) for t in (2,6)],axis=1)
        rec,hist=recursion(x,h,[[],roots])
        prev=stored.get((radius,step*2)) if radius==8 else stored.get((8,step))
        change=differences(prev[0],rec) if prev else None
        level=dict(radius=radius,spacing=step,comparison=change,records=rec)
        levels.append(level);stored[(radius,step)]=(rec,x,h,hist)
        name=f'reference-{radius}-{step}'
        save(out/(name+'.json'),level);arrays(out/(name+'.npz'),x=x,h=h,p=hist)
        print(json.dumps(dict(level=name,comparison=change,elapsed=time.time()-started)),flush=True)
    final=levels[-1];step=final['spacing'];core=stored[(8,step)];wide=stored[(10,step)]
    convergence=(levels[-1]['comparison']<=1e-5 and next(l for l in levels if l['radius']==8 and l['spacing']==step)['comparison']<=1e-5)
    x=wide[1]
    linear_records,_=recursion(x,x[:,None],[[]]);m=0.;v=1.21;logz=0.;kalman=[]
    for t,y in enumerate(OBS):
        if t:m=.7*m;v=.49*v+.64
        logz+=math.log(float(density(y,m,v+.04)));gain=v/(v+.04);m+=gain*(y-m);v*=1-gain
        r=linear_records[t]
        kalman.append(max(abs(r['mean']-m),abs(r['second']-(m*m+v)),abs(r['logEvidence']-logz)))
    anchor=[]
    for t in (2,6):
        for y in (-.5,0.,.5):
            calls=0;start=time.perf_counter()
            def residual(x):
                nonlocal calls
                calls+=1
                return solve(x,t)-y
            xs=np.linspace(-8,8,321);vs=[residual(x) for x in xs]
            rr=[brentq(residual,a,b,xtol=1e-11) for a,b,u,vv in zip(xs,xs[1:],vs,vs[1:]) if u*vv<0]
            elapsed=time.perf_counter()-start
            anchor.append(dict(T=t,y=y,roots=rr,calls=calls,seconds=elapsed,withinCap=len(rr)<=4))
    # Independent point sets are generated before any table is read.
    queries={}
    for t in (2,6):
        for group,n,offset in [(0,1024,.37),(1,4096,.73)]:
            xx=-8+(np.arange(n)+offset)*16/n
            queries[f'{t}-{group}']=dict(x=xx.tolist(),h=response(xx,t).tolist())
    save(out/'independent-queries.json',queries)
    branch=[r for r in wide[0] if r['column']==1]
    qualified=[r['step'] for r in branch if sum(p>=.05 for p in r['branches'])>=2]
    result=dict(scipy=scipy.__version__,numpy=np.__version__,turningPoints=roots,
        converged=convergence,kalmanMaxError=max(kalman),solverChecks=3*len(controls),
        pathExitMax=max(r['pathExitScreen'] for r in wide[0]),qualifiedSteps=qualified,
        branchRelevant=len(qualified)>=2,records=wide[0],anchors=anchor,
        finalRadius=10,finalSpacing=step,seconds=time.time()-started)
    save(out/'reference-summary.json',result)
    assert convergence and max(kalman)<=1e-8 and result['pathExitMax']<=1e-6, 'Independent reference gates'
    print(json.dumps(result),flush=True)


def compare(out,logs):
    allruns=[[json.loads(s) for s in Path(log).read_text().splitlines()] for log in logs]
    assert len(allruns)==3
    for rows in allruns:
        assert rows[-1]==dict(type='complete')
        for kind,n in [('node',30008),('query',40960),('timing',140),('table',8),('fallback',8)]:
            assert sum(r['type']==kind for r in rows)==n,(kind,n)
    science=lambda rows:[r for r in rows if r['type'] in ('node','query','fallback')]
    assert science(allruns[0])==science(allruns[1])==science(allruns[2])
    refs=json.loads((out/'reference-summary.json').read_text());qrefs=json.loads((out/'independent-queries.json').read_text())
    data=np.load(out/f'reference-10-{refs["finalSpacing"]}.npz');x=data['x'];truth=data['h'];truthp=data['p']
    results=[];selected={};splines={}
    for t in (2,6):
        for method in range(1,5):
            rows=allruns[0];nodes=[r for r in rows if r['type']=='node' and r['T']==t and r['method']==method]
            knots=np.array([r['x'] for r in nodes]);vals=np.array([r['h'] for r in nodes])
            spline=CubicSpline(knots,vals,bc_type='natural',extrapolate=False);splines[(t,method)]=spline
            errors=[];agreement=[];coincidences=[];knotset=set(knots)
            for group in (0,1):
                query=[r for r in rows if r['type']=='query' and r['T']==t and r['method']==method and r['set']==group]
                assert [r['index'] for r in query]==list(range(len(query)))
                xx=np.array([r['x'] for r in query]);yy=np.array([r['h'] for r in query]);ref=qrefs[f'{t}-{group}']
                assert np.max(abs(xx-np.array(ref['x'])))<1e-12
                agreement.append(float(np.max(abs(yy-spline(xx)))))
                hh=np.array(ref['h'])
                errors.append(max(float(np.max(abs(((y-hh)**2-(y-yy)**2)/.08))) for y in (-.5,0,.5)))
                coincidences.append(sum(float(v) in knotset for v in xx))
            assert max(agreement)<=1e-9
            record=dict(T=t,method=method,nodes=len(knots),developmentLogError=errors[0],heldOutLogError=errors[1],
                        reconstructionErrors=agreement,trainingCoincidences=coincidences,developmentPass=errors[0]<=.001)
            results.append(record)
        eligible=[r['method'] for r in results if r['T']==t and r['developmentPass']]
        selected[t]=min(eligible) if eligible else None
    # Compare all tables, not just those selected. Out-of-domain uses the true direct reference
    # here; actual JVM fallback identity is checked separately for every table and run.
    for r in results:
        t=r['T'];column=0 if t==2 else 1
        hh=truth[:,column].copy();inside=abs(x)<=8;hh[inside]=splines[(t,r['method'])](x[inside])
        rr,pp=recursion(x,hh[:,None],[[] if t==2 else refs['turningPoints']])
        target=[dict(v,column=0) for v in refs['records'] if v['column']==column]
        r['posteriorMaxError']=differences(rr,target)
        w=weights(len(x),x[1]-x[0]);r['maxTV']=max(float(.5*w@abs(pp[i,:,0]-truthp[i,:,column])) for i in range(6))
        r['accepted']=bool(r['developmentPass'] and r['heldOutLogError']<=.001 and r['posteriorMaxError']<=1e-4 and r['maxTV']<=1e-4)
        timing=[]
        for rows in allruns:
            cold=next(v['constructionNs'] for v in rows if v['type']=='table' and v['T']==t and v['method']==r['method'])/1e9
            for prior in (False,True):
                def cost(method):return float(np.median([v['ns']/v['calls']/1e9 for v in rows if v['type']=='timing' and v['T']==t and v['prior']==prior and v['method']==method]))
                direct,hot=cost(0),cost(r['method']);saving=direct-hot
                timing.append(dict(prior=prior,coldSeconds=cold,directSecondsPerCall=direct,hotSecondsPerCall=hot,
                    breakEvenQueries=math.ceil(cold/saving) if saving>0 else None,
                    costs=[dict(queries=n,direct=n*direct,lookup=cold+n*hot) for n in (384,3840,38400)]))
        r['timing']=timing
    final=dict(selected=selected,results=results,branchRelevant=refs['branchRelevant'],
               selectedAccepted={t:any(r['T']==t and r['method']==selected[t] and r['accepted'] for r in results) for t in (2,6)})
    save(out/'comparison.json',final)
    print(json.dumps(final),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['reference','compare']);parser.add_argument('out',type=Path);parser.add_argument('inputs',nargs='+')
    a=parser.parse_args()
    if a.mode=='reference':reference(a.out,a.inputs[0])
    else:compare(a.out,a.inputs)
