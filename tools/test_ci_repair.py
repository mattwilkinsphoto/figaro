"""Negative controls for repository-only repair/benchmark tooling."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).parent/path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

repair=load('repair','sbt-worker-repair/prepare.py')
benchmark=load('benchmark','collection-benchmark/run.py')
linux=load('linux_validation','sbt-worker-repair/linux_validation.py')

class LinuxValidationTests(unittest.TestCase):
    def log(self,jar):
        return '\n'.join(['ans: java.net.URL = '+jar.resolve().as_uri(),
          'Total number of tests run: 102', 'Suites: completed 9, aborted 0',
          'Tests: succeeded 102, failed 0, canceled 0, ignored 0, pending 0',
          'All tests passed.'])

    def test_complete_loaded_repair(self):
        jar=Path('isolated/actions.jar').resolve()
        linux.check_figaro_log(self.log(jar),jar)

    def test_reject_wrong_binary_and_incomplete_outcomes(self):
        jar=Path('isolated/actions.jar').resolve()
        cases=[self.log(Path('stock/actions.jar')),self.log(jar).replace('run: 102','run: 101'),
               self.log(jar).replace('failed 0','failed 1'),self.log(jar)+'\nPromise already completed',
               self.log(jar)+'\nans: java.net.URL = '+jar.as_uri()]
        for text in cases:
            with self.assertRaises(ValueError):linux.check_figaro_log(text,jar)

class RepairTests(unittest.TestCase):
    def test_reject_unreviewed_source(self):
        with self.assertRaisesRegex(ValueError,'Unreviewed'):repair.patch('arbitrary source')

    def test_exact_completion_sites_only(self):
        source='promise.success(0)\npromise.success(exitCode)\n'+('promise.failure(error)\n'*3)
        with patch.object(repair,'SOURCE_SHA',hashlib.sha256(source.encode()).hexdigest()):
            result=repair.patch(source)
        self.assertEqual(result.count('promise.trySuccess('),2)
        self.assertEqual(result.count('promise.tryFailure('),3)
        self.assertNotIn('promise.success(',result)

    def test_refuse_unexpected_site_count(self):
        source='promise.success(0)'
        with patch.object(repair,'SOURCE_SHA',hashlib.sha256(source.encode()).hexdigest()):
            with self.assertRaisesRegex(ValueError,'sites'):repair.patch(source)

class BenchmarkTests(unittest.TestCase):
    def row(self):
        return dict(benchmark='figaro.benchmark.EnumerationBenchmark.enumerate',params={'size':'1000'},
          forks=1,threads=1,measurementIterations=5,warmupIterations=5,
          primaryMetric=dict(scoreUnit='ns/op',rawData=[[10,11,12,13,14]]),
          secondaryMetrics={'gc.alloc.rate.norm':{'score':100}})

    def test_complete_output(self):
        self.assertEqual(benchmark.validate([self.row()],1000,5,5)['nsPerOp'],12)

    def test_missing_and_duplicate(self):
        for rows in [[],[self.row(),self.row()]]:
            with self.assertRaises(ValueError):benchmark.validate(rows,1000,5,5)

    def test_protocol_drift(self):
        for key,value in [('forks',0),('threads',2),('warmupIterations',0),('measurementIterations',4),('params',{'size':'2000'})]:
            row=self.row();row[key]=value
            with self.assertRaises(ValueError):benchmark.validate([row],1000,5,5)

    def test_nonfinite_or_partial(self):
        for values in [[1,2],[1,2,3,4,float('nan')],[0,1,2,3,4]]:
            row=self.row();row['primaryMetric']['rawData']=[values]
            with self.assertRaises(ValueError):benchmark.validate([row],1000,5,5)

    def test_invalid_allocation(self):
        row=self.row();row['secondaryMetrics']['gc.alloc.rate.norm']['score']=float('inf')
        with self.assertRaises(ValueError):benchmark.validate([row],1000,5,5)

if __name__=='__main__':unittest.main()
