"""GVM-08 test-only composite map and resumable, least-work restart scheduler.

The unmodified GVM-07 fitter remains the primary control. This additive adapter
preserves map algebra/guards and exposes cycle-boundary state for scheduling.
"""
import copy, time
import numpy as np
import gvm_dimension_controls as c
import gvm_gradient_fit as g
import gvm_wholefit_math as m
import gvm_wholefit_fit as old
import gvm_weight_solver as weights

GLOBAL = ('WorkBudgetExhausted','TimeBudgetExhausted')


class Budget(old.Budget):
    def __init__(self,limit,seconds,starts):
        super().__init__(limit,seconds)
        self.work['weight']=0;self.active=0;self.by_start=[0]*starts

    def charge(self,kind,n):
        super().charge(kind,n);self.by_start[self.active]+=n


def choose(states,charged):
    active=[i for i,s in enumerate(states) if s=='Active']
    return min(active,key=lambda i:(charged[i],i)) if active else None


class Start:
    def __init__(self,data,k,seed,index,budget,maps,tolerance,polish):
        self.data=data;self.n=len(data);self.d=data.shape[1]-1;self.k=k
        self.seed=seed;self.index=index;self.budget=budget;self.limit=maps
        self.tolerance=tolerance;self.polish=polish;self.features=c.phase_features(data[:,:-1])
        self.current=None;self.value=None;self.diag=None
        self.record=dict(start=index,status='Active',trace=[],gradients=[],maps=0,
            accepted=0,rejected=0,alphas=[],angularUnqualified=0,polishes=[],checkpoints=[])

    def evaluate(self,model):
        self.budget.charge('score',self.n*self.k);self.budget.charge('gradient',self.n*self.k)
        diag=m.stationarity(self.data,model)
        if not np.isfinite(diag['maximum']):raise g.Limit('NumericallyUnresolved')
        if min(diag['minimumMass'],diag['minimumEss'])<max(30,5*self.d):raise g.Limit('InsufficientComponent')
        self.budget.check();return -self.n*diag['objective'],diag

    def retain(self,model,score,diag):
        self.current,self.value,self.diag=model,score,diag
        self.record['trace'].append(score);self.record['gradients'].append(diag['maximum'])
        self.record['checkpoints'].append(dict(work=self.budget.by_start[self.index],
            totalWork=sum(self.budget.work.values()),maps=self.record['maps'],gradient=diag['maximum']))

    def initialize(self):
        labels=c.partitions(self.data,self.k,self.index,self.seed+self.index);components=[]
        for j in range(self.k):
            r=(labels==j).astype(float);self.budget.charge('moments',self.n)
            a,_=g.moments(self.data,r);self.budget.phase(self.n)
            real,imag=r@np.cos(self.data[:,-1])/r.sum(),r@np.sin(self.data[:,-1])/r.sum()
            a.update(coeff=np.zeros(self.features.shape[1]).tolist(),alpha=float(np.arctan2(imag,real)),
                     kappa=g.concentration(min(1.,float(np.hypot(real,imag)))))
            components.append(a)
        model=dict(components=components);score,diag=self.evaluate(model);self.retain(model,score,diag)

    def step(self,model):
        if self.record['maps']>=self.limit:raise g.Limit('MapLimit')
        self.record['maps']+=1;self.budget.charge('score',self.n*self.k)
        terms=np.array([np.log(a['weight'])+c.gvm_log(self.data,dict(components=[dict(a,weight=1.)])) for a in model['components']])
        normalizer=g.logsumexp(terms,axis=0)
        if not np.all(np.isfinite(normalizer)):raise g.Limit('NumericallyUnresolved')
        responsibilities=np.exp(terms-normalizer);components=[]
        for a,r in zip(model['components'],responsibilities):
            self.budget.charge('moments',self.n);fresh,_=g.moments(self.data,r)
            phase,report=g.angular(np.array(a['coeff']),self.features,self.data[:,-1],r,self.budget)
            self.record['angularUnqualified']+=int(not report['qualified'])
            fresh.update(phase);components.append(fresh)
        candidate=dict(components=components);score,diag=self.evaluate(candidate)
        if not self.polish or self.k==1:return candidate,score,diag
        self.budget.charge('score',self.n*self.k)
        logs=np.array([c.gvm_log(self.data,dict(components=[dict(a,weight=1.)])) for a in components]).T
        try:
            w,report=weights.solve(logs,[a['weight'] for a in components],self.budget.charge)
            report=dict(report,map=self.record['maps']);self.record['polishes'].append(report)
            if not report['qualified']:return candidate,score,diag
            refined=copy.deepcopy(candidate)
            for a,value in zip(refined['components'],w):a['weight']=float(value)
            try:new_score,new_diag=self.evaluate(refined)
            except g.Limit as error:
                if str(error) in GLOBAL:raise
                report['status']='LowSupport' if str(error)=='InsufficientComponent' else str(error)
                report['accepted']=False;return candidate,score,diag
            if new_score<score-1e-10*self.n:
                report.update(status='NonmonotonePolish',accepted=False);return candidate,score,diag
            report['accepted']=True;return refined,new_score,new_diag
        except weights.LocalLimit as error:
            self.record['polishes'].append(dict(status=str(error),qualified=False,map=self.record['maps']))
            return candidate,score,diag
        except (ArithmeticError,ValueError,np.linalg.LinAlgError) as error:
            self.record['polishes'].append(dict(status='NumericalPolishRefusal:'+str(error),
                qualified=False,map=self.record['maps']))
            return candidate,score,diag

    def cycle(self):
        if self.diag['maximum']<=self.tolerance:self.record['status']='Stationary';return
        if self.record['maps']>=self.limit:self.record['status']='MapLimit';return
        base=self.current
        first,score,diag=self.step(base)
        if score<self.value-1e-10*self.n:self.record['status']='NonmonotoneMap';return
        self.retain(first,score,diag)
        if diag['maximum']<=self.tolerance or self.record['maps']>=self.limit:return
        second,score,diag=self.step(first)
        if score<self.value-1e-10*self.n:self.record['status']='NonmonotoneMap';return
        self.retain(second,score,diag)
        if diag['maximum']<=self.tolerance or self.record['maps']>=self.limit:return
        trial,alpha=old.extrapolate(base,first,second,self.d,self.k);self.record['alphas'].append(alpha)
        try:
            trial_score,_=self.evaluate(trial)
            if trial_score<self.value-1e-10*self.n:self.record['rejected']+=1;return
            stable,score,diag=self.step(trial)
            if score<max(self.value,trial_score)-1e-10*self.n:self.record['rejected']+=1;return
            self.retain(stable,score,diag);self.record['accepted']+=1
        except g.Limit as error:
            if str(error) not in ('InsufficientComponent','NumericallyUnresolved'):raise
            self.record['rejected']+=1
        except (ArithmeticError,np.linalg.LinAlgError):self.record['rejected']+=1


