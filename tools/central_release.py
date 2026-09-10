#!/usr/bin/env python3
"""Stage/publish the approved immutable Figaro release using the Central Portal.

Runs only on Linux release runners. No private key or token is written into the
repository, receipt, artifact upload, command arguments, or log output.
"""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
from zipfile import ZipFile

from build_release_bundle import archive_bytes

REPO = 'mattwilkinsphoto/figaro'
VERSION = '6.1.0'
REVISION = '85a12fd559dee4e1cc1233036fd2bbaa06331a65'
RELEASE_SHA = 'f5e4cea326c56d18cc3243d37eb9119097aee255dde03e34ad61f83d421fd67b'
PREFIX = f'io/github/mattwilkinsphoto/figaro_3/{VERSION}/'
PURL = f'pkg:maven/io.github.mattwilkinsphoto/figaro_3@{VERSION}'
DEPLOYMENT_NAME = f'Figaro-{VERSION}-{RELEASE_SHA[:16]}'
PORTAL = 'https://central.sonatype.com/api/v1/publisher/'
CENTRAL = 'https://repo.maven.apache.org/maven2/'
SECRET_NAMES = ('GPG_PRIVATE_KEY', 'GPG_PASSPHRASE', 'GPG_FINGERPRINT',
                'MAVEN_CENTRAL_USERNAME', 'MAVEN_CENTRAL_PASSWORD')


def digest(data, algorithm='sha256'):
    return hashlib.new(algorithm, data).hexdigest()


def approved_payload(packed):
    if digest(packed) != RELEASE_SHA:
        raise ValueError('GitHub release bundle does not match the approved SHA-256')
    with ZipFile(io.BytesIO(packed)) as archive:
        names = archive.namelist()
        if len(set(names)) != len(names):
            raise ValueError('Duplicate release ZIP entries')
        record = json.loads(archive.read('release.json'))
        if (record['version'] != VERSION or record['sourceRevision'] != REVISION
                or record['candidate'] is not False or record['dirtySource'] is not False):
            raise ValueError('Release provenance mismatch')
        payload = {}
        for suffix in ('.jar', '-sources.jar', '-javadoc.jar', '.pom'):
            name = f'figaro_3-{VERSION}{suffix}'
            data = archive.read('maven/'+PREFIX+name)
            if record['artifactSha256']['maven/'+PREFIX+name] != digest(data):
                raise ValueError('Release payload digest mismatch')
            payload[PREFIX+name] = data
    check_metadata(payload[PREFIX+f'figaro_3-{VERSION}.pom'])
    return payload


def check_metadata(data):
    root = ET.fromstring(data)
    ns = {'p': 'http://maven.apache.org/POM/4.0.0'}
    expected = {'groupId': 'io.github.mattwilkinsphoto', 'artifactId': 'figaro_3', 'version': VERSION}
    for key, value in expected.items():
        if root.findtext('p:'+key, namespaces=ns) != value:
            raise ValueError('POM coordinates mismatch')
    for path in ('name', 'description', 'url', 'licenses/license/name',
                 'licenses/license/url', 'developers/developer/name',
                 'scm/url', 'scm/connection'):
        if not root.findtext('/'.join('p:'+part for part in path.split('/')), namespaces=ns):
            raise ValueError('Missing Central POM metadata: '+path)


def signed_payload(payload, sign):
    result = {}
    for name, data in sorted(payload.items()):
        result[name] = data
        signature = sign(name, data)
        if not signature.startswith(b'-----BEGIN PGP SIGNATURE-----'):
            raise ValueError('Missing detached signature')
        result[name+'.asc'] = signature
        for algorithm in ('md5', 'sha1', 'sha256', 'sha512'):
            result[name+'.'+algorithm] = (digest(data, algorithm)+'\n').encode('ascii')
    return result


def command(args, input_data=None, env=None):
    result = subprocess.run(args, input=input_data, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, env=env, check=False)
    if result.returncode:
        # Never echo child diagnostics or arguments: this helper also signs keys.
        raise RuntimeError(Path(args[0]).name+' failed; private diagnostics suppressed')
    return result.stdout


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError('Authenticated Portal redirect refused')


def portal_request(path, token, body=b'', content_type=None):
    headers = {'Authorization': 'Bearer '+token}
    if content_type:
        headers['Content-Type'] = content_type
    request = urllib.request.Request(PORTAL+path, data=body, headers=headers, method='POST')
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=90) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'Central Portal HTTP {error.code}; inspect Portal deployment details') from None


def deployment_id(value):
    if not re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', value):
        raise ValueError('Invalid deployment ID')
    return value


def check_deployment(status, expected_id):
    if status.get('deploymentId') != expected_id or status.get('deploymentName') != DEPLOYMENT_NAME:
        raise ValueError('Deployment identity mismatch')
    if status.get('deploymentState') in ('VALIDATED', 'PUBLISHING', 'PUBLISHED'):
        if set(status.get('purls', [])) != {PURL}:
            raise ValueError('Deployment coordinates mismatch')


def status_of(identifier, token):
    value = json.loads(portal_request('status?id='+deployment_id(identifier), token))
    check_deployment(value, identifier)
    return value


def wait_status(identifier, token, desired):
    for _ in range(40):
        value = status_of(identifier, token)
        state = value.get('deploymentState')
        print('Central deployment state: '+str(state), flush=True)
        if state in desired:
            return value
        if state == 'FAILED':
            print(json.dumps(value.get('errors', {})), flush=True)
            raise RuntimeError('Central validation failed; deployment retained for inspection')
        if state not in ('PENDING', 'VALIDATING', 'VALIDATED', 'PUBLISHING'):
            raise RuntimeError('Unexpected Central deployment state')
        time.sleep(15)
    raise RuntimeError('Central polling timed out; inspect existing deployment, do not blindly re-upload')


