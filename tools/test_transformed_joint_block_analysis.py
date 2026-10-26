import base64,gzip,unittest
import numpy as np
from transformed_joint_block_analysis import check_trace,unique,score,decide,ARMS,NAMES
from tied_quantile_audit import summary

class AnalysisControls(unittest.TestCase):
    def test_trace_and_wrong_diagnostics(self):
        x=np.random.default_rng(7412202301).normal(size=(4,64))
        encoded=base64.b64encode(gzip.compress(x.astype('>f8').tobytes())).decode()
        values=summary(x)[0];actual=check_trace(encoded,values,64)[0]
        np.testing.assert_array_equal(actual,x)
        bad=values.copy();bad[0]+=.01
        with self.assertRaises(AssertionError):check_trace(encoded,bad,64)
        with self.assertRaises(AssertionError):check_trace(encoded,values,63)
    def test_duplicates(self):
        seen={};unique(seen,'x',1)
        with self.assertRaises(AssertionError):unique(seen,'x',1)
    def test_score_keeps_missing_loss(self):
        self.assertEqual(score([{'seconds':2,'loss':{}},{'seconds':4,'loss':{'q':0}}],{'q'}),1.5)
    def fixture(self):
        ref={'fixtures':{f:{'queries':{'q':{}},'derived':{},'ordinary':i<4} for i,f in enumerate(NAMES)}}
        rows=[dict(fixture=f,arm=a,seedIndex=i,seconds=1,loss={'q':.5 if a=='candidate' else 1.},accurate=True)
          for f in NAMES for a in ARMS for i in range(8)]
        return ref,rows
    def test_all_gates_and_incomplete(self):
        ref,rows=self.fixture();self.assertTrue(all(decide(rows,ref,8,True,True)[0].values()))
        self.assertFalse(all(decide(rows[:-1],ref,8,False,True)[0].values()))
        self.assertFalse(all(decide(rows,ref,8,True,False)[0].values()))
    def test_accuracy_and_utility_not_dropped(self):
        ref,rows=self.fixture()
        next(r for r in rows if r['fixture']=='product8' and r['arm']=='candidate')['accurate']=False
        self.assertFalse(decide(rows,ref,8,True,True)[0]['ordinaryAccuracy'])
        for r in rows:
            if r['fixture']=='coupledSupport' and r['arm']=='candidate':r['seconds']=2
        self.assertFalse(decide(rows,ref,8,True,True)[0]['ordinaryUtility'])
    def test_zero_comparator_inconclusive(self):
        ref,rows=self.fixture()
        for r in rows:
            if r['arm']=='default':r['loss']['q']=0
        self.assertFalse(decide(rows,ref,8,True,True)[0]['ordinaryUtility'])

if __name__=='__main__':unittest.main()
