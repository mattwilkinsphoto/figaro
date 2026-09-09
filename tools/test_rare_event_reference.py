"""Independent event integrals and proposal-risk calculations; research-only mpmath."""
import unittest
import mpmath as m

class RareEventReferenceTest(unittest.TestCase):
    def test_tail_probabilities(self):
        with m.workdps(70):
            sf=lambda x:m.erfc(x/m.sqrt(2))/2
            self.assertLess(abs(sf(5)-m.mpf('2.8665157187919391e-7')),m.mpf('1e-22'))
            self.assertLess(abs(m.log(sf(40))-m.mpf('-804.6084420137538')),m.mpf('2e-14'))
            self.assertLess(abs(sf(m.mpf('4.999'))-sf(m.mpf('5.001'))-m.mpf('2.9734509232365575e-9')),m.mpf('1e-25'))

    def test_defensive_box_mean_and_variance(self):
        with m.workdps(40):
            q=m.mpf('.2')+m.mpf('.8')/m.mpf('.25')
            first=m.quad(lambda x:q/q,[m.mpf('.75'),1])
            second=m.quad(lambda x:q/q**2,[m.mpf('.75'),1])
            self.assertEqual(first,m.mpf('.25'))
            self.assertLess(abs(second-first**2-m.mpf(3)/272),m.mpf('1e-35'))

    def test_weighted_elite_normal_moments(self):
        with m.workdps(40):
            phi=lambda x:m.exp(-x*x/2)/m.sqrt(2*m.pi)
            p=m.erfc(5/m.sqrt(2))/2
            mean=m.quad(lambda x:x*phi(x),[5,6,m.inf])/p
            variance=m.quad(lambda x:(x-mean)**2*phi(x),[5,6,m.inf])/p
            self.assertLess(abs(mean-phi(5)/p),m.mpf('1e-30'))
            self.assertLess(abs(variance-(1+5*mean-mean**2)),m.mpf('1e-30'))

    def test_defense_does_not_certify_missing_region_detection(self):
        with m.workdps(40):
            sf=lambda x:m.erfc(x/m.sqrt(2))/2
            region=sf(m.mpf('4.999'))-sf(m.mpf('5.001'))
            miss=(1-m.mpf('.1')*region)**20000
            self.assertGreater(miss,m.mpf('.99999'))
            # The missing mode's rare weight-10 contributions matter in expectation.
            self.assertEqual(m.mpf('.1')*region*10,region)

if __name__=='__main__': unittest.main()
