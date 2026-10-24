import copy
import unittest
from expensive_likelihood_relevance import validate


def fixture():
    return ([dict(type='environment')] +
            [dict(type='point', T=t, x=-8+i*.05) for t in (2,6) for i in range(321)] +
            [dict(type='timing', T=t, tangent=a, gbs=g, batch=b, calls=321, ns=1, rhs=1)
             for t in (2,6) for a in (False,True) for g in (False,True) for b in range(7)] +
            [dict(type='checks', checks=7704, failures=0), dict(type='complete')])


class InventoryTest(unittest.TestCase):
    def test_complete(self):
        self.assertEqual(len(validate(fixture())[0]), 642)

    def test_missing_and_duplicate(self):
        for kind in ('missing','duplicate'):
            rows = fixture()
            if kind == 'missing': del rows[1]
            else: rows[2] = copy.deepcopy(rows[1])
            with self.assertRaises(AssertionError): validate(rows)

    def test_failure_and_incomplete(self):
        rows = fixture()
        rows[-2]['failures'] = 1
        with self.assertRaises(AssertionError): validate(rows)
        with self.assertRaises(AssertionError): validate(fixture()[:-1])

    def test_missing_timing(self):
        rows = fixture()
        del rows[643]
        with self.assertRaises(AssertionError): validate(rows)


if __name__ == '__main__': unittest.main()
