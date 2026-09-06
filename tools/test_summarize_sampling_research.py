import csv
from contextlib import redirect_stdout
import io
import unittest

from summarize_sampling_research import load, summarize, TARGETS, SAMPLERS, QUERIES


class ResearchSummaryTest(unittest.TestCase):
    def fixture(self):
        return [dict(research="research", target=t, sampler=s, round="0", seed="141011", method=m,
                     draws="12000", query=q, truth="0", estimate="0", fullWidth="1", covered="true",
                     criteriaMet="true", reason="FixedBudget" if m == "fixed" else "PrecisionReached",
                     meanEss="1000", evaluationsFullRun="56000", failureReasons="")
                for t in TARGETS for s in SAMPLERS for m in ("fixed", "stopped") for q in QUERIES]

    def encode(self, rows):
        text = io.StringIO()
        writer = csv.DictWriter(text, rows[0].keys(), quoting=csv.QUOTE_ALL)
        writer.writeheader(); writer.writerows(rows)
        return text.getvalue()

    def test_complete_shards(self):
        rows = self.fixture()
        _, loaded, groups = load([self.encode(rows[:100]), self.encode(rows[100:])], 1, 12000)
        self.assertEqual(len(loaded), 150)
        self.assertEqual(len(groups), 30)
        groups["gaussian", "figaro-block", 0, "fixed"][-1]["meanEss"] = "NaN"
        output = io.StringIO()
        with redirect_stdout(output):
            summarize(groups, 1)
        self.assertIn("unavailable", output.getvalue().splitlines()[2])

    def test_missing_duplicate_and_bad_metadata(self):
        rows = self.fixture()
        for bad in (rows[1:], rows + [rows[0]], [dict(rows[0], seed="2")] + rows[1:],
                    [dict(rows[0], covered="false")] + rows[1:], [dict(rows[0], failureReasons="InvalidRHat")] + rows[1:],
                    [dict(rows[0], covered="invalid")] + rows[1:], [dict(rows[0], fullWidth="inf")] + rows[1:],
                    [dict(rows[0], meanEss="-1")] + rows[1:]):
            with self.assertRaises(ValueError):
                load([self.encode(bad)], 1, 12000)

    def test_replayed_cap_and_cost_must_match(self):
        for change in (dict(evaluationsFullRun="999"), dict(estimate="0.01")):
            rows = self.fixture()
            for row in rows:
                if row["target"] == "gaussian" and row["sampler"] == "mess-1" and row["method"] == "stopped":
                    row.update(change)
            with self.assertRaises(ValueError):
                load([self.encode(rows)], 1, 12000)


if __name__ == "__main__":
    unittest.main()
