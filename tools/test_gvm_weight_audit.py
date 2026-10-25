"""Negative controls independent of the fitter's scheduler implementation."""
import copy,unittest
from gvm_weight_audit import ledger,inventory
from gvm_weight_study import ARMS

class AuditTests(unittest.TestCase):
    def test_missing_and_duplicate_cases(self):
        cfg=dict(dimensions=[2],families=['seam'],seeds=[9],components=[1],developmentSeed=8)
        rows=[dict(d=2,family='seam',seed=9,k=k,method=a) for k,a in [(1,a) for a in ARMS]+[(4,'wrapped')]]
        inventory(rows,cfg)
        for bad in (rows[:-1],rows+[rows[0]]):
            with self.assertRaises(ValueError):inventory(bad,cfg)
    def test_omitted_work_and_wrong_allocation(self):
        fit=dict(totalWork=5,work=dict(score=5),workByStart=[3,2],allocation='balanced',
            starts=[dict(trace=[0],checkpoints=[dict(work=1)]),dict(trace=[0],checkpoints=[dict(work=1)])],
            schedule=[dict(start=0,before=2,after=4,status='Active'),dict(start=1,before=4,after=5,status='MapLimit')])
        ledger(fit,5)
        bad=copy.deepcopy(fit);bad['work']['score']=4
        with self.assertRaises(ValueError):ledger(bad,5)
        bad=copy.deepcopy(fit);bad['schedule'][1]['start']=0
        with self.assertRaises(ValueError):ledger(bad,5)
        bad=copy.deepcopy(fit);bad['schedule'][1]['before']=3
        with self.assertRaises(ValueError):ledger(bad,5)

if __name__=='__main__':unittest.main()
