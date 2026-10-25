"""GVM-09 test-only joint constrained quasi-Newton adapter; no runtime imports."""
import copy,time
import numpy as np
from scipy.optimize import minimize
import gvm_dimension_controls as c
import gvm_gradient_fit as g
import gvm_wholefit_math as m
import gvm_weight_fit as prefix


class Coordinates:
    def __init__(self,d,k):
        self.d,self.k=d,k;self.tri=list(zip(*np.tril_indices(d)))
        self.q=d+d*(d+1)//2;self.width=1+d+len(self.tri)+1+self.q+1

    def pack(self,model):
        rows=[]
        for a in model['components']:
            lower=np.linalg.cholesky(a['cov'])
            rows.append(np.r_[a['weight'],a['mean'],
                [np.log(lower[i,j]) if i==j else lower[i,j] for i,j in self.tri],
                a['alpha'],a['coeff'],a['kappa']])
        return np.concatenate(rows)

    def lower(self,block):
        lower=np.zeros((self.d,self.d))
        for value,(i,j) in zip(block[1+self.d:],self.tri):
            lower[i,j]=np.exp(value) if i==j else value
        return lower

    def unpack(self,x):
        if len(x)!=self.k*self.width or not np.all(np.isfinite(x)):raise ValueError('Invalid coordinates')
        components=[];offset=1+self.d+len(self.tri)
        for b in np.asarray(x).reshape(self.k,self.width):
            lower=self.lower(b)
            components.append(dict(weight=float(b[0]),mean=b[1:1+self.d].tolist(),
                cov=(lower@lower.T).tolist(),alpha=float(b[offset]),
                coeff=b[offset+1:-1].tolist(),kappa=float(b[-1])))
        return dict(components=components)

    def pullback(self,x,gradients):
        rows=[]
        for b,a in zip(np.asarray(x).reshape(self.k,self.width),gradients):
            lower=self.lower(b);matrix=2*np.asarray(a['cov'])@lower
            rows.append(np.r_[a['weight'],a['mean'],
                [matrix[i,j]*(lower[i,j] if i==j else 1) for i,j in self.tri],
                a['alpha'],a['coeff'],a['kappa']])
        return np.concatenate(rows)

    def bounds(self):
        return ([(1e-8,1.)]+[(None,None)]*self.d+
            [(np.log(.1),np.log(5.)) if i==j else (None,None) for i,j in self.tri]+
            [(None,None)]+[(-4.,4.)]*self.q+[(0.,50.)])*self.k

    def spectral(self,x,jacobian=False):
        result=[];jac=[]
        for j,b in enumerate(np.asarray(x).reshape(self.k,self.width)):
            lower=self.lower(b);eigen,vectors=np.linalg.eigh(lower@lower.T)
            for sign,offset in ((1.,-.01),(-1.,25.)):
                for index,value in enumerate(eigen):
                    result.append(sign*value+offset)
                    if jacobian:
                        matrix=2*sign*np.outer(vectors[:,index],vectors[:,index])@lower
                        row=np.zeros(self.k*self.width)
                        for col,(i,l) in enumerate(self.tri):
                            row[j*self.width+1+self.d+col]=matrix[i,l]*(lower[i,l] if i==l else 1)
                        jac.append(row)
        return np.asarray(jac) if jacobian else np.asarray(result)

    def simplex(self,x):return float(np.asarray(x)[::self.width].sum()-1)
    def simplex_jac(self,x):
        row=np.zeros(len(x));row[::self.width]=1.;return row

    def feasible(self,x):
        return (np.all(np.isfinite(x)) and abs(self.simplex(x))<=1e-12 and
            min(self.spectral(x))>=-1e-9 and
            all((lo is None or value>=lo-1e-12) and (hi is None or value<=hi+1e-12)
                for value,(lo,hi) in zip(x,self.bounds())))


class Qualified(Exception):pass


class Budget(prefix.Budget):
    def __init__(self,limit,seconds,starts):
        super().__init__(limit,seconds,starts);self.work['joint']=0;self.ledger=[]
    def charge(self,kind,n):
        super().charge(kind,n);self.ledger.append(dict(start=self.active,kind=kind,units=n))


