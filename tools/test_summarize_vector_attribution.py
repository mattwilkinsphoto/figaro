import copy
import unittest
import summarize_vector_attribution as attribution
import summarize_interleaved_performance as audit
import test_summarize_vector_profile as fixtures


class VectorAttributionTest(unittest.TestCase):
    def inputs(self):
        fixture = fixtures.VectorProfileSummaryTest()
        previous = attribution.profile.load(fixture.encode(fixture.rows()))
        rows = []
        for record in previous.values():
            if record['kind'] != 'metric':
                rows.append(dict(zip(attribution.FIELDS, ['a'*64, record['kind'], record['group'],
                    'samplerObserved' if record['group']=='sampling' else record['group'],
                    record['detail'], record['site'], 'none', 'false', record['count'], record['value']])))
        return rows, previous

    def load(self, rows, previous):
        return attribution.load(audit.encode(rows, attribution.FIELDS), previous)

    def test_complete_and_split_attribution(self):
        rows, previous = self.inputs()
        self.assertEqual(len(self.load(rows, previous)), 2)
        divided = dict(rows[0], count='1', value='1024')
        rows[0] = divided
        rows.append(dict(divided, truncated='true'))
        self.assertEqual(len(self.load(rows, previous)), 3)

    def test_missing_duplicate_and_changed_totals(self):
        rows, previous = self.inputs()
        for changed in [rows[1:], rows+[rows[0]], [dict(rows[0], count='1')]+rows[1:],
                        [dict(rows[0], value='2049')]+rows[1:]]:
            with self.assertRaises(ValueError): self.load(changed, previous)

    def test_privacy_schema_and_identity(self):
        rows, previous = self.inputs()
        for field, value in [('caller','C:/Users/private'), ('site','local file'), ('group','callbackObserved'),
                             ('truncated','maybe'), ('recordingSha256','b'*64), ('count','0'), ('value','NaN')]:
            changed = copy.deepcopy(rows); changed[0][field] = value
            with self.assertRaises(ValueError): self.load(changed, previous)

    def test_execution_counts_and_class_reconciliation(self):
        rows, previous = self.inputs()
        for field, value in [('count','4'), ('detail','another.method'), ('site','another.site:1')]:
            changed = copy.deepcopy(rows); changed[-1][field] = value
            with self.assertRaises(ValueError): self.load(changed, previous)


if __name__ == '__main__': unittest.main()
