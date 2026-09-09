"""Independent high-precision identities for the construction milestone; no Figaro imports."""
import unittest
import mpmath as m
m.mp.dps=60

class ConstructionReference(unittest.TestCase):
    def test_half_normal_and_tail_mass(self):
        phi=lambda x: m.exp(-x*x/2)/m.sqrt(2*m.pi)
        self.assertLess(abs(m.quad(lambda x: 2*phi(x),[0,1,m.inf])-1),m.mpf('1e-50'))
        self.assertLess(abs(m.erfc(8/m.sqrt(2))/2-m.mpf('6.2209605742717841235159951725881884224887172789002758e-16')),m.mpf('1e-64'))
    def test_folded_shifted_normal(self):
        phi=lambda x: m.exp(-(x-1)**2/2)/m.sqrt(2*m.pi)
        self.assertLess(abs(m.quad(lambda y: phi(y)+phi(-y),[0,1,m.inf])-1),m.mpf('1e-50'))
        self.assertAlmostEqual(float(m.quad(phi,[-1,1])),.4772498680518208,14)
    def test_wrapped_cauchy_kl_and_circular_moment(self):
        p=m.mpf('.5'); q=m.mpf('.3'); delta=m.mpf('.7')
        density=lambda x,r,mu: (1-r*r)/(2*m.pi*(1+r*r-2*r*m.cos(x-mu)))
        numeric=m.quad(lambda x: density(x,p,0)*m.log(density(x,p,0)/density(x,q,delta)),[-m.pi,0,m.pi])
        exact=m.log((1+(p*q)**2-2*p*q*m.cos(delta))/((1-p*p)*(1-q*q)))
        self.assertLess(abs(numeric-exact),m.mpf('1e-45'))
        self.assertLess(abs(m.quad(lambda x: m.cos(x)*density(x,p,0),[-m.pi,0,m.pi])-p),m.mpf('1e-45'))
    def test_weighted_empirical_moments(self):
        mean=(3*(-1)+2)/m.mpf(4)
        variance=(3*(-1-mean)**2+(2-mean)**2)/4
        self.assertEqual(mean,m.mpf('-.25'))
        self.assertEqual(variance,m.mpf('1.6875'))

if __name__=='__main__': unittest.main()
