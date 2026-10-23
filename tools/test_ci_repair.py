"""Negative controls for repository-only repair/benchmark tooling."""
import copy
import hashlib
import importlib.util
import json
import sys
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
import unittest
from unittest.mock import patch

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).parent/path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

repair=load('repair','sbt-worker-repair/prepare.py')
benchmark=load('benchmark','collection-benchmark/run.py')
linux=load('linux_validation','sbt-worker-repair/linux_validation.py')
sys.path.insert(0,str(Path(__file__).parent/'sbt-worker-repair'))
pilot=load('branch_pilot','sbt-worker-repair/branch_pilot.py')
from pilot_support import check_failure,check_identity
from failure_controls import check_matrix,MODES

class PilotTests(unittest.TestCase):
    def test_gate_scope_does_not_enable_unreviewed_runner_modes(self):
        self.assertIn('branch-only',pilot.check_mode('codex/guided-fixed-budget',False,{}))
        env={'GITHUB_ACTIONS':'true','GITHUB_WORKFLOW':'Java 17, Scala 3, and sbt 2 modernization'}
        self.assertIn('other invocations remain stock',pilot.check_mode('',True,env))
        for branch,gate,environment in [('main',False,env),('',True,{}),
              ('main',True,dict(env,GITHUB_WORKFLOW='publication'))]:
            with self.assertRaises(ValueError):pilot.check_mode(branch,gate,environment)

    def test_workflow_keeps_stock_coverage_and_publication_separate(self):
        root=Path(__file__).resolve().parents[1]
        workflow=(root/'.github/workflows/ci.yml').read_text()
        job=workflow.split('  named-direct-launcher-tests:',1)[1].split('  research-reference-integrity:',1)[0]
        self.assertEqual(job.count('--ci-gate'),1)
        for forbidden in ['continue-on-error','cache: sbt','secrets.','sbt --server']:
            self.assertNotIn(forbidden,job)
        self.assertIn('if: always()',job)
        self.assertIn('persist-credentials: false',job)
        stock=workflow.split('  research-reference-integrity:',1)[1]
        self.assertNotIn('--ci-gate',stock)
        coverage=next(line for line in stock.splitlines() if 'clean; coverage;' in line)
        for name in ['GaussianBlockProposalRegressionTest','VectorSliceSamplerRegressionTest','MultiChainVectorSliceSamplerRegressionTest']:
            self.assertIn(name,coverage)
        self.assertIn('sbt --server --batch',coverage)
        self.assertIn('GuidedFixedBudgetConsumerCheck',stock)
        self.assertIn('com.cra.figaro.test.util.SelectableSetTest',stock) # advisory Performance tag only

    def test_frozen_inventory_uses_committed_source_bytes(self):
        root=Path(__file__).resolve().parents[1]
        inventory=json.loads((root/'tools/sbt-worker-repair/pilot-inventory.json').read_text())
        self.assertEqual(len(inventory['suites']),11)
        self.assertEqual(sum(len(s['tests']) for s in inventory['suites']),131)
        for suite in inventory['suites']:
            committed=subprocess.check_output(['git','show','HEAD:'+suite['source']],cwd=root)
            self.assertEqual(hashlib.sha256(committed).hexdigest(),suite['sourceSha256'],suite['source'])

    def test_compiler_drift_rejected(self):
        pilot.check_pins({'a':'hash'},{'a':'hash'})
        for actual in [{},{'a':'wrong'},{'a':'hash','b':'extra'}]:
            with self.assertRaises(ValueError):pilot.check_pins(actual,{'a':'hash'})

    def test_framework_failure_requires_notification_not_just_exit(self):
        check_failure('ForkTestMain$ForkError SENTINEL_FRAMEWORK_FORK_ERROR')
        for log in ['No tests match','SENTINEL_FRAMEWORK_FORK_ERROR',
                    'ForkTestMain$ForkError SENTINEL_FRAMEWORK_FORK_ERROR All tests passed']:
            with self.assertRaises(ValueError):check_failure(log)

    def test_complete_reports_and_reject_drift(self):
        inventory={'suites':[{'suite':'S','tests':['a','b']}]}
        text='<testsuite name="S" tests="2"><testcase name="a"/><testcase name="b"/></testsuite>'
        pilot.check_reports([ET.fromstring(text)],inventory)
        for reports in [[],[ET.fromstring(text)]*2,[ET.fromstring(text.replace('name="b"','name="a"'))],
          [ET.fromstring(text.replace('name="b"','name="c"'))],
          [ET.fromstring(text.replace('<testcase name="b"/>','<testcase name="b"><skipped/></testcase>'))]]:
            with self.assertRaises(ValueError):pilot.check_reports(reports,inventory)

    def test_in_process_identity_and_full_execution(self):
        jar=Path('isolated/actions.jar').resolve()
        text='\n'.join(['FIGARO_REACT_PATH='+str(jar),'FIGARO_REACT_SHA256='+'a'*64,
          'compiling 120 Scala sources to /fresh/figaro/test-classes ...',
          'Total number of tests run: 131','Suites: completed 11, aborted 0',
          'Tests: succeeded 131, failed 0, canceled 0, ignored 0, pending 0','All tests passed.'])
        with patch('pilot_support.sha',return_value='a'*64):
            pilot.check_log(text,jar)
            for bad in [text.replace('131','130'),text.replace('compiling','cached'),text+'\nException in thread',
                        text.replace('a'*64,'b'*64),text+'\nAll tests passed.',text.replace(str(jar),'/wrong.jar')]:
                with self.assertRaises(ValueError):pilot.check_log(bad,jar)

    def test_notification_matrix_requires_winners(self):
        boot=Path('stock').resolve()
        rows=[dict(mode=n,trials=20,duplicateExceptions=20 if n=='fork-during-exit' else 0,
          firstErrorPreserved=not n.startswith('wrong-') and n!='success-before-fork',
          lateErrorLimitation=n=='success-before-fork') for n in MODES]
        prefix='BINARY '+(boot/pilot.REL).as_uri()+'\n'
        log=prefix+'\n'.join(map(json.dumps,rows))
        check_matrix(log,'stock',boot)
        for bad in [prefix+'\n'.join(map(json.dumps,rows[:-1])),log.replace('"trials": 20','"trials": 19'),
                    log.replace('"firstErrorPreserved": true','"firstErrorPreserved": false')]:
            with self.assertRaises(ValueError):check_matrix(bad,'stock',boot)

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
