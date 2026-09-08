"""JVM prototype provenance, independent oracle freshness and complete timing evidence."""
import re
import unittest
from pathlib import Path
from gvm_scalar_cell_oracles import scala_source
from summarize_gvm_scalar_tail import parse

ROOT = Path(__file__).resolve().parents[1]
TESTS = ROOT/'Figaro/src/test/scala/com/cra/figaro/test/modernization'


class ScalarTailJvmTest(unittest.TestCase):
    def test_candidate_only_changes_radius_policy(self):
        current = (ROOT/'Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesScalarBhattacharyya.scala').read_text()
        candidate = (TESTS/'GvmScalarTailCandidate.scala').read_text()
        candidate = '\n'.join(candidate.splitlines()[2:])+'\n'
        candidate = candidate.replace('package com.cra.figaro.test.modernization\n\nimport com.cra.figaro.library.atomic.continuous.GaussVonMisesDistribution',
                                      'package com.cra.figaro.library.atomic.continuous')
        candidate = candidate.replace('private[modernization] object GvmScalarTailCandidate', 'object GaussVonMisesScalarBhattacharyya')
        candidate = candidate.replace('var minimum=', 'val minimum=')
        candidate = re.sub(r'      // BEGIN TEST-ONLY CELL-BOUND CANDIDATE\n.*?      // END TEST-ONLY CELL-BOUND CANDIDATE\n', '', candidate, flags=re.S)
        self.assertEqual(candidate, current)

    def test_candidate_runs_every_public_contract_test(self):
        current = (TESTS/'GaussVonMisesScalarBhattacharyyaTest.scala').read_text()
        candidate = (TESTS/'GvmScalarTailCandidateTest.scala').read_text().split('\n', 1)[1]
        candidate = candidate.replace('class GvmScalarTailCandidateTest', 'class GaussVonMisesScalarBhattacharyyaTest')
        candidate = candidate.replace('GvmScalarTailCandidate.', 'GaussVonMisesScalarBhattacharyya.')
        self.assertEqual(candidate, current)

    def test_independent_oracles_are_fresh(self):
        self.assertEqual((TESTS/'GvmScalarCellFixtures.scala').read_text(), scala_source())

    def test_complete_evidence(self):
        self.assertEqual(sum(len(r['timings']) for r in parse(self.evidence())), 210)

    @staticmethod
    def evidence():
        return (ROOT/'docs/GVM_SCALAR_TAIL_JVM_RUNS.txt').read_text()

    def test_missing_duplicate_or_incomplete_records(self):
        text = self.evidence()
        row = next(x for x in text.splitlines() if x.startswith('GVM_TAIL_JVM_TIMING'))
        for changed in (text.replace(row+'\n', '', 1), text.replace(row, row+'\n'+row, 1),
                        text.rsplit('GVM_TAIL_JVM_COMPLETE', 1)[0]):
            with self.assertRaises(ValueError): parse(changed)

    def test_invalid_or_inconsistent_accuracy_work_and_timing(self):
        text = self.evidence()
        for pattern, replacement in ((r'afterError=\S+', 'afterError=1e-4'), (r'afterWork=\d+', 'afterWork=50001'),
                                     (r'afterWork=712', 'afterWork=713'), (r'afterRadius=\S+', 'afterRadius=17'),
                                     (r'nsPerCall=\S+', 'nsPerCall=NaN'), (r'nsPerCall=\S+', 'nsPerCall=0'),
                                     (r'batch=\d+', 'batch=3')):
            with self.assertRaises(ValueError): parse(re.sub(pattern, replacement, text, count=1))

    def test_reused_jvm_or_unknown_protocol(self):
        text = self.evidence()
        pids = re.findall(r'pid=(\d+)', text)
        with self.assertRaises(ValueError): parse(text.replace('pid='+pids[1], 'pid='+pids[0]))
        with self.assertRaises(ValueError): parse(text.replace('rounds=7', 'rounds=5', 1))
        with self.assertRaises(ValueError): parse(text.replace('GVM_TAIL_JVM_TIMING', 'GVM_TAIL_JVM_UNKNOWN', 1))


if __name__ == '__main__':
    unittest.main()
