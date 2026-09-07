import io
import unittest
from zipfile import ZipFile
from check_acceptance_artifacts import FAT_LICENSE, REQUIRED, validate


class AcceptanceArtifactsTest(unittest.TestCase):
    def check(self, entries, kind='thin'):
        stream=io.BytesIO()
        with ZipFile(stream,'w') as archive:
            for name,data in entries: archive.writestr(name,data)
        with ZipFile(stream) as archive: return validate(archive,kind,{'META-INF/LICENSE':b'license\n'})

    def base(self):
        return [('META-INF/LICENSE',b'license\r\n')]+[(name,b'\xca\xfe\xba\xbe\0\0\0=') for name in REQUIRED]

    def test_valid_thin_and_fat(self):
        for kind in ('thin','fat'): self.check(self.base(),kind)

    def test_legal_missing_or_changed(self):
        with self.assertRaises(ValueError): self.check(self.base()[1:])
        with self.assertRaises(ValueError): self.check([('META-INF/LICENSE',b'changed')]+self.base()[1:])

    def test_missing_runtime_and_leakage(self):
        with self.assertRaises(ValueError): self.check(self.base()[:-1])
        for prefix in ('org/scalatest/','scoverage/','com/cra/figaro/example/','scala/'):
            with self.assertRaises(ValueError): self.check(self.base()+[(prefix+'Leak.class',b'')])

    def test_assembly_license_requires_figaro_content(self):
        renamed = [(FAT_LICENSE,b'license\n')]+self.base()[1:]
        self.check(renamed,'fat')
        with self.assertRaises(ValueError): self.check(renamed,'thin')
        with self.assertRaises(ValueError): self.check([(FAT_LICENSE,b'changed')]+self.base()[1:],'fat')
        with self.assertRaises(ValueError): self.check([('META-INF/LICENSE_commons-math3-3.6.1.txt',b'license\n')]+self.base()[1:],'fat')
        with self.assertRaises(ValueError): self.check(self.base()+[(FAT_LICENSE,b'changed')],'fat')

    def test_incompatible_bytecode(self):
        with self.assertRaises(ValueError): self.check(self.base()+[('New.class',b'\xca\xfe\xba\xbe\0\0\0B')])
        with self.assertRaises(ValueError): self.check(self.base()+[('Covered.class',b'\xca\xfe\xba\xbe\0\0\0=scoverage/Invoker$')])

    def test_fat_parallel_collections_not_core_runtime(self):
        data = b'\xca\xfe\xba\xbe\0\0\0='
        self.check(self.base()+[('scala/collection/Parallel.class',data)],'fat')
        for name in ('scala/runtime/BoxesRunTime.class','scala/Predef$.class','scala/Option.class'):
            with self.assertRaises(ValueError): self.check(self.base()+[(name,data)],'fat')

    def test_classifiers(self):
        self.check(self.base()+[('VectorSliceSampler.scala',b'source')],'sources')
        self.check(self.base()+[('index.html',b'html')],'javadoc')
        for kind in ('sources','javadoc','bad'):
            with self.assertRaises(ValueError): self.check(self.base(),kind)


if __name__=='__main__': unittest.main()
