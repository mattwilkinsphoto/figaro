import pathlib
import unittest
import mpmath as mp
from common_metrics_reference import scalar_reference,count_reference,scala_source


class CommonMetricsReferenceTest(unittest.TestCase):
    def test_fixture_freshness(self):
        path=pathlib.Path(__file__).resolve().parents[1]/'Figaro/src/test/scala/com/cra/figaro/test/modernization/CommonMetricFixtures.scala'
        self.assertEqual(path.read_text(),scala_source())

    def test_closed_forms_against_independent_integrals(self):
        with mp.workdps(50):
            cauchy=scalar_reference('Cauchy',[0,1],[1,2])[0]
            self.assertLess(abs(cauchy-mp.log(mp.mpf(10)/8)),mp.mpf('1e-40'))
            kl,bd=scalar_reference('Weibull',[2,1],[2,3])
            self.assertLess(abs(kl-(2*mp.log(3)+mp.mpf(1)/9-1)),mp.mpf('1e-40'))
            self.assertLess(abs(bd-mp.log(mp.mpf(5)/3)),mp.mpf('1e-40'))
            kl,bd=count_reference('NegativeBinomial',[2,mp.mpf('.4')],[2,mp.mpf('.6')])
            self.assertLess(abs(kl-(2*mp.log(mp.mpf(2)/3)+3*mp.log(mp.mpf(3)/2))),mp.mpf('1e-40'))

    def test_numerical_precision_refinement(self):
        for name,p,q in [('StudentT',[5,0,1],[8,.4,1.2]),('Weibull',[1.7,2.3],[2.4,1.4]),('Kumaraswamy',[1.4,2.3],[2.2,1.3])]:
            a=scalar_reference(name,p,q,50); b=scalar_reference(name,p,q,70)
            self.assertTrue(all(abs(x-y)<mp.mpf('1e-35') for x,y in zip(a,b)))


if __name__ == '__main__': unittest.main()
