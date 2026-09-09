import unittest
from pathlib import Path
from summarize_vector_importance import load, validate, summaries


class VectorImportanceEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = (Path(__file__).resolve().parents[1] / 'docs/vector-importance-results.csv').read_text(encoding='utf-8')
        cls.rows = load(cls.text)

    def test_complete_saved_grid(self):
        self.assertEqual(len(self.rows), 450)
        self.assertEqual(validate(self.rows), 210)
        result = list(summaries(self.rows))
        self.assertEqual(sum(r['allAccurate'] for r in result), 210)
        self.assertEqual(sum(r['dangerRuns'] for r in result), 2)

    def test_incomplete_duplicate_and_smoke_rejected(self):
        for rows in (self.rows[:-1], self.rows + [self.rows[0]], self.rows[:15]):
            with self.assertRaises(ValueError): validate(rows)

    def test_corrupted_evidence_rejected(self):
        for field, value in (('reference', '123'), ('tolerance', '10'), ('mean', 'nan'),
                             ('error', '10'), ('accurate', 'false'), ('pilotCalls', '0'),
                             ('productionCalls', '9999'), ('fit', 'Success'),
                             ('health', 'NotRun'), ('seconds', '0'), ('ess', '10001')):
            rows = [dict(r) for r in self.rows]
            rows[0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError): validate(rows)

    def test_refusal_cannot_publish_estimate(self):
        rows = [dict(r) for r in self.rows]
        rows[0].update(fit='InsufficientPilot', productionCalls='0', health='NotRun')
        with self.assertRaises(ValueError): validate(rows)

    def test_log_envelope_and_metadata(self):
        log = 'noise\n' + '\n'.join('VI,' + line for line in self.text.splitlines()) + '\nnoise'
        self.assertEqual(load(log), self.rows)
        rows = [dict(r) for r in self.rows]
        rows[1]['seconds'] = '123'
        with self.assertRaises(ValueError): validate(rows)


if __name__ == '__main__': unittest.main()
