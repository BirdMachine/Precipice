# Install test builds as updates

Precipice development APKs use a persistent **development-only** signing identity,
following the same model as Keywi. The application ID remains
`dev.birdmachine.precipice`.

The encoded development keystore is stored at
`.github/precipice-debug.keystore.b64`. GitHub Actions reconstructs it on each
fresh runner and signs every test APK with the same certificate. This is
intentional for personal development builds: the key is not secret and must
never be reused as a Play Store or production release key.

The pinned SHA-256 certificate is recorded in `signing-certificate.sha256`.
CI verifies both that certificate and the package ID before publishing an APK.

## Updating on Android

CI version codes are `400000 + GITHUB_RUN_NUMBER`, so newer workflow builds
have monotonically increasing versions and can install over older builds signed
with this development identity.

The older September Precipice APK and the abandoned private-signer experiment
use different certificates. Either may require one final uninstall before the
first APK using this Keywi-style development identity. After that transition,
future CI APKs should update in place and retain app-private data.

## Local builds

A local signer is not required. GitHub Actions is the canonical source of
installable development APKs. Ordinary local builds may remain unsigned.

If local signing is ever useful later, reconstructing the development keystore
from the repository is possible, but it is not part of the normal workflow.

## Production signing

Do not promote this development key to a production identity. If Precipice is
ever distributed through an app store or other public release channel, create a
separate protected release key and signing configuration.
