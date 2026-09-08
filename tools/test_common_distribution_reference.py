import pathlib
import unittest
import mpmath as mp
from common_distribution_reference import continuous, law, scala_source


class CommonDistributionReferenceTest(unittest.TestCase):
    def test_fixture_freshness(self):
        path=pathlib.Path(__file__).resolve().parents[1]/'Figaro/src/test/scala/com/cra/figaro/test/modernization/CommonDistributionFixtures.scala'
        self.assertEqual(path.read_text(),scala_source())

    def test_normalization_and_cdf_derivative(self):
        with mp.workdps(40):
            for name,params in continuous():
                pdf,cdf,support,splits=law(name,params)
                if name != 'StudentT' or params[0] > 1:
                    self.assertLess(abs(mp.quad(pdf,splits)-1),mp.mpf('1e-30'),name)
                for x in [mp.mpf('.2'),mp.mpf('.8')]:
                    self.assertLess(abs(mp.diff(cdf,x)-pdf(x)),mp.mpf('1e-30'),name)

    def test_student_cauchy_and_weibull_exponential_reductions(self):
        with mp.workdps(40):
            for x in [mp.mpf('.1'),mp.mpf('2'),mp.mpf('10')]:
                for index in [0,1]:
                    self.assertLess(abs(law('StudentT',[1,0,2])[index](x)-law('Cauchy',[0,2])[index](x)),mp.mpf('1e-35'))
                self.assertLess(abs(law('Weibull',[1,2])[0](x)-mp.exp(-x/2)/2),mp.mpf('1e-35'))


if __name__ == '__main__': unittest.main()
