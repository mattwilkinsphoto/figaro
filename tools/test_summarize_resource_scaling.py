import csv
import io
import unittest
import summarize_resource_scaling as study


def fixture(smoke=False):
    rows=[]
    for i,case in enumerate(study.schedule(smoke)):
        kind,variant,draws,workers,jobs,r,mode=case
        count=1 if jobs=='single' else 2
        row={f:'0' for f in study.FIELDS}
        row.update(dict(zip(('kind','variant','draws','workers','jobs','round','mode'),map(str,case))))
        row.update(caseIndex=str(i),revision='a'*40,runtimeHash='b'*64,resourceStudy='row',
                   osPeakSource='WindowsPeakWorkingSet',osPeakBytes='100',seed=str(420013+7919*r),
                   wallSeconds='1',warmup0Seconds='2',warmup1Seconds='1',gcBeforeObserved='true',gcAfterObserved='true',
                   storedValues=str(count*4*draws*(1 if kind=='graphWide' else 8 if kind=='likelihood8' else 32)),
                   evaluations='-1' if kind=='graphWide' else str(count*draws*10),
                   fingerprints=';'.join(['c'*64]*count),recordingHash='none' if mode=='plain' else 'd'*64)
        for f in ('constructionSeconds','samplingSeconds','diagnosticsSeconds'):
            row[f]='NaN' if kind=='graphWide' else '0.1'
        for f in study.BASE[25:]: row[f]='-1' if mode=='plain' else '0'
        if mode=='profile': row['allocationSamples']='100'
        rows.append(row)
    return rows


def encode(rows):
    stream=io.StringIO(); writer=csv.DictWriter(stream,study.FIELDS)
    writer.writeheader(); writer.writerows(rows); return stream.getvalue()


class ResourceStudyTest(unittest.TestCase):
    def test_complete_grids(self):
        self.assertEqual(len(study.load(encode(fixture()))),108)
        self.assertEqual(len(study.load(encode(fixture(True)),True)),4)

    def test_missing_duplicate_and_reordered_cases(self):
        rows=fixture()
        for candidate in (rows[:-1],rows+[rows[0]],rows[1:]+rows[:1]):
            with self.assertRaises(ValueError): study.load(encode(candidate))

    def test_invalid_fields_and_private_metadata(self):
        for field,value in (('runtimeHash','local-user/project'),('osPeakSource','local-path'),
                            ('wallSeconds','NaN'),('cpuSeconds','inf'),('heapRetainedBytes','-1'),
                            ('storedValues','1'),('evaluations','0'),('gcBeforeObserved','unknown'),
                            ('fingerprints','c'*63),('recordingHash','x'),('callbackWeight','0')):
            rows=fixture(); rows[0][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError): study.load(encode(rows))

    def test_result_and_work_mismatch(self):
        for field,value in (('fingerprints','e'*64),('evaluations','40001')):
            rows=fixture(); rows[1][field]=value
            with self.assertRaises(ValueError): study.load(encode(rows))

    def test_profile_loss_and_absence(self):
        for field,value in (('allocationSamples','0'),('lostBytes','1'),('truncatedSamples','101'),('vectorWeight','-1')):
            rows=fixture(); next(row for row in rows if row['mode']=='profile')[field]=value
            with self.assertRaises(ValueError): study.load(encode(rows))

    def test_unavailable_counters_not_zero(self):
        rows=fixture()
        for row in rows: row.update(osPeakSource='unavailable',osPeakBytes='-1',cpuSeconds='NaN',gcAfterObserved='false')
        study.load(encode(rows))
        rows[0]['osPeakBytes']='0'
        with self.assertRaises(ValueError): study.load(encode(rows))

    def test_log_requires_matching_warmups(self):
        row=fixture(True)[0]
        stream=io.StringIO(); writer=csv.DictWriter(stream,study.BASE,quoting=csv.QUOTE_ALL)
        writer.writeheader(); writer.writerow({f:row[f] for f in study.BASE})
        text=stream.getvalue()+f'resourceWarmup,-2,2,{row["fingerprints"]}\nresourceWarmup,-1,1,{row["fingerprints"]}\nRESOURCE_READY\n'
        self.assertEqual(study.parse_log(text)[1],['2','1'])
        with self.assertRaises(ValueError): study.parse_log(text.replace('resourceWarmup,-1,1,c','resourceWarmup,-1,1,e'))
        with self.assertRaises(ValueError): study.parse_log(text.replace('RESOURCE_READY',''))


if __name__=='__main__': unittest.main()
