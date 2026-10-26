"""TJB-01 pre-sampling truth: analytic moments plus independent bounded quadrature."""
import argparse,math,platform
from pathlib import Path
import numpy as np
import scipy
from scipy.integrate import quad
from scipy.special import expit,roots_hermitenorm
from scipy.stats import norm
from gvm_study_io import save,sha

NAMES=['product2','product8','correlatedNormal','coupledSupport','curved','separated']
KINDS=[['gamma','beta'],['gamma','beta','normal','gamma','beta','normal','gamma','normal'],
       ['normal','normal'],['gamma','beta'],['gamma','beta'],['gamma','beta']]
MEANS=[[.2,-.3],[.2,-.3,.1,.2,-.3,.1,.2,.1],[0,0],[.2,-.3],[0,0],[0,0]]
COV=[np.diag([.25,.64]),np.diag([.25,.64,.49,.25,.64,.49,.25,.49]),
     np.array([[1,.97],[.97,1]]),np.array([[.25,.388],[.388,.64]]),
     np.diag([1,1.37]),np.array([[6.61,3.87],[3.87,2.74]])]

def normal_moment(k,m,s):return float(norm.moment(k,loc=m,scale=s))

def checked_quad(f,tol):
    value,error=quad(f,-12,12,epsabs=tol,epsrel=tol,limit=300)
    assert math.isfinite(value) and error<max(1e-10,10*tol*abs(value)),(value,error)
    return value

def marginal_moment(f,i,k,raw,tol):
    kind=KINDS[f][i] if raw else 'normal'
    if f==5:
        mixtures=[(.5,-(2.5 if i==0 else 1.5),math.sqrt(.36 if i==0 else .49)),
                  (.5,(2.5 if i==0 else 1.5),math.sqrt(.36 if i==0 else .49))]
    elif f==4 and i==1:
        if kind=='normal':
            nodes,weights=roots_hermitenorm(32)
            return float(sum(w*normal_moment(k,.8*(x*x-1),.3) for x,w in zip(nodes,weights))/math.sqrt(2*math.pi))
        # Bounded logistic integrand; discarded standard-normal tails < 8e-33.
        return checked_quad(lambda t:norm.pdf(t)*checked_quad(
            lambda e:norm.pdf(e)*expit(.8*(t*t-1)+.3*e)**k,tol),tol)
    else:mixtures=[(1.,MEANS[f][i],math.sqrt(COV[f][i,i]))]
    answer=0.
    for w,m,s in mixtures:
        if kind=='normal':value=normal_moment(k,m,s)
        elif kind=='gamma':value=math.exp(k*m+.5*k*k*s*s)
        else:value=checked_quad(lambda t:norm.pdf(t)*expit(m+s*t)**k,tol)
        answer+=w*value
    return answer

def contrast_moment(f,k):
    if f==4:
        nodes,weights=roots_hermitenorm(32)
        return float(sum(w*normal_moment(k,x-.8*(x*x-1),.3) for x,w in zip(nodes,weights))/math.sqrt(2*math.pi))
    if f==5:return .5*(normal_moment(k,-1,math.sqrt(.61))+normal_moment(k,1,math.sqrt(.61)))
    c=COV[f];return normal_moment(k,MEANS[f][0]-MEANS[f][1],math.sqrt(c[0,0]+c[1,1]-2*c[0,1]))

def references(tol):
    fixtures={}
    for f,name in enumerate(NAMES):
        queries={}; derived={}
        def add(label,moments):
            m,m2,_,m4=moments; variance=m2-m*m; square_variance=m4-m2*m2
            assert variance>0 and square_variance>0
            queries[label]={'mean':m,'variance':variance,'tolerance':.12*math.sqrt(variance)}
            queries[label+'sq']={'mean':m2,'variance':square_variance,'tolerance':.12*math.sqrt(square_variance)}
            derived['var:'+label]={'mean':variance,'tolerance':.15*variance,'source':label}
        for i in range(len(KINDS[f])):
            for raw,prefix in ((True,'x'),(False,'z')):
                add(prefix+str(i),[marginal_moment(f,i,k,raw,tol) for k in range(1,5)])
        add('contrast',[contrast_moment(f,k) for k in range(1,5)])
        for i in (0,1):
            if f==5 or (f==4 and i==0):p=.5
            elif f==4:p=checked_quad(lambda t:norm.pdf(t)*norm.cdf(.8*(t*t-1)/.3),tol)
            else:p=float(norm.cdf(MEANS[f][i]/math.sqrt(COV[f][i,i])))
            queries['positive'+str(i)]={'mean':p,'variance':p*(1-p),'tolerance':.04}
        fixtures[name]={'ordinary':f<4,'kinds':KINDS[f],'mean':MEANS[f],'covariance':COV[f].tolist(),
            'queries':queries,'derived':derived}
    return fixtures

def generate():
    first=references(2e-11); second=references(2e-13)
    worst=0.
    for name in NAMES:
        for group in ('queries','derived'):
            for label,value in second[name][group].items():
                for key in ('mean','tolerance'):
                    error=abs(first[name][group][label][key]-value[key])/max(1,abs(value[key]))
                    assert error<1e-9,(name,label,key,error)
                    worst=max(worst,error)
    return {'protocol':'TJB-01-v1','fixtures':second,'quadratureRelativeAgreement':worst,
        'quadratureTolerances':[2e-11,2e-13],'boundedTailMassUpperBound':float(4*norm.sf(12)),
        'developmentSeeds':list(range(7412202601,7412202609)),
        'confirmationSeeds':list(range(7412202701,7412202733)),
        'fixtureSeedOffset':10000,'chains':4,'warmUp':4000,'drawsPerChain':8000,
        'maxWallSecondsPerPhase':14400,'maxProcessBytes':12*1024**3,'maxOutputBytes':4*1024**3,
        'versions':{'python':platform.python_version(),'numpy':np.__version__,'scipy':scipy.__version__}}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('output',type=Path);a=p.parse_args()
    result=generate();result['sourceSha256']=sha(__file__)
    result['protocolSha256']=sha(Path(__file__).resolve().parents[1]/'docs/TRANSFORMED_JOINT_BLOCK_PROTOCOL.md')
    save(a.output,result);print('Qualified independent references for six fixtures:',result['quadratureRelativeAgreement'])
