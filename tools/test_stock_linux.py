"""In-memory negative controls for the Linux/server evidence audit."""
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parent/'sbt-worker-repair'))
from stock_linux import check_pid,check_slow,client_command,client_output,persistent_steps,IDENTITY,PID

class ServerEvidenceTest(unittest.TestCase):
    def test_full_tests_use_setting_not_inline_args(self):
        for _,task,code,_ in persistent_steps():
            if code==0:
                self.assertTrue(task.endswith(';testFull'))
                self.assertIn('Tests.Argument("-oD", "-z", "event-")',task)
                self.assertNotIn('testFull --',task)
    def test_negative_controls_reset_filter(self):
        for _,task,code,_ in persistent_steps():
            if code==1:
                self.assertTrue(task.startswith('set Test / testOptions := Seq(Tests.Argument("-oD"));'))
                self.assertNotIn('event-',task)
    def test_client_sequence_keeps_test_last(self):
        self.assertEqual(client_command('testOnly WorkerEvents'),';'+IDENTITY+';'+PID+';testOnly WorkerEvents')
    def test_client_terminal_normalization(self):
        self.assertEqual(check_pid(client_output('\x1b[0JFIGARO_SERVER_PID=123\n')),123)
    def fixture(self):
        r=ET.Element('testsuite',name='ManyTests',tests='40',failures='1',errors='0',skipped='0')
        for i in range(1,41):
            c=ET.SubElement(r,'testcase',name='test '+str(i))
            if i==40:ET.SubElement(c,'failure')
        return r
    def test_same_pid_required(self):self.assertEqual(check_pid('FIGARO_SERVER_PID=123\n',123),123)
    def test_restart_rejected(self):
        with self.assertRaises(ValueError):check_pid('FIGARO_SERVER_PID=124\n',123)
    def test_missing_pid_rejected(self):
        with self.assertRaises(ValueError):check_pid('successful exit')
    def test_ambiguous_pid_rejected(self):
        with self.assertRaises(ValueError):check_pid('FIGARO_SERVER_PID=123\nFIGARO_SERVER_PID=123\n')
    def test_complete_slow_listener(self):check_slow([self.fixture()])
    def test_missing_last_event_rejected(self):
        r=self.fixture();r.remove(r[-1])
        with self.assertRaises(ValueError):check_slow([r])
    def test_final_failure_required(self):
        r=self.fixture();r[-1].remove(r[-1][0])
        with self.assertRaises(ValueError):check_slow([r])
    def test_false_green_count_rejected(self):
        r=self.fixture();r.set('failures','0')
        with self.assertRaises(ValueError):check_slow([r])
    def test_duplicate_identity_rejected(self):
        r=self.fixture();r[0].set('name','test 2')
        with self.assertRaises(ValueError):check_slow([r])

if __name__=='__main__':unittest.main()
