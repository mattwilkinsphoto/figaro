"""Pure in-memory release packaging tests; no temporary files."""
import hashlib
import io
import unittest
from zipfile import ZipFile
from build_release_bundle import archive_bytes, check_pom, checksums


class ReleaseBundleTests(unittest.TestCase):
    def test_deterministic_archive_and_content(self):
        payload = {'b/data': b'content', 'a': b'first'}
        packed = archive_bytes(payload)
        self.assertEqual(packed, archive_bytes(dict(reversed(list(payload.items())))))
        with ZipFile(io.BytesIO(packed)) as archive:
            self.assertEqual(archive.namelist(), ['a', 'b/data'])
            for name, data in payload.items():
                self.assertEqual(archive.read(name), data)
                self.assertEqual(archive.getinfo(name).date_time, (2026, 1, 1, 0, 0, 0))

    def test_checksums(self):
        self.assertEqual(checksums({'file': b'abc'}), (hashlib.sha256(b'abc').hexdigest()+'  file\n').encode())

    def test_unsafe_entries_refused(self):
        for name in ('/absolute', '../escape', 'path/../escape', 'path\\escape'):
            with self.assertRaises(ValueError):
                archive_bytes({name: b''})

    def test_pom_contract(self):
        pom = b'''<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>io.github.mattwilkinsphoto</groupId><artifactId>figaro_3</artifactId><version>6.1.0</version><dependencies><dependency><version>3.9.0</version></dependency></dependencies></project>'''
        check_pom(pom)
        for bad in (pom.replace(b'6.1.0', b'6.0.0'), pom.replace(b'3.9.0', b'3.9.0-SNAPSHOT')):
            with self.assertRaises(ValueError):
                check_pom(bad)


if __name__ == '__main__':
    unittest.main()
