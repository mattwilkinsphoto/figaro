"""Independent NumPy/inverse-matrix oracle for the deterministic regularized EM fixture."""
import json
import numpy as np

def reference():
    t=np.arange(320,dtype=float)
    x=np.column_stack((np.where(t%4<3,-1.,2.)+.8*np.sin(t*1.7),
                       np.where(t%4<3,.5,-1.)+.6*np.cos(t*.9)))
    mean=x.mean(axis=0); covariance=np.cov(x,rowvar=False)+np.eye(2)*1e-4
    distance=lambda a,b: ((a-b)**2/np.diag(covariance)).sum(axis=-1)
    center=[x[np.argmax(distance(x,mean))]]
    center.append(x[np.argmax(distance(x,center[0]))])
    means=np.array(center); covs=np.array([covariance,covariance]); weights=np.ones(2)/2
    previous=None
    for iteration in range(200):
        logs=[]
        for w,m,c in zip(weights,means,covs):
            residual=x-m
            logs.append(np.log(w)-np.log(2*np.pi)-np.linalg.slogdet(c)[1]/2-
                        np.einsum('ni,ij,nj->n',residual,np.linalg.inv(c),residual)/2)
        logs=np.array(logs).T; peak=logs.max(axis=1)
        relative=np.exp(logs-peak[:,None]); density=peak+np.log(relative.sum(axis=1))
        objective=density.mean(); resp=relative/relative.sum(axis=1)[:,None]
        if previous is not None and abs(objective-previous)<=1e-9*(1+abs(previous)): break
        mass=resp.sum(axis=0); weights=mass/len(x); means=resp.T@x/mass[:,None]
        for j in range(2):
            residual=x-means[j]
            covs[j]=(residual.T*resp[:,j])@residual/mass[j]+np.eye(2)*1e-4
        previous=objective
    else: raise ValueError('Oracle did not converge')
    order=np.argsort(means[:,0])
    return dict(iterations=iteration+1,weights=weights[order].tolist(),means=means[order].tolist(),
                covariance=covs[order].tolist(),averageLogDensity=float(objective))

if __name__=='__main__': print(json.dumps(reference(),indent=2))
