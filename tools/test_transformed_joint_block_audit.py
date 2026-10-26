import math,unittest
import numpy as np
from transformed_joint_block_audit import reference,audit_text

class Controls(unittest.TestCase):
    def fixture(self):
        lines=[]
        for a in (-3.,-.5,1.):
            for b in (-4.,0.,3.):
                for h in (-.4,.3):
                    x=math.exp(a);p=1/(1+math.exp(-b));nx=math.exp(a+h);np_=1/(1+math.exp(-b+2*h))
                    q,m=reference(x,p,nx,np_)
                    lines.append('\t'.join(map(str,('GRAPH',x,p,nx,np_,q,m))))
        for d in (2,4,8,2):
            c=np.full((d,d),.1);np.fill_diagonal(c,1.)
            lines.append('\t'.join(map(str,['FACTOR',d,*c.ravel(),*np.linalg.cholesky(c).ravel()])))
        return lines+['COMPLETE\t18']
    def test_valid(self):self.assertEqual(audit_text('\n'.join(self.fixture()))['status'],'pass')
    def test_jacobian_negatives(self):
        for mult in (0,2,-1):
            lines=self.fixture();f=lines[0].split('\t');f[5]=str(float(f[5])*mult);lines[0]='\t'.join(f)
            with self.assertRaises(AssertionError):audit_text('\n'.join(lines))
    def test_wrong_prior(self):
        lines=self.fixture();f=lines[0].split('\t');f[6]=str(float(f[6])+1);lines[0]='\t'.join(f)
        with self.assertRaises(AssertionError):audit_text('\n'.join(lines))
    def test_inventories(self):
        for lines in (self.fixture()[1:],self.fixture()+self.fixture()[:1],self.fixture()[:-1],self.fixture()[:-2]+['COMPLETE\t18']):
            with self.assertRaises(AssertionError):audit_text('\n'.join(lines))
    def test_wrong_factor(self):
        lines=self.fixture();f=lines[18].split('\t');f[-1]='9';lines[18]='\t'.join(f)
        with self.assertRaises(AssertionError):audit_text('\n'.join(lines))

if __name__=='__main__':unittest.main()
