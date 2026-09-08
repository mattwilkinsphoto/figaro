"""Independent 80-digit state-gradient fixtures; mpmath==1.3.0, writes no files.

Run with python -B. Differentiates the joint density definition numerically at high
precision, using an explicit known covariance inverse and Cholesky factor.
"""
import mpmath as mp

mp.mp.dps = 80
mu = mp.matrix([1, -2])
cov = mp.matrix([[4, mp.mpf('1.2')], [mp.mpf('1.2'), mp.mpf('2.61')]])
a = mp.matrix([[2, 0], [mp.mpf('.6'), mp.mpf('1.5')]])
b = mp.matrix([mp.mpf('.7'), mp.mpf('-.4')])
g = mp.matrix([[mp.mpf('.3'), mp.mpf('.2')], [mp.mpf('.2'), mp.mpf('-.5')]])
k = mp.mpf('4.5')


def log_density(x0, x1, theta):
    x = mp.matrix([x0, x1])
    z = a**-1*(x-mu)
    center = mp.mpf('3.05')+(b.T*z)[0]+(z.T*g*z)[0]/2
    normal = -mp.log(2*mp.pi)-mp.log(mp.det(cov))/2-((x-mu).T*(cov**-1)*(x-mu))[0]/2
    return normal+k*mp.cos(theta-center)-mp.log(2*mp.pi*mp.besseli(0,k))


for point in [('1','-2','3.1'), ('2','-1','-3.1'), ('-2','1','1.2')]:
    values = tuple(map(mp.mpf, point))
    derivatives = [mp.diff(log_density,values,tuple(int(i == j) for i in range(3))) for j in range(3)]
    print(point, 'gradient', [mp.nstr(v,40) for v in derivatives], flush=True)
