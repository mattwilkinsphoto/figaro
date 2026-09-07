"""Print independent GVM fixtures; requires mpmath==1.3.0, writes no files."""
import mpmath as mp

mp.mp.dps = 80
mu = mp.matrix([1, -2])
p = mp.matrix([[4, mp.mpf('1.2')], [mp.mpf('1.2'), mp.mpf('2.61')]])
# Known exact lower factor: not the production factorization algorithm.
a = mp.matrix([[2, 0], [mp.mpf('.6'), mp.mpf('1.5')]])
b = mp.matrix([mp.mpf('.7'), mp.mpf('-.4')])
g = mp.matrix([[mp.mpf('.3'), mp.mpf('.2')], [mp.mpf('.2'), mp.mpf('-.5')]])
k = mp.mpf('4.5')
for values, angle in [([1, -2], '3.1'), ([2, -1], '-3.1'), ([-2, 1], '1.2')]:
    x = mp.matrix(values)
    z = a**-1 * (x - mu)
    center = mp.mpf('3.05') + (b.T*z)[0] + (z.T*g*z)[0]/2
    log_gaussian = -mp.log(2*mp.pi) - mp.log(mp.det(p))/2 - ((x-mu).T*(p**-1)*(x-mu))[0]/2
    log_joint = log_gaussian + k*mp.cos(mp.mpf(angle)-center) - mp.log(2*mp.pi*mp.besseli(0, k))
    print(values, angle, 'center', mp.nstr(center, 35), 'logMarginal', mp.nstr(log_gaussian, 35), 'logJoint', mp.nstr(log_joint, 35))
print('meanResultant', mp.nstr(mp.besseli(1,k)/mp.besseli(0,k),35))
