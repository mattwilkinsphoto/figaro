import unittest
from unittest.mock import patch
from nonlinear_proposal_analysis import parse,modes,COLS

class NonlinearAnalysisTest(unittest.TestCase):
    def test_incomplete_input_rejected(self):
        with self.assertRaises(AssertionError):parse(__file__,True)
    def test_fixed_inventory(self):
        self.assertEqual(modes('mono',False),['bootstrap','cubature','ekf','aux','bad'])
        self.assertIn('manual',modes('symmetric',True))
        self.assertIn('exact',modes('affine',True))
        self.assertEqual(len(COLS),26)
    def test_duplicate_rows_rejected(self):
        row=['mono','bootstrap','64','90001','0','ok']+['0']*19+['']
        row[11]=row[12]='64'
        text='NP_COLUMNS\t'+'\t'.join(COLS)+'\n'+('NP_ROW\t'+'\t'.join(row)+'\n')*2
        with patch('pathlib.Path.read_text',return_value=text):
            with self.assertRaises(AssertionError):parse('unused',True)
    def test_nonfinite_completed_row_rejected(self):
        row=['mono','bootstrap','64','90001','0','ok']+['0']*19+[''];row[6]='NaN'
        text='NP_COLUMNS\t'+'\t'.join(COLS)+'\nNP_ROW\t'+'\t'.join(row)
        with patch('pathlib.Path.read_text',return_value=text):
            with self.assertRaises(AssertionError):parse('unused',True)

if __name__=='__main__':unittest.main()
