"""GVM-05 experiment-only output helpers; never imported by the library."""
import hashlib
import json
import os
from pathlib import Path
import subprocess


def grant(path):
    if os.name == 'nt':
        helper = os.environ.get('FIGARO_ACCESS_HELPER')
        if not helper:
            raise RuntimeError('Set FIGARO_ACCESS_HELPER for explicit workspace access grants')
        subprocess.run(['pwsh', '-NoProfile', '-File', helper, '-Paths', str(Path(path).resolve())],
                       check=True, stdout=subprocess.DEVNULL)


def save(path, obj):
    path = Path(path)
    body = json.dumps(obj, indent=2, allow_nan=False)
    with path.open('x', encoding='utf-8') as stream:
        grant(path)
        stream.write(body)


def text_file(path, text):
    path = Path(path)
    with path.open('x', encoding='utf-8') as stream:
        grant(path)
        stream.write(text)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()
