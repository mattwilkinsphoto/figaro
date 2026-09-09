"""Independent high-precision multivariate-t normalization and correlated density controls."""
import unittest
import mpmath as m

class MultivariateTReferenceTest(unittest.TestCase):
    def test_correlated_density_fixture(self):
        with m.workdps(60):
            shape=m.matrix([[2,m.mpf('.3')],[m.mpf('.3'),1]])
            delta=m.matrix([1,-2]); square=(delta.T*shape**-1*delta)[0]
            log=m.loggamma(m.mpf(7)/2)-m.loggamma(m.mpf(5)/2)-m.log(5*m.pi)-m.log(m.det(shape))/2-m.mpf(7)/2*m.log1p(square/5)
            self.assertLess(abs(log-m.mpf('-4.70457186642933672170078522299636459824336805466309847079571')),m.mpf('1e-55'))
    def test_radial_normalization(self):
        with m.workdps(45):
            for d,df in [(1,1),(2,5),(3,2),(8,10)]:
                dimension=m.mpf(d);nu=m.mpf(df)
                normalizer=m.gamma((nu+dimension)/2)/m.gamma(nu/2)/(nu*m.pi)**(dimension/2)
                surface=2*m.pi**(dimension/2)/m.gamma(dimension/2)
                integral=m.quad(lambda r:surface*r**(d-1)*normalizer*(1+r*r/nu)**(-(nu+dimension)/2),[0,1,10,m.inf])
                self.assertLess(abs(integral-1),m.mpf('1e-35'))

if __name__=='__main__':unittest.main()
