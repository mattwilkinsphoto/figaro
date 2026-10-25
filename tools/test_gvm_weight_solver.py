"""Independent GVM-08 fixed-density qualification and failure controls."""
import unittest
import numpy as np
import gvm_weight_solver as s
import gvm_weight_reference as r


class WeightTests(unittest.TestCase):
    def test_reference_fixtures(self):
        fixtures=[[[3.,1.],[2.,1.],[1.,4.]],[[1.,1.],[2.,2.]],
                  [[5.,1.],[3.,1.]],[[5.,1.,1.],[1.,5.,1.],[1.,1.,5.]],
                  [[5.,1.,.1],[1.,5.,.1],[2.,2.,.1]],
                  [[1.,1.+1e-10,2.],[2.,2.-1e-10,1.]]]
        for values in fixtures:
            logs=np.log(values); k=logs.shape[1]
            w,report=s.solve(logs,np.ones(k)/k)
            _,objective=r.optimum(logs); checked=r.evaluate(logs,w)
            self.assertTrue(report['qualified'],report)
            self.assertLess(abs(report['objective']-objective),1e-8)
            self.assertLess(abs(checked['gap']-report['gap']),report['allowance'])
            np.testing.assert_allclose(checked['gradient'],report['gradient'],atol=2e-6,rtol=0)

    def test_row_shift_permutation_and_extreme_logs(self):
        logs=np.array([[0.,-1000.],[-1000.,0.],[0.,-1000.]])
        w,a=s.solve(logs,[.5,.5]); z,b=s.solve(logs[:,::-1]+np.array([10000.,-10000.,50.])[:,None],[.5,.5])
        np.testing.assert_allclose(w,z[::-1],atol=1e-8)
        self.assertTrue(a['qualified'] and b['qualified'])
        reference=r.evaluate(logs,w)
        self.assertLess(abs(reference['objective']-a['objective']),1e-8)

    def test_invalid_rows_and_weights(self):
        for logs in ([[float('-inf'),float('-inf')]],[[float('nan'),0]],[]):
            with self.assertRaises(ValueError):s.solve(logs,[.5,.5])
        with self.assertRaises(ValueError):s.solve([[0.,0.]],[.1,.1])

    def test_local_and_global_limits(self):
        logs=np.log([[2.,1.],[1.,3.]])
        _,report=s.solve(logs,[.5,.5],max_calls=1)
        self.assertFalse(report['qualified'])
        self.assertEqual(report['status'],'WeightCallLimit')
        def fail(kind,n):raise RuntimeError('GlobalBudget')
        with self.assertRaisesRegex(RuntimeError,'GlobalBudget'):s.solve(logs,[.5,.5],charge=fail)
        _,report=s.solve(logs,[.5,.5],max_work=12)
        self.assertFalse(report['qualified'])


if __name__=='__main__':unittest.main()
