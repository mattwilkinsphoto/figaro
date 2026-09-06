import csv
import io
import unittest

from summarize_high_dimensional import CASES, FIELDS, QUERIES, TRUTHS, load


class HighDimensionalSummaryTest(unittest.TestCase):
    def fixture(self):
        rows = []
        for d, t, m in CASES:
            for kind, budget in (("fixed", 75000), ("fixed", 150000), ("fixed", 300000), ("stopped", 75000)):
                for q, truth in zip(QUERIES, TRUTHS[t]):
                    rows.append(dict(zip(FIELDS, map(str, ("highDimensional", d, t, m, 0, 1700113, kind, budget,
                        budget // 10, budget // 2, 4 * budget, 10000, "Ok", "FixedBudget" if kind == "fixed" else "PrecisionReached",
                        q, truth, truth, 1, 1000, "true", "true", "")))))
        return rows

    def encode(self, rows):
        stream = io.StringIO()
        writer = csv.DictWriter(stream, FIELDS, quoting=csv.QUOTE_ALL)
        writer.writeheader(); writer.writerows(rows)
        return stream.getvalue()

    def test_complete_disjoint_shards(self):
        rows = self.fixture()
        loaded, groups = load([self.encode(rows[:120]), self.encode(rows[120:])], 1, 300000)
        self.assertEqual(len(loaded), 576)
        self.assertEqual(len(groups), 96)

    def test_missing_duplicate_and_corrupt_data(self):
        rows = self.fixture()
        bad = [rows[1:], rows + [rows[0]]]
        for change in (dict(dimension="2"), dict(seed="0"), dict(truth="999"), dict(covered="false"), dict(criteriaMet="invalid"),
                       dict(evaluations="9999999"), dict(warmupEvaluations="9999999"), dict(fullWidth="NaN"),
                       dict(fullWidth="inf"), dict(meanEss="-1"), dict(meanEss="NaN"), dict(failureReasons="InvalidRHat")):
            bad.append([dict(rows[0], **change)] + rows[1:])
        for data in bad:
            with self.assertRaises(ValueError):
                load([self.encode(data)], 1, 300000)

    def test_selected_stop_must_match_first_success(self):
        rows = self.fixture()
        for row in rows:
            if row["record"] == "stopped":
                row.update(budgetPerChain="300000", drawsPerChain="30000", availableDraws="150000", evaluations="1200000")
        with self.assertRaises(ValueError):
            load([self.encode(rows)], 1, 300000)

    def test_failed_run_is_explicit_not_dropped(self):
        rows = self.fixture()
        failed = [r for r in rows if r["target"] == "gaussian" and r["dimension"] == "8" and r["sampler"] == "gpss"]
        for row in failed:
            row.update(status="SearchExhausted", reason="RunFailure", estimate="NaN", fullWidth="NaN", meanEss="NaN",
                       covered="false", criteriaMet="false", failureReasons="SearchExhausted")
            if row["record"] == "stopped":
                row.update(budgetPerChain="300000", drawsPerChain="30000", availableDraws="150000", evaluations="1200000")
        loaded, _ = load([self.encode(rows)], 1, 300000)
        self.assertEqual(len(loaded), 576)
        failed[0]["estimate"] = "0"
        with self.assertRaises(ValueError):
            load([self.encode(rows)], 1, 300000)


if __name__ == "__main__":
    unittest.main()
