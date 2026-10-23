"""Synthetic analysis-unit inputs only; not candidate acceptance data."""
import unittest
from unittest.mock import patch
from multicomponent_analysis import *

def text_rows(change=None):
    rows=[]
    for name in NAMES:
        for mode in modes(name,False):
            for seed in seeds(False):
                for t in range(len(spec(name)['observations'])):
                    r={k:0 for k in COLS};r.update(name=name,mode=mode,n=64,seed=seed,t=t,status='ok',
                        draws=64*(t+1),likelihoods=64*(t+1),r0=.5,r1=.5,ess=64,nanos=100*(t+1),fitStatus='none',error='')
                    if mode=='pilot':r.update(pilotDraws=512*(t+1),pilotLikelihoods=512*(t+1),pilotAncestors=512*t,fitStatus='Fitted')
                    if change:change(r)
                    rows.append('MC_ROW\t'+'\t'.join(str(r[k]) for k in COLS))
    return 'MC_COLUMNS\t'+'\t'.join(COLS)+'\n'+'\n'.join(rows)+'\n'

class MulticomponentAnalysisTest(unittest.TestCase):
    def test_complete_inventory_and_counts(self):
        with patch('pathlib.Path.read_text',return_value=text_rows()):
            rows,digest=parse('unused',False)
            self.assertEqual(len(rows),5104);self.assertEqual(len(digest),64)
    def test_missing_duplicate_and_nonfinite_rejected(self):
        text=text_rows()
        for bad in (text.rsplit('\n',2)[0],text+text.splitlines()[1]+'\n',text.replace('\tok\t0\t','\tok\tNaN\t',1)):
            with patch('pathlib.Path.read_text',return_value=bad):
                with self.assertRaises(AssertionError):parse('unused',False)
    def test_discarded_pilot_does_not_count_as_production(self):
        with patch('pathlib.Path.read_text',return_value=text_rows(lambda r:r.update(draws=r['draws']+512) if r['mode']=='pilot' else None)):
            with self.assertRaises(AssertionError):parse('unused',False)
    def test_failed_calibration_is_unavailable_not_favorable(self):
        def change(r):
            if r['mode']=='pilot' and r['seed']==110001:
                r.update(status='error' if r['t']==0 else 'unavailable',error='refusal',fitStatus='InsufficientPilot',
                         draws=0,likelihoods=0,pilotDraws=512,pilotLikelihoods=512,pilotAncestors=0)
                r.update({k:float('nan') for k in FLOATS})
        with patch('pathlib.Path.read_text',return_value=text_rows(change)),patch('pathlib.Path.read_bytes',return_value=b'synthetic'):
            cal=calibrate('unused')
            self.assertTrue(all(not d['available'] and d['n']==4096 for d in cal['details'] if d['candidate']=='pilot'))
            self.assertTrue(all(d['available'] for d in cal['details'] if d['candidate']=='branch'))
    def test_no_restart_after_terminal_error(self):
        def change(r):
            if r['mode']=='pilot' and r['t']==0:
                r.update(status='error',error='refusal');r.update({k:float('nan') for k in FLOATS})
        with patch('pathlib.Path.read_text',return_value=text_rows(change)):
            with self.assertRaises(AssertionError):parse('unused',False)
    def test_loss_does_not_double_count_positive_or_tail(self):
        truth=[dict(zip(METRICS,[0,1,.5,.1,.5,.5,0]))]
        values=np.array([[[0,1,.9,.8,.5,.5,0]],[[1,1,.5,.1,.5,.5,0]]])
        loss,errors=loss_components(values,truth)
        self.assertEqual(loss.tolist(),[0,1]);self.assertGreater(errors[0,3],0)
    def test_seeds_and_modes_are_frozen(self):
        self.assertEqual((min(seeds(True)),max(seeds(True)),len(seeds(True))),(120001,120256,256))
        self.assertIn('manual',modes('merging',True));self.assertIn('exact',modes('affine',True))
        self.assertEqual(len(COLS),36)
if __name__=='__main__':unittest.main()
