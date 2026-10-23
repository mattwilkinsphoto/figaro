"""INF-17 independent density-grid oracle; no candidate proposal formulas.

All observations are specified synthetic controls in frozen protocol v1.
"""
import argparse
import json
import math
import sys
import numpy as np
from continuous_filter_reference import Output

NAMES = ('affine','mono','sharp','weak','symmetric','asymmetric','tail','curved')
YS = [.4,1.2,-.3,.7,1.5,.2]

def spec(name):
    assert name in NAMES
    square = name in ('symmetric','asymmetric')
    return dict(name=name, mean=.8 if name=='asymmetric' else 0., sd=1.1,
                noise=.15 if name=='sharp' else 4. if name=='weak' else .5,
                observations=[1.] if name=='curved' else [6.] if name=='tail' else
                [1.,1.2,.8,1.4,1.,.7] if square else YS,
                square=square)

def normal(x,m,s):
    return np.exp(-.5*((x-m)/s)**2)/(s*math.sqrt(2*math.pi))

def rule(limit,dx):
    n=round(2*limit/dx)
    assert n%4==0
    x=np.linspace(-limit,limit,n+1)
    w=np.ones(n+1);w[1:-1:2]=4;w[2:-1:2]=2;w*=dx/3
    positive=np.zeros(n+1);half=np.ones(n//2+1)
    half[1:-1:2]=4;half[2:-1:2]=2
    positive[n//2:]=half*dx/3
    return x,w,positive

def oracle(name,limit,dx):
    s=spec(name);x,w,pw=rule(limit,dx);out=[];logz=0.
    if name=='curved':
        a,b=np.meshgrid(x,x,indexing='ij')
        # Direct correlated bivariate Gaussian, no sigma-point or Kalman update.
        density=np.exp(-(a*a-2*.3*a*b+b*b)/(2*.91))/(2*math.pi*math.sqrt(.91))
        v=density*normal(1.,a+.3*b*b,.5)
        z=float(w@v@w);v/=z
        return [dict(logEvidence=math.log(z),mean=float(w@(v*a)@w),
                     second=float(w@(v*a*a)@w),positive=float(pw@v@w))]
    density=normal(x,s['mean'],s['sd'])
    transition=normal(x[:,None],.7*x[None,:],.8)
    h=x if name=='affine' else .25*x*x if s['square'] else x+.25*x**3
    for t,y in enumerate(s['observations']):
        if t:density=transition@(density*w)
        density=density*normal(y,h,s['noise']);z=float(w@density)
        assert math.isfinite(z) and z>0
        logz+=math.log(z);density/=z
        out.append(dict(logEvidence=logz,mean=float(w@(density*x)),
                        second=float(w@(density*x*x)),positive=float(pw@density)))
    return out

def kalman():
    m=0.;v=1.21;l=0.;out=[]
    for t,y in enumerate(YS):
        if t:m*=.7;v=.49*v+.64
        s=v+.25;l+=math.log(float(normal(y,m,math.sqrt(s))))
        m+=v/s*(y-m);v=v*.25/s
        out.append(dict(logEvidence=l,mean=m,second=v+m*m,
                        positive=.5*(1+math.erf(m/math.sqrt(2*v)))))
    return out

def prepare():
    references={};checks=[]
    for name in NAMES:
        grids=[(8,.1),(8,.05),(8,.025),(10,.025)] if name=='curved' else [(10,.05),(10,.025),(10,.0125),(12,.0125)]
        if name=='affine':grids=[(10,.025),(10,.0125),(10,.00625),(12,.00625)]
        rows=[oracle(name,*g) for g in grids]
        for i,j in ((2,1),(3,2)):
            for t,(a,b) in enumerate(zip(rows[i],rows[j])):
                for k in a:
                    tolerance=2e-5 if k=='positive' else 1e-5
                    error=abs(a[k]-b[k]);checks.append(dict(name=name,t=t,metric=k,grids=[i,j],error=error,tolerance=tolerance,passed=error<=tolerance))
        if name=='affine':
            for t,(a,b) in enumerate(zip(rows[2],kalman())):
                for k in a:checks.append(dict(name=name,t=t,metric=k,control='kalman',error=abs(a[k]-b[k]),passed=abs(a[k]-b[k])<=1e-9))
        if name=='symmetric':
            for t,a in enumerate(rows[2]):checks.append(dict(name=name,t=t,control='symmetry',passed=abs(a['positive']-.5)<=1e-9))
        references[name]=rows[2]
    return dict(protocol='nonlinear-v1',passed=all(c['passed'] for c in checks),checks=checks,
                references=references,fixtures=[spec(n) for n in NAMES],python=sys.version,numpy=np.__version__)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--acl-script');a=p.parse_args()
    result=prepare();Output(a.output,a.acl_script).json('reference.json',result)
    print(json.dumps(dict(passed=result['passed'],checks=len(result['checks']),failures=[c for c in result['checks'] if not c['passed']])))
    sys.exit(0 if result['passed'] else 1)
