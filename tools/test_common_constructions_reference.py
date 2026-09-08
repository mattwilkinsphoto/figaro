"""Independent high-precision controls; research-only mpmath, not a runtime dependency.

Uses inverses/determinants and direct integrals rather than the Scala Cholesky path.
Run: python -B -m unittest discover -s tools -p 'test_common_constructions_reference.py'
"""
import unittest
import mpmath as mp


class ConstructionReferenceTest(unittest.TestCase):
    def test_multivariate_controls(self):
        with mp.workdps(70):
            p = mp.matrix([[2, '.3'], ['.3', 1]])
            q = mp.matrix([[1, '-.2'], ['-.2', 3]])
            a, b = mp.matrix([0, 1]), mp.matrix([1, -1])
            delta, average = b-a, (p+q)/2
            product = q**-1*p
            kl = (sum(product[i, i] for i in range(2)) + (delta.T*q**-1*delta)[0]
                  - 2 + mp.log(mp.det(q)/mp.det(p)))/2
            bhat = (delta.T*average**-1*delta)[0]/8 + mp.log(mp.det(average)/mp.sqrt(mp.det(p)*mp.det(q)))/2
            mi = mp.log(p[0, 0]*p[1, 1]/mp.det(p))/2
            for actual, expected in [(kl, '1.46904301313871524988492104160180486'),
                                     (bhat, '.457767802716435331501684665284033846'),
                                     (mi, '.0230219692507034023016325393122689183')]:
                self.assertLess(abs(actual-mp.mpf(expected)), mp.mpf('1e-33'))
            def density(x, mean, covariance):
                residual = x-mean
                return mp.exp(-(residual.T*covariance**-1*residual)[0]/2)/(2*mp.pi*mp.sqrt(mp.det(covariance)))
            x = mp.matrix(['.2', '-.4'])
            total = mp.mpf('.25')*density(x, a, p)+mp.mpf('.75')*density(x, b, q)
            self.assertLess(abs(mp.log(total)-mp.mpf('-2.838210812077037765313019136600484301')), mp.mpf('1e-33'))
            self.assertLess(abs(mp.mpf('.25')*density(x, a, p)/total-mp.mpf('.166936711953222053162569302005279567')), mp.mpf('1e-33'))

    def test_scalar_integral_reductions(self):
        with mp.workdps(50):
            pdf = lambda x, mu, sd: mp.exp(-((x-mu)/sd)**2/2)/(sd*mp.sqrt(2*mp.pi))
            overlap = mp.quad(lambda x: mp.sqrt(pdf(x, 0, 1)*pdf(x, 2, 3)), [-mp.inf, 0, 2, mp.inf])
            analytic = mp.log(mp.mpf(10)/6)/2+mp.mpf(4)/(4*(1+9))
            self.assertLess(abs(-mp.log(overlap)-analytic), mp.mpf('1e-40'))
            tail = mp.quad(lambda x: pdf(x, 0, 1), [8, 9])
            self.assertLess(abs(tail-mp.mpf('6.219831985865830282868259670512219675e-16')), mp.mpf('1e-50'))

    def test_zero_adjustment_and_total_covariance(self):
        with mp.workdps(50):
            nb = lambda k: (k+1)/mp.mpf(2)**(k+2)
            inflated = lambda k: mp.mpf('.7')*nb(k)+(mp.mpf('.3') if k == 0 else 0)
            hurdle = lambda k: mp.mpf('.3') if k == 0 else mp.mpf('.7')*nb(k)/mp.mpf('.75')
            self.assertEqual(inflated(0), mp.mpf('.475'))
            for law in (inflated, hurdle):
                self.assertLess(abs(mp.fsum(law(k) for k in range(400))-1), mp.mpf('1e-49'))
            # Within + between components, not a weighted covariance average alone.
            self.assertEqual(mp.mpf('.25')*2+mp.mpf('.75')*1+mp.mpf('.25')*mp.mpf('.75'), mp.mpf('1.4375'))


if __name__ == '__main__':
    unittest.main()
