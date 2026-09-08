"""Independent 80-digit GVM moment fixtures; mpmath==1.3.0, no files written.

Run with python -B. Uses a known physical Cholesky factor and complex matrix inverse,
not the production real eigensystem. The determinant square root is restricted to
this two-dimensional fixture; higher dimensions need a continuous branch.
"""
import mpmath as mp

mp.mp.dps = 80


def matrix(rows):
    return mp.matrix([[mp.mpf(str(x)) for x in row] for row in rows])


mu = matrix([[1], [-2]])
a = matrix([[2, 0], [.6, 1.5]])
b = matrix([[.7], [-.4]])
g = matrix([[.3, .2], [.2, -.5]])
k = mp.mpf('4.5')
q = (mp.eye(2)-1j*g)**-1
f = mp.besseli(1,k)/mp.besseli(0,k) * mp.exp(1j*mp.mpf('3.05')-(b.T*q*b)[0]/2)
f /= mp.sqrt(mp.det(mp.eye(2)-1j*g))
w = mu + 1j*a*q*b
first = w*f
second = (a*q*a.T+w*w.T)*f
print('meanCos', mp.nstr(mp.re(f),40), 'meanSin', mp.nstr(mp.im(f),40))
print('resultant',mp.nstr(abs(f),40),'logResultant',mp.nstr(mp.log(abs(f)),40),'direction',mp.nstr(mp.arg(f),40))
for name, values in [('first',first),('second',second)]:
    for i in range(values.rows):
        for j in range(values.cols):
            print(name,i,j,'cos',mp.nstr(mp.re(values[i,j]),40),'sin',mp.nstr(mp.im(values[i,j]),40))