def verify_central(payload):
    for attempt in range(40):
        unavailable = False
        for name, expected in payload.items():
            try:
                with urllib.request.urlopen(CENTRAL+name, timeout=30) as response:
                    actual = response.read()
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    unavailable = True
                    break
                raise
            if digest(actual) != digest(expected):
                raise RuntimeError('Central artifact differs from the approved GitHub release')
        if not unavailable:
            print('All four Central artifacts match the original release bytes.', flush=True)
            return
        print('Waiting for Central artifact availability...', flush=True)
        time.sleep(15)
    raise RuntimeError('Central propagation timeout; publication may already be complete')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('stage', 'publish', 'verify'))
    parser.add_argument('--deployment', default='')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if os.name != 'posix':
        raise RuntimeError('Use the Linux GitHub release runner; do not handle production keys here')
    secrets = {key: os.environ.pop(key, '') for key in SECRET_NAMES}
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    release = json.loads(command(['gh', 'api', f'repos/{REPO}/releases/tags/v{VERSION}']))
    if release['draft'] or release['prerelease']:
        raise RuntimeError('A final GitHub release is required')
    runs = json.loads(command(['gh', 'run', 'list', '--repo', REPO, '--commit', REVISION,
                              '--workflow', 'ci.yml', '--json', 'conclusion,headBranch,event']))
    if not any(r['conclusion'] == 'success' and r['headBranch'] == 'main' and r['event'] == 'push' for r in runs):
        raise RuntimeError('Missing successful main CI for approved release source')
    command(['gh', 'release', 'download', 'v'+VERSION, '--repo', REPO,
             '--pattern', f'figaro-{VERSION}-maven.zip', '--dir', str(output)])
    payload = approved_payload((output/f'figaro-{VERSION}-maven.zip').read_bytes())
    if args.mode == 'verify':
        verify_central(payload)
        return
    if not secrets['MAVEN_CENTRAL_USERNAME'] or not secrets['MAVEN_CENTRAL_PASSWORD']:
        raise RuntimeError('Missing Central credentials')
    token = base64.b64encode((secrets['MAVEN_CENTRAL_USERNAME']+':'+secrets['MAVEN_CENTRAL_PASSWORD']).encode()).decode()
    identifier = args.deployment
    if args.mode == 'stage':
        fingerprint = secrets['GPG_FINGERPRINT'].strip()
        if not re.fullmatch('[0-9A-F]{40}', fingerprint) or not secrets['GPG_PRIVATE_KEY'] or not secrets['GPG_PASSPHRASE']:
            raise RuntimeError('Missing or malformed signing configuration')
        with tempfile.TemporaryDirectory(prefix='figaro-gpg-') as temporary:
            home = Path(temporary)
            home.chmod(0o700)
            gpg = ['gpg', '--homedir', str(home), '--batch', '--no-tty']
            try:
                command(gpg+['--import'], secrets['GPG_PRIVATE_KEY'].encode())
                listing = command(gpg+['--with-colons', '--list-secret-keys']).decode()
                primaries = [line for line in listing.splitlines() if line.startswith('sec:')]
                fingerprints = [line.split(':')[9] for line in listing.splitlines() if line.startswith('fpr:')]
                if len(primaries) != 1 or not fingerprints or fingerprints[0] != fingerprint:
                    raise RuntimeError('Imported signing identity does not match expected fingerprint')
                def sign(name, data):
                    path = home/Path(name).name
                    path.write_bytes(data)
                    command(gpg+['--pinentry-mode', 'loopback', '--passphrase-fd', '0',
                                 '--local-user', fingerprint+'!', '--armor', '--detach-sign', str(path)],
                            (secrets['GPG_PASSPHRASE']+'\n').encode())
                    command(gpg+['--verify', str(path)+'.asc', str(path)])
                    return Path(str(path)+'.asc').read_bytes()
                signed = signed_payload(payload, sign)
            finally:
                subprocess.run(['gpgconf', '--homedir', str(home), '--kill', 'all'],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        packed = archive_bytes(signed)
        (output/'central-bundle.zip').write_bytes(packed)
        boundary = 'figaro-'+uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="bundle"; filename="central-bundle.zip"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode()
                + packed + f'\r\n--{boundary}--\r\n'.encode())
        # Never auto-retry an upload: an uncertain response may have created a deployment.
        response = portal_request('upload?'+urllib.parse.urlencode({'name': DEPLOYMENT_NAME, 'publishingType': 'USER_MANAGED'}),
                                  token, body, 'multipart/form-data; boundary='+boundary)
        identifier = deployment_id(response.decode().strip())
        receipt = {'deploymentId': identifier, 'version': VERSION, 'sourceRevision': REVISION,
                   'releaseBundleSha256': RELEASE_SHA, 'centralBundleSha256': digest(packed)}
        (output/'deployment.json').write_text(json.dumps(receipt, indent=2)+'\n')
        print('Deployment ID: '+identifier, flush=True)
        wait_status(identifier, token, {'VALIDATED'})
        print('Validated; NOT published. Use the separate publish action after review.', flush=True)
    else:
        current = status_of(deployment_id(identifier), token)
        if current['deploymentState'] == 'VALIDATED':
            portal_request('deployment/'+identifier, token)
        elif current['deploymentState'] not in ('PUBLISHING', 'PUBLISHED'):
            raise RuntimeError('Only the validated approved deployment may be published')
        wait_status(identifier, token, {'PUBLISHED'})
        verify_central(payload)


if __name__ == '__main__':
    main()
