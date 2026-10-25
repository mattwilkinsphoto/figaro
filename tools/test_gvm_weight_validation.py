"""Additional corruption controls against the unchanged frozen audit."""
import json,unittest
from pathlib import Path
from unittest.mock import patch
import gvm_weight_audit as audit

class CorruptionTests(unittest.TestCase):
    def test_changed_input_is_rejected_before_inventory_or_numerical_checks(self):
        result=dict(records=[],development=False,inputSha256={'input.npz':'original'})
        with patch.object(Path,'read_text',return_value=json.dumps(result)),patch.object(audit,'sha',return_value='corrupted'):
            with self.assertRaises(AssertionError):audit.audit(Path('input'),{},Path('unused'))

    def test_changed_protocol_is_rejected_before_numerical_checks(self):
        result=dict(records=[],development=False,protocolSha256='original',inputSha256={})
        with patch.object(Path,'read_text',return_value=json.dumps(result)),patch.object(audit,'sha',return_value='corrupted'):
            with self.assertRaises(AssertionError):audit.audit(Path('input'),{},Path('unused'),Path('protocol'))

if __name__=='__main__':unittest.main()
