import unittest
from fractions import Fraction as F
from particle_diversity_reference import finite_control, mh_matrix, evolve, binomial_upper, read_experiment

class ParticleDiversityReferenceTest(unittest.TestCase):
    def test_exact_two_step_unnormalized_moments(self):
        r=finite_control()
        self.assertEqual([x['sweeps'] for x in r['two_step']],[0,1,4,8])
        self.assertTrue(all(x['unnormalized']==['93/500','23/500'] for x in r['two_step']))

    def test_asymmetric_proposal_and_zero_support(self):
        q=[[F(2,3),F(1,3)],[F(3,4),F(1,4)]]
        p=mh_matrix([F(1,5),F(4,5)],q)
        self.assertEqual(p,[[F(2,3),F(1,3)],[F(1,12),F(11,12)]])
        self.assertEqual(evolve([F(1,5),F(4,5)],p,8),[F(1,5),F(4,5)])
        self.assertEqual(mh_matrix([F(1),F(0)],q)[0][1],0)

    def test_zero_failures_is_not_zero_risk(self):
        self.assertAlmostEqual(binomial_upper(0,256),1-.05**(1/256),places=13)
        self.assertGreater(binomial_upper(3,256),binomial_upper(0,256))
        self.assertEqual(binomial_upper(256,256),1)

    def test_missing_records_are_not_a_pass(self):
        # This source contains no experiment records; no temporary files required.
        with self.assertRaises(AssertionError):
            read_experiment(__file__,[('none',64)],range(10001,10002))

if __name__=='__main__':unittest.main()
