import unittest
from fractions import Fraction as F
from particle_diversity_reference import finite_control, mh_matrix, evolve

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

if __name__=='__main__':unittest.main()
