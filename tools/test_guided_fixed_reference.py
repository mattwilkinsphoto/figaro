import unittest
from guided_fixed_reference import finite, CONFIGS
from resampling_schedule_reference import read

class GuidedFixedReferenceTest(unittest.TestCase):
    def test_exact_guided_auxiliary_measure(self):
        rows=finite()
        self.assertEqual(len(rows),48)
        self.assertTrue(all(r['mass']==['93/500','23/500'] for r in rows))

    def test_incomplete_confirmation_is_rejected(self):
        with self.assertRaises(AssertionError):
            read(__file__,CONFIGS,range(70001,70257),['growth'])

    def test_protocol_configurations(self):
        self.assertEqual(len(CONFIGS),10)
        self.assertEqual(len(set(CONFIGS)),10)
        self.assertEqual(dict(CONFIGS)['big-s-50'],512)

if __name__=='__main__':unittest.main()
