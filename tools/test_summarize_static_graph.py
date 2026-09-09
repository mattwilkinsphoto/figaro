import unittest
from summarize_static_graph import parse_log,validate


class StaticEvidenceTest(unittest.TestCase):
    def grid(self):
        lines=[]
        for m in range(3):
            a=.9999**(64 if m==2 else 0); truth=a*a/((.01 if m==1 else 1)+a*a)
            for n in (2000,20000):
                for method in range(4): lines.append(f'STATIC_STUDY,0,{m},{n},{method},.5,{truth},1000,100,-123')
        return parse_log('\n'.join(lines),0)

    def test_complete(self): self.assertEqual(len(validate(self.grid(),1,1)),24)

    def test_grid_failures(self):
        for rows in (self.grid()[:-1],self.grid()+self.grid()[:1]):
            with self.assertRaises(ValueError): validate(rows,1,1)

    def test_numeric_failures(self):
        for field,value in (('mean','nan'),('truth','0'),('ess','0'),('nanos','0')):
            rows=self.grid(); rows[0][field]=value
            with self.assertRaises(ValueError): validate(rows,1,1)

    def test_replay_failure(self):
        rows=self.grid(); rows[2]['mean']='.7'
        with self.assertRaises(ValueError): validate(rows,1,1)
