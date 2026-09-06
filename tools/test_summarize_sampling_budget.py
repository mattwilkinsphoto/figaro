import csv
import io
import unittest

from summarize_sampling_budget import FIELDS, TARGETS, METHODS, QUERIES, TRUTHS, load


class BudgetSummaryTest(unittest.TestCase):
    def fixture(self):
        rows = []
        for t in TARGETS:
            for m in METHODS:
                for record, budget in (("fixed", 25000), ("fixed", 50000), ("fixed", 100000), ("stopped", 25000)):
                    for q, truth in zip(QUERIES, TRUTHS[t]):
                        rows.append(dict(zip(FIELDS, map(str, ("budgetResearch", t, m, 0, 812031, record, budget,
                            budget // 10, budget // 2, 4 * budget, 10000, 20000, "Ok",
                            "FixedBudget" if record == "fixed" else "PrecisionReached", q, truth, truth, 1,
                            1000, "true", "true", "")))))
        return rows

    def encode(self, rows):
        stream = io.StringIO()
        writer = csv.DictWriter(stream, FIELDS, quoting=csv.QUOTE_ALL)
        writer.writeheader(); writer.writerows(rows)
        return stream.getvalue()

    def test_complete_shards(self):
        rows = self.fixture()
        loaded, groups = load([self.encode(rows[:100]), self.encode(rows[100:])], 1, 100000)
        self.assertEqual(len(loaded), 400)
        self.assertEqual(len(groups), 80)

    def test_rejects_missing_duplicate_corrupt_records(self):
        rows = self.fixture()
        bad_cases = [rows[1:], rows + [rows[0]]]
        for change in (dict(seed="1"), dict(truth="999"), dict(covered="false"), dict(covered="invalid"),
                       dict(evaluations="999999"), dict(fullWidth="inf"), dict(meanEss="-1"),
                       dict(fullWidth="NaN"), dict(meanEss="NaN"), dict(meanEss="100"),
                       dict(failureReasons="InvalidRHat"), dict(availableDraws="0")):
            bad_cases.append([dict(rows[0], **change)] + rows[1:])
        for bad in bad_cases:
            with self.assertRaises(ValueError):
                load([self.encode(bad)], 1, 100000)

    def test_rejects_late_selected_stop(self):
        rows = self.fixture()
        for row in rows:
            if row["record"] == "stopped":
                fixed = next(r for r in rows if r["record"] == "fixed" and r["budgetPerChain"] == "100000"
                             and all(r[k] == row[k] for k in ("target", "sampler", "query")))
                row.update({k: v for k, v in fixed.items() if k not in ("record", "reason")})
        with self.assertRaises(ValueError):
            load([self.encode(rows)], 1, 100000)

    def test_retains_explicit_failures_without_fabricated_estimates(self):
        rows = self.fixture()
        failed = [r for r in rows if r["target"] == "gaussian" and r["sampler"] == "gpss"]
        for row in failed:
            row.update(status="NumericalFailure", reason="RunFailure", estimate="NaN", fullWidth="NaN", meanEss="NaN",
                       covered="false", criteriaMet="false", failureReasons="NumericalFailure")
            if row["record"] == "stopped":
                row.update(budgetPerChain="100000", drawsPerChain="10000", availableDraws="50000", evaluations="400000")
        loaded, _ = load([self.encode(rows)], 1, 100000)
        self.assertEqual(len(loaded), 400)
        failed[0]["estimate"] = "0"
        with self.assertRaises(ValueError):
            load([self.encode(rows)], 1, 100000)


if __name__ == "__main__":
    unittest.main()
