"""Evidence parser must not silently accept cherry-picked, duplicated or corrupt runs."""
from pathlib import Path
import unittest
from summarize_statistical_validation import parse


class StatisticalValidationEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = (Path(__file__).resolve().parents[1] / 'docs/statistical-validation-results.csv').read_text(encoding='utf-8')

    def test_complete_evidence(self):
        rows, _ = parse(self.text)
        self.assertEqual(len(rows), 915)

    def test_complete_five_backend_evidence(self):
        text = (Path(__file__).resolve().parents[1] / 'docs/rng-statistical-results.csv').read_text(encoding='utf-8')
        self.assertEqual(len(parse(text)[0]), 2250)
        with self.assertRaises(ValueError):
            parse('\n'.join(text.splitlines()[:-1]))

    def test_missing_or_duplicate_runs_rejected(self):
        lines = self.text.splitlines()
        for altered in ('\n'.join(lines[:-1]), self.text+'\n'+lines[1]):
            with self.assertRaises(ValueError):
                parse(altered)

    def test_nonfinite_or_false_accuracy_rejected(self):
        lines = self.text.splitlines()
        fields = lines[1].split(',')
        fields[10] = 'nan'
        with self.assertRaises(ValueError):
            parse('\n'.join([lines[0], ','.join(fields), *lines[2:]]))
        fields = lines[1].split(',')
        fields[13] = 'false' if fields[13] == 'true' else 'true'
        with self.assertRaises(ValueError):
            parse('\n'.join([lines[0], ','.join(fields), *lines[2:]]))


if __name__ == '__main__':
    unittest.main()
