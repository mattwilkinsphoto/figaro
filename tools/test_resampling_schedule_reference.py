import unittest
from fractions import Fraction as F
from resampling_schedule_reference import finite, offspring, read

class ScheduleReferenceTest(unittest.TestCase):
    def test_exact_two_step_expectations(self):
        rows=finite()
        self.assertEqual(len(rows),10)
        self.assertTrue(all(r['unnormalized']==['93/500','23/500'] for r in rows))

    def test_stratified_parent_marginals_and_zeros(self):
        for weights in ([F(1,5),F(4,5)],[F(1),F(0)]):
            for scheme in ('multi','strat'):
                outcomes=offspring(weights,scheme)
                self.assertEqual(sum(p for _,p in outcomes),1)
                self.assertEqual(sum(p*a.count(0) for a,p in outcomes),2*weights[0])

    def test_missing_outcomes_fail(self):
        with self.assertRaises(AssertionError):read(__file__,[('strat',256)],[40001],['switching'])

if __name__=='__main__':unittest.main()
