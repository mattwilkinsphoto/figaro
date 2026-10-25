"""Confirmation harness controls; no fresh confirmation data are generated."""
import copy,math,unittest
from unittest.mock import patch
import numpy as np
import gvm_joint_confirm_study as run
import gvm_joint_confirm_audit as audit
from gvm_joint_confirm_stats import report,bounds


def fixture():
    cfg=dict(dimensions=[2],families=['curved'],components=[2],seeds=list(range(8)),arms=['baseline','joint'],
        repetitions=3,alpha=.05,comparisons=21,qualifiedRate=.95,completionGain=.2,maximumLostCompletions=0,
        scoreMargin=.02,maximumCostRatio=1.)
    rows=[]
    for d,f,k,s,r,p,a in run.schedule(cfg):
        rows.append(dict(d=d,family=f,k=k,seed=s,repeat=r,position=p,arm=a,error=None,
            wallSeconds=(2 if a=='baseline' else 1)*(r+1),cpuSeconds=.5,testScore=0.,
            fit=dict(status='Stationary' if a=='joint' or s%2==0 else 'BudgetExhausted',totalWork=100,seconds=.1)))
    return cfg,rows


class ConfirmationControls(unittest.TestCase):
    def test_inventory_rejects_missing_duplicate_and_order(self):
        cfg,rows=fixture();audit.inventory(rows,cfg)
        for bad in (rows[:-1],rows+[rows[0]]):
            with self.assertRaises(ValueError):audit.inventory(bad,cfg)
        rows[0]['position']=1-rows[0]['position']
        with self.assertRaises(ValueError):audit.inventory(rows,cfg)

    def test_nonfinite_negative_and_zero_timing(self):
        cfg,rows=fixture()
        for value in (float('nan'),float('inf'),-1,0):
            bad=copy.deepcopy(rows);bad[0]['wallSeconds']=value
            with self.assertRaises(ValueError):audit.inventory(bad,cfg)
        for value in (-1,float('nan')):
            bad=copy.deepcopy(rows);bad[0]['cpuSeconds']=value
            with self.assertRaises(ValueError):audit.inventory(bad,cfg)

    def test_failed_costs_not_omitted_and_repeats_not_seeds(self):
        cfg,rows=fixture();result=report(rows,cfg)
        self.assertEqual(result['totals']['baseline']['actualWall'],96)
        self.assertEqual(result['totals']['baseline']['sumMedianWall'],32)
        self.assertEqual(result['completed'],dict(baseline=4,joint=8))
        self.assertEqual(result['cells'][0]['score']['n'],8)
        self.assertTrue(all(result['gates'].values()))
        self.assertLess(audit.stats_check(rows,cfg,result),1e-12)
        result['totals']['baseline']['actualWall']/=2
        with self.assertRaises(AssertionError):audit.stats_check(rows,cfg,result)

    def test_missing_score_and_lost_completion_fail(self):
        cfg,rows=fixture();rows[1]['fit']=None;rows[1]['testScore']=None
        result=report(rows,cfg)
        self.assertFalse(result['gates']['scoreNoninferiority'])
        self.assertFalse(result['gates']['noLostCompletions'])
        self.assertFalse(result['gates']['perCellReliability'])

    def test_large_exponentiated_bound_checked_in_log_space(self):
        cfg,rows=fixture();cfg['seeds']=[0,1];rows=[r for r in rows if r['seed'] in cfg['seeds']]
        for r in rows:
            if r['arm']=='joint' and r['seed']==1:r['wallSeconds']*=3
        result=report(rows,cfg)
        self.assertGreater(result['costBound']['ratioUpper'],1e20)
        self.assertLess(audit.stats_check(rows,cfg,result),1e-12)
        result['costBound']['upper']+=1e-5
        with self.assertRaises(AssertionError):audit.stats_check(rows,cfg,result)

    def test_only_seconds_removed_from_repeat_identity(self):
        a=dict(seconds=1,starts=[dict(trace=[1,2],seconds=2)],status='Stationary')
        b=copy.deepcopy(a);b['seconds']=9;b['starts'][0]['seconds']=8
        self.assertEqual(audit.scientific(a),audit.scientific(b))
        b['starts'][0]['trace'][1]=3
        self.assertNotEqual(audit.scientific(a),audit.scientific(b))

    def test_false_stationarity_and_unfinished_search(self):
        fit=dict(status='Stationary',starts=[{}, {}, {}],searchDisposition='AllStartsFinished')
        audit.stationary(fit,dict(maximum=1e-5))
        with self.assertRaises(AssertionError):audit.stationary(fit,dict(maximum=.001))
        fit['searchDisposition']='BudgetExhausted'
        with self.assertRaises(AssertionError):audit.stationary(fit,dict(maximum=1e-5))

    def test_cost_upper_bound_not_point_estimate(self):
        cfg,rows=fixture()
        for r in rows:
            if r['arm']=='joint':r['wallSeconds']*=1 if r['seed']<4 else 3
        result=report(rows,cfg)
        self.assertLess(result['costBound']['geometricMeanRatio'],1)
        self.assertFalse(result['gates']['totalCostNonincrease'])
        with self.assertRaises(ValueError):bounds([1],.05)

    def test_outer_timer_contains_preparation_and_return_or_failure(self):
        cfg=dict(maxWork=20000000,maxSeconds=120,starts=3,baselineMaps=200,prefixMaps=10,jointIterations=200,jointCalls=2000)
        for arm in ('baseline','joint'):
            target='baseline' if arm=='baseline' else 'candidate'
            with patch.object(run.time,'perf_counter_ns',side_effect=[100,1000000100]),patch.object(run.time,'process_time_ns',side_effect=[200,500000200]),patch.object(run.c,'whitening',return_value=(0,1)) as w,patch.object(run.c,'transform',return_value=np.zeros((2,2))),patch.object(getattr(run,target),'fit',return_value={'status':'test'}) as f:
                result=run.measured(np.zeros((2,2)),2,123,arm,cfg)
                self.assertEqual(result['wallSeconds'],1);self.assertEqual(result['cpuSeconds'],.5)
                self.assertIsNone(result['error']);w.assert_called_once();f.assert_called_once()
                self.assertEqual(f.call_args.kwargs['max_work'],20000000)
        with patch.object(run.c,'whitening',side_effect=RuntimeError('injected failure')):
            result=run.measured(np.zeros((2,2)),2,123,'joint',cfg)
            self.assertIsNone(result['fit']);self.assertIn('injected failure',result['error'])
            self.assertGreater(result['wallSeconds'],0)

    def test_full_schedule_balance_and_curved_cases(self):
        cfg=dict(dimensions=[2,3],families=list(run.FAMILIES),components=[2,3],seeds=list(range(1250001,1250009)),repetitions=3,arms=['baseline','joint'])
        order=list(run.schedule(cfg));self.assertEqual(len(order),960)
        for repeat in range(3):
            self.assertEqual(sum(r[4]==repeat and r[5]==0 and r[6]=='baseline' for r in order),80)
        for k in (2,3):self.assertEqual(sum(r[0]==3 and r[1]=='curved' and r[2]==k for r in order),48)


if __name__=='__main__':unittest.main()
