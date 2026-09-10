"""In-memory release guard tests: never access a key, token, or network."""
import io
import json
import unittest
from unittest.mock import patch
from zipfile import ZipFile
import central_release as central
from build_release_bundle import archive_bytes

POM = b'''<project xmlns="http://maven.apache.org/POM/4.0.0">
<groupId>io.github.mattwilkinsphoto</groupId><artifactId>figaro_3</artifactId><version>6.1.0</version>
<name>Figaro</name><description>Probabilistic programming</description><url>https://github.com/mattwilkinsphoto/figaro</url>
<licenses><license><name>Figaro License</name><url>https://github.com/mattwilkinsphoto/figaro/blob/main/LICENSE</url></license></licenses>
<developers><developer><name>Figaro contributors</name></developer></developers>
<scm><url>https://github.com/mattwilkinsphoto/figaro</url><connection>scm:git:https://github.com/mattwilkinsphoto/figaro.git</connection></scm>
</project>'''
ID = '12345678-abcd-1234-abcd-123456789abc'


def fixture(**changes):
    payload = {'maven/'+central.PREFIX+f'figaro_3-{central.VERSION}'+suffix:
               POM if suffix == '.pom' else b'test artifact '+suffix.encode()
               for suffix in ('.jar', '-sources.jar', '-javadoc.jar', '.pom')}
    record = dict(version=central.VERSION, sourceRevision=central.REVISION,
                  candidate=False, dirtySource=False,
                  artifactSha256={name: central.digest(data) for name, data in payload.items()})
    record.update(changes)
    payload['release.json'] = json.dumps(record).encode()
    return archive_bytes(payload)


class CentralTests(unittest.TestCase):
    def test_approved_bundle(self):
        packed = fixture()
        with patch.object(central, 'RELEASE_SHA', central.digest(packed)):
            payload = central.approved_payload(packed)
        self.assertEqual(len(payload), 4)
        self.assertTrue(all(name.startswith('io/github/') for name in payload))
        self.assertFalse(any('fat' in name for name in payload))

    def test_wrong_bundle_digest(self):
        with self.assertRaises(ValueError):
            central.approved_payload(fixture())

    def test_dirty_candidate_revision_and_version_refused(self):
        for changes in ({'candidate': True}, {'dirtySource': True},
                        {'sourceRevision': '0'*40}, {'version': '6.2.0'}):
            packed = fixture(**changes)
            with patch.object(central, 'RELEASE_SHA', central.digest(packed)):
                with self.assertRaises(ValueError):
                    central.approved_payload(packed)

    def test_payload_hash_checked(self):
        packed = fixture(artifactSha256={'maven/'+central.PREFIX+'figaro_3-6.1.0.jar': '0'*64})
        with patch.object(central, 'RELEASE_SHA', central.digest(packed)):
            with self.assertRaises(ValueError):
                central.approved_payload(packed)

    def test_required_pom_fields(self):
        central.check_metadata(POM)
        for bad in (POM.replace(b'6.1.0', b'6.0.0'), POM.replace(b'<name>Figaro</name>', b''),
                    POM.replace(b'<name>Figaro contributors</name>', b'')):
            with self.assertRaises(ValueError):
                central.check_metadata(bad)

    def test_signatures_and_checksums(self):
        data = {'group/file.jar': b'jar', 'group/file.pom': b'pom'}
        signed = central.signed_payload(data, lambda name, content: b'-----BEGIN PGP SIGNATURE-----\ntest')
        self.assertEqual(len(signed), 12)
        for name, value in data.items():
            self.assertEqual(signed[name], value)
            for algorithm in ('md5', 'sha1', 'sha256', 'sha512'):
                self.assertEqual(signed[name+'.'+algorithm], (central.digest(value, algorithm)+'\n').encode())
        with ZipFile(io.BytesIO(archive_bytes(signed))) as archive:
            self.assertEqual(set(archive.namelist()), set(signed))

    def test_missing_signature_refused(self):
        with self.assertRaises(ValueError):
            central.signed_payload({'a.jar': b'x'}, lambda name, value: b'')

    def test_deployment_id_validation(self):
        self.assertEqual(central.deployment_id(ID), ID)
        for value in ('', '../deployments', ID+'?x=y', 'not-a-uuid'):
            with self.assertRaises(ValueError):
                central.deployment_id(value)

    def test_deployment_identity_and_coordinates(self):
        status = dict(deploymentId=ID, deploymentName=central.DEPLOYMENT_NAME,
                      deploymentState='VALIDATED', purls=[central.PURL])
        central.check_deployment(status, ID)
        for change in ({'deploymentId': 'other'}, {'deploymentName': 'other'},
                       {'purls': []}, {'purls': [central.PURL, 'pkg:maven/other/x@1']}):
            with self.assertRaises(ValueError):
                central.check_deployment(dict(status, **change), ID)

    def test_no_authenticated_redirect(self):
        with self.assertRaises(RuntimeError):
            central.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://other.example/')

    def test_post_publication_coordinates_may_be_unavailable(self):
        for state in ('PUBLISHING', 'PUBLISHED'):
            for missing in (None, []):
                central.check_deployment(dict(deploymentId=ID, deploymentName=central.DEPLOYMENT_NAME,
                                              deploymentState=state, purls=missing), ID)
            with self.assertRaises(ValueError):
                central.check_deployment(dict(deploymentId=ID, deploymentName=central.DEPLOYMENT_NAME,
                                              deploymentState=state, purls=['pkg:maven/wrong/x@1']), ID)

    def test_validation_failure_stops_without_publish(self):
        with patch.object(central, 'status_of', return_value={'deploymentState': 'FAILED', 'errors': {'test': ['invalid signature']}}), patch.object(central.time, 'sleep') as sleep, patch('builtins.print'):
            with self.assertRaises(RuntimeError):
                central.wait_status(ID, 'not-a-token', {'VALIDATED'})
            sleep.assert_not_called()

    def test_validation_wait_is_bounded(self):
        with patch.object(central, 'status_of', return_value={'deploymentState': 'PENDING'}) as status, patch.object(central.time, 'sleep'), patch('builtins.print'):
            with self.assertRaises(RuntimeError):
                central.wait_status(ID, 'not-a-token', {'VALIDATED'})
            self.assertEqual(status.call_count, 40)

    def test_verified_bytes_from_central(self):
        with patch.object(central.urllib.request, 'urlopen', return_value=io.BytesIO(b'jar')), patch('builtins.print'):
            central.verify_central({'group/file.jar': b'jar'})
        with patch.object(central.urllib.request, 'urlopen', return_value=io.BytesIO(b'wrong')):
            with self.assertRaises(RuntimeError):
                central.verify_central({'group/file.jar': b'jar'})


if __name__ == '__main__':
    unittest.main()
