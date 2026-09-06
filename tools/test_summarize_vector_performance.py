import csv
import io
import itertools
import unittest
from summarize_vector_performance import FIELDS, FIXTURES, METHODS, WORKERS, load


class VectorPerformanceSummaryTest(unittest.TestCase):
    def rows(self):
        rows = []
        for f, m, w, r in itertools.product(FIXTURES, METHODS, WORKERS, range(-2, 1)):
            rows.append(dict(zip(FIELDS, map(str, ("vectorPerformance", f, m, w, r, 420013 + 7919*r,
                100, 20, "Complete", 1, 0.1, 0.5, 0.4, 2, 0, 10000, 100, 100, 100, 100, 1.01, 0.05, 0, "a"*64, "")))))
        return rows

    def encode(self, rows):
        s = io.StringIO(); writer = csv.DictWriter(s, FIELDS)
        writer.writeheader(); writer.writerows(rows)
        return s.getvalue()

    def test_complete_report(self):
        rows = self.rows()
        self.assertEqual(len(load([self.encode(rows[:50]), self.encode(rows[50:])], 1, 100, 20)), 108)

    def test_missing_duplicate_and_corrupt(self):
        rows = self.rows()
        bad = [rows[1:], rows + [rows[0]]]
        for change in (dict(seed="0"), dict(status="Unknown"), dict(wallSeconds="2"), dict(cpuSeconds="inf"),
                       dict(evaluations="400000001"), dict(alignedDraws="101"), dict(minMeanEss="999999"),
                       dict(fingerprint="bad"), dict(warningCoordinates="100"), dict(error="hidden"), dict(maxMeanError="NaN")):
            bad.append([dict(rows[0], **change)] + rows[1:])
        for data in bad:
            with self.assertRaises(ValueError): load([self.encode(data)], 1, 100, 20)

    def test_worker_dependent_output(self):
        rows = self.rows(); rows[0]["fingerprint"] = "b"*64
        with self.assertRaises(ValueError): load([self.encode(rows)], 1, 100, 20)

    def test_failure_retained_not_fabricated(self):
        rows = self.rows()
        rows[0].update(status="Failed", fingerprint="", error="callback", evaluations="-1", alignedDraws="-1", warningCoordinates="-1")
        for field in ("wallSeconds", "constructionSeconds", "samplingSeconds", "diagnosticsSeconds", "minMeanEss", "minBulkEss", "minTailEss", "maxRHat", "maxMeanError"):
            rows[0][field] = "NaN"
        self.assertEqual(len(load([self.encode(rows)], 1, 100, 20)), 108)
        rows[0]["maxMeanError"] = "0"
        with self.assertRaises(ValueError): load([self.encode(rows)], 1, 100, 20)


if __name__ == "__main__":
    unittest.main()
