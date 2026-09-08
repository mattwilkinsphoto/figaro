from pathlib import Path
import unittest
from summarize_rng_benchmark import parse, PHILOX_ALGORITHMS


class RngBenchmarkEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = (Path(__file__).resolve().parents[1] / 'docs/rng-benchmark-results.csv').read_text(encoding='utf-8')

    def test_complete_evidence(self):
        self.assertEqual(len(parse(self.text)[0]), 250)

    def test_philox_profile(self):
        text = (Path(__file__).resolve().parents[1] / 'docs/rng-philox-benchmark-results.csv').read_text(encoding='utf-8')
        self.assertEqual(len(parse(text, PHILOX_ALGORITHMS)[0]), 100)
        with self.assertRaises(ValueError):
            parse(text)
        lines = text.splitlines()
        row = lines[1].split(',')
        row[6] = 'nan'
        for bad in ('\n'.join(lines[:-1]), text+'\n'+lines[1],
                    '\n'.join([lines[0], ','.join(row), *lines[2:]])):
            with self.assertRaises(ValueError):
                parse(bad, PHILOX_ALGORITHMS)

    def test_missing_duplicate_and_nonfinite_rejected(self):
        lines = self.text.splitlines()
        row = lines[1].split(',')
        row[6] = 'nan'
        for text in ('\n'.join(lines[:-1]), self.text+'\n'+lines[1],
                     '\n'.join([lines[0], ','.join(row), *lines[2:]])):
            with self.assertRaises(ValueError):
                parse(text)


if __name__ == '__main__':
    unittest.main()
