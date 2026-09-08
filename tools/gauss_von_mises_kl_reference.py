"""Print 80-digit GVM KL fixtures; mpmath==1.3.0, no files written.

Uses explicit inverses/complex determinants independently of production triangular
solves/real eigendecomposition. The complex square-root shortcut here is restricted
to dimension two; the test suite separately checks the higher-dimensional branch.
Run: python -B tools/gauss_von_mises_kl_reference.py
"""
import mpmath as mp

mp.mp.dps = 80


def matrix(rows):
    return mp.matrix([[mp.mpf(str(x)) for x in row] for row in rows])


ap = matrix([[2, 0], [.6, 1.5]])
aq = matrix([[.8, 0], [-.3, 1.1]])
mup = matrix([[1], [-2]])
muq = matrix([[-.4], [.8]])
bp = matrix([[.7], [-.4]])
bq = matrix([[-.2], [.6]])
gp = matrix([[.3, .2], [.2, -.5]])
gq = matrix([[-.3, .15], [.15, .25]])
kp, kq = mp.mpf('4.5'), mp.mpf('1.2')
pp, pq = ap * ap.T, aq * aq.T
d, bmat = aq**-1 * (mup - muq), aq**-1 * ap
c = mp.mpf('3.05') - mp.mpf('-2.9') - (bq.T*d)[0] - (d.T*gq*d)[0]/2
b = bp - bmat.T*bq - bmat.T*gq*d
g = gp - bmat.T*gq*bmat
t = mp.eye(2) - 1j*g
expected_cosine = mp.re(mp.exp(1j*c - (b.T*t**-1*b)[0]/2) / mp.sqrt(mp.det(t)))
product = pq**-1 * pp
gaussian = (sum(product[i, i] for i in range(2)) +
            ((mup-muq).T*pq**-1*(mup-muq))[0] - 2 + mp.log(mp.det(pq)/mp.det(pp))) / 2
rp = mp.besseli(1, kp)/mp.besseli(0, kp)
angular = mp.log(mp.besseli(0, kq)/mp.besseli(0, kp)) + kp*rp - kq*rp*expected_cosine
for name, value in [('gaussian', gaussian), ('angular', angular), ('total', gaussian+angular)]:
    print(name, mp.nstr(value, 40))

for p, q in [('0', '.000001'), ('4', '4.000001'), ('50', '51'), ('100000000', '99990000'),
             ('100000000', '1000000'), ('0', '100000000')]:
    p, q = mp.mpf(p), mp.mpf(q)
    result = mp.log(mp.besseli(0,q)/mp.besseli(0,p)) + (p-q)*mp.besseli(1,p)/mp.besseli(0,p)
    print('concentration', mp.nstr(p), mp.nstr(q), mp.nstr(result,40))
