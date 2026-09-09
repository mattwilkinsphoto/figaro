import math
import pathlib
import unittest
import mpmath as mp
from breadth_reference import scalar, wishart, metric_values, scala_source


class BreadthReferenceTest(unittest.TestCase):
    def test_fixture_freshness(self):
        path=pathlib.Path(__file__).resolve().parents[1]/'Figaro/src/test/scala/com/cra/figaro/test/modernization/BreadthFixtures.scala'
        self.assertEqual(path.read_text(encoding='utf-8'),scala_source())

    def test_scalar_cdf_inverse_and_limits(self):
        with mp.workdps(80):
            for kind in ('GEV','GPD'):
                for xi in (-2,-1,-.2,-1e-8,0,1e-8,.2,2):
                    for p in (.13,.5,.91):
                        x,lp,cdf=scalar(kind,xi,p)
                        self.assertLess(abs(cdf-mp.mpf(str(p))),mp.mpf('1e-65'))
                        self.assertTrue(mp.isfinite(lp))
                self.assertLess(abs(scalar(kind,1e-8,.5)[0]-scalar(kind,0,.5)[0]),mp.mpf('1e-7'))

    def test_multinomial_normalization_kl_overlap_and_partition_mi(self):
        with mp.workdps(60):
            p=list(map(mp.mpf,('.2','.3','.5'))); q=list(map(mp.mpf,('.4','.2','.4'))); n=4
            def mass(x,r):
                return mp.factorial(n)*mp.fprod(r[i]**x[i]/mp.factorial(x[i]) for i in range(3))
            points=[(i,j,n-i-j) for i in range(n+1) for j in range(n-i+1)]
            self.assertLess(abs(sum(mass(x,p) for x in points)-1),mp.mpf('1e-55'))
            kl=sum(mass(x,p)*mp.log(mass(x,p)/mass(x,q)) for x in points)
            bh=-mp.log(sum(mp.sqrt(mass(x,p)*mass(x,q)) for x in points))
            self.assertLess(abs(kl-n*sum(a*mp.log(a/b) for a,b in zip(p,q))),mp.mpf('1e-55'))
            self.assertLess(abs(bh+n*mp.log(sum(mp.sqrt(a*b) for a,b in zip(p,q)))),mp.mpf('1e-55'))
            marg={i:sum(mass(x,p) for x in points if x[0]==i) for i in range(n+1)}
            # The other block determines x0 because the total count is fixed.
            mi=sum(mass(x,p)*mp.log(1/marg[x[0]]) for x in points)
            entropy=-sum(v*mp.log(v) for v in marg.values())
            self.assertLess(abs(mi-entropy),mp.mpf('1e-55'))

    def test_wishart_scalar_normalization_and_chi_square(self):
        with mp.workdps(40):
            law=lambda x: mp.exp(wishart(mp.mpf(4),mp.matrix([[1]]),mp.matrix([[x]])))
            self.assertLess(abs(mp.quad(law,[0,1,mp.inf])-1),mp.mpf('1e-35'))
            self.assertLess(abs(law(3)-mp.mpf(3)*mp.exp(-mp.mpf('1.5'))/4),mp.mpf('1e-35'))
            self.assertTrue(all(mp.isfinite(v) for v in metric_values().values()))

    def test_spherical_surface_measure_and_divergence(self):
        with mp.workdps(40):
            c=lambda k: k/(4*mp.pi*mp.sinh(k))
            # Polar coordinate w=cos(theta); integrating azimuth gives 2*pi.
            self.assertLess(abs(mp.quad(lambda w: 2*mp.pi*c(3)*mp.exp(3*w),[-1,1])-1),mp.mpf('1e-35'))
            kl=mp.quad(lambda w: 2*mp.pi*c(3)*mp.exp(3*w)*(mp.log(c(3)/c(7))+3*w),[-1,1])
            affinity=mp.quad(lambda w: 2*mp.pi*mp.sqrt(c(3)*c(7))*mp.exp(mp.mpf('1.5')*w)*mp.besseli(0,mp.mpf('3.5')*mp.sqrt(1-w*w)),[-1,1])
            self.assertLess(abs(kl-metric_values()['vmfKl']),mp.mpf('1e-35'))
            self.assertLess(abs(-mp.log(affinity)-metric_values()['vmfBh']),mp.mpf('1e-35'))


if __name__=='__main__': unittest.main()
