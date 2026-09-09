"""Independent density-integral controls; research-only mpmath, no Figaro import."""
import unittest
import mpmath as m

class LegacyInformationReferenceTest(unittest.TestCase):
    def test_positive_digamma_recurrence(self):
        import math
        with m.workdps(80):
            for value in (.001,.01,.5,1,2,3.5,15.99,16,100,1e6):
                x=value; c=0.0
                while x<16:
                    c-=1/x; x+=1
                z=1/x/x
                actual=c+math.log(x)-.5/x-z*(1/12-z*(1/120-z*(1/252-z*(1/240-z*(1/132-z*691/32760)))))
                self.assertLess(abs(m.mpf(actual)-m.digamma(m.mpf(value))),m.mpf('5e-13'))

    def test_gamma_integrals(self):
        with m.workdps(60):
            p=lambda x:x*m.exp(-x/3)/9
            q=lambda x:x**3*m.exp(-x/5)/(6*625)
            kl=m.quad(lambda x:p(x)*m.log(p(x)/q(x)),[0,1,10,m.inf])
            bh=-m.log(m.quad(lambda x:m.sqrt(p(x)*q(x)),[0,1,10,m.inf]))
            self.assertLess(abs(kl-m.mpf('2.1894932940950835')),m.mpf('1e-15'))
            self.assertLess(abs(bh-m.mpf('.5549531476434343')),m.mpf('1e-15'))
            self.assertLess(abs(m.quad(p,[0,1,10,m.inf])-1),m.mpf('1e-50'))

    def test_inverse_gamma_scale_and_information_invariance(self):
        with m.workdps(50):
            f=lambda x:7**4/m.factorial(3)*x**-5*m.exp(-7/x)
            self.assertLess(abs(m.quad(f,[0,1,10,m.inf])-1),m.mpf('1e-40'))
            self.assertLess(abs(m.quad(lambda x:x*f(x),[0,1,10,m.inf])-m.mpf(7)/3),m.mpf('1e-40'))
            g=lambda y:f(1/y)/y**2
            self.assertLess(abs(g(m.mpf('.5'))-7**4/m.factorial(3)*m.mpf('.5')**3*m.exp(-m.mpf('3.5'))),m.mpf('1e-40'))

    def test_beta_and_dirichlet_normalizer_identity(self):
        with m.workdps(50):
            p=lambda x:12*x*(1-x)**2
            q=lambda x:280*x**3*(1-x)**4
            kl=m.quad(lambda x:p(x)*m.log(p(x)/q(x)),[0,m.mpf('.5'),1])
            oracle=m.log(m.beta(4,5)/m.beta(2,3))-2*(m.digamma(2)-m.digamma(5))-2*(m.digamma(3)-m.digamma(5))
            self.assertLess(abs(kl-oracle),m.mpf('1e-40'))
            bh=-m.log(m.quad(lambda x:m.sqrt(p(x)*q(x)),[0,m.mpf('.5'),1]))
            self.assertLess(abs(bh-(m.log(m.beta(2,3))+m.log(m.beta(4,5)))/2+m.log(m.beta(3,4))),m.mpf('1e-40'))

    def test_count_sums(self):
        with m.workdps(50):
            p=lambda k:m.exp(-2)*m.mpf(2)**k/m.factorial(k)
            q=lambda k:m.exp(-5)*m.mpf(5)**k/m.factorial(k)
            kl=m.fsum(p(k)*m.log(p(k)/q(k)) for k in range(200))
            bh=-m.log(m.fsum(m.sqrt(p(k)*q(k)) for k in range(200)))
            self.assertLess(abs(kl-(2*m.log(m.mpf(2)/5)+3)),m.mpf('1e-40'))
            self.assertLess(abs(bh-(m.sqrt(2)-m.sqrt(5))**2/2),m.mpf('1e-40'))

if __name__=='__main__': unittest.main()
