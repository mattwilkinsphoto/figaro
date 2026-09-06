import contextlib
import csv
import io
import unittest

from summarize_reliability import load, summarize, STRATEGIES, RULES, QUERIES


class ReliabilitySummaryTest(unittest.TestCase):
    def fixture(self):
        rows = []
        for strategy in STRATEGIES:
            for rule in RULES:
                for method, draws in (("fixed", 2000), ("fixed", 12000), ("fixed", 48000), ("stopped", 2000)):
                    for query in QUERIES:
                        rows.append(dict(reliability="reliability", strategy=strategy, rule=rule,
                            round="0", method=method, draws=str(draws), query=query, truth="0",
                            estimate="0", error="0", fullWidth="2", covered="true", criteriaMet="true",
                            allCriteriaMet="true", reason="FixedBudget" if method == "fixed" else "PrecisionReached",
                            failureReasons="", batchMcse="0.1", spectralMcse="0.2", iidOracleCovered="true"))
        return rows

    def encode(self, rows):
        stream = io.StringIO()
        writer = csv.DictWriter(stream, rows[0].keys(), quoting=csv.QUOTE_ALL)
        writer.writeheader()
        writer.writerows(rows)
        return stream.getvalue()

    def test_complete_and_sharded_input(self):
        rows = self.fixture()
        fields, measured, runs = load([self.encode(rows[:120]), self.encode(rows[120:])], 1, 48000)
        self.assertEqual(len(runs), 12)
        self.assertEqual(len(measured), 240)
        stream = io.StringIO()
        with contextlib.redirect_stdout(stream):
            summarize(runs, 1, 48000)
        self.assertIn("iid / mcse-floor: 1/1", stream.getvalue())

    def test_missing_duplicate_and_unexpected_runs_fail(self):
        rows = self.fixture()
        for changed in (rows[1:], rows + [rows[0]], [dict(rows[0], round="1")] + rows[1:]):
            with self.assertRaises(ValueError):
                load([self.encode(changed)], 1, 48000)
        with self.assertRaises(ValueError):
            load(["no audit"], 1, 48000)

    def test_corrupt_measurements_and_decisions_fail(self):
        for changes in (dict(error="1"), dict(covered="false"), dict(reason="MaxDrawsReached"),
                        dict(estimate="NaN"), dict(draws="3000"), dict(allCriteriaMet="false")):
            rows = self.fixture()
            rows[0].update(changes)
            with self.assertRaises(ValueError):
                load([self.encode(rows)], 1, 48000)

    def test_floor_cannot_narrow_or_change_trace_or_hide_failed_reason(self):
        for changes in (dict(fullWidth="1"), dict(estimate="0.1", error="0.1"), dict(failureReasons="WidthTooLarge")):
            rows = self.fixture()
            next(r for r in rows if r["rule"] == "mcse-floor").update(changes)
            with self.assertRaises(ValueError):
                load([self.encode(rows)], 1, 48000)
        rows = self.fixture()
        for row in rows:
            if row["strategy"] == "iid" and row["rule"] == "legacy-batch" and row["method"] == "stopped":
                row["draws"] = "4000"
        with self.assertRaisesRegex(ValueError, "stopped earlier"):
            load([self.encode(rows)], 1, 48000)

    def test_pilot_rejection_keeps_attempt_denominator(self):
        rows = self.fixture()
        rejected = []
        for rule in RULES:
            row = dict(next(r for r in rows if r["strategy"] == "calibrated" and r["rule"] == rule))
            row.update(method="rejected", query="", reason="PilotRejected", draws="0")
            rejected.append(row)
        rows = [r for r in rows if r["strategy"] != "calibrated"] + rejected
        _, _, runs = load([self.encode(rows)], 1, 48000)
        stream = io.StringIO()
        with contextlib.redirect_stdout(stream):
            summarize(runs, 1, 48000)
        self.assertIn("calibrated / mcse-floor | 1/1 | 0 / 0 / 0 of 0 | 0/0 | 0/1 | unavailable", stream.getvalue())
        rejected[1]["strategy"] = "manual"
        with self.assertRaises(ValueError):
            load([self.encode(rows)], 1, 48000)


if __name__ == "__main__":
    unittest.main()
