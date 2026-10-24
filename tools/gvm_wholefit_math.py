"""GVM-07 independent whole-fit derivatives in fixed training coordinates.

Mean negative log likelihood; symmetric covariance uses the Frobenius metric.
This is a first-order diagnostic, not a global-optimum or identifiability test.
"""
import numpy as np
from scipy.special import i0e,i1e,logsumexp
import gvm_dimension_controls as c


def simplex(values,floor=1e-8):
    values=np.asarray(values,dtype=float);mass=1-len(values)*floor
    if mass<=0 or not np.all(np.isfinite(values)):raise ValueError('Invalid simplex')
    ordered=np.sort(values-floor)[::-1]
    partial=np.cumsum(ordered)-mass
    active=np.flatnonzero(ordered-partial/np.arange(1,len(values)+1)>0)
    threshold=partial[active[-1]]/(active[-1]+1)
    return np.maximum(values-floor-threshold,0)+floor


def covariance_project(matrix):
    values,vectors=np.linalg.eigh((matrix+matrix.T)/2)
    result=(vectors*np.clip(values,.01,25.)) @ vectors.T
    return (result+result.T)/2


def derivatives(data,model):
    """Return objective, fresh responsibilities and every physical-parameter gradient."""
    n=len(data);x=data[:,:-1];features=c.phase_features(x)
    terms=np.array([np.log(a['weight'])+c.gvm_log(data,dict(components=[dict(a,weight=1.)]))
                    for a in model['components']])
    normalizer=logsumexp(terms,axis=0);responsibilities=np.exp(terms-normalizer)
    if not np.all(np.isfinite(normalizer)):raise ArithmeticError('Nonfinite likelihood')
    gradients=[]
    for component,r in zip(model['components'],responsibilities):
        mass=r.sum()/n;cov=np.array(component['cov']);inverse=np.linalg.inv(cov)
        delta=x-np.array(component['mean']);solved=delta @ inverse
        residual=data[:,-1]-component['alpha']-features @ np.array(component['coeff'])
        sine=np.sin(residual);cosine=np.cos(residual);k=component['kappa']
        gradients.append(dict(weight=-mass/component['weight'],mean=-(r @ solved)/n,
            cov=.5*(mass*inverse-(solved.T*r) @ solved/n),
            alpha=-k*float(r @ sine)/n,coeff=-k*((r*sine) @ features)/n,
            kappa=mass*float(i1e(k)/i0e(k))-float(r @ cosine)/n))
    return -float(normalizer.mean()),responsibilities,gradients


def stationarity(data,model):
    objective,r,gradient=derivatives(data,model)
    weights=np.array([a['weight'] for a in model['components']])
    mapped=weights-simplex(weights-np.array([a['weight'] for a in gradient]))
    blocks=dict(weight=float(np.max(np.abs(mapped))),mean=0.,cov=0.,alpha=0.,coeff=0.,kappa=0.)
    for a,g in zip(model['components'],gradient):
        cov=np.array(a['cov']);beta=np.array(a['coeff'])
        blocks['mean']=max(blocks['mean'],float(np.max(np.abs(g['mean']))))
        blocks['cov']=max(blocks['cov'],float(np.linalg.norm(cov-covariance_project(cov-g['cov']),'fro')))
        blocks['alpha']=max(blocks['alpha'],abs(float(g['alpha'])))
        blocks['coeff']=max(blocks['coeff'],float(np.max(np.abs(beta-np.clip(beta-g['coeff'],-4,4)))))
        blocks['kappa']=max(blocks['kappa'],abs(float(a['kappa']-np.clip(a['kappa']-g['kappa'],0,50))))
    masses=r.sum(axis=1);ess=masses*masses/np.sum(r*r,axis=1)
    return dict(objective=objective,blocks=blocks,maximum=max(blocks.values()),
                minimumMass=float(masses.min()),minimumEss=float(ess.min()))


def pack(model):
    return np.concatenate([np.r_[a['weight'],a['mean'],np.array(a['cov']).ravel(),
                                  a['alpha'],a['coeff'],a['kappa']] for a in model['components']])


def unpack(vector,d,k):
    q=d+d*(d+1)//2;width=1+d+d*d+1+q+1
    if len(vector)!=k*width or not np.all(np.isfinite(vector)):raise ValueError('Invalid parameter vector')
    components=[]
    for block in np.asarray(vector).reshape(k,width):
        index=1+d
        components.append(dict(weight=float(block[0]),mean=block[1:index].tolist(),
            cov=covariance_project(block[index:index+d*d].reshape(d,d)).tolist(),
            alpha=float(c.wrap(block[index+d*d])),coeff=np.clip(block[index+d*d+1:-1],-4,4).tolist(),
            kappa=float(np.clip(block[-1],0,50))))
    for component,w in zip(components,simplex([a['weight'] for a in components])):component['weight']=float(w)
    return dict(components=components)


def difference(new,old,d,k):
    result=pack(new)-pack(old);q=d+d*(d+1)//2;width=1+d+d*d+1+q+1
    for j in range(k):
        index=j*width+1+d+d*d;result[index]=c.wrap(result[index])
    return result
