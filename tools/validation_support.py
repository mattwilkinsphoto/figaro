"""Shared, repository-only validation I/O. Never used by the Figaro runtime."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request


def sha(path):
    with Path(path).open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def access(path, recursive=False):
    """Preserve ACLs; Windows requires the explicitly selected workspace owner."""
    if os.name != 'nt':
        return
    owner = os.environ.get('FIGARO_WORKSPACE_OWNER')
    if not owner:
        raise RuntimeError('Set FIGARO_WORKSPACE_OWNER to the non-administrator workspace account')
    path = Path(path).resolve(strict=True)
    env = dict(os.environ, FIGARO_ACL_TARGET=str(path))
    script = """$ErrorActionPreference='Stop'; $p=$env:FIGARO_ACL_TARGET;
$items=@(Get-Item -LiteralPath $p);
if($env:FIGARO_ACL_RECURSE -eq '1'){$items+=@(Get-ChildItem -LiteralPath $p -Recurse -Force)};
foreach($i in $items){$acl=Get-Acl -LiteralPath $i.FullName;
$inherit=if($i.PSIsContainer){[System.Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'}else{[System.Security.AccessControl.InheritanceFlags]::None};
$rule=[System.Security.AccessControl.FileSystemAccessRule]::new($env:FIGARO_WORKSPACE_OWNER,[System.Security.AccessControl.FileSystemRights]::FullControl,$inherit,[System.Security.AccessControl.PropagationFlags]::None,[System.Security.AccessControl.AccessControlType]::Allow);
$acl.AddAccessRule($rule);Set-Acl -LiteralPath $i.FullName -AclObject $acl;
$a=@((Get-Acl -LiteralPath $i.FullName).Access|Where-Object {$_.IdentityReference.Value -ieq $env:FIGARO_WORKSPACE_OWNER -and -not $_.IsInherited});
if(@($a|Where-Object {$_.AccessControlType -eq 'Deny'}).Count -or -not @($a|Where-Object {$_.AccessControlType -eq 'Allow' -and ($_.FileSystemRights -band [System.Security.AccessControl.FileSystemRights]::FullControl) -eq [System.Security.AccessControl.FileSystemRights]::FullControl}).Count){throw "Required explicit access missing: $($i.FullName)"}}"""
    env['FIGARO_ACL_RECURSE'] = '1' if recursive else '0'
    subprocess.run(['pwsh', '-NoProfile', '-Command', script], env=env, check=True,
                   stdout=subprocess.DEVNULL)


def directory(path):
    path = Path(path)
    path.mkdir(parents=True, exist_ok=False)
    access(path)
    return path


def write(path, value):
    path = Path(path)
    with path.open('x', encoding='utf-8', newline='\n') as f:
        f.write(value if isinstance(value, str) else json.dumps(value, indent=2) + '\n')
    access(path)


def download(url, path, expected):
    path = Path(path)
    # Explicit offline input option; identical pinned bytes, never relaxed TLS.
    cache = os.environ.get('FIGARO_VALIDATION_DOWNLOAD_CACHE')
    if cache:
        data = (Path(cache)/url.rsplit('/',1)[-1]).read_bytes()
    else:
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f'Unexpected upstream bytes: {url}')
    with path.open('xb') as f:
        f.write(data)
    access(path)


def command(args, output, name, cwd=None, expected=0):
    """Always retain exact command, output and nonzero status; no retry."""
    output = Path(output)
    log = output / f'{name}.log'
    start = time.time()
    with log.open('xb') as f:
        access(log)
        result = subprocess.run(list(map(str, args)), cwd=cwd, stdout=f, stderr=subprocess.STDOUT)
    write(output/f'{name}.json', dict(command=list(map(str,args)), cwd=str(cwd),
          startedUnix=start, finishedUnix=time.time(), exitCode=result.returncode,
          logSha256=sha(log)))
    if result.returncode != expected:
        raise RuntimeError(f'{name}: exit {result.returncode}, expected {expected}; see {log}')
    return result.returncode
