"""Fail-closed audit optimization evidence and frozen-reference provenance checks."""
import re
import hashlib
import unittest
from pathlib import Path
from summarize_gvm_scalar_audit import parse


class AuditEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        cls.text = (cls.root/'docs/GVM_SCALAR_AUDIT_RUNS.txt').read_text()

    def test_complete(self):
        self.assertEqual(sum(len(r['timings']) for r in parse(self.text)), 126)

    def test_frozen_reference_provenance(self):
        source = self.root/'Figaro/src/test/scala/com/cra/figaro/test/modernization/GvmScalarFullSumBaseline.scala'
        self.assertEqual(hashlib.sha256(source.read_text(encoding='utf-8').encode('utf-8')).hexdigest(),
                         '9dbc329dd159c4f021623b70c18579f932d2c579bcbb6518f8f10cc36a88f72a')

    def test_missing_or_duplicate_record(self):
        row = next(x for x in self.text.splitlines() if x.startswith('GVM_AUDIT_TIMING'))
        for text in (self.text.replace(row+'\n', '', 1), self.text.replace(row, row+'\n'+row, 1),
                     self.text.rsplit('GVM_AUDIT_COMPLETE', 1)[0]):
            with self.assertRaises(ValueError): parse(text)

    def test_accuracy_or_work_mismatch(self):
        for before, after in [('identical=true', 'identical=false'), ('evaluations=712', 'evaluations=713')]:
            with self.assertRaises(ValueError): parse(self.text.replace(before, after, 1))
        with self.assertRaises(ValueError): parse(re.sub(r'error=\S+', 'error=1e-4', self.text, count=1))

    def test_invalid_timing(self):
        for value in ('NaN', 'Infinity', '-1', '0'):
            with self.assertRaises(ValueError): parse(re.sub(r'nsPerCall=\S+', 'nsPerCall='+value, self.text, count=1))
        with self.assertRaises(ValueError): parse(re.sub(r'batch=\d+', 'batch=3', self.text, count=1))

    def test_reused_jvm_and_wrong_protocol(self):
        pids = re.findall(r'pid=(\d+)', self.text)
        with self.assertRaises(ValueError): parse(self.text.replace('pid='+pids[1], 'pid='+pids[0]))
        with self.assertRaises(ValueError): parse(self.text.replace('rounds=7', 'rounds=5', 1))


if __name__ == '__main__':
    unittest.main()
