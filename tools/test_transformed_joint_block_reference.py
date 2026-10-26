import math,unittest
from transformed_joint_block_reference import marginal_moment,contrast_moment,COV
import numpy as np

class ReferenceControls(unittest.TestCase):
    def test_log_normal_moments(self):
        for k in (1,2,3,4):
            self.assertAlmostEqual(marginal_moment(0,0,k,True,2e-12),math.exp(.2*k+.125*k*k))
    def test_correlated_contrast(self):
        self.assertAlmostEqual(contrast_moment(2,2),.06)
        self.assertAlmostEqual(contrast_moment(2,4),3*.06**2)
    def test_curved_moments(self):
        self.assertAlmostEqual(marginal_moment(4,1,1,False,2e-12),0,places=12)
        self.assertAlmostEqual(marginal_moment(4,1,2,False,2e-12),1.37,places=12)
        self.assertAlmostEqual(contrast_moment(4,2),2.37,places=12)
    def test_separated_mixture(self):
        self.assertAlmostEqual(marginal_moment(5,0,2,False,2e-12),6.61)
        self.assertAlmostEqual(contrast_moment(5,2),1.61)
        self.assertAlmostEqual(marginal_moment(5,1,1,True,2e-12),.5,places=12)
    def test_covariances_are_positive_definite(self):
        for c in COV:self.assertTrue(np.all(np.linalg.eigvalsh(c)>0))

if __name__=='__main__':unittest.main()
