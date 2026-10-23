"""INF-18 independent true-model quadrature. No candidate builder/fitting formulas."""
import argparse,json,math,sys
import numpy as np
from continuous_filter_reference import Output
from nonlinear_proposal_reference import normal,kalman

NAMES=('affine','weak','mono','tail','symmetric','asymmetric','three','merging','flat','omitted','narrow')
METRICS=('mean','second','positive','tail','r0','r1','r2')
SQUARE=('symmetric','asymmetric','merging','omitted','narrow')

def spec(name):
    assert name in NAMES
    ys=([6.] if name=='tail' else [0,.2,-.2,0,.1,-.1] if name=='three' else
        [.04,.01,0,-.02] if name=='merging' else [0,.1,-.1,0] if name=='flat' else
        [1,1.2,.8,1.4,1,.7] if name in SQUARE else [.4,1.2,-.3,.7,1.5,.2])
    return dict(name=name,observations=ys,mean=.8 if name=='asymmetric' else 0.,
                initialVariance=4. if name=='narrow' else 1.21,
                transitionCoefficient=.95 if name=='narrow' else .7,
                transitionSd=.15 if name=='narrow' else .8,
                noise=4. if name=='weak' else .2 if name=='asymmetric' else .35 if name=='three' else .3 if name=='narrow' else .5)

def observation(name,x):
    return x if name=='affine' else np.zeros_like(x) if name=='flat' else x**3-3*x if name=='three' else .25*x*x if name in SQUARE else x+.25*x**3

def grid(limit,dx):
    n=round(2*limit/dx);assert n%4==0
    x=np.linspace(-limit,limit,n+1)
    def interval(a,b):
        i=round((a+limit)/dx);j=round((b+limit)/dx)
        assert j>i and (j-i)%2==0 and abs(x[i]-a)<1e-10 and abs(x[j]-b)<1e-10
        w=np.zeros(n+1);v=np.ones(j-i+1);v[1:-1:2]=4;v[2:-1:2]=2;w[i:j+1]=v*dx/3
        return w
    return x,interval

def oracle(name,limit,dx):
    s=spec(name);x,interval=grid(limit,dx);w=interval(-limit,limit)
    p=normal(x,s['mean'],math.sqrt(s['initialVariance']));h=observation(name,x)
    transition=normal(x[:,None],s['transitionCoefficient']*x[None,:],s['transitionSd'])
    cuts=(-limit,-1.,1.,limit) if name=='three' else (-limit,0.,limit)
    rw=[interval(a,b) for a,b in zip(cuts,cuts[1:])]
    positive=interval(0,limit);tail=interval(2.5,limit);logz=0.;out=[]
    for t,y in enumerate(s['observations']):
        if t:p=transition@(p*w)
        p=p*normal(y,h,s['noise']);z=float(w@p);assert z>0 and math.isfinite(z)
        logz+=math.log(z);p/=z
        regions=[float(v@p) for v in rw]+([0.] if name!='three' else [])
        out.append(dict(logEvidence=logz,mean=float(w@(p*x)),second=float(w@(p*x*x)),
                        positive=float(positive@p),tail=float(tail@p),**dict(zip(('r0','r1','r2'),regions))))
    return out

def prepare():
    references={};checks=[];grids_used={}
    for name in NAMES:
        grids=[(10,.05),(10,.025),(10,.0125),(12,.0125)]
        if name=='affine':grids=[(10,.025),(10,.0125),(10,.00625),(12,.00625)]
        rows=[oracle(name,*g) for g in grids];grids_used[name]=grids
        for i,j in ((2,1),(3,2)):
            for t,(a,b) in enumerate(zip(rows[i],rows[j])):
                for k in a:
                    tol=1e-5 if k in ('logEvidence','mean','second') else 2e-5
                    error=abs(a[k]-b[k]);checks.append(dict(name=name,t=t,metric=k,grids=[i,j],error=error,tolerance=tol,passed=error<=tol))
        if name=='affine':
            for t,(a,b) in enumerate(zip(rows[2],kalman())):
                v=b['second']-b['mean']**2;b=dict(b,r0=1-b['positive'],r1=b['positive'],r2=0.,tail=.5*math.erfc((2.5-b['mean'])/math.sqrt(2*v)))
                for k in a:checks.append(dict(name=name,t=t,metric=k,control='analytic',error=abs(a[k]-b[k]),passed=abs(a[k]-b[k])<=1e-9))
        for t,a in enumerate(rows[2]):
            checks.append(dict(name=name,t=t,control='partition',passed=abs(a['r0']+a['r1']+a['r2']-1)<1e-10))
            if name=='symmetric':checks.append(dict(name=name,t=t,control='symmetry',passed=abs(a['positive']-.5)<1e-9))
        references[name]=rows[2]
    return dict(protocol='multicomponent-v1',passed=all(c['passed'] for c in checks),checks=checks,
                references=references,fixtures=[spec(n) for n in NAMES],grids=grids_used,python=sys.version,numpy=np.__version__)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--acl-script');a=p.parse_args()
    r=prepare();Output(a.output,a.acl_script).json('reference.json',r)
    print(json.dumps(dict(passed=r['passed'],checks=len(r['checks']),failures=[c for c in r['checks'] if not c['passed']])))
    sys.exit(0 if r['passed'] else 1)
