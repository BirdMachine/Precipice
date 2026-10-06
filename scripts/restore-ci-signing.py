#!/usr/bin/env python3
"""Load the owner-provided Actions secret without printing credentials."""
import base64
import json
import os
from pathlib import Path

raw = os.environ.get('SIGNING_RECORD', '')
if not raw:
    raise SystemExit('PRECIPICE_TEST_SIGNING is missing. Set the backed-up signing record in repository Actions secrets. Refusing to create a new signer or upload an incompatible APK.')
record = json.loads(raw)
folder = Path('.signing')
folder.mkdir(mode=0o700, exist_ok=True)
key = folder / 'precipice-test.p12'
key.write_bytes(base64.b64decode(record['keystore'], validate=True))
key.chmod(0o600)
values = {
    'PRECIPICE_KEYSTORE': '.signing/precipice-test.p12',
    'PRECIPICE_STORE_PASSWORD': record['storePassword'],
    'PRECIPICE_KEY_PASSWORD': record['keyPassword'],
    'PRECIPICE_KEY_ALIAS': record['keyAlias'],
}
for value in values.values():
    if any(c in value for c in '\n\r\\:='):
        raise SystemExit('Signing record contains unsupported properties characters.')
path = folder / 'signing.properties'
path.write_text(''.join(f'{k}={v}\n' for k, v in values.items()))
path.chmod(0o600)
print('Persistent private signer restored. No new key was generated.')
