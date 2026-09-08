"""Optional research checks; mpmath==1.3.0 required, no Figaro runtime imports."""
import unittest
import mpmath as mp
from gvm_bhattacharyya_research import Comparison, Kernel, mat, scalar


class BhattacharyyaResearchTest(unittest.TestCase):
    def setUp(self):
        context = mp.workdps(60)
        context.__enter__()
        self.addCleanup(context.__exit__,None,None,None)

    def close(self, actual, expected, tolerance='1e-45'):
        self.assertLess(abs(actual-expected),mp.mpf(tolerance))

    def curved(self):
        return (scalar('.3','1.1','.2','.7','.3','4.5'),
                scalar('-.4','.8','-.5','-.2','-.15','1.2'))

    def test_uniform_gaussian_and_equal_covariance_mahalanobis_reductions(self):
        p,q = scalar(kappa=0),scalar(mean=2,kappa=0)
        c = Comparison(p,q)
        self.close(c.gaussian_distance,mp.mpf('.5')) # squared Mahalanobis / 8
        self.close(c.series(0)[0],1)
        self.close(c.series(0)[1],0)
        q = scalar(mean=1,sd=2,kappa=0)
        c = Comparison(p,q)
        self.close(c.gaussian_distance,mp.mpf(1)/20+mp.log(mp.mpf(5)/4)/2)

    def test_identical_curved_and_same_physical_conditional(self):
        p,_ = self.curved()
        self.close(Comparison(p,p).series(64)[0],1)
        p = scalar(0,1,'.2','.4','.3',4)
        q = scalar(2,'1.5','1.6','1.5','.675',4)
        c = Comparison(p,q)
        self.close(c.constant,0)
        self.close(mp.norm(c.linear),0)
        self.close(mp.norm(c.quadratic),0)
        self.close(c.series(64)[0],1)

    def test_circular_closed_form_and_one_uniform_conditional(self):
        p,q = scalar(alpha='.3',kappa=4),scalar(alpha='-.8',kappa=2)
        c = Comparison(p,q)
        radius = mp.sqrt(4+1+4*mp.cos(mp.mpf('1.1')))
        expected = mp.besseli(0,radius)/mp.sqrt(mp.besseli(0,4)*mp.besseli(0,2))
        self.close(c.series(48)[0],expected)
        p,q = scalar(beta=3,gamma=4,kappa=0),scalar(beta=-7,gamma=2,kappa=6)
        c = Comparison(p,q)
        self.close(c.series(0)[0],mp.besseli(0,3)/mp.sqrt(mp.besseli(0,6)))
        self.close(c.series(0)[1],0)

    def test_angular_elimination_against_direct_periodic_integration(self):
        p,q = self.curved()
        c = Comparison(p,q)
        for x in [-2,0,mp.mpf('1.3')]:
            point = mp.matrix([x])
            # Integrate sqrt of the actual conditional densities: no combined-radius formula.
            direct = mp.quad(lambda theta: mp.exp((p.kappa*mp.cos(theta-p.center(point))
                +q.kappa*mp.cos(theta-q.center(point)))/2)/(2*mp.pi*c.denominator),
                [-mp.pi,0,mp.pi])
            self.close(c.angular_affinity(point),direct)

    def test_gaussian_overlap_factorization_at_physical_points(self):
        p,q = self.curved()
        c = Comparison(p,q)
        def density(mean,variance,x):
            return mp.exp(-(x-mean)**2/(2*variance))/mp.sqrt(2*mp.pi*variance)
        for x in [-2,0,2]:
            left = mp.sqrt(density(p.mean[0],p.factor[0]**2,x)*density(q.mean[0],q.factor[0]**2,x))
            right = mp.exp(-c.gaussian_distance)*density(c.mean[0],c.covariance[0],x)
            self.close(left,right)

    def test_series_against_positive_adaptive_linear_integral(self):
        c = Comparison(*self.curved())
        value,tail = c.series(12)
        direct = mp.quad(lambda z: mp.exp(-z*z/2)*c.angular_affinity(c.mean+c.factor*mp.matrix([z])),
                         [-12,-6,-3,0,3,6,12])/mp.sqrt(2*mp.pi)
        # 12-sigma omitted standard-Gaussian probability; affinity <= 1.
        omitted = mp.erfc(12/mp.sqrt(2))
        self.assertLess(abs(value-direct),tail+omitted+mp.mpf('1e-50'))
        self.close(c.series(32)[0],mp.mpf('0.767698821999035514675116'),'1e-24')
        self.assertLess(abs(c.quadrature(64)-direct),mp.mpf('1e-10'))

    def test_symmetry_common_angle_shift_and_linear_units(self):
        p,q = self.curved()
        c,r = Comparison(p,q),Comparison(q,p)
        self.close(c.gaussian_distance,r.gaussian_distance)
        self.close(c.series(32)[0],r.series(32)[0])
        p.alpha += 2*mp.pi; q.alpha += 2*mp.pi
        self.close(Comparison(p,q).series(32)[0],c.series(32)[0])
        p.mean *= 100; p.factor *= 100; q.mean *= 100; q.factor *= 100
        transformed = Comparison(p,q)
        self.close(transformed.gaussian_distance,c.gaussian_distance)
        self.close(transformed.series(32)[0],c.series(32)[0])

    def test_two_dimensional_curvature_against_tensor_reference(self):
        p = Kernel(mat([[0],[0]]),mp.eye(2),mp.mpf('.2'),mat([[.2],[-.1]]),
                   mat([[.1,.04],[.04,-.05]]),mp.mpf(2))
        q = Kernel(mat([[.2],[-.1]]),mat([[1.1,0],[.1,.9]]),mp.mpf('-.1'),
                   mat([[-.1],[.2]]),mat([[0,0],[0,.1]]),mp.mpf(3))
        c = Comparison(p,q)
        self.close(c.series(32)[0],c.quadrature(20),'1e-10')

    def test_eight_dimensional_characteristic_has_continuous_determinant_branch(self):
        n = 8
        p = Kernel(mp.zeros(n,1),mp.eye(n),0,mp.zeros(n,1),2*mp.eye(n),mp.mpf(2))
        q = Kernel(mp.zeros(n,1),mp.eye(n),0,mp.zeros(n,1),mp.zeros(n),mp.mpf(2))
        c = Comparison(p,q)
        expected = mp.exp(-n*mp.log(1-2j)/2)
        self.close(c.characteristic(1),expected)
        wrong = 1/mp.sqrt(mp.det(mp.eye(n)-2j*mp.eye(n)))
        self.assertGreater(abs(wrong-expected),mp.mpf('.001'))

    def test_tail_bounds_and_unresolved_distance_are_not_silent_success(self):
        c = Comparison(scalar(kappa=50),scalar(alpha=mp.pi,kappa=50))
        exact = 1/mp.besseli(0,50)
        for terms in [16,24,32,48]:
            value,tail = c.series(terms)
            self.assertLessEqual(abs(value-exact),tail+mp.mpf('1e-58'))
        self.assertTrue(mp.isinf(c.distance_interval(32)[1]))
        lo,hi = c.distance_interval(64)
        self.assertLessEqual(lo-mp.mpf('1e-35'),mp.log(mp.besseli(0,50)))
        self.assertGreaterEqual(hi+mp.mpf('1e-35'),mp.log(mp.besseli(0,50)))
        self.assertLess(hi-lo,mp.mpf('1e-15'))
        c = Comparison(scalar(kappa=1000),scalar(alpha='.01',beta='.1',kappa=1000))
        self.assertTrue(mp.isinf(c.series(128)[1]))
        self.assertTrue(mp.isfinite(c.series(512)[1]))

    def test_tiny_truncation_does_not_bound_finite_precision_cancellation(self):
        with mp.workdps(17):
            c = Comparison(scalar(kappa=50),scalar(alpha=mp.pi,kappa=50))
            value,tail = c.series(128)
            exact = 1/mp.besseli(0,50)
            self.assertGreater(abs(value/exact-1),10)
            self.assertLess(tail,mp.mpf('1e-150'))

    def test_budgets_and_basic_invalid_dimensions(self):
        c = Comparison(*self.curved())
        for terms in [-1,1001,1.5,True]:
            with self.assertRaises(ValueError): c.series(terms)
        with self.assertRaises(ValueError): c.quadrature(97)
        with self.assertRaises(ValueError): Comparison(scalar(kappa=-1),scalar())


if __name__ == '__main__':
    unittest.main()
