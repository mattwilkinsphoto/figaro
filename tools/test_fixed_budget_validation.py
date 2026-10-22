"""Validation-method tests use in-memory synthetic rows, never substitute study inputs."""
import json
import unittest
from unittest.mock import patch
import fixed_budget_validation as v

class Gates(unittest.TestCase):
    def run_case(self, method='multi-always', status='ok', error='', digest='same'):
        rows=[dict(seed=s,t=0,status=status,error=error,logZ=0.,mean=1.,second=1.,positive=.5,
                   work=768,nanos=1000,resamplings=0) for s in range(50001,50257)]
        reference=json.dumps({'references':{'gaussian':[dict(logZ=0.,mean=1.,second=1.,positive=.5)]}})
        with patch.object(v,'read',return_value=({('gaussian',method,256):rows},digest,[])),patch.object(v.Path,'read_text',return_value=reference):
            return v.analyze(['a','b','c'],'unused')
    def test_complete_control(self):
        result=self.run_case();self.assertTrue(result['passed']);self.assertEqual(len(result['checks']),4)
    def test_refusal_is_not_accuracy_or_efficiency_success(self):
        result=self.run_case('strat-always','refused','StratifiedPrecisionLoss')
        self.assertTrue(result['passed']);self.assertEqual(result['checks'],[])
        self.assertNotIn('integrated_sign_rmse',result['summaries'][0])
        self.assertEqual(result['summaries'][0]['refused'],256)
    def test_log_tree_refusal_fails(self):
        self.assertFalse(self.run_case('multi-always','refused','StratifiedPrecisionLoss')['passed'])
    def test_unexpected_error_or_orphaned_unavailable_fails(self):
        self.assertFalse(self.run_case('strat-always','error','other')['passed'])
        self.assertFalse(self.run_case('strat-always','unavailable','StratifiedPrecisionLoss')['passed'])

if __name__=='__main__':unittest.main()
