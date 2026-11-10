#!/usr/bin/env python3
"""Rebuild the exact supported input APK with the reviewed ESP-only patch."""
import argparse
import copy
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

EXPECTED_INPUT = '701faec99b61efe8883518569ab4e741c858776b625020e055ebcefa0ac8f54e'


def run(*command, cwd=None):
    subprocess.run([str(value) for value in command], cwd=cwd, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original_apk', type=Path)
    parser.add_argument('--apktool', required=True, type=Path)
    parser.add_argument('--signer', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    options = parser.parse_args()
    original = options.original_apk.resolve()
    output = options.output.resolve()
    apktool = options.apktool.resolve()
    signer = options.signer.resolve()
    patch = Path(__file__).resolve().with_name('esp.patch')
    if hashlib.sha256(original.read_bytes()).hexdigest() != EXPECTED_INPUT:
        parser.error('Input differs from the supported Nova_V2_Demo_v2.apk; no changes applied.')
    if output.exists() or output == original:
        parser.error('Output must be a new file; the original is never replaced.')
    if not apktool.is_file() or not signer.is_file() or not patch.is_file():
        parser.error('Apktool, signer and esp.patch must exist.')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='zion-build-', dir=output.parent) as temp:
        root = Path(temp)
        decoded = root / 'decoded'
        framework = root / 'framework'
        run('java', '-jar', apktool, 'd', '-r', '-p', framework, '-o', decoded, original)
        run('patch', '--batch', '-p1', '-i', patch, cwd=decoded)
        rebuilt = root / 'rebuilt.apk'
        run('java', '-jar', apktool, 'b', '-p', framework, decoded, '-o', rebuilt)
        unsigned = root / 'Zion-unsigned.apk'
        with zipfile.ZipFile(original) as source, zipfile.ZipFile(rebuilt) as changed, zipfile.ZipFile(unsigned, 'w') as destination:
            for info in source.infolist():
                if re.fullmatch(r'META-INF/[^/]+\.(?:SF|RSA|DSA|EC)', info.filename, re.I) or info.filename == 'META-INF/MANIFEST.MF':
                    continue
                data = changed.read('classes3.dex') if info.filename == 'classes3.dex' else source.read(info.filename)
                destination.writestr(copy.copy(info), data)
        signed_dir = root / 'signed'
        run('java', '-jar', signer, '--apks', unsigned, '--out', signed_dir)
        signed_files = list(signed_dir.glob('*-aligned-debugSigned.apk'))
        if len(signed_files) != 1:
            raise RuntimeError('Expected exactly one signed APK.')
        signed = signed_files[0]
        with zipfile.ZipFile(original) as source, zipfile.ZipFile(signed) as result:
            if result.testzip() is not None:
                raise RuntimeError('APK ZIP validation failed.')
            differences = [name for name in source.namelist() if not name.startswith('META-INF/')
                           and source.read(name) != result.read(name)]
            if differences != ['classes3.dex']:
                raise RuntimeError('Unexpected payload changes: ' + repr(differences))
        with signed.open('rb') as source, output.open('xb') as destination:
            shutil.copyfileobj(source, destination)
    checksum = hashlib.sha256(output.read_bytes()).hexdigest()
    print(checksum + '  ' + output.name)
    print('Experimental build only. Android installation and gameplay have not been verified.')


if __name__ == '__main__':
    main()
