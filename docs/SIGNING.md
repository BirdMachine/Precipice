# Install test builds as updates

The application ID remains `dev.birdmachine.precipice`. Distributed test APKs
must match the certificate pinned in `signing-certificate.sha256`.

The September 11 CI APK used an ephemeral Android debug key with SHA-256
`7b6a5fdc977ff524ba231d571fc6ec6ea1092dc2818398a26f7da79ab1cef838`.
The workflow did not retain its private key. The new private test key differs.
If that older APK is installed, one initial uninstall is needed unless its
original keystore is recovered. Future builds with the fixed signer can update
in place and retain app-private data.

## Local builds

Restore `.signing/precipice-test.p12` and `.signing/signing.properties` from the
private backup. They are ignored by Git. Never regenerate the key.
Use Gradle 9.6/JDK 17, then verify and install:

```bash
scripts/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk "$ANDROID_SDK_ROOT/build-tools/37.0.0"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Without the fixed signer Gradle builds an unsigned compile-check APK, which
must not be distributed as an installable test build.

## GitHub Actions

Set the repository Actions secret `PRECIPICE_TEST_SIGNING` to the contents of
the private `.signing/github-secret.json` backup: base64 PKCS12 key, alias and
passwords. Do not commit the record. With authenticated GitHub CLI:

```bash
gh secret set PRECIPICE_TEST_SIGNING --repo BirdMachine/Precipice --body-file .signing/github-secret.json
```

Pull requests compile/lint without secrets. Owner branch builds restore the
fixed key and verify the certificate before uploading. Missing credentials
fail explicitly; CI never silently generates a replacement key.
Version codes default to UTC epoch minutes in local and CI builds. Android also permits replacing the same version. If you manually override `-PprecipiceVersionCode=<number>`, keep it at least as high as the installed APK.
This is a private personal test signer, separate from any Play release key.
