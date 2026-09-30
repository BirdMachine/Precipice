#!/usr/bin/env bash
set -euo pipefail
apk=${1:?Pass an APK path}
build_tools=${2:?Pass the Android build-tools directory}
certificate=$("$build_tools/apksigner" verify --print-certs "$apk" | sed -nE 's/^(Signer #[0-9]+|V[0-9]+ Signer): certificate SHA-256 digest: //p' | head -1)
expected=$(tr -d '\r\n' < signing-certificate.sha256)
if [[ "$certificate" != "$expected" ]]; then
  echo 'APK certificate does not match the pinned persistent test signer.' >&2
  exit 1
fi
package=$("$build_tools/aapt" dump badging "$apk" | sed -n '1p')
[[ "$package" == *"name='dev.birdmachine.precipice'"* ]] || { echo 'Unexpected application ID' >&2; exit 1; }
echo "Verified persistent signing certificate: $certificate"
echo "$package"
