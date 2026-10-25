"""Descriptive-report controls; no modification of frozen confirmation gates."""
import json,unittest
from unittest.mock import patch
from pathlib import Path
import gvm_weight_report as report

class ReportTests(unittest.TestCase):
    def test_incomplete_stationary_fit_is_not_reported_complete(self):
        rows=[]
        for arm in report.ARMS:
            for k in (1,2,3):
                fit=dict(status='WorkBudgetExhausted',diagnostic=dict(maximum=0.),
                    totalWork=10,seconds=1,work=dict(score=10),starts=[])
                rows.append(dict(d=2,family='seam',seed=1,k=k,method=arm,fit=fit,
                                 validationScore=0.,testScore=0.))
        captured=[]
        with patch.object(Path,'read_text',return_value=json.dumps(dict(records=rows))),patch.object(report,'save',side_effect=lambda p,x:captured.append(x)),patch('builtins.print'):
            report.report(Path('unused'),Path('unused-output'))
        payload=captured[0]
        self.assertEqual(payload['arms']['combined']['byK']['3']['completed'],0)
        self.assertEqual(payload['arms']['combined']['byK']['3']['retainedStationary'],1)
        self.assertTrue(all(x['k'] is None for x in payload['validationSelection']))
        self.assertTrue(all(not x['checkpointReportingAvailable'] for x in payload['firstStationaryWork'] if x['arm']=='baseline'))

if __name__=='__main__':unittest.main()
