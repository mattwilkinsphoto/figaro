import unittest
from summarize_bounded_precision import parse_log, validate


class EvidenceTest(unittest.TestCase):
    def grid(self):
        return parse_log('\n'.join(f'BOUNDED_STUDY,0,{m},{a},500,100,true,true,.01' for m in range(4) for a in range(2)),0)

    def test_complete(self):
        self.assertEqual(len(validate(self.grid(),1,1)),8)

    def test_missing_duplicate(self):
        for rows in (self.grid()[:-1],self.grid()+self.grid()[:1]):
            with self.assertRaises(ValueError): validate(rows,1,1)

    def test_impossible_result(self):
        for field,value in (('error','nan'),('error','.1'),('nanos','0'),('covered','yes')):
            rows=self.grid(); rows[0][field]=value
            with self.assertRaises(ValueError): validate(rows,1,1)

    def test_retain_failures(self):
        rows=self.grid(); rows[0]['covered']='false'; rows[0]['precision']='false'; rows[0]['error']='.3'
        validate(rows,1,1)
