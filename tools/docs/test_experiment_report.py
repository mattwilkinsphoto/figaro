"""Standard-library checks; optional PDF-generation dependencies are not needed."""
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[2]

class ExperimentReportTests(unittest.TestCase):
    def test_pdf_matches_source_and_generator(self):
        record=json.loads((ROOT/'docs/reports/FIGARO_LITERATURE_EXPERIMENTS.json').read_text(encoding='utf-8'))
        for field,path in [('source','docs/LITERATURE_EXPERIMENTS_REPORT.md'),
                           ('pdf','docs/reports/FIGARO_LITERATURE_EXPERIMENTS.pdf'),
                           ('generator','tools/docs/build_experiment_report.py'),
                           ('tokenizer','tools/docs/report_tokens.cjs')]:
            self.assertEqual(record[field+'Sha256'],hashlib.sha256((ROOT/path).read_bytes()).hexdigest(),path)
        pdf=(ROOT/'docs/reports/FIGARO_LITERATURE_EXPERIMENTS.pdf').read_bytes()
        self.assertTrue(pdf.startswith(b'%PDF-'))
        self.assertTrue(pdf.rstrip().endswith(b'%%EOF'))

    def test_report_coverage_and_public_context(self):
        report=(ROOT/'docs/LITERATURE_EXPERIMENTS_REPORT.md').read_text(encoding='utf-8')
        self.assertEqual(len(re.findall(r'^### \d+\.\d+ ',report,re.M)),25)
        self.assertIn('## Abstract',report)
        self.assertIn('## Executive summary',report)
        self.assertIn('No promotion',report)
        for name in ['docs/LITERATURE_EXPERIMENTS_REPORT.md','docs/reports/EXPERIMENT_RECORDS.md']:
            body=(ROOT/name).read_text(encoding='utf-8')
            self.assertNotRegex(body,r'(?<![A-Za-z])[A-Za-z]:[\\/]')
            self.assertNotRegex(body,r'(?i)Users[\\/]')

    def test_snapshot_anchors_resolve(self):
        report=(ROOT/'docs/LITERATURE_EXPERIMENTS_REPORT.md').read_text(encoding='utf-8')
        records=(ROOT/'docs/reports/EXPERIMENT_RECORDS.md').read_text(encoding='utf-8')
        ids=re.findall(r'<a id="([^"]+)"></a>',records)
        self.assertEqual(len(ids),len(set(ids)))
        for anchor in re.findall(r'EXPERIMENT_RECORDS\.md#([^ )]+)',report):
            self.assertIn(anchor,ids)

if __name__=='__main__':
    unittest.main()
