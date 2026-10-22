"""INF-14: independent references and predeclared analysis; never an inference backend."""
import argparse
from fractions import Fraction as F
import hashlib
import itertools
import json
import math
from pathlib import Path
import sys
import time
import numpy as np
from continuous_filter_reference import Output, normal, simpson_uniform

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def mh_matrix(target, q):
    total=sum(target); pi=[x/total for x in target]
    p=[[F(0),F(0)],[F(0),F(0)]]
    for i in range(2):
        j=1-i
        p[i][j]=q[i][j]*min(F(1),pi[j]*q[j][i]/(pi[i]*q[i][j])) if pi[i] else F(0)
        p[i][i]=1-p[i][j]
    assert all(sum(r)==1 for r in p)
    assert pi[0]*p[0][1]==pi[1]*p[1][0]
    return p

def evolve(weights,p,k):
    for _ in range(k): weights=[sum(weights[i]*p[i][j] for i in range(2)) for j in range(2)]
    return weights

def finite_control():
    prior=[F(1,3),F(2,3)];g0=[F(2,5),F(4,5)];g1=[F(9,10),F(1,10)]
    trans=[[F(3,4),F(1,4)],[F(1,5),F(4,5)]];q=[[F(2,3),F(1,3)],[F(3,4),F(1,4)]]
    gamma0=[prior[i]*g0[i] for i in range(2)]
    truth=[sum(gamma0[i]*trans[i][j] for i in range(2))*g1[j] for j in range(2)]
    controls=[]
    for k in (0,1,4,8):
        result=[F(0),F(0)]
        for initial in itertools.product(range(2),repeat=2):
            prob=math.prod(prior[i] for i in initial);z0=sum(g0[i] for i in initial)/2
            weights=[sum(g0[i] for i in initial if i==j)/(2*z0) for j in range(2)]
            moved=evolve(weights,mh_matrix(gamma0,q),k)
            for old in itertools.product(range(2),repeat=2):
                op=prob*math.prod(moved[i] for i in old)
                predictive=[sum(trans[i][j] for i in old)/2 for j in range(2)]
                gamma=[predictive[j]*g1[j] for j in range(2)]
                kernel=mh_matrix(gamma,q)
                for draw in itertools.product(range(2),repeat=2):
                    dp=math.prod(predictive[i] for i in draw);z1=sum(g1[i] for i in draw)/2
                    w=[sum(g1[i] for i in draw if i==j)/(2*z1) for j in range(2)]
                    post=evolve(w,kernel,k)
                    for j in range(2): result[j]+=op*dp*z0*z1*post[j]
        assert result==truth,(k,result,truth)
        controls.append(dict(sweeps=k,unnormalized=[str(x) for x in result],passed=True))
    zero=mh_matrix([F(1),F(0)],q)
    assert zero[0][1]==0
    return dict(two_step=controls,zero_support=True,matrix=[[str(v) for v in r] for r in mh_matrix(gamma0,q)])

