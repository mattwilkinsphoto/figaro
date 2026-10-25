"""Independent 60-digit convex reference: no candidate solver/projection imports."""
import itertools
import mpmath as mp


def evaluate(logs, weights):
    with mp.workdps(60):
        w = [mp.mpf(float(x)) for x in weights]
        a = [[mp.exp(mp.mpf(float(x))) for x in row] for row in logs]
        den = [mp.fsum(x*y for x,y in zip(row,w)) for row in a]
        obj = -mp.fsum(mp.log(x) for x in den)/len(a)
        grad = [-mp.fsum(row[j]/z for row,z in zip(a,den))/len(a) for j in range(len(w))]
        eps = mp.mpf(float(1e-8))
        gap = mp.fsum(x*y for x,y in zip(w,grad)) - eps*mp.fsum(grad)-(1-len(w)*eps)*min(grad)
        return dict(objective=float(obj),gradient=list(map(float,grad)),gap=float(gap))


def optimum(logs):
    """Small independent fixtures: vertices, bracketed edge roots and interior KKT.

    Failure to find/certify an interior solution is an explicit reference failure.
    Duplicate columns need objective/gap agreement, not unique weights.
    """
    with mp.workdps(60):
        a = [[mp.exp(mp.mpf(float(x))) for x in row] for row in logs]
        k=len(a[0]); eps=mp.mpf(float(1e-8)); mass=1-k*eps
        def grad(w):
            den=[mp.fsum(x*y for x,y in zip(row,w)) for row in a]
            return [-mp.fsum(row[j]/z for row,z in zip(a,den))/len(a) for j in range(k)]
        candidates=[]
        for j in range(k):
            w=[eps]*k;w[j]+=mass;candidates.append(w)
        for j,l in itertools.combinations(range(k),2):
            def point(t):
                w=[eps]*k;w[j]+=t;w[l]+=mass-t;return w
            def slope(t):
                g=grad(point(t));return g[j]-g[l]
            if slope(0)<0<slope(mass):
                lo,hi=mp.mpf(0),mass
                for _ in range(160):
                    mid=(lo+hi)/2
                    if slope(mid)>0:hi=mid
                    else:lo=mid
                candidates.append(point((lo+hi)/2))
        if k==3:
            def equations(x,y):
                g=grad([x,y,1-x-y]);return g[0]-g[2],g[1]-g[2]
            for initial in ((.33,.33),(.1,.1),(.8,.1),(.1,.8)):
                try:
                    x,y=mp.findroot(equations,initial,maxsteps=80,tol=mp.mpf('1e-40'))
                    w=[x,y,1-x-y]
                    if min(w)>=eps:candidates.append(w)
                except (ValueError,ZeroDivisionError):pass
        def score(w):return -mp.fsum(mp.log(mp.fsum(x*y for x,y in zip(row,w))) for row in a)/len(a)
        w=min(candidates,key=score); g=grad(w)
        gap=mp.fsum(x*y for x,y in zip(w,g))-eps*mp.fsum(g)-mass*min(g)
        if gap>mp.mpf('1e-25'):raise ArithmeticError('Independent reference not certified')
        return list(map(float,w)),float(score(w))
