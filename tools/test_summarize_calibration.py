import contextlib
import csv
import io
import unittest

from summarize_calibration import load, table


class SummaryTest(unittest.TestCase):
    def fixture(self):
        fields = ["calibration", "geometry", "strategy", "round", "method", "query", "reason",
                  "totalSeconds", "productionSeconds", "pilotSeconds", "covered", "meanEssPerTotalSecond"]
        rows = []
        for geometry in ("independent-2", "correlated-2", "narrow-2", "scaled-2", "correlated-6", "banana-2"):
            d = 6 if geometry == "correlated-6" else 2
            for strategy in ("default", "joint-prior", "manual", "calibrated"):
                for method in ("fixed", "precision"):
                    queries = [f"{p}{i}" for p in ("x", "square") for i in range(d)]
                    rejected = geometry == "correlated-6" and strategy == "calibrated"
                    for query in ([""] if rejected else queries):
                        rows.append(dict(zip(fields, ["calibration", geometry, strategy, "0", method, query,
                            "PilotRejected" if rejected else ("PrecisionReached" if method == "precision" else "FixedBudget"),
                            "1", "0" if rejected else "1", "1" if rejected else "0", "true", "100"])))
        return fields, rows

    def encode(self, fields, rows):
        stream = io.StringIO()
        writer = csv.DictWriter(stream, fields, quoting=csv.QUOTE_ALL)
        writer.writeheader(); writer.writerows(rows)
        return stream.getvalue()

    def test_complete_groups_exclude_warmup_and_keep_rejection_denominators(self):
        fields, rows = self.fixture()
        raw = self.encode(fields, rows + [dict(rows[0], round="-1")])
        _, measured, groups = load(raw, 1)
        self.assertEqual(len(measured), len(rows))
        self.assertEqual(len(groups), 48)
        stream = io.StringIO()
        with contextlib.redirect_stdout(stream):
            table(groups, 1)
        self.assertIn("correlated-6 / calibrated | 1/1 | 0/0 | unavailable | 0/1 | not reached", stream.getvalue())

    def test_missing_duplicate_and_wrong_cost_rows_fail(self):
        fields, rows = self.fixture()
        for changed in (rows[1:], rows + [rows[0]], [dict(rows[0], totalSeconds="2")] + rows[1:]):
            with self.assertRaises(ValueError):
                load(self.encode(fields, changed), 1)

    def test_unexpected_repetition_or_missing_log_fails(self):
        fields, rows = self.fixture()
        with self.assertRaises(ValueError):
            load(self.encode(fields, rows), 2)
        with self.assertRaises(ValueError):
            load("no benchmark data", 1)


if __name__ == "__main__":
    unittest.main()