def late_grid(limit,dx):
    x=np.linspace(-limit,limit,round(2*limit/dx)+1);dx=x[1]-x[0]
    quadrature=np.full(len(x),dx);quadrature[[0,-1]]*=.5
    prediction=(normal(x,-4,.5)+normal(x,4,.5))/2
    rows=[];logz=0.;mass=None
    for t in range(40):
        if t:
            prediction=np.empty_like(x)
            for lo in range(0,len(x),128): prediction[lo:lo+128]=normal(x[lo:lo+128,None],x[None,:],.3)@mass
        pm=float(prediction@quadrature)
        weighted=prediction*normal(4.,x*x/4 if t<32 else x,.5)*quadrature
        z=float(weighted.sum());mass=weighted/z;logz+=math.log(z)
        pos=simpson_uniform((mass/quadrature)[len(x)//2:],dx)
        rows.append(dict(t=t,logZ=logz,mean=float(mass@x),second=float(mass@(x*x)),positive=pos,predictive_mass=pm))
    return rows

def prepare(out,base):
    base=Path(base);ref=json.loads((base/'reference.json').read_text());assert ref['passed']
    out.write('fixtures.tsv',(base/'fixtures.tsv').read_text())
    refs=dict(ref['references']);grids=[late_grid(12,.025),late_grid(12,.0125),late_grid(16,.0125)]
    metrics=('logZ','mean','second','positive')
    changes=[np.max(np.abs(np.array([[r[k] for k in metrics] for r in grids[j]])-
                                   np.array([[r[k] for k in metrics] for r in grids[1]])),axis=0).tolist() for j in (0,2)]
    passed=all(np.all(np.array(c)<=np.array([1e-5,1e-5,1e-5,2e-4])) for c in changes)
    mass_error=max(abs(r['predictive_mass']-1) for rows in grids for r in rows)
    passed=passed and mass_error<=1e-8
    refs['late']=grids[-1];rows=[];m=0.;v=4.;logz=0.
    for t in range(20):
        if t:m=.8*m;v=.64*v+.3
        y=math.sin(t);logz+=-.5*(math.log(2*math.pi*(v+.5))+(y-m)**2/(v+.5))
        gain=v/(v+.5);m+=gain*(y-m);v*=1-gain
        rows.append(dict(t=t,logZ=logz,mean=m,second=v+m*m,positive=.5*(1+math.erf(m/math.sqrt(2*v)))))
    refs['gaussian']=rows
    result=dict(passed=bool(passed),references=refs,late_grid_changes=changes,late_mass_error=mass_error,
                finite=finite_control(),original_reference_sha256=sha(base/'reference.json'),
                original_fixture_sha256=sha(base/'fixtures.tsv'),python=sys.version,numpy=np.__version__)
    out.json('reference.json',result)
    print(json.dumps({k:v for k,v in result.items() if k!='references'}),flush=True)
    assert passed,'Independent late reference convergence failed'

def diagnostics(out,log,reference):
    names='scenario mode seed t logZ mean second positive ess parents concentration expected attempts draws likelihoods densities lookups nanos'.split()
    lines=[l.split('DIVERSITY_DIAG ',1)[1] for l in Path(log).read_text(encoding='utf-8-sig').splitlines() if 'DIVERSITY_DIAG ' in l]
    rows=[dict(zip(names,l.split('\t'))) for l in lines]
    assert all(len(l.split('\t'))==len(names) for l in lines)
    keys={(r['scenario'],r['mode'],int(r['seed']),int(r['t'])) for r in rows}
    modes=('bootstrap256','bootstrap1024','auxiliary256','auxiliary1024','floor32','floor128')
    expected={(s,m,k,t) for s in ('growth','growth-sharp','persistent-modes') for m in modes for k in range(1,257) for t in range(20)}
    assert len(rows)==len(keys) and keys==expected,'Missing or duplicate diagnostics'
    refs=json.loads(Path(reference).read_text())['references'];summary=[]
    for s in refs:
        for m in modes:
            group=[r for r in rows if r['scenario']==s and r['mode']==m]
            final=[r for r in group if int(r['t'])==19]
            if not final:continue
            truth=refs[s][-1]['positive']
            summary.append(dict(scenario=s,mode=m,rmse=math.sqrt(np.mean([(float(r['positive'])-truth)**2 for r in final])),
              depleted=sum(not .01<=float(r['positive'])<=.99 for r in final) if s=='persistent-modes' else None,
              mean_ess=float(np.mean([float(r['ess']) for r in group])),
              mean_parents=float(np.mean([float(r['parents']) for r in group if int(r['t'])>0])),
              concentration=float(np.mean([float(r['concentration']) for r in group if int(r['t'])>0])),
              draws=float(np.mean([float(r['draws']) for r in final]))))
    out.write('diagnostics.tsv','\t'.join(names)+'\n'+'\n'.join(lines)+'\n')
    out.json('diagnostics.json',dict(runs=4608,rows=len(rows),summary=summary,input_sha256=sha(log)))
    print(json.dumps(summary),flush=True)

def main():
    p=argparse.ArgumentParser();p.add_argument('command',choices=['prepare','diagnostics'])
    p.add_argument('--output',required=True);p.add_argument('--base');p.add_argument('--log');p.add_argument('--reference');p.add_argument('--acl-script')
    a=p.parse_args();out=Output(a.output,a.acl_script)
    print('START_UTC',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),flush=True)
    if a.command=='prepare':prepare(out,a.base)
    else:diagnostics(out,a.log,a.reference)
    print('COMPLETE_UTC',time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),flush=True)
if __name__=='__main__':main()
