import math
import unittest
import numpy as np
from tied_quantile_audit import summary, rh, check_values, insert_unique, qualifies


class TiedQuantileAuditControls(unittest.TestCase):
    def test_exact_constant_rank_variance(self):
        self.assertTrue(math.isnan(rh(np.full((8, 50), -.6568859528122107))))
        self.assertEqual(rh(np.repeat(np.arange(8)[:, None], 50, axis=1)), math.inf)

    def test_constants_and_separated_constants(self):
        for value in (0., -.6568859528122107):
            s, cuts, constant = summary(np.full((4, 101), value))
            self.assertTrue(constant)
            self.assertTrue(math.isnan(s[2]) and math.isnan(s[4]))
            np.testing.assert_array_equal(cuts, [value, value])
        s, _, constant = summary(np.repeat(np.arange(4)[:, None], 101, axis=1))
        self.assertEqual(s[2], math.inf)
        self.assertFalse(qualifies('x0', s, constant))

    def test_non_tail_change_cannot_hide_inside_tolerance(self):
        s = [0., 1., 1., 1000., 1200., 1000., .01]
        changed = s.copy(); changed[0] = 1e-12
        with self.assertRaisesRegex(AssertionError, 'Non-tail'):
            check_values(s, changed, s)

    def test_wrong_tail_is_detected(self):
        s = [0., 1., 1., 1000., 1200., 1000., .01]
        changed = s.copy(); changed[4] = 1250.
        with self.assertRaises(AssertionError): check_values(s, changed, s)

    def test_duplicate_inventory_is_rejected(self):
        rows = {}; insert_unique(rows, 'key', 1)
        with self.assertRaises(AssertionError): insert_unique(rows, 'key', 2)

    def test_qualification_threshold_not_waived(self):
        s = [0., 1., 1., 1000., 400., 1000., .01]
        self.assertTrue(qualifies('x0', s, False))
        s[4] = math.nextafter(400., 0.)
        self.assertFalse(qualifies('x0', s, False))
        self.assertTrue(qualifies('tail0', s, False))
        self.assertFalse(qualifies('tail0', s, True))


if __name__ == '__main__': unittest.main()