def fit(data,k,seed,polish=False,schedule='sequential',max_work=20000000,maps=200,
        seconds=120,tolerance=1e-4,starts=3):
    data=np.asarray(data)
    if (data.ndim!=2 or not 2<=data.shape[1]<=4 or not 30<=len(data)<=5000
        or not np.all(np.isfinite(data)) or not 1<=k<=3 or maps<1 or max_work<0
        or seconds<=0 or tolerance<=0 or not 1<=starts<=3
        or not np.all(np.isfinite([seconds,tolerance,max_work,maps,starts]))
        or schedule not in ('sequential','balanced')):raise ValueError('Invalid data/configuration')
    if np.linalg.eigvalsh(np.atleast_2d(np.cov(data[:,:-1].T,bias=True))).min()<=1e-10:
        return dict(status='RankDeficient',components=[],starts=[],work={},seconds=0.)
    begun=time.perf_counter();budget=Budget(max_work,seconds,starts);sessions=[];failure=None;events=[]
    def invoke(session,method):
        nonlocal failure
        budget.active=session.index
        try:method()
        except g.Limit as error:
            session.record['status']=str(error)
            if str(error) in GLOBAL:failure=str(error)
        except (ArithmeticError,np.linalg.LinAlgError) as error:session.record['status']='NumericallyUnresolved:'+str(error)
    def initialize(index):
        session=Start(data,k,seed,index,budget,maps,tolerance,polish);sessions.append(session)
        invoke(session,session.initialize)
        return session
    if schedule=='balanced':
        for index in range(starts):
            initialize(index)
            if failure:break
    while not failure:
        if schedule=='sequential':
            if not sessions or sessions[-1].record['status']!='Active':
                if len(sessions)==starts:break
                initialize(len(sessions))
                if failure:break
                if sessions[-1].record['status']!='Active':continue
            index=len(sessions)-1
        else:
            index=choose([s.record['status'] for s in sessions],budget.by_start)
            if index is None:break
        session=sessions[index];before=sum(budget.work.values())
        invoke(session,session.cycle)
        # Match GVM-07's post-loop stationarity check, but never hide global failure.
        if not failure and session.diag is not None and session.diag['maximum']<=tolerance:
            session.record['status']='Stationary'
        elif not failure and session.record['status']=='Active' and session.record['maps']>=maps:
            session.record['status']='MapLimit'
        events.append(dict(start=index,before=before,after=sum(budget.work.values()),
                           status=session.record['status']))
    eligible=[s for s in sessions if s.value is not None]
    best=max(eligible,key=lambda s:s.value) if eligible else None
    result=dict(components=best.current['components'] if best else [],trainScore=best.value if best else None,
        diagnostic=best.diag if best else None,selectedStart=best.index if best else None,
        status=failure or (best.record['status'] if best else sessions[-1].record['status']),
        retainedQualified=bool(best and best.diag['maximum']<=tolerance),
        searchDisposition=failure or 'AllStartsFinished',starts=[s.record for s in sessions],
        work=budget.work,totalWork=sum(budget.work.values()),workByStart=budget.by_start,
        angularCalls=budget.calls,seconds=time.perf_counter()-begun,allocation=schedule,schedule=events)
    return result
