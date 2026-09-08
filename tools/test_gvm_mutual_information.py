"""Independent MI controls and checked-in fixture freshness; mpmath is research-only."""
import pathlib
import unittest
import mpmath as mp
from gvm_mutual_information_research import coefficients, reference, scala_source


class MutualInformationResearchTest(unittest.TestCase):
    def test_fixture_freshness(self):
        path = pathlib.Path(__file__).resolve().parents[1]/'Figaro/src/test/scala/com/cra/figaro/test/modernization/GvmMutualInformationFixtures.scala'
        self.assertEqual(path.read_text(), scala_source())

    def test_precision_and_harmonic_refinement(self):
        for b,g,k in [([.7],[[.3]],4.), ([1e-5],[[0.]],50.), ([0.],[[1.2]],8.)]:
            self.assertLess(abs(reference(b,g,k)-reference(b,g,k,precision=80,terms=192)),mp.mpf('1e-40'))

    def test_independent_positive_gaussian_mixture_density(self):
        # Direct positive normal expectation: no quadratic-form characteristic function.
        with mp.workdps(40):
            b,g,k=mp.mpf('.4'),mp.mpf('.15'),mp.mpf('2')
            cs=coefficients([b],[[g]],k)
            for theta in [mp.mpf('-2'),mp.mpf('.3'),mp.mpf('2.5')]:
                harmonic=1+2*sum(mp.re(c*mp.exp(-1j*(j+1)*theta)) for j,c in enumerate(cs))
                direct=mp.quad(lambda z: mp.exp(-z*z/2+k*mp.cos(theta-b*z-g*z*z/2)),[-12,-4,0,4,12])/(mp.sqrt(2*mp.pi)*mp.besseli(0,k))
                self.assertLess(abs(harmonic-direct),mp.mpf('1e-26'))

    def test_limits_and_linear_dependence_order(self):
        self.assertEqual(reference([0.],[[0.]],50.),0)
        self.assertEqual(reference([9.],[[3.]],0.),0)
        values=[reference([b],[[0.]],2.) for b in [.1,.4,1.,8.]]
        self.assertTrue(all(a < b for a,b in zip(values,values[1:])))
        with mp.workdps(50):
            ceiling=2*mp.besseli(1,2)/mp.besseli(0,2)-mp.log(mp.besseli(0,2))
            self.assertLess(abs(values[-1]-ceiling),mp.mpf('1e-25'))

    def test_multidimensional_phase_branch_with_positive_chi_square_mixture(self):
        # For Gamma=.7 I and beta=0 in eight dimensions, phase=.7 U, U~Gamma(4,1).
        with mp.workdps(40):
            cs=coefficients([0.]*8,[[mp.mpf('.7') if i == j else 0 for j in range(8)] for i in range(8)],2)
            for theta in [mp.mpf('0.3'),mp.mpf('2.5')]:
                harmonic=1+2*sum(mp.re(c*mp.exp(-1j*(j+1)*theta)) for j,c in enumerate(cs))
                direct=mp.quad(lambda u: u**3*mp.exp(-u+2*mp.cos(theta-mp.mpf('.7')*u)),list(range(0,141,5)))/(6*mp.besseli(0,2))
                self.assertLess(abs(harmonic-direct),mp.mpf('1e-30'))


if __name__ == '__main__':
    unittest.main()
