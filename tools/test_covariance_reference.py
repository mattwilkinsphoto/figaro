"""Independent high-precision matrix-prior references; no Figaro import or file writes."""
import unittest
import mpmath as m

def lkj_partition(d, eta):
    return m.fsum((d-j-1)*(m.log(m.pi)/2+m.loggamma(eta+(d-j-2)/2)-m.loggamma(eta+(d-j-1)/2)) for j in range(d-1))

def inverse_wishart_log(df, scale, x):
    d=len(x)
    s=m.matrix(scale); v=m.matrix(x)
    mg=m.pi**(m.mpf(d)*(d-1)/4)*m.fprod(m.gamma((df-i)/2) for i in range(d))
    return df/2*m.log(m.det(s))-df*d/2*m.log(2)-m.log(mg)-(df+d+1)/2*m.log(m.det(v))-m.fsum((s*v**-1)[i,i] for i in range(d))/2

class CovarianceReferenceTest(unittest.TestCase):
    def test_lkj_two_dimensional_normalization_and_metrics(self):
        with m.workdps(50):
            p=lambda r:m.mpf('.5')
            q=lambda r:m.mpf('.75')*(1-r*r)
            for eta in (m.mpf('.5'),m.mpf(1),m.mpf(2),m.mpf(5)):
                area=m.quad(lambda r:(1-r*r)**(eta-1),[-1,0,1])
                self.assertLess(abs(m.log(area)-lkj_partition(2,eta)),m.mpf('1e-24'))
            kl=m.quad(lambda r:p(r)*m.log(p(r)/q(r)),[-1,0,1])
            bh=-m.log(m.quad(lambda r:m.sqrt(p(r)*q(r)),[-1,0,1]))
            self.assertLess(abs(kl-(2-2*m.log(2)-m.log(m.mpf('1.5')))),m.mpf('1e-40'))
            self.assertLess(abs(bh+m.log(m.pi*m.sqrt(m.mpf('1.5'))/4)),m.mpf('1e-40'))

    def test_three_dimensional_volume_and_gaussian_evidence(self):
        with m.workdps(40):
            # For fixed a=R12,b=R13, admissible R23 width is 2*sqrt((1-a^2)*(1-b^2)).
            width=m.quad(lambda a:m.sqrt(1-a*a),[-1,0,1])
            self.assertLess(abs(m.log(2*width*width)-lkj_partition(3,m.mpf(1))),m.mpf('1e-35'))
            # LKJ(2,2) times a centered N(0,R) observation leaves exponent 1/2.
            num=m.quad(lambda r:r*r*m.sqrt(1-r*r),[-1,0,1])
            den=m.quad(lambda r:m.sqrt(1-r*r),[-1,0,1])
            self.assertLess(abs(num/den-m.mpf('.25')),m.mpf('1e-35'))

    def test_inverse_gamma_and_matrix_inversion_identity(self):
        with m.workdps(50):
            for x in (m.mpf('.1'),m.mpf(1),m.mpf(10)):
                actual=inverse_wishart_log(m.mpf(8),[[m.mpf(14)]],[[x]])
                expected=4*m.log(7)-m.loggamma(4)-5*m.log(x)-7/x
                self.assertLess(abs(actual-expected),m.mpf('1e-40'))
            scale=[[m.mpf(2),m.mpf('.4')],[m.mpf('.4'),m.mpf(1)]]
            x=[[m.mpf('.8'),m.mpf('.1')],[m.mpf('.1'),m.mpf('.5')]]
            actual=inverse_wishart_log(m.mpf(6),scale,x)
            self.assertLess(abs(actual-m.mpf('-1.8476711382197565551199365012827016866048808896409235')),m.mpf('1e-45'))

if __name__=='__main__': unittest.main()