def joint(session,iterations=200,calls=2000):
    coords=Coordinates(session.d,session.k);budget=session.budget
    report=dict(objectiveCalls=0,qualificationCalls=0,constraintCalls=0,
        constraintJacobianCalls=0,infeasibleTrials=0,lowSupportTrials=0,
        solverSuccess=False,solverMessage=None)
    session.record['joint']=report
    def objective(x):
        if report['objectiveCalls']>=calls:raise g.Limit('JointCallLimit')
        budget.charge('joint',2*session.n*session.k);report['objectiveCalls']+=1
        model=coords.unpack(x);value,_,gradient=m.derivatives(session.data,model)
        if not np.isfinite(value):raise ArithmeticError('Nonfinite objective')
        pullback=coords.pullback(x,gradient)
        if not np.all(np.isfinite(pullback)):raise ArithmeticError('Nonfinite gradient')
        if not coords.feasible(x):report['infeasibleTrials']+=1
        elif -session.n*value>=session.value:
            # Independent physical-coordinate qualification, charged even on refusal.
            report['qualificationCalls']+=1
            try:score,diag=session.evaluate(model)
            except g.Limit as error:
                if str(error)=='InsufficientComponent':report['lowSupportTrials']+=1
                else:raise
            else:
                for a in model['components']:a['alpha']=float(c.wrap(a['alpha']))
                session.retain(model,score,diag)
                if diag['maximum']<=session.tolerance:raise Qualified()
        budget.check();return value,pullback
    def constraints(x):
        budget.check();report['constraintCalls']+=1;return coords.spectral(x)
    def jacobian(x):
        budget.check();report['constraintJacobianCalls']+=1;return coords.spectral(x,True)
    try:
        result=minimize(objective,coords.pack(session.current),jac=True,method='SLSQP',
            bounds=coords.bounds(),constraints=[dict(type='eq',fun=coords.simplex,jac=coords.simplex_jac),
                dict(type='ineq',fun=constraints,jac=jacobian)],
            options=dict(maxiter=iterations,ftol=1e-12))
        report.update(solverSuccess=bool(result.success),solverMessage=str(result.message),iterations=int(result.nit))
        session.record['status']='SolverStoppedUnqualified'
    except Qualified:
        session.record['status']='Stationary';report['solverMessage']='IndependentPhysicalQualification'


def fit(data,k,seed,max_work=20000000,seconds=120,starts=3,prefix_maps=10,iterations=200,calls=2000):
    data=np.asarray(data)
    if (data.ndim!=2 or not 2<=data.shape[1]<=4 or not 30<=len(data)<=5000 or not np.all(np.isfinite(data))
        or k not in (1,2,3) or starts not in (1,2,3) or prefix_maps<1 or iterations<1 or calls<1
        or max_work<0 or seconds<=0 or not np.all(np.isfinite([max_work,seconds,prefix_maps,iterations,calls]))):
        raise ValueError('Invalid data/configuration')
    if np.linalg.eigvalsh(np.atleast_2d(np.cov(data[:,:-1].T,bias=True))).min()<=1e-10:
        return dict(status='RankDeficient',components=[],starts=[],work={},seconds=0.,totalWork=0)
    begun=time.perf_counter();budget=Budget(max_work,seconds,starts);sessions=[];failure=None
    for index in range(starts):
        budget.active=index;s=prefix.Start(data,k,seed,index,budget,prefix_maps,1e-4,False);sessions.append(s)
        try:
            s.initialize()
            while s.record['maps']<prefix_maps and s.diag['maximum']>1e-4 and s.record['status']=='Active':s.cycle()
            s.record['prefixModel']=copy.deepcopy(s.current)
            s.record['prefixWork']=sum(budget.work.values())
            if s.diag['maximum']<=1e-4:s.record['status']='Stationary'
            elif s.record['status']=='Active':joint(s,iterations,calls)
        except g.Limit as error:
            s.record['status']=str(error)
            if str(error) in prefix.GLOBAL:failure=str(error)
        except (ArithmeticError,np.linalg.LinAlgError,ValueError) as error:
            s.record['status']='NumericallyUnresolved:'+str(error)
        if failure:break
    eligible=[s for s in sessions if s.value is not None];best=max(eligible,key=lambda s:s.value) if eligible else None
    return dict(components=best.current['components'] if best else [],trainScore=best.value if best else None,
        diagnostic=best.diag if best else None,selectedStart=best.index if best else None,
        status=failure or (best.record['status'] if best else sessions[-1].record['status']),
        searchDisposition=failure or 'AllStartsFinished',starts=[s.record for s in sessions],
        work=budget.work,totalWork=sum(budget.work.values()),workByStart=budget.by_start,ledger=budget.ledger,
        angularCalls=budget.calls,seconds=time.perf_counter()-begun)
