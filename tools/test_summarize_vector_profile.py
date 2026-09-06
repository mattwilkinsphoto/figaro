import csv
import io
import unittest
from summarize_vector_profile import FIELDS, METRICS, load


class VectorProfileSummaryTest(unittest.TestCase):
    def rows(self):
        metrics = {name: 0 for name in METRICS}
        metrics.update(eventSpanSeconds=1, gcCount=1, heapSummaryCount=2,
                       gcPauseSeconds=0.01, longestGcPauseSeconds=0.01,
                       maxObservedHeapBytes=1024, maxObservedAfterGcHeapBytes=512)
        return [["vectorProfile", "metric", name, "", "", 1, value] for name, value in metrics.items()] + [
            ["vectorProfile", "allocation", "diagnostics", "[D", "figaro.Summary.fft:15", 2, 2048],
            ["vectorProfile", "execution", "sampling", "figaro.Sampler.run", "figaro.Sampler.run:10", 3, 3]]

    def encode(self, rows):
        out = io.StringIO(); writer = csv.writer(out)
        writer.writerow(FIELDS); writer.writerows(rows)
        return out.getvalue()

    def test_complete_and_unrelated_log_lines(self):
        self.assertEqual(len(load("build message\n" + self.encode(self.rows()))), 10)

    def test_missing_duplicate_and_empty(self):
        rows = self.rows()
        for data in ([], rows[1:], rows + [rows[0]], rows[:-1], rows[:-2]):
            with self.assertRaises(ValueError): load(self.encode(data))

    def test_corrupt_or_unsanitized_aggregates(self):
        for index, value in ((1, "unknownKind"), (2, "invalid"), (3, "C:/Users/name"), (4, "a file"),
                             (5, 0), (6, "NaN"), (6, -1), (6, 2)):
            rows = self.rows(); rows[-1][index] = value
            with self.assertRaises(ValueError): load(self.encode(rows))

    def test_loss_and_inconsistent_heap_pause(self):
        for metric, value in (("lostBytes", 1), ("eventSpanSeconds", 0), ("longestGcPauseSeconds", 2),
                              ("maxObservedAfterGcHeapBytes", 2048), ("gcCount", 0)):
            rows = self.rows()
            next(row for row in rows if row[2] == metric)[6] = value
            with self.assertRaises(ValueError): load(self.encode(rows))


if __name__ == "__main__":
    unittest.main()
