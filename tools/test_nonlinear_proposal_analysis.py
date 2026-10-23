import unittest
from nonlinear_proposal_analysis import parse,modes,COLS

class NonlinearAnalysisTest(unittest.TestCase):
    def test_incomplete_input_rejected(self):
        with self.assertRaises(AssertionError):parse(__file__,True)
    def test_fixed_inventory(self):
        self.assertEqual(modes('mono',False),['bootstrap','cubature','ekf','aux','bad'])
        self.assertIn('manual',modes('symmetric',True))
        self.assertIn('exact',modes('affine',True))
        self.assertEqual(len(COLS),26)

if __name__=='__main__':unittest.main()
