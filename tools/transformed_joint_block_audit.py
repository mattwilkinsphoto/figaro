"""TJB-01 independent graph/Jacobian and Cholesky audit; no production helper reuse."""
import argparse,math,platform
from pathlib import Path
import numpy as np
import scipy
from scipy.stats import gamma,beta,norm
from gvm_study_io import save,sha

def reference(g,p,ng,np_):
    def logp(x,y):
        return gamma.logpdf(x,a=2,scale=3)+beta.logpdf(y,a=2,b=3)+norm.logpdf(x*y,loc=1,scale=.3)
    q=math.log(ng*np_*(1-np_))-math.log(g*p*(1-p))
    return q,float(logp(ng,np_)-logp(g,p))

def audit_text(text):
    cases=[]; seen=set(); complete=0; matrices=[]
    for line in text.splitlines():
        if line.startswith('[info] '): line=line[7:]
        f=line.split('\t')
        if f[0]=='GRAPH':
            assert len(f)==7
            g,p,ng,np_,aq,am=map(float,f[1:])
            assert all(math.isfinite(x) for x in (g,p,ng,np_,aq,am))
            key=(g,p,ng,np_); assert key not in seen; seen.add(key)
            q,m=reference(g,p,ng,np_)
            assert abs(q-aq)<2e-11 and abs(m-am)<2e-10,(key,q,m,aq,am)
            rq,rm=reference(ng,np_,g,p)
            assert abs(q+rq)<2e-11 and abs(m+rm)<2e-10 and abs(q)>1e-6
            cases.append(dict(old=[g,p],new=[ng,np_],actual=[aq,am],reference=[q,m]))
        elif f[0]=='FACTOR':
            d=int(f[1]); values=np.array(list(map(float,f[2:])))
            assert values.size==2*d*d
            cov=values[:d*d].reshape(d,d); actual=values[d*d:].reshape(d,d)
            expected=np.linalg.cholesky(cov)
            assert np.allclose(actual,expected,rtol=2e-9,atol=2e-12)
            assert np.allclose(actual@actual.T,cov,rtol=2e-12,atol=2e-12)
            matrices.append(d)
        elif f[0]=='COMPLETE':
            assert f[1:]==['18']; complete+=1
    assert len(cases)==18 and complete==1
    starts={(round(math.log(c['old'][0]),10),round(math.log(c['old'][1]/(1-c['old'][1])),10)) for c in cases}
    assert starts=={(a,b) for a in (-3.,-.5,1.) for b in (-4.,0.,3.)}
    assert sorted(matrices)==[2,2,4,8]
    return dict(cases=cases,matrixDimensions=matrices,status='pass',
        versions=dict(python=platform.python_version(),numpy=np.__version__,scipy=scipy.__version__))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('log',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();result=audit_text(a.log.read_text(encoding='utf-8'))
    result['inputSha256']=sha(a.log);save(a.output,result)
    print('Independent audit passed: 18 graph-density cases and four matrix cases; no utility claim.')
